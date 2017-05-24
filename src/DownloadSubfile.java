/**************************************
 * DownloadSubfile.java
 * 
 * Subroutine called by `Video` to download part of the resource.
 * Runs in parallel with other calls to `DownloadSubfile`. 
 * 
 * @author: Jeremy Corren
 * 
 **************************************/

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class DownloadSubfile implements Runnable {

	private int subfileNum;
	private URL url;
	private HttpURLConnection connection;
	
	private int rangePosition;
	private int buffer;
	private int threshold;
	
	private File subfile;
	private ReadableByteChannel in;
	private FileOutputStream out;
	
	DownloadSubfile(int subNum, String resource, int range, String filepath, int buf, int thresh) {
		this.subfileNum = subNum;
		
		// starting position for byte range request
		this.rangePosition = range;
				
		// buffer size: ~65 KB
		this.buffer = buf;
				
		// loop threshold for byte transfer
		this.threshold = thresh;
		
		try {
			// get target URL of resource
			this.url = new URL(resource);
			
			// create file path for subfile on disk
			this.subfile = new File(filepath + "/vid-" 
				+ String.format("%04d", subfileNum) + ".mp4"); // 4d to accommodate 100 subfiles, possibly
		} catch (MalformedURLException e) {
			System.out.println("MalformedURLException while downloading subfile : " + e.getMessage());
		}
	}
	
	/*
	 * Read ~100 MB from resource and store in subfile on local disk
	 */
	@Override
	public void run() {
		try {
			// specify byte range request and connect
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestProperty("Range", "bytes=" + rangePosition + "-");
			connection.connect();

			// prepare I/O stream from server to subfile on local disk
        	in = Channels.newChannel(connection.getInputStream());
        	out = new FileOutputStream(subfile);
 
        	// read subfile
			int bytesReceivedSubfile = 0;
        	int i = 0;
        	while(i < threshold) {
        		long chunk = out.getChannel().transferFrom(in, bytesReceivedSubfile, buffer);
        		bytesReceivedSubfile += chunk;

        		if(chunk == 0)
        			break;
        		i++;
        	}
        	System.out.println("   Subfile " + subfileNum + " downloaded...");
		} catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException while downloading subfile : " + e.getMessage());
		} catch (IOException e) {
			System.out.println("IOException while downloading subfile : " + e.getMessage());
		} finally {
			try {
	        	in.close();
	        	out.close();
			} catch (IOException e) {
				System.out.println("IOException while closing subfile streams : " + e.getMessage());
			}
		}
	}
}