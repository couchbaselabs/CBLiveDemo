import com.couchbase.client.CouchbaseClient;
import com.couchbase.client.CouchbaseConnectionFactoryBuilder;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import net.spy.memcached.FailureMode;
import net.spy.memcached.OperationTimeoutException;
import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.BulkGetCompletionListener;
import net.spy.memcached.internal.BulkGetFuture;
import net.spy.memcached.internal.CheckedOperationTimeoutException;
import net.spy.memcached.internal.GetCompletionListener;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationCompletionListener;
import net.spy.memcached.internal.OperationFuture;


// TODO Failure Handling in bulk Get
// TODO Investigate Bulk timeouts.

public class CBLiveDemo {
	// Global options
	// gets, sets per second.

	private static String HOST = "192.168.60.105:8091";
	private static String BUCKET = "PRIMARY";
	
//	private static String HOST = "192.168.60.205:8091";
//	private static String BUCKET = "BACKUP";
	
	private static int MAX_SETS_SEC = 50000;
	private static int MAX_GETS_SEC = 50000;
	private static int BULK_GETS = 1;
	private static boolean USE_ASYNC = true;


	private static int FRAME_RATE = 25;                     // Frames Per Second
	private static int ZOOM_PIXEL_SIZE = 10;
	private static int ZOOM_WIDTH = 60;
	private static int ZOOM_HEIGHT = 60;
	private static int ZOOM_X_OFFSET = 130;
	private static int ZOOM_Y_OFFSET = 20;
	private static int NUM_IMAGES = 2;
	private static BufferedImage[] imageSet;
	private static int[] pixelOrder;
	private static final String IMAGE_0_PATH = "/Users/dhaikney/Desktop/London_500x500.jpg";
	private static final String IMAGE_1_PATH = "/Users/dhaikney/Desktop/CB_Demo_500x500.jpg";
	
	private static CountDownLatch frameLatch;
	// Thread 1 - reader
	// Thread 2 - writer
	// Thread 3 - draw window 1
	// Thread 4 - draw window 2


	public static class CBReader extends Thread {


		class MyBulkListener implements BulkGetCompletionListener{

			private int base;

			MyBulkListener(int i)
			{
				base = i;
			}

			public void onComplete(BulkGetFuture<?> bulkGetFuture) {
				if (bulkGetFuture.getStatus().isSuccess()) 
				{
					Map<String, ?> response;
					try {
						response = bulkGetFuture.get();
					} catch (Exception e) {
						mainWindow.window.pixelData[base] = 0xFF00FF;
						return;
					}
					for (int j = 0; j < BULK_GETS; j++)
					{
						Object res = response.get("px_" + (base+j));
						if (res != null){
							mainWindow.window.pixelData[base+j] = (Integer) res;
						}
						else
						{
							mainWindow.window.pixelData[base+j] = 0xFF00FF;
						}
					}
				}
				else
				{
					// This one / set? didn't work
					System.out.println("Multi Borked");
				}
			}
		}

		class MyListener implements GetCompletionListener{

			private int base;

			MyListener(int i)
			{
				base = i;
			}

			public void onComplete(GetFuture<?> future) {
				if (future.getStatus().isSuccess()) 
				{
					Object res;
					try {
						res = future.get();
					} catch (Exception e) {
						mainWindow.window.pixelData[base] = 0xFF00FF;
						return;
					}
					if (res != null){
						mainWindow.window.pixelData[base] = (Integer) res;
					}
					else
					{
						// Null - show magenta.
						mainWindow.window.pixelData[base] = 0xFF00FF;
					}
				}
				else
				{
					// Unsuccessful - show cyan.
					mainWindow.window.pixelData[base] = 0x00FFFF;
				}
				frameLatch.countDown();
			}
		}

