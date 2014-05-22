CBLiveDemo
==========

*Performance, Scalability and Availability. That's Couchbase!*

Demo app originally written by @dhaikney and Dave Rigby for Couchbase Live London 2014. The application was intended to visualise some of Couchbase's core tenets. 

### Client Application

The client read and writes a dataset from Couchbase and displays the results. The dataset is a hundred thousand documents where each document in this dataset represents a colour. This couchbase client is retriving these documents, updating them (their colour) and storing them back.
The application creates two windows. The window on top shows the entire dataset - 100,000 documents. Below I have magnified a subset of these documents so you can see the individual document updates being performed. 
The application has 2 main threads, one which walks across the entire data set updating each document and writing it to the cluster. And a second thread which reads these documents and writes them to the screen.

### Key Demo Points

Here are some of the key highlights of the demo:

**Performance**

Start the app up and observe the following:

* A single laptop running 3 VMs is capable of 20,000 operations per second.
* Note how evenly distributed the number of items and operations are across all nodes
* The cbstats / timings command can be used to demonstrate how the cluster repsonds in micro-seconds to a request
* How smooth the performance is - the number of ops should be relatively flat

**Scalability**

Grow the cluster from 3 nodes to 4 nodes. And note the following:

* First and foremost this is done live, whilst the application is running. No downtime.
* Performance is unaffected. You'll still see the same flat number of operations
* Watch the number of vbuckets be moved around as the rebalance ballet continues
* The new node starts performing operations as soon as the first vbucket transfer is complete. It becomes useful, fast.

**Availabilty**

* Perform some disruptive operation on the cluster. e.g. Kill all the couchbase processes on one node. 
* Observe the app now shows that proportion of the pixels as unavailable. 
* Hit the failover button - note how the client responds automatically and all data is available
* This behaviour can be triggered automatically by the cluster

Then demonstrate XDCR

* Bring up another cluster and set up XDCR between the two (show incoming and outgoing XDCR ops)
* Kill the first cluster (shut the laptop, disconnect its network)
* Observe the client has temporarily stopped running
* Reconfigure the app to point to the backup cluster and restart it (this could be triggered automatically if necessary).
* Observe the application is back online within seconds. With a few clicks, XDCR has made a complete replica copy of your data-set.

###Configuring the app

The app needs some tuning for the environemnt (laptop / server) it will be running in and the display it will be viewed on. Here are some suggestions or tips for best performance:

* Play with it first to get a feel for what it does. Meddle with the constants at the top of the file - particularly the get / set rate limits.
* All images should be the same size
* VM Fusion VMs running Ubuntu linux seemed to perform much better than their VirtualBox counterparts.
* Check the pom.xml to ensure you are running against the latest version of the Java SDK!
* I've tried to tune the app to fail fast (to give the best visual impact) but this is probably not how you would configure a real production app.
* Depending on how you fail the node (VM halt / vs stop networking vs kill vs kill -9) can have an impact on how quickly the client detects the connection is unavailable.
* Finally (and cheers for getting this far) This is obviously demo code so sold-as-seen but all feedback and improvements gratefully received.
 

Thanks,
DH
