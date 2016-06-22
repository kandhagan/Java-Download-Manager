/**
Copyright (c) 2011-present - Luu Gia Thuy

Permission is hereby granted, free of charge, to any person
obtaining a copy of this software and associated documentation
files (the "Software"), to deal in the Software without
restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following
conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.
*/

package com.luugiathuy.apps.downloadmanager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpDownloader extends Downloader{
	
	public HttpDownloader(URL url, String outputFolder, int numConnections) {
		super(url, outputFolder, numConnections);
		download();
	}
	
	private void error() {
		System.out.println("ERROR");
		setState(ERROR);
	}
	
	@Override
	public void run() {
		HttpURLConnection conn = null;
		try {
			// Open connection to URL
			conn = (HttpURLConnection)mURL.openConnection();
			conn.setConnectTimeout(10000);
			
			// Connect to server
			conn.connect();
			
			// Make sure the response code is in the 200 range.
            if (conn.getResponseCode() / 100 != 2) {
                error();
            }
            
            // Check for valid content length.
            int contentLength = conn.getContentLength();
            if (contentLength < 1) {
                error();
            }
			
            if (mFileSize == -1) {
            	mFileSize = contentLength;
            	stateChanged();
            	System.out.println("File size: " + mFileSize);
            }
               
            // if the state is DOWNLOADING (no error) -> start downloading
            if (mState == DOWNLOADING) {
            	// check whether we have list of download threads or not, if not -> init download
            	if (mListDownloadThread.size() == 0)
            	{  
            		if (mFileSize > MIN_DOWNLOAD_SIZE) {
		                // downloading size for each thread
						int partSize = Math.round(((float)mFileSize / mNumConnections) / BLOCK_SIZE) * BLOCK_SIZE;
						System.out.println("Part size: " + partSize);
						
						// start/end Byte for each thread
						int startByte = 0;
						int endByte = partSize - 1;
						HttpDownloadThread aThread = new HttpDownloadThread(1, mURL, mOutputFolder + mFileName, startByte, endByte);
						mListDownloadThread.add(aThread);
						int i = 2;
						boolean lastLap = false;
						while (endByte < mFileSize) {
							startByte = endByte + 1;
							endByte += partSize;
							if(endByte >= mFileSize){ //To compute last chunk correct
								endByte = (mFileSize-1);
								lastLap = true;
							}
							aThread = new HttpDownloadThread(i, mURL, mOutputFolder + mFileName, startByte, endByte);
							mListDownloadThread.add(aThread);
							++i;
							if(lastLap) break;
						}
            		} else
            		{
            			HttpDownloadThread aThread = new HttpDownloadThread(1, mURL, mOutputFolder + mFileName, 0, mFileSize);
						mListDownloadThread.add(aThread);
            		}
            	} else { // resume all downloading threads
            		for (int i=0; i<mListDownloadThread.size(); ++i) {
            			if (!mListDownloadThread.get(i).isFinished())
            				mListDownloadThread.get(i).download();
            		}
            	}
				
				// waiting for all threads to complete
				for (int i=0; i<mListDownloadThread.size(); ++i) {
					mListDownloadThread.get(i).waitFinish();
				}
				
				// check the current state again
				if (mState == DOWNLOADING) {
					setState(COMPLETED);
				}
            }
		} catch (Exception e) {
			error();
		} finally {
			if (conn != null)
				conn.disconnect();
		}
	}
	
	/**
	 * Thread using Http protocol to download a part of file
	 */
	private class HttpDownloadThread extends DownloadThread {
		
		/**
		 * Constructor
		 * @param threadID
		 * @param url
		 * @param outputFile
		 * @param startByte
		 * @param endByte
		 */
		public HttpDownloadThread(int threadID, URL url, String outputFile, int startByte, int endByte) {
			super(threadID, url, outputFile, startByte, endByte);
		}

		/*public void runOrg() {
			BufferedInputStream in = null;
			RandomAccessFile raf = null;
			
			try {
				// open Http connection to URL
				HttpURLConnection conn = (HttpURLConnection)mURL.openConnection();
				
				// set the range of byte to download
				String byteRange = mStartByte + "-" + mEndByte;
				conn.setRequestProperty("Range", "bytes=" + byteRange);
				System.out.println("bytes=" + byteRange);
				
				// connect to server
				conn.connect();
				
				// Make sure the response code is in the 200 range.
	            if (conn.getResponseCode() / 100 != 2) {
	                error();
	            }
				
				// get the input stream
				in = new BufferedInputStream(conn.getInputStream());
				
				// open the output file and seek to the start location
				raf = new RandomAccessFile(mOutputFile, "rw");
				raf.seek(mStartByte);
				
				byte data[] = new byte[BUFFER_SIZE];
				int numRead;
				int loopCounter=0,orgStartByte = mStartByte;
				while((mState == DOWNLOADING) && ((numRead = in.read(data,0,BUFFER_SIZE)) != -1))
				{
					// write to buffer
					raf.write(data,0,numRead);
					// increase the startByte for resume later
					mStartByte += numRead;
					// increase the downloaded size
					downloaded(numRead);
					loopCounter++;
				}
				
				if (mState == DOWNLOADING) {
					System.out.println("["+mThreadID+"]-Expected["+(mEndByte-orgStartByte)
							+"], Read["+(mStartByte-orgStartByte)+"],looped["+loopCounter+"]");
					mIsFinished = true;
				}
			} catch (IOException e) {
				error();
			} finally {
				if (raf != null) {
					try {
						raf.close();
					} catch (IOException e) {}
				}
				
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {}
				}
			}
			
			System.out.println("End thread " + mThreadID);
		}*/
		
		@Override
		public void run(){
			final int START_POS = mStartByte;
			int currStartByte = mStartByte;
			int attempt = 0;
			boolean exit = false;
			while(!exit){
				long outerLoopStartTime = System.currentTimeMillis(); //Just for averages
				BufferedInputStream in = null;
				RandomAccessFile raf = null;
				try {
					// open Http connection to URL
					HttpURLConnection conn = (HttpURLConnection)mURL.openConnection();
					conn.setReadTimeout(60*1000); //Set a read timeout of 1 min
					
					// set the range of byte to download
					String byteRange = mStartByte + "-" + mEndByte;
					conn.setRequestProperty("Range", "bytes=" + byteRange);
					System.out.println("["+mThreadID+"]["+attempt+"] bytes=" + byteRange);
					attempt++; //May go on to be a failed attempt, but recording
					
					// connect to server
					conn.connect();
					
					// Make sure the response code is in the 200 range.
		            if (conn.getResponseCode() / 100 != 2) {
		                error();
		                break;
		            }
					
					// get the input stream
					in = new BufferedInputStream(conn.getInputStream());
					
					// open the output file and seek to the start location
					raf = new RandomAccessFile(mOutputFile, "rw");
					raf.seek(mStartByte);
					
					byte data[] = new byte[BUFFER_SIZE];
					int numRead;
					int loopCounter=0;
					while((mState == DOWNLOADING) && ((numRead = in.read(data,0,BUFFER_SIZE)) != -1))
					{
						// write to buffer, handle overrun
						if((mStartByte+numRead) > (mEndByte+1))
							raf.write(data,0,(mEndByte-mStartByte+1));
						else
							raf.write(data,0,numRead);
						// increase the startByte for resume later
						mStartByte += numRead;
						if(loopCounter%30==0){
							float perct = (mStartByte-START_POS)*100F/(mEndByte-START_POS);
							float kbps = ((mStartByte-currStartByte)*1000f)/((System.currentTimeMillis()-outerLoopStartTime)*1024f);
							System.out.println("["+mThreadID+"]["+attempt+"]["+loopCounter+"]-SS/LC/C/E/P/R-["+START_POS+"]["+currStartByte+"]["+mStartByte+"]["+mEndByte+"]["+perct+"%]["+kbps+"kB/s]");
						}
						// increase the downloaded size
						downloaded(numRead);
						loopCounter++;
						if(mStartByte >= mEndByte) break; //No need to continue
					}
					
					if (mState == DOWNLOADING) {
						/*System.out.println("["+mThreadID+"]["+attempt+"]-Expected["+(mEndByte-orgStartByte)
								+"], Read["+(mStartByte-orgStartByte)+"],looped["+loopCounter+"]");*/
					}
					if(mStartByte >= mEndByte){
						mIsFinished = true;
						exit = true;
						
					}
					
				} catch (IOException e) {
					//Mostly due to connectivity - sleeping here
					System.out.println("["+mThreadID+"]["+attempt+"]- Waiting for connectivity");
					try{Thread.sleep(2*60*1000);}
					catch(InterruptedException ie){/*ignore*/}
					//Not marking as error - error();
				} finally {
					currStartByte = mStartByte;
					if (raf != null) {
						try {
							raf.close();
						} catch (IOException e) {}
					}
					
					if (in != null) {
						try {
							in.close();
						} catch (IOException e) {}
					}
				}
			}//End while
			
			System.out.println("End thread " + mThreadID);
		}
	}
}
