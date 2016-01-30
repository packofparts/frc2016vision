package org.usfirst.frc.team1294.vision;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Application {

	
	
	public static void main(String[] args) throws Exception {
		
		
		int webcam = 0;
		int cameraPort = 8080;
		
		displayBanner();
		
		loadNativeHackedStandardCPPLibraries();
		loadNativeOpenCvLibraries();
		
		VisionNetworkTable visionTable = new VisionNetworkTable();
		ImageProcessor imageProcessor = new ImageProcessor(webcam, visionTable);
		imageProcessor.start();
		
		MJpegStreamer vs = new MJpegStreamer(cameraPort, imageProcessor, visionTable);
		vs.start();
        
		commandLineInterface(visionTable);
	}

	private static void commandLineInterface(VisionNetworkTable visionTable) {
		while (true) {
			System.out.println(String.format("LH:%d LS:%d LL:%d HH:%d HS:%d HL:%d Quality:(%d) Fps:(%d) Mask:(%b) Brightness:(%d) Exposure:(%d)", 
					visionTable.getThresholdLowH(),
					visionTable.getThresholdLowS(),
					visionTable.getThresholdLowL(),
					visionTable.getThresholdHighH(),
					visionTable.getThresholdHighS(),
					visionTable.getThresholdHighL(),
					visionTable.getQuality(),
					visionTable.getFPS(),
					visionTable.isDisplayMask(),
					visionTable.getBrightness(),
					visionTable.getAbsoluteExposure()));
					
			switch (System.console().readLine().toUpperCase()) {
			case "M": {
				visionTable.setDisplayMask(!visionTable.isDisplayMask());
				break;
			}
			case "E": {
				try {
				visionTable.setAbsoluteExposure(Integer.parseInt(System.console().readLine("New Absolute Exposure Value: ")));
				} catch (Exception ex) {
					break;
				}
			}
			case "B": {
				try {
					visionTable.setBrightness(Integer.parseInt(System.console().readLine("New brightness value: ")));
				} catch (Exception ex) {
					break;
				}
				break;
			}
			case "LH": {
				try {
					visionTable.setThresholdLowH(Double.parseDouble(System.console().readLine("New Low Threshold Hue value: ")));
				} catch (Exception ex) {
					break;
				}
				break;
			}
			case "LS": {
				try {
					visionTable.setThresholdLowS(Double.parseDouble(System.console().readLine("New Low Threshold Saturation value: ")));
				
				} catch (Exception ex) {
				break;
				}
			}
			case "LL": {
				try {
					visionTable.setThresholdLowL(Double.parseDouble(System.console().readLine("New Low Threshold Luminance value: ")));
				} catch (Exception ex) {
				break;
				}
			}
			case "HH": {
				try {
					visionTable.setThresholdHighH(Double.parseDouble(System.console().readLine("New High Threshold Hue value: ")));
				} catch (Exception ex) {
				break;
				}
			}
			case "HS": {
				try {
					visionTable.setThresholdHighS(Double.parseDouble(System.console().readLine("New High Threshold Saturation Value:")));
				} catch (Exception ex) {	
					break;
				}
			}
			case "HL": {
				try {
					visionTable.setThresholdHighL(Double.parseDouble(System.console().readLine("New High Threshold Luminance Value:")));
				} catch (Exception ex) {
 				break;
			}
			}
			case "Q":
				// TODO
				break;
			case "F":
				// TODO
				break;
			case " ":
				visionTable.setCaptureNextFrame(true);
				break;
			}
		}
	}

	private static void displayBanner() throws IOException {
		InputStream is = Application.class.getResourceAsStream("/Banner.txt");
		if (is == null) {	        	
        	if (new File("./" + "Banner.txt").exists()) {
        		is = new FileInputStream("./" + "Banner.txt");
        	} else if (new File("./src/main/resources/" + "Banner.txt").exists()) {
        		is = new FileInputStream("./src/main/resources/" + "Banner.txt");
        	}
        }
		java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
	    String banner = s.hasNext() ? s.next() : "";
	    is.close();
	    System.out.println(banner);
	    
	    System.out.println("FRC 1294 Top Gun - 2016 Vision System\n");
	    
	    VersionInformation vi = new VersionInformation();
		System.out.println("Version: " + vi.getVersion());
		System.out.println("Author: " + vi.getAuthor());
		
		System.out.println("\n");
	}
	
	public static void loadNativeOpenCvLibraries() {
		try {
	        String osname = System.getProperty("os.name");
	        String resname = "/opencv/";
	        if (osname.startsWith("Windows"))
	          resname += "Windows/" + System.getProperty("os.arch") + "/";
	        else
	          resname += osname + "/" + System.getProperty("os.arch") + "/";
	        
	        if (System.getProperty("os.name").startsWith("Linux") && new File("/usr/lib/arm-linux-gnueabihf").exists()) {
	        	resname += "hf/";
	        }
	        System.out.println(resname);
	        if (osname.startsWith("Windows"))
	          resname += "opencv_java310.dll";
	        else if (osname.startsWith("Mac"))
	          resname += "libopencv_java310.jnilib";
	        else
	          resname += "libopencv_java310.so";
	        
	        InputStream is = Application.class.getResourceAsStream(resname);
	        if (is == null) {	        	
	        	if (new File("./" + resname).exists()) {
	        		is = new FileInputStream("./" + resname);
	        	} else if (new File("./src/main/resources/" + resname).exists()) {
	        		is = new FileInputStream("./src/main/resources/" + resname);
	        	}
	        }
	        
	        File jniLibrary;
	        if (is != null) {
	          // create temporary file
	          if (System.getProperty("os.name").startsWith("Windows"))
	            jniLibrary = File.createTempFile("opencv_java310", ".dll");
	          else if (System.getProperty("os.name").startsWith("Mac"))
	            jniLibrary = File.createTempFile("opencv_java310", ".dylib");
	          else
	            jniLibrary = File.createTempFile("opencv_java310", ".so");
	          // flag for delete on exit
	          jniLibrary.deleteOnExit();
	          OutputStream os = new FileOutputStream(jniLibrary);

	          byte[] buffer = new byte[1024];
	          int readBytes;
	          try {
	            while ((readBytes = is.read(buffer)) != -1) {
	              os.write(buffer, 0, readBytes);
	            }
	          } finally {
	            os.close();
	            is.close();
	          }

	          System.load(jniLibrary.getAbsolutePath());
	        } else {
	          System.loadLibrary("opencv_java310");
	        }
	        System.out.println("Successfully loaded opencv native libraries.");
	      } catch (IOException ex) {
	        ex.printStackTrace();
	        System.exit(1);
	      }
	}
	
	private static void loadNativeHackedStandardCPPLibraries()
			throws IOException, FileNotFoundException {
		// If this is a raspberrypi, preload the hacked version of libstdc++ so that NetworkTables works
		if (System.getProperty("os.name").startsWith("Linux") && new File("/usr/lib/arm-linux-gnueabihf").exists()) {
			System.out.println("Detected gnueabihf: pre-loading hacked version of libstdc++.so.6 contained in jar resources");
			InputStream is = Application.class.getResourceAsStream("/rpi/libstdc++.so.6");
			File jniLibrary = File.createTempFile("libNetworkTablesJNI", ".so");
			jniLibrary.deleteOnExit();
	        OutputStream os = new FileOutputStream(jniLibrary);
	        byte[] buffer = new byte[1024];
	        int readBytes;
	        try {
	        	while ((readBytes = is.read(buffer)) != -1) {
	        		os.write(buffer, 0, readBytes);
	        	}
	        } finally {
	        	os.close();
	        	is.close();	
	    	}
	        System.load(jniLibrary.getAbsolutePath());
	        System.out.println("Successfully loaded hacked libstdc++.so.6 native library.");
		}
	}
}