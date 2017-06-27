## DownloadClient
Concurrently downloads video resource from HTTP server, given a user-supplied raw URL, and stores in a user-supplied filepath on disk.

### Usage
Download `Video.java` and `DownloadSubfile.java`. Note: when prompted to enter the number of subfiles to download in parallel, optimal range is between 10 and 25. Also, make sure the download destination folder is different from the folder in which the Java source code for the program is stored.

Example run:
```
>>
>> javac Video.java
>> java Video
**DownloadClient**
Enter URL: <raw-URL>
Enter directory filepath for download destination: <empty-dir>
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
>>
```
