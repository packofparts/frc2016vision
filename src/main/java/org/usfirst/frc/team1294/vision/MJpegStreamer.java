package org.usfirst.frc.team1294.vision;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;

public class MJpegStreamer implements ThreadFactory {

	private static final Logger LOG = LoggerFactory.getLogger(MJpegStreamer.class);
	private static final String BOUNDARY = "mjpegframe";
	private static final String CRLF = "\r\n";
	
	private int number = 0;
	private int port = 0;
	//private ExecutorService executor = Executors.newCachedThreadPool(this);
	private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private ImageProcessor imageProcessor;
	private WebcamExceptionHandler exceptionHandler = new WebcamExceptionHandler();
	private VisionNetworkTable visionTable;
	
	public MJpegStreamer(int port, ImageProcessor imageProcessor, VisionNetworkTable visionTable) {
		this.port = port;
		this.imageProcessor = imageProcessor;
		this.visionTable = visionTable;
	}
	
	public void start() {
		ServerSocket server = null;
		try {
			server = new ServerSocket(port, 150);
			while (true) {
				Socket socket = server.accept();
				LOG.info("New connection from {}", socket.getRemoteSocketAddress());
				scheduler.execute(new Connection(socket));
			}
		} catch (Exception e) {
			LOG.error("Cannot accept socket connection", e);
		} finally {
			try {
				server.close();
			} catch (IOException e) {
				LOG.error("Could not close server", e);
			}
		}
	}
		
	@Override
	public Thread newThread(Runnable r) {
		Thread thread = new Thread(r, String.format("streamer-thread-%s", number++));
		thread.setUncaughtExceptionHandler(exceptionHandler);
		thread.setDaemon(true);
		return thread;
	}

	private class WebcamExceptionHandler implements UncaughtExceptionHandler {

		@Override
		public void uncaughtException(Thread t, Throwable e) {
			Object context = LoggerFactory.getILoggerFactory();
			if (context instanceof NOPLoggerFactory) {
				System.err.println(String.format("Exception in thread %s", t.getName()));
				e.printStackTrace();
			} else {
				LOG.error(String.format("Exception in thread %s", t.getName()), e);
			}
		}
	}

	private class Connection implements Runnable {
		private Socket socket = null;

		public Connection(Socket socket) {
			this.socket = socket;
		}
		
		@Override
		public void run() {
			LOG.info("run");
			BufferedReader br = null;
			BufferedOutputStream bos = null;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			try {
				br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				bos = new BufferedOutputStream(socket.getOutputStream());
			} catch (IOException e) {
				LOG.error("Fatal I/O exception when creating socket streams", e);
				try {
					socket.close();
				} catch (IOException e1) {
					LOG.error("Canot close socket connection from " + socket.getRemoteSocketAddress(), e1);
				}
				return;
			}

			// consume whole input
			try {
				while (br.ready()) {
					br.readLine();
				}
			} catch (IOException e) {
				LOG.error("Error when reading input", e);
				return;
			}


			try {

				socket.setSoTimeout(0);
				socket.setKeepAlive(false);
				socket.setTcpNoDelay(true);

				while (true) {

					StringBuilder sb = new StringBuilder();
					sb.append("HTTP/1.0 200 OK").append(CRLF);
					sb.append("Connection: close").append(CRLF);
					sb.append("Cache-Control: no-cache").append(CRLF);
					sb.append("Cache-Control: private").append(CRLF);
					sb.append("Pragma: no-cache").append(CRLF);
					sb.append("Content-type: multipart/x-mixed-replace; boundary=--").append(BOUNDARY).append(CRLF);
					sb.append(CRLF);

					bos.write(sb.toString().getBytes());

					do {

						if (socket.isInputShutdown() || socket.isClosed()) {
							br.close();
							bos.close();
							return;
						}

						baos.reset();
						byte[] latestImage = imageProcessor.getLatestImage();
						baos.write(latestImage);

						sb.delete(0, sb.length());
						sb.append("--").append(BOUNDARY).append(CRLF);
						sb.append("Content-type: image/jpeg").append(CRLF);
						sb.append("Content-Length: ").append(baos.size()).append(CRLF);
						sb.append(CRLF);

						try {
							bos.write(sb.toString().getBytes());
							bos.write(baos.toByteArray());
							bos.write(CRLF.getBytes());
							bos.flush();
						} catch (SocketException e) {
							LOG.error("Socket exception from " + socket.getRemoteSocketAddress(), e);
							br.close();
							bos.close();
							return;
						}

						Thread.sleep((long)(1 / (double)visionTable.getFPS() * 1000));

					} while (true);
				}
			} catch (Exception e) {

				String message = e.getMessage();

				if (message != null) {
					if (message.startsWith("Software caused connection abort")) {
						LOG.info("User closed stream");
						return;
					}
					if (message.startsWith("Broken pipe")) {
						LOG.info("User connection broken");
						return;
					}
				}

				LOG.error("Error", e);

				try {
					bos.write("HTTP/1.0 501 Internal Server Error\r\n\r\n\r\n".getBytes());
				} catch (IOException e1) {
					LOG.error("Not ablte to write to output stream", e);
				}

			} finally {
				for (Closeable closeable : new Closeable[] { br, bos, baos }) {
					try {
						closeable.close();
					} catch (IOException e) {
						LOG.error("Cannot close socket", e);
					}
				}
				try {
					socket.close();
				} catch (IOException e) {
					LOG.error("Cannot close socket", e);
				}
			}
		}
		
		
		
	}

	
}