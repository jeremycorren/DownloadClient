/**************************************
 * HttpVideoDownloader.java
 * 
 * Downloads video from raw URL.
 * 
 * @author: Jeremy Corren
 * 
 **************************************/

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Video {
	private static String target;
	private static int contentLength;
	private static String directory;
	private static String filepath;
	
	private static String checksum = null;
	private static boolean supportsRange = false;
	
	private static Scanner user = null;
	private static boolean success;
	
	/*
	 * Tester
	 */
	public static void main(String[] args) {
		System.out.println("**DownloadClient**\n");
		
		// retry in case of download error
		do {
			getUrl();
			getFilepath();
			cleanDir();
			checkServer();
			
	    	// if server accepts byte range requests
	    	if(supportsRange) {	
	    		downloadInParts();
	        	mergeSubfiles();
	        	removeSubfiles();

	        	success = compareChecksum();
	        	if(!success)
	        		confirmRetry();
	        	else
	        		openDir();
	    	} else {
	    		downloadAll();
	    		
	        	success = compareChecksum();
	        	if(!success)
	        		confirmRetry();
	        	else
	        		openDir();
	    	}
		} while(!success);
		user.close();
	}	
	
	/*
	 * Get raw URL for media file.
	 */
	public static void getUrl() {
		user = new Scanner(System.in);
		System.out.println("Enter URL: ");
			
		// get raw URL
		String input = user.nextLine();
		input = input.trim();
		target = input;
		filepath = directory + "/vid.mp4";
	}
	
	/*
	 * Get directory destination from user input and set download filepath.
	 */
	public static void getFilepath() {
		boolean retry = false;
		do {
			System.out.println("Enter directory filepath for download destination: ");
			
			// get filepath for destination directory
			String input = user.nextLine();
			input = input.trim();
			directory = input;
			
			// check if directory is valid
			File temp = new File(directory);	
			if(!temp.isDirectory()) {
				System.out.println("Filepath not valid...");
				retry = true;
			} else {
				retry = false;
			}
		} while(retry);
		filepath = directory + "/vid.mp4";		
	}
	
	/*
	 * Connect to server to check for byte range support. 
	 * Store content length and ETag of resource.
	 */
	public static void checkServer() {
		System.out.println("\nChecking server...");
		try {
			// send HEAD request to server
			URL urlCheck = new URL(target);
		    HttpURLConnection connectionCheck = (HttpURLConnection) urlCheck.openConnection();
		    connectionCheck.setRequestMethod("HEAD");
		    connectionCheck.connect();
		    
		    // get content length of resource
		    contentLength = connectionCheck.getContentLength();
		    
		    // get ETag of resource
		    String eTag = connectionCheck.getHeaderField("ETag");
		    checksum = eTag.substring(1, eTag.length()-1);
		    
		    // check if server supports byte range requests
		    String rangeHeader = connectionCheck.getHeaderField("Accept-Ranges");
		    supportsRange = rangeHeader.equals("bytes");
		} catch (MalformedURLException e) {
	        System.out.println("MalformedURLException while checking server : " + e.getMessage());    
	    } catch (IOException e) {
			System.out.println("IOException while checking server : " + e.getMessage());
	    }
	}
	
	public static void timer(long start) {
		long endTime = System.nanoTime();
		long elapsed = endTime - start;
		double seconds = (double) elapsed / 1000000000.0;
		System.out.format("\nTime elapsed: %f seconds\n", seconds);
	}
	
	/*
	 * Deploy threads to download parts of resource in parallel.
	 */
	public static void downloadInParts() {
		System.out.println("Byte range requests supported!\n");
		System.out.println("Enter number of subfiles: ");

		int input = user.nextInt();
		final int no_of_subfiles = input;
		final int buffer_size = 4096; // ~4 KB
		final int read_threshold = (contentLength / no_of_subfiles) / buffer_size + 1;
		
		System.out.println("\nDownloading parts in parallel...");
		
		// set position for byte range requests
		int[] bytePos = new int[no_of_subfiles];
		for(int i = 0; i < bytePos.length; i++)
			bytePos[i] = (read_threshold * i) * buffer_size;

		// start timer for micro-benchmark
		long startTime = System.nanoTime();
		
		// call threads to download each subfile in parallel
		ExecutorService exec = Executors.newFixedThreadPool(no_of_subfiles);
		for(int i = 0; i < no_of_subfiles; i++) {
			Runnable subfile = new DownloadSubfile(i, 
					target,
					bytePos[i], 
					directory, 
					buffer_size, 
					read_threshold);
			exec.execute(subfile);
		}
		
		// wait until all threads are complete before program continues
		exec.shutdown();
		try {
			exec.awaitTermination(10, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			System.out.println("InterruptedException while waiting for threads to complete : " + e.getMessage());
		}
		
		// end timer for micro-benchmark
		timer(startTime);
	}
	
	/*
	 * Merge subfiles into a single playable file.
	 */
	public static void mergeSubfiles() {
		System.out.println("\nMerging subfiles...");
    	
    	// delete .DS_Store from directory
    	File dsStore = new File(directory +  "/.DS_Store");
    	dsStore.delete();
    	
    	// arrange subfile paths into array
    	File[] subfiles = new File(directory).listFiles();
    	
    	// create path for merge file
    	File merge = new File(filepath);
    	
    	FileInputStream in;
    	FileOutputStream out;
    	int bytesRead = 0;
    	
    	// merge subfiles
    	try {
    		out = new FileOutputStream(merge, true);
    		for(File f : subfiles) {
    			in = new FileInputStream(f);
    			int fileSize = (int) f.length();
    			byte[] buf = new byte[fileSize];
    			
    			// read bytes to buffer
    			bytesRead = in.read(buf, 0, fileSize);
    			if(bytesRead == buf.length && bytesRead == fileSize) {
    				out.write(buf);
        			in.close();
        			in = null;	
    			} else {
    				System.out.println("Merging error.\n");
    			}
    		}
    		out.close();
			out = null;
			System.out.println("Merging complete.\n");
			
    	} catch (FileNotFoundException e) {
        	System.out.println("FileNotFoundException while merging subfiles : " + e.getMessage());
        } catch (IOException e) {
        	System.out.println("IOException while merging subfiles : " + e.getMessage());
        }
	}

	/*
	 * Remove subfiles from directory after merging.
	 */
	public static void removeSubfiles() {
		System.out.print("Deleting subfiles from directory...\n");
		
		// arrange subfile paths into array
    	File[] subfiles = new File(directory).listFiles();
		
		// delete subfiles from directory
		for(File f : subfiles) {
			if(f.getName().contains("vid-0")) {
				File file = new File(f.getAbsolutePath());
				file.delete();
			}
		}
	}
	
	/*
	 * If server denies byte range requests, download the resource all at once.
	 */
	public static void downloadAll() {
		System.out.println("Downloading all at once...");
		InputStream in = null;
		FileOutputStream out = null;
		
		try {
			// get target URL of resource
        	URL url = new URL(target);
    		
        	// prepare I/O streams
    		in = url.openStream();
            out = new FileOutputStream(filepath);

            // read file from server to local disk
            int bytesRead = -1;
            byte[] buffer = new byte[4096];
            while ((bytesRead = in.read(buffer)) != -1)
                out.write(buffer, 0, bytesRead);
            
            System.out.println("Download complete.\n");
		} catch (MalformedURLException e) {
        	System.out.println("MalformedURLException while downloading : " + e.getMessage());
        } catch (FileNotFoundException e) {
        	System.out.println("FileNotFoundException while downloading : " + e.getMessage());
        } catch (IOException e) {
        	System.out.println("IOException while downloading : " + e.getMessage());
        } finally {
        	try {
        		in.close();
         		out.close();
        	} catch (IOException e) {
            	System.out.println("IOException while downloading : " + e.getMessage());
        	}
        }
	}

	/*
	 * Compute checksum of file on local disk.
	 * Compare to ETag to verify file integrity.
	 */
	public static boolean compareChecksum() {
		System.out.println("Comparing checksum...");
		byte[] buf;
		byte[] hash = null;
		
		try {
			// read file from local disk
			buf = Files.readAllBytes(Paths.get(filepath));
			
			// complete hash computation
			hash = MessageDigest.getInstance("MD5").digest(buf);
		} catch (NoSuchAlgorithmException e) {
			System.out.println("NoSuchAlgorithmException while computing checksum : " + e.getMessage());	
		} catch (IOException e) {
			System.out.println("IOException while computing checksum : " + e.getMessage());
		}
		
		// convert byte array to string
		String result = new BigInteger(1, hash).toString(16); 
		
		// compare ETag with computed checksum
		if(checksum.equals(result)) {
			System.out.println("File integrity verified, job complete.");
			return true;
			
		} else {
			System.out.println("File may have been corrupted during transfer.\n");
			
			// return control to main method and ask for retry
			return false;
		}
	}
	
	/*
	 * On failed download, confirm retry from user input.
	 */
	public static void confirmRetry() {
		// in case of file corruption
    	user = new Scanner(System.in);
    	boolean invalid = false;

		do {
			// get user input
			System.out.println("Retry? (y/n)");
    		String reply = user.nextLine();
				
			if(reply.equals("y")) {
				cleanDir();
				return;
			} else if(reply.equals("n")) {
    			System.out.println("Exiting program.");
    			System.exit(0);
    		} else {
    			System.out.println("Invalid input.\n");
    			invalid = true;
    		}	
		} while(invalid);
	}
	
	/*
	 * Remove all files from directory before retrying download.
	 */
	public static void cleanDir() {
		System.out.print("Cleaning up directory...\n");
		
		// arrange subfile paths into array
    	File[] files = new File(directory).listFiles();
		
		// delete all files from directory
		for(File f : files) {
			File chunk = new File(f.getAbsolutePath());
			chunk.delete();
		}
	}
	
	/*
	 * Open directory with downloaded file.
	 */
	public static void openDir() {
		try {
			Desktop.getDesktop().open(new File(directory));
		} catch (IOException e) {
			System.out.println("IOException while opening directory : " + e.getMessage());
		}
	}
}
