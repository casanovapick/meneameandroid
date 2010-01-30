package com.dcg.meneame;

import java.util.concurrent.Semaphore;

import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

import android.app.Application;
import android.util.Log;

public class ApplicationMNM extends Application {
	
	/** log tag for this class */
	private static final String TAG = "ApplicationMNM";
	
	/** Semaphore used by our rss worker thread */
	private final Semaphore mRssSemaphore = new Semaphore(1);
	
	/** Shared HttpClient used by our application */
	private HttpClient mHttpClient = null;
	
	/** Current Rss thread */
	private RssWorkerThread mRssThread = null;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		// Create shared HttpClient
		mHttpClient = createHttpClient();
	}
	
	public void registerRssWorkerThread( RssWorkerThread newThread ) {
		mRssThread = newThread;
		Log.d(TAG,"registerRssWorkerThread() > " + mRssThread.toString() + " registered");
	}
	
	public void stopRssWorkerThread() {
		Log.d(TAG,"stopRssWorkerThread()");
		if ( mRssThread != null ) 
		{
			Log.d(TAG,"stopRssWorkerThread() > " + mRssThread.toString() + " stopped");
			if ( mRssThread.isAlive() )
			{
				mRssThread.requestStop();
			}
			mRssThread = null;
		}
		else
		{
			Log.d(TAG,"stopRssWorkerThread() > no worker thread registered");
		}
	}
	
	/**
	 * Aquire Rss Semaphore
	 * @throws InterruptedException
	 */
	public void acquireRssSemaphore() throws InterruptedException {
		mRssSemaphore.acquire();
	}
	
	/**
	 * Release Rss Semaphore
	 */
	public void releaseRssSemaphore() {
		mRssSemaphore.release();
	}
	
	@Override
	public void onLowMemory()
	{
		super.onLowMemory();
		shutdownHttpClient();
		stopRssWorkerThread();
	}
	
	@Override
	public void onTerminate()
	{
		super.onTerminate();
		shutdownHttpClient();
		stopRssWorkerThread();
	}
	
	private HttpClient createHttpClient()
	{
		Log.d(TAG,"createHttpClient()");
		
		HttpParams params = new BasicHttpParams();
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		HttpProtocolParams.setContentCharset(params, HTTP.DEFAULT_CONTENT_CHARSET);
		HttpProtocolParams.setUseExpectContinue(params, true);
		
		// Register http/s shemas!
		SchemeRegistry schReg = new SchemeRegistry();
		schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		schReg.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
		ClientConnectionManager conMgr = new ThreadSafeClientConnManager(params,schReg);
		
		return new DefaultHttpClient(conMgr, params);
	}
	
	/**
	 * Returns the sharde http client
	 * @return HttpClient
	 */
	public HttpClient getHttpClient() {
		return mHttpClient;
	}
	
	/**
	 * Shutdown current HttpClient's connection
	 */
	private void shutdownHttpClient()
	{
		if(mHttpClient!=null && mHttpClient.getConnectionManager()!=null)
		{
			mHttpClient.getConnectionManager().shutdown();
		}
	}
}
