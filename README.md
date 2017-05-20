# DownloadManager
Downloads video content from HTTP server, given a user-supplied raw URL, and stores the resource in a user-supplied filepath on the local disk.

### Usage
Download `Video.java` and `DownloadSubfile.java`. Note: when prompted to enter the number of subfiles to download in parallel, optimal range is between 10 and 25.

Example run:
```
>>
>> javac Video.java
>> java Video
**HttpVideoDownloader**
Enter URL: <raw-URL>
Enter directory filepath for download destination: <my-dir>
Cleaning up directory...

Checking server...
Byte range requests supported!

Enter number of subfiles: <integer>

Downloading parts in parallel...
   <...>
   
Merging subfiles...
Merging complete.

Deleting subfiles from directory...
Comparing checksum...
File integrity verified, job complete.
>>
```
