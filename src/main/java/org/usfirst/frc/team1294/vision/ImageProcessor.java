package org.usfirst.frc.team1294.vision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageProcessor implements Runnable {
	

	private static final Logger LOG = LoggerFactory.getLogger(ImageProcessor.class);
	
	private AtomicReference<byte[]> lastImage = new AtomicReference<byte[]>();
	private VisionNetworkTable visionTable;

	private VideoCapture capture;
	private Mat originalImage;
	private Mat hslImage;
	private Mat maskImage;
	private Mat hierarchy;
	
	private ScheduledExecutorService scheduler;
	private long lastRun;
	
	Scalar color_gray = new Scalar(160,160,160);
	Scalar color_white = new Scalar(255,255,255);
	Scalar color_red = new Scalar(0,0,255);
	Scalar color_yellow = new Scalar(0,255,255);
	
	public ImageProcessor(int webcam, VisionNetworkTable visionTable) {
		capture = new VideoCapture(webcam);
		capture.set(Videoio.CAP_PROP_FRAME_WIDTH, 640); // width
		capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480); // height
		
		this.visionTable = visionTable;
		this.scheduler = Executors.newScheduledThreadPool(1);
		
		originalImage = new Mat();
		hslImage = new Mat();
		maskImage = new Mat();
		hierarchy = new Mat();
		
		setCameraManualExposure();
		setCameraAbsoluteExposure();
		setCameraBrightness();
		
		System.out.println("ImageProcesser constructor done");
	}
	
	public void start() {
		long delay = (long) (1 / (double)visionTable.getFPS() * 1000);
		if (delay < 0) delay = 0;
		scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
		this.lastRun = System.currentTimeMillis();
	}
	
	@Override
	public void run() {
		try {
			// grab a frame from the webcam
			captureImage();
			// process Image
			processImage();
			
			originalImage.release();
			hslImage.release();
			maskImage.release();
			hierarchy.release();
		} catch (Exception e) {
			LOG.error("Error while processing image", e);
		}
		
		// schedule the next run
		long delay = (long) (1 / (double)visionTable.getFPS() * 1000) - (System.currentTimeMillis() - this.lastRun);
		if (delay < 0) delay = 0;
		scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
		this.lastRun = System.currentTimeMillis();
	}
	
	public void processImage() {
		long startTime = System.currentTimeMillis();
		
		if (visionTable.isCaptureNextFrame()) {
			String filename = String.format("%s/original_%d.jpg", System.getProperty("user.dir"), startTime);
			saveImage(filename, originalImage);
		}
		
		// convert it to HSL
		Imgproc.cvtColor(originalImage, hslImage, Imgproc.COLOR_BGR2HLS);
		
		// mask out only those pixels in the HSL range
		Core.inRange(
				hslImage, 
				new Scalar(visionTable.getThresholdLowH(), visionTable.getThresholdLowL(), visionTable.getThresholdLowS()),
				new Scalar(visionTable.getThresholdHighH(), visionTable.getThresholdHighL(), visionTable.getThresholdHighS()),
				maskImage);
		
		if (visionTable.isCaptureNextFrame()) {
			String filename = String.format("%s/mask_%d.jpg", System.getProperty("user.dir"), startTime);
			saveImage(filename, maskImage);
		}
		
		// find all the contours
		List<MatOfPoint> contours = new ArrayList<>();
		if (visionTable.isDisplayMask()) {
			maskImage.copyTo(originalImage, maskImage);
		}
		Imgproc.findContours(maskImage, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
		
		//find the target contour TODO: much more logic needed here
		Optional<MatOfPoint> targetContour = contours.stream()
				.filter((c) -> {
					Rect r = Imgproc.boundingRect(c);
					return (r.width > r.height);
				})
				.filter(c -> shapeMatches(c))
				.max(Comparator.comparing(c -> Imgproc.contourArea(c)));
		
		if (targetContour.isPresent()) {
			Rect boundingRect = Imgproc.boundingRect(targetContour.get());
			
			// find upper left and upper right points on the contour
			double[] pRectUpperLeft = new double[2];
			pRectUpperLeft[0] = boundingRect.x;
			pRectUpperLeft[1] = boundingRect.y;
			
			double[] pRectUpperRight = new double[2];
			pRectUpperRight[0] = boundingRect.x + boundingRect.width;
			pRectUpperRight[1] = boundingRect.y;
			
			double[] pUpperLeft = null;
			double[] pUpperRight = null;
			double minDistanceUpperLeft = Double.MAX_VALUE;
			double minDistanceUpperRight = Double.MAX_VALUE;
			
			for(int col=0;col<targetContour.get().cols();col++) {
				for (int row=0;row<targetContour.get().rows();row++) {
					double[] p = targetContour.get().get(row, col);
					
					double dUpperLeft = distance(p, pRectUpperLeft);
					if (dUpperLeft < minDistanceUpperLeft) {
						minDistanceUpperLeft = dUpperLeft;
						pUpperLeft = p;
					}
					
					double dUpperRight = distance(p, pRectUpperRight);
					if (dUpperRight < minDistanceUpperRight) {
						minDistanceUpperRight = dUpperRight;
						pUpperRight = p;
					}
				}
			}
			
			// find the midpoint between the upper left and right points on the contour
			double[] pMidpoint = new double[2];
			pMidpoint[0] = (pUpperLeft[0] + pUpperRight[0]) / 2;
			pMidpoint[1] = (pUpperLeft[1] + pUpperRight[1]) / 2;
		
			// draw the contours, target rect, midpoint, etc
			Imgproc.drawContours(originalImage, Arrays.asList(targetContour.get()), -1, color_yellow);
			Imgproc.rectangle(originalImage, boundingRect.tl(), boundingRect.br(), color_gray);
			Imgproc.circle(originalImage, new Point(pUpperLeft), 2, color_white, -1);
			Imgproc.circle(originalImage, new Point(pUpperRight), 2, color_white, -1);
			Imgproc.circle(originalImage, new Point(pMidpoint), 6, color_red, 3);
			
			if (visionTable.isCaptureNextFrame()) {
				visionTable.setCaptureNextFrame(false);
				String filename = String.format("%s/marked_%d.jpg", System.getProperty("user.dir"), startTime);
				saveImage(filename, originalImage);
			}
			
			// update NetworkTables
			visionTable.setTargetAcquired(true);
			visionTable.setTargetX((int)pMidpoint[0]);
			visionTable.setTargetY((int)pMidpoint[1]);
			visionTable.setLastUpdated(System.currentTimeMillis());
		} else {
			// update NetworkTables
			visionTable.setTargetAcquired(false);
			visionTable.setTargetX(0);
			visionTable.setTargetY(0);
			visionTable.setLastUpdated(System.currentTimeMillis());
		}

		
		// encode it as jpeg
		MatOfByte m = new MatOfByte();
		MatOfInt parameters = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, visionTable.getQuality());
		Imgcodecs.imencode(".jpg", originalImage, m, parameters);
		lastImage.set(m.toArray());
		
		
	}

	private boolean shapeMatches(MatOfPoint contour) {
		// http://docs.opencv.org/3.1.0/d3/dc0/group__imgproc__shape.html#gaadc90cb16e2362c9bd6e7363e6e4c317
//		MatOfPoint perfectContour = null; // TODO load this with the shape we are looking for
//		double percentMatch = Imgproc.matchShapes(perfectContour, contour, Imgproc.CV_CONTOURS_MATCH_I3, 0);
//		return percentMatch > 0.75;
		return true;
	}

	public void captureImage() {
		setCameraAbsoluteExposure();
		setCameraBrightness();
		capture.read(originalImage);
	}
	
	public void loadImage(String filename) {
		originalImage = Imgcodecs.imread(filename);
	}
	
	public void saveImage(String filename) {
		saveImage(filename, originalImage);
	}
	
	private void saveImage(String filename, Mat image) {
		Imgcodecs.imwrite(filename, image);
	}
	
	private static double distance(double[] p1, double[] p2) {
		  double dx = Math.abs(p1[0] - p2[0]);
		  double dy = Math.abs(p1[1] - p2[1]);
		  return Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
	  }

	public byte[] getLatestImage() {
		return lastImage.get();
	}

	private int brightness;
	private void setCameraBrightness() {
		if (brightness != visionTable.getBrightness()) {
			brightness = visionTable.getBrightness();
			try {
				Runtime.getRuntime().exec("/usr/bin/v4l2-ctl --set-ctrl brightness=" + brightness);
			} catch (Exception ex) {
				LOG.error("Could not adjust brightness", ex);
			}
		}
	}
	
	private void setCameraManualExposure() {
		try {
			Runtime.getRuntime().exec("/usr/bin/v4l2-ctl --set-ctrl exposure_auto=1");
		} catch (Exception ex) {
			LOG.error("Could not set manual exposure", ex);
		}
	}
	
	private int absoluteExposure;
	private void setCameraAbsoluteExposure() {
		if (absoluteExposure != visionTable.getAbsoluteExposure()) {
			absoluteExposure = visionTable.getAbsoluteExposure();
			try {
				Runtime.getRuntime().exec("/usr/bin/v4l2-ctl --set-ctrl exposure_absolute=" + absoluteExposure);
			} catch (Exception ex) {
				LOG.error("Could not set manual exposure", ex);
			}
		}
	}
	
}