		public void run() {

			long startTime, currentTime;
			int pixel;
			int numPixels = imageSet[0].getWidth() * imageSet[0].getHeight();
			List<String> keyList = new ArrayList<String>();
			
			// Loop forever:
			//
			//     For every pixel in the image; chunked into bulk size...
			//         getbulk(); with async callback.
			//         update the pixal array with the fetched pixels
			//
			//         after MAX_GETS_PER_SECOND
			//         if <1 has passed, wait until the end of that second.

			while (true){

				startTime = System.currentTimeMillis();

				frameLatch = new CountDownLatch(numPixels);

				
				for(int i = 0; i < numPixels; i+= BULK_GETS)
				{
					keyList.clear();

					pixel = pixelOrder[i];

					if (USE_ASYNC)
					{
						if (BULK_GETS == 1)
						{
							try{
								GetFuture<Object> future = client.asyncGet("px_" + pixel);
								future.addListener(new MyListener(pixel));
							}
							catch (IllegalStateException e)
							{
								frameLatch.countDown(); //unable to request this pixel. Ignore it.
							}
						}
						else
						{
							for (int j = 0; j < BULK_GETS; j++)
							{
								keyList.add("px_" + pixelOrder[(i+j)]);
							}
							client.asyncGetBulk(keyList).addListener(new MyBulkListener(pixel));
						}
					}
					else // Synchronous
					{
						if (BULK_GETS == 1)
						{
							try
							{
								Object res = client.get("px_" + pixel);
								mainWindow.window.pixelData[pixel] = (Integer) res;
							}
							catch(OperationTimeoutException e)
							{
								System.out.println("TIMEOUT on px_"  + pixel);
								mainWindow.window.pixelData[pixel] = 0x00FFFF;
							}
						}
						else
						{
							// TODO
							assert(false);
						}
					}
					if (( i % (MAX_GETS_SEC/20)) == 0)
					{
						currentTime = System.currentTimeMillis();
						if (currentTime < (startTime + 50))
						{
							try {
								Thread.sleep((startTime + 50) - currentTime);
								startTime = System.currentTimeMillis();

							} catch (InterruptedException e) {}
						}
					}
				}
				try {
					frameLatch.await();
					Thread.sleep(500); // Dirty pause to allow the writer thread to start making progress;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public static class CBWriter extends Thread  {
		
		class WriteListener implements OperationCompletionListener{

			private int base;

			WriteListener(int i)
			{
				base = i;
			}

			public void onComplete(OperationFuture<?> future) {
				try {
					future.get();
					mainWindow.window.pixelData[base] = 0x00FF00;
				} catch (Exception e) {
					System.out.println("WRITE EXCEPTION!");
					mainWindow.window.pixelData[base] = 0xFF00FF;
				}
			}
		}
		
		public void run() {

			long startTime, currentTime;
			int numPixels = imageSet[0].getWidth() * imageSet[0].getHeight();
			int r,g,b, pixelValue, pixel;
			int imageID = 0;
			byte[] imageData;

			// Loop forever:
			//
			//    Select image to write (1 of 2).
			//    For every pixel in the image...
			//        write pixel value to Couchbase (set).
			//
			//        after MAX_SETS_PER_SECOND
			//        if <1 has passed, wait until the end of that second.
			while (true){
				startTime = System.currentTimeMillis();
				imageID = imageID ^ 0x1;
				imageData = ((DataBufferByte)imageSet[imageID].getRaster().getDataBuffer()).getData();
				for(int i = 0; i < numPixels; i++)
				{
					pixel = pixelOrder[i];
					
					b = imageData[(pixel*3) + 0] & 0xff;
					g = imageData[(pixel*3) + 1 ] & 0xff;
					r = imageData[(pixel*3) + 2] & 0xff;
					pixelValue = (r << 16) | (g << 8) | (b << 0 ); 					

					try{
						OperationFuture<Boolean> future = client.set("px_"+pixel,pixelValue);
					}
					catch (IllegalStateException e)
					{
						//couldn't write this pixel. Ignore
					}
					//future.addListener(new WriteListener(i));
//					try {
//						future.get();
//						mainWindow.window.pixelData[i] = 0x00FF00;
//					} catch (Exception e) {
//						System.out.println("WRITE EXCEPTION (2)!");
//						mainWindow.window.pixelData[i] = 0xFF00FF;
//					}
					
					if (( i % (MAX_SETS_SEC/20)) == 0)
					{
						currentTime = System.currentTimeMillis();
						if (currentTime < (startTime + 50))
						{
							try {
								Thread.sleep((startTime + 50) - currentTime);
								startTime = System.currentTimeMillis();
							} catch (InterruptedException e) {}
						}
					}
				}
				try {
					frameLatch.await();
				} catch (InterruptedException e) {
					System.out.println("Frame Latch Interrupted");
				
				}
			}
		}
	}

	public static class RenderMainWindow extends Thread  {

		public RenderWindow window;

		RenderMainWindow(int w, int h, int x, int y){
			window = new RenderWindow (w,h,x,y);
		}

		public void run() {
			long startTime, currentTime;
			int framePause = 1000 / FRAME_RATE;

			// Loop forever drawing contents of pixelData FRAME_RATE times per sec.
			do
			{      
				// Record the time we started this frame
				startTime=System.currentTimeMillis();


				window.drawFrame();

				// FRAME_RATE pause
				currentTime = System.currentTimeMillis();
				if(currentTime < (startTime + framePause))
				{
					try {
						Thread.sleep((startTime + framePause) - currentTime);
					} catch (InterruptedException e) {}
				}

			} while (true);

		}
	}

	public static class RenderZoomWindow extends Thread {

		public RenderWindow window;

		RenderZoomWindow(int w, int h, int x, int y){
			window = new RenderWindow (w,h,x,y);
		}

		public void run() {
			long startTime, currentTime;
			int framePause = 1000 / FRAME_RATE;
			int sourceRow, sourceCol, sourcePixel;
			// Loop forever drawing contents of pixelData FRAME_RATE times per sec.
			do
			{      
				// Record the time we started this frame
				startTime = System.currentTimeMillis();

				for (int i=0; i < (window.frameWidth * window.frameHeight); i++)
				{
					if ((i % ZOOM_PIXEL_SIZE == 0) ||
							((i % (window.frameWidth * ZOOM_PIXEL_SIZE)) < window.frameWidth))
					{
						window.pixelData[i] = 0xFFFFFF;
					}
					else
					{
						sourceRow = ZOOM_Y_OFFSET + (i / (window.frameWidth * ZOOM_PIXEL_SIZE));
						sourceCol = ZOOM_X_OFFSET + ((i % window.frameWidth) / ZOOM_PIXEL_SIZE);
						sourcePixel = (sourceRow * mainWindow.window.frameWidth) + sourceCol;
						window.pixelData[i] = mainWindow.window.pixelData[sourcePixel];
					}
				}

				window.drawFrame();

				// FRAME_RATE pause
				currentTime = System.currentTimeMillis();
				if(currentTime < (startTime + framePause))
				{
					try {
						Thread.sleep((startTime + framePause) - currentTime);
					} catch (InterruptedException e) {}
				}

			} while (true);

		}
	}

	private static void shufflePixelOrder()
	{
		int numPixels = imageSet[0].getWidth() * imageSet[0].getHeight();
		List<Integer> randomList = new ArrayList<Integer>();
		    for (int i = 0; i < numPixels; i++) {
		      randomList.add(i);
		    }
		    Collections.shuffle(randomList);
		    pixelOrder = new int[numPixels];
		    for (int i = 0; i < numPixels; i++) {
		      pixelOrder[i] = randomList.get(i);
		    }
	}
	
	public static void main(String[] args) throws Exception {
		ArrayList<URI> nodes = new ArrayList<URI>();

		imageSet = new BufferedImage[NUM_IMAGES];
		imageSet[0] = ImageIO.read(new File(IMAGE_0_PATH));
		imageSet[1] = ImageIO.read(new File(IMAGE_1_PATH));
		mainWindow = new RenderMainWindow(imageSet[0].getWidth(),imageSet[0].getHeight(),800,0);
		mainWindow.start();
		zoomWindow = new  RenderZoomWindow(ZOOM_WIDTH * ZOOM_PIXEL_SIZE,ZOOM_HEIGHT * ZOOM_PIXEL_SIZE,800,400);
		zoomWindow.start();

		shufflePixelOrder();

		// Tell things using spymemcached logging to use internal SunLogger API
		Properties systemProperties = System.getProperties();
		systemProperties.put("net.spy.log.LoggerImpl", "net.spy.memcached.compat.log.SunLogger");
		System.setProperties(systemProperties);

		Logger.getLogger("net.spy.memcached").setLevel(Level.WARNING);
		Logger.getLogger("com.couchbase.client").setLevel(Level.WARNING);
		Logger.getLogger("com.couchbase.client.vbucket").setLevel(Level.WARNING);
		
		// Add one or more nodes of your cluster (exchange the IP with yours)
		nodes.add(URI.create("http://" + HOST + "/pools"));

		// Try to connect to the client
		try {
			CouchbaseConnectionFactoryBuilder cfb = new CouchbaseConnectionFactoryBuilder();
			cfb.setOpTimeout(1000);  // wait up to 0.5 seconds for an operation to succeed
			cfb.setOpQueueMaxBlockTime(1); // wait up to 0.5 seconds when trying to enqueue an operation
			//cfb.setMaxReconnectDelay(500);
			//cfb.setTimeoutExceptionThreshold(10);
			cfb.setFailureMode(FailureMode.Cancel);
			
			cfb.setUseNagleAlgorithm(true);
			client = new CouchbaseClient(cfb.buildCouchbaseConnection(nodes, BUCKET, ""));

		} catch (Exception e) {
			System.err.println("Error connecting to Couchbase: " + e.getMessage());
			System.exit(1);
		}

		(new CBWriter()).start();
		(new CBReader()).start();

		mainWindow.join();

		// Shutdown the client
		client.shutdown();
	}

	private static CouchbaseClient client;
	private static RenderZoomWindow zoomWindow;
	private static RenderMainWindow mainWindow;
}
