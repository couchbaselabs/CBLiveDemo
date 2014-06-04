import com.couchbase.client.CouchbaseClient;
import com.couchbase.client.CouchbaseConnectionFactoryBuilder;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
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

public class CBLiveDemo {

	// Select PRIMARY datacentre
    private static String HOST = "localhost:9000";
    private static String BUCKET = "cblive";
    private static String PASS = "cblive";

	// Select BACKUP datacentre	
//    private static String HOST = "10.8.7.5:8091";
//    private static String BUCKET = "target-bucket";
//    private static String PASS = "";


	private static int MAX_SETS_SEC = 10000;
	private static int MAX_GETS_SEC = 10000;
	private static boolean USE_ASYNC = true;


    private static int FRAME_RATE = 25;                     // Frames Per Second
    private static int ZOOM_PIXEL_SIZE = 10;
    private static int ZOOM_WIDTH = 30;
    private static int ZOOM_HEIGHT = 40;
    private static int ZOOM_X_OFFSET = 130;
    private static int ZOOM_Y_OFFSET = 220;
    private static int[] pixelOrder;
    private static ArrayList<BufferedImage> imageSet;
    private static final String IMAGES[] = {"images/CB_Symbol_250x400.jpg", "images/hashtag_250x400.jpg",
            "images/3_values_250x400.jpg", "images/CB_SKY_250x400.jpg"};
    // This latch is used to keep the reader / writer threads in sync
    private static CountDownLatch frameLatch;


	public static class CBReader extends Thread {



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
                        // Error - show magenta.
						mainWindow.window.pixelData[base] = 0xFF00FF;
                        System.err.println("Exception on get " + e.getMessage());
                        System.err.println("Status on future " + future.getStatus().getMessage());
						return;
					}
					if (res != null){
						mainWindow.window.pixelData[base] = (Integer) res;
					}
					else
					{
						// Null - show black.
                        // “Indigo with terra sienna, Prussian blue with burnt sienna, really give much deeper tones
                        // than pure black itself. When I hear people say ‘there is no black in nature’, I sometimes
                        // think, ‘There is no real black in colors either’. However, you must beware of falling into
                        // the error of thinking that the colorists do not use black, for of course as soon as an
                        // element of blue, red, or yellow is mixed with black, it becomes a gray, namely, a dark, r
                        // eddish, yellowish, or bluish gray.”
                        // Vincent van Gogh (Letter to Theo van Gogh, June 1884)
						mainWindow.window.pixelData[base] = 0x000000;
                        System.err.println("Empty for some reason " + future.getStatus().getMessage());
					}
				}
				else
				{
					// Unsuccessful - show cyan.
                    mainWindow.window.pixelData[base] = 0x00FFFF;
                    System.err.println("No result " + future.getStatus().getMessage());
                }
				frameLatch.countDown();
			}
		}

		public void run() {

			long startTime, currentTime;
			int pixel;
			int numPixels = imageSet.get(0).getWidth() * imageSet.get(0).getHeight();
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


				for(int i = 0; i < numPixels; i++)
				{
					keyList.clear();

					pixel = pixelOrder[i];

					if (USE_ASYNC)
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
					else // Synchronous
					{
						try
						{
							Object res = client.get("px_" + pixel);
							mainWindow.window.pixelData[pixel] = (Integer) res;
						}
						catch(OperationTimeoutException e)
						{
							System.out.println("TIMEOUT on px_"  + pixel);
							mainWindow.window.pixelData[pixel] = 0xFF00FF;
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
				} catch (Exception e) {
				    System.err.println("WRITE EXCEPTION!");
                    System.err.println(e.getMessage());
                    mainWindow.window.pixelData[base] = 0xFF00FF;
				}
			}
		}

		public void run() {

			long startTime, currentTime;
			int numPixels = imageSet.get(0).getWidth() * imageSet.get(0).getHeight();
			int r,g,b, pixelValue, pixel;
			int imageID = 0;
			byte[] imageData;

			// Loop forever:
			//
			//    Select image to write
			//    For every pixel in the image...
			//        write pixel value to Couchbase (set).

			while (true){
				startTime = System.currentTimeMillis();

				// cycle through the images
				imageID = (imageID + 1) % IMAGES.length;
				imageData = ((DataBufferByte)imageSet.get(imageID).getRaster().getDataBuffer()).getData();

				for(int i = 0; i < numPixels; i++)
				{
					pixel = pixelOrder[i];

					b = imageData[(pixel*3) + 0] & 0xff;
					g = imageData[(pixel*3) + 1 ] & 0xff;
					r = imageData[(pixel*3) + 2] & 0xff;
					pixelValue = (r << 16) | (g << 8) | (b << 0 ); 					

					try{
						OperationFuture<Boolean> future = client.set("px_"+pixel,pixelValue);
						future.addListener(new WriteListener(pixel));
					}
					catch (IllegalStateException e)
					{
						// couldn't write this pixel. Ignore
					}

					// 20 times a second we are going to rate-limit the number of ops to increase smoothness
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

				// At the end of each frame sit and wait for all pixels to be read before we go again
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
					} catch (InterruptedException ignored) {}
				}

			} while (true);

		}
	}

	// One-off call in order to give a "dissolve" effect
	private static void shufflePixelOrder()
	{
		int numPixels = imageSet.get(0).getWidth() * imageSet.get(0).getHeight();
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

        imageSet = new ArrayList<BufferedImage>();
        int i = 0;
        for (String image : IMAGES) {
            try {
                imageSet.add(ImageIO.read(new File(image)));
                i++;
            } catch (IOException e) {
                throw new RuntimeException("Could not read the file " + image, e);
            }
        }


        mainWindow = new RenderMainWindow(imageSet.get(0).getWidth(),imageSet.get(0).getHeight(),1000,0);
		mainWindow.start();
		zoomWindow = new  RenderZoomWindow(ZOOM_WIDTH * ZOOM_PIXEL_SIZE,ZOOM_HEIGHT * ZOOM_PIXEL_SIZE,1000,400);
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
			cfb.setOpTimeout(1000);  // wait up to 1 second for an operation to succeed
			cfb.setOpQueueMaxBlockTime(500); // wait up to 0.5 seconds when trying to enqueue an operation
			//cfb.setMaxReconnectDelay(500);
			//cfb.setTimeoutExceptionThreshold(10);
			cfb.setFailureMode(FailureMode.Cancel);

			cfb.setUseNagleAlgorithm(true);
			client = new CouchbaseClient(cfb.buildCouchbaseConnection(nodes, BUCKET, PASS));

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
