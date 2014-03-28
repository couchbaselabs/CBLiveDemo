import com.couchbase.client.CouchbaseClient;

import java.net.URI;
import java.util.ArrayList;

public class CBLiveDemo {
	// Global options
	// gets, sets per second.

	private static String HOST = "127.0.0.1:8091";
	private static String BUCKET = "default";

	// Thread 1 - reader
	// Thread 2 - writer
	// Thread 3 - draw window 1
	// Thread 4 - draw window 2

	public class CBReader extends Thread {
		public void run() {
			// Loop forever:
			//
			//     For every pixel in the image; chunked into bulk size...
			//         getbulk(); with async callback.
			//         update the pixal array with the fetched pixels
			//
			//         after MAX_GETS_PER_SECOND
			//         if <1 has passed, wait until the end of that second.
		}
	}

	public class CBWriter extends Thread  {
		public void run() {
			// Loop forever:
			//
			//    Select image to write (1 of 2).
			//    For every pixel in the image...
			//        write pixel value to Couchbase (set).
			//
			//        after MAX_SETS_PER_SECOND
			//        if <1 has passed, wait until the end of that second.
		}
	}

	public class RenderMainWindow extends Thread {
		public void run() {
			// Loop forever:
		}
	}

	public class RenderZoomWindow extends Thread {
		public void run() {
			// Loop forever:
		}
	}

	public static void main(String[] args) throws Exception {
		ArrayList<URI> nodes = new ArrayList<URI>();

		// Add one or more nodes of your cluster (exchange the IP with yours)
		nodes.add(URI.create("http://" + HOST + "/pools"));

		// Try to connect to the client
		try {
			client = new CouchbaseClient(nodes, BUCKET, "");
		} catch (Exception e) {
			System.err.println("Error connecting to Couchbase: " + e.getMessage());
			System.exit(1);
		}

		// Set your first document with a key of "hello" and a value of "couchbase!"
		client.set("hello_there", "couchbase!").get();

		// Return the result and cast it to string
		String result = (String) client.get("hello");
		System.out.println(result);

		// Shutdown the client
		client.shutdown();
	}

	private static CouchbaseClient client;
}
