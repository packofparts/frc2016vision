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
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
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
	private Mat hlsImage;
	private Mat maskImage;
	private Mat hierarchy;
	
	private MatOfPoint targetContour;
	
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
		hlsImage = new Mat();
		maskImage = new Mat();
		hierarchy = new Mat();
		
		//setCameraManualExposure();
		//setCameraAbsoluteExposure();
		setCameraAutoExposure();
		setCameraBrightness();
		
		targetContour = loadTargetContour();
		
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
			hlsImage.release();
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
		boolean captureFrame = false;
		if (visionTable.isCaptureNextFrame()) {
			captureFrame = true;
		}
		
		// capture original image
		if (captureFrame) {
			String filename = String.format("%s/%d_original.jpg", System.getProperty("user.dir"), startTime);
			saveImage(filename, originalImage);
		}
		
		// convert it to HLS
		Imgproc.cvtColor(originalImage, hlsImage, Imgproc.COLOR_BGR2HLS);
		
		// mask out only those pixels in the HSL range
		Core.inRange(
				hlsImage, 
				new Scalar(visionTable.getThresholdLowH(), visionTable.getThresholdLowL(), visionTable.getThresholdLowS()),
				new Scalar(visionTable.getThresholdHighH(), visionTable.getThresholdHighL(), visionTable.getThresholdHighS()),
				maskImage);

		// if toggled, display the mask as the original image
		if (visionTable.isDisplayMask()) {
			maskImage.copyTo(originalImage, maskImage);
		}
		
		// capture masked image
		if (captureFrame) {
			String filename = String.format("%s/%d_masked.jpg", System.getProperty("user.dir"), startTime);
			saveImage(filename, maskImage);
		}
		
		// find all the contours
		List<MatOfPoint> contours = new ArrayList<>();	
		Imgproc.findContours(maskImage, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
		
		// draw all the contours
		Imgproc.drawContours(originalImage, contours, -1, color_gray);
		
		// filter out the contours that are not the right size
		contours = contours.stream()
				.filter(c -> isContourAppropriateSize(c))
				.collect(Collectors.toList());
		
		// draw the filtered contours
		Imgproc.drawContours(originalImage, contours, -1, color_yellow);
		
		// pick the best contour
		Optional<MatOfPoint> bestMatch = contours.stream()
				.min(Comparator.comparing(c -> matchShapes(c)));
		
		if (bestMatch.isPresent()) {
			Rect boundingRect = Imgproc.boundingRect(bestMatch.get());
			
			// find the midpoint of the bounding rect
			Point midpoint = new Point();
			midpoint.x = (boundingRect.tl().x + boundingRect.br().x) / 2;
			midpoint.y = (boundingRect.tl().y + boundingRect.br().y) / 2;
		
			// draw the contour and the midpoint
			Imgproc.drawContours(originalImage, Arrays.asList(bestMatch.get()), -1, color_red);
			Imgproc.circle(originalImage, midpoint, 6, color_red, 3);
			
			// update NetworkTables
			visionTable.setTargetAcquired(true);
			visionTable.setTargetX((int)midpoint.x);
			visionTable.setTargetY((int)midpoint.y);
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
		
		// capture final image
		if (captureFrame) {
			visionTable.setCaptureNextFrame(false);
			String filename = String.format("%s/%d_marked.jpg", System.getProperty("user.dir"), startTime);
			saveImage(filename, originalImage);
		}
		
	}

	private MatOfPoint loadTargetContour() {
		try {
			byte[] targetBytes = IOUtils.toByteArray(getClass().getResourceAsStream("/target.jpg"));
			Mat targetImage = Imgcodecs.imdecode(new MatOfByte(targetBytes), Imgcodecs.IMREAD_UNCHANGED);
			Mat targetHslImage = new Mat();
			// convert it to HSL
			Imgproc.cvtColor(targetImage, targetHslImage, Imgproc.COLOR_BGR2HLS);
			// mask out only those pixels in the HSL range
			Mat targetMaskImage = new Mat();
			Core.inRange(
					targetHslImage, 
					new Scalar(0,100,0),
					new Scalar(255,255,255),
					targetMaskImage);
			Mat h = new Mat();
			List<MatOfPoint> targetContours = new ArrayList<MatOfPoint>();
			Imgproc.findContours(targetMaskImage, targetContours, h, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
			return targetContours.get(0);
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}
	
	private boolean isContourAppropriateSize(MatOfPoint contour) {
		Rect boundingRect = Imgproc.boundingRect(contour);
		if (boundingRect.width < 50)
			return false;
		if (boundingRect.height < 10)
			return false;
		if (boundingRect.width > 500)
			return false;
		if (boundingRect.height > 300)
			return false;
		
		return true;
	}
	
	private double matchShapes(MatOfPoint contour) {
		double match = Imgproc.matchShapes(targetContour, contour, Imgproc.CV_CONTOURS_MATCH_I1, 0);
		return match;
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
	
	private void setCameraAutoExposure() {
		try {
			Runtime.getRuntime().exec("/usr/bin/v4l2-ctl --set-ctrl exposure_auto=3");
		} catch (Exception ex) {
			LOG.error("Could not set auto exposure", ex);
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