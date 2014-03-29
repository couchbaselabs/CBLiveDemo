import com.couchbase.client.CouchbaseClient;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.BulkGetCompletionListener;
import net.spy.memcached.internal.BulkGetFuture;


// TODO Latching
// TODO Randomise Pixel List
// TODO Failure Handling in bulk Get
// TODO Investigate Bulk timeouts.

public class CBLiveDemo {
	// Global options
	// gets, sets per second.

	private static String HOST = "192.168.60.105:8091";
	private static String BUCKET = "default";
	private static int MAX_SETS_SEC = 10000;
	private static int MAX_GETS_SEC = 10000;
	private static int BULK_GETS = 20;


	private static int FRAME_RATE = 25;                     // Frames Per Second
	private static int ZOOM_PIXEL_SIZE = 10;
	private static int ZOOM_WIDTH = 100;
	private static int ZOOM_HEIGHT = 60;
	private static int ZOOM_X_OFFSET = 100;
	private static int ZOOM_Y_OFFSET = 0;
	private static int NUM_IMAGES = 2;
	private static BufferedImage[] imageSet;
	private static final String IMAGE_0_PATH = "/Users/dhaikney/Desktop/London_400x200.jpg";
	private static final String IMAGE_1_PATH = "/Users/dhaikney/Desktop/CB_Demo_400x200.jpg";


	// Thread 1 - reader
	// Thread 2 - writer
	// Thread 3 - draw window 1
	// Thread 4 - draw window 2


	public static class CBReader extends Thread {


		class MyListener implements BulkGetCompletionListener{

			private int base;

			MyListener(int i)
			{
				base = i;
			}

			public void onComplete(BulkGetFuture<?> bulkGetFuture) throws Exception {
				if (bulkGetFuture.getStatus().isSuccess()) 
				{
					Map<String,?> response = bulkGetFuture.get();
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
					System.out.println("Borked");
				}
			}
		}

		public void run() {

			long startTime, currentTime;
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

				for(int i = 0; i < numPixels; i+= BULK_GETS)
				{
					keyList.clear();

					for (int j = 0; j < BULK_GETS; j++)
					{
						keyList.add("px_" + (i+j));
					}
					client.asyncGetBulk(keyList).addListener(new MyListener(i));
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
			}
		}
	}

	public static class CBWriter extends Thread  {
		public void run() {

			long startTime, currentTime;
			int numPixels = imageSet[0].getWidth() * imageSet[0].getHeight();
			int r,g,b, pixelValue;
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
				for(int i = 0; i < numPixels; i++)
				{
					imageData = ((DataBufferByte)imageSet[imageID].getRaster().getDataBuffer()).getData();

					b = imageData[(i*3) + 0] & 0xff;
					g = imageData[(i*3) + 1 ] & 0xff;
					r = imageData[(i*3) + 2] & 0xff;
					pixelValue = (r << 16) | (g << 8) | (b << 0 ); 					

					client.set("px_"+i,pixelValue);
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

	public static void main(String[] args) throws Exception {
		ArrayList<URI> nodes = new ArrayList<URI>();

		imageSet = new BufferedImage[NUM_IMAGES];
		imageSet[0] = ImageIO.read(new File(IMAGE_0_PATH));
		imageSet[1] = ImageIO.read(new File(IMAGE_1_PATH));
		mainWindow = new RenderMainWindow(imageSet[0].getWidth(),imageSet[0].getHeight(),0,0);
		mainWindow.start();
		zoomWindow = new  RenderZoomWindow(ZOOM_WIDTH * ZOOM_PIXEL_SIZE,ZOOM_HEIGHT * ZOOM_PIXEL_SIZE,300,0);
		zoomWindow.start();


		// Add one or more nodes of your cluster (exchange the IP with yours)
		nodes.add(URI.create("http://" + HOST + "/pools"));

		// Try to connect to the client
		try {
			client = new CouchbaseClient(nodes, BUCKET, "");
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
