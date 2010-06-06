package com.dcg.meneame;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.dcg.adapter.FeedItemAdapter;
import com.dcg.adapter.FeedItemViewHolder;
import com.dcg.app.ApplicationMNM;
import com.dcg.dialog.AboutDialog;
import com.dcg.provider.FeedItemElement;
import com.dcg.provider.SystemValue;
import com.dcg.task.MenealoTask;
import com.dcg.task.RequestFeedTask;
import com.dcg.task.RequestFeedTaskParams;
import com.dcg.task.RequestFeedTask.RequestFeedListener;

/**
 * Basic activity that handles feed parsing and stuff like that
 * @author Moritz Wundke (b.thax.dcg@gmail.com)
 */
abstract public class FeedActivity extends ListActivity implements RequestFeedListener {
	
	/** Log tags */
	private static final String TAG = "FeedActivity";
	private static final String SYSTEM_VALUE_TAG = "SystemValue";
	
	/** Our RssWorkerThread class so subclasses will be able to call another one */
	protected static String mRssWorkerThreadClassName = "com.dcg.rss.RSSWorkerThread";
	protected static String mLocalRssWorkerThreadClassName = "com.dcg.rss.LocalRSSWorkerThread";

	/** Feed URL */
	protected String mFeedURL = "";
	
	/** 
	 * Semaphore used by the activities feed worker thread
	 * Do we need the semaphore? 
	 */
	//private Semaphore mSemaphore = new Semaphore(1);
	
	/** Our cached main list view */
	private ListView mListView = null;
	
	/** Refresh menu item id */
	private static final int MENU_REFRESH = 0;
	
	/** Notame menu item id */
    private static final int MENU_NOTAME = 1;
	
	/** Settings menu item id */
    private static final int MENU_SETTINGS = 2;
    
    /** About menu item id */
    private static final int MENU_ABOUT = 3;
    
    /** Sub activity ID's */
    private static final int SUB_ACT_SETTINGS_ID = 0;
    private static final int SUB_ACT_NOTAME_ID = 1;
    
    /** Context menu options */
    private static final int CONTEXT_MENU_OPEN = 0;
    private static final int CONTEXT_MENU_OPEN_SOURCE = 1;
    private static final int CONTEXT_MENU_VOTE = 2;
    
    /** Used to debug, will print all article ID for this feed tab into the log */
    public static final boolean mbPrintArticleIDsOnStart = false;
    
    /** Is this an article or an comments feed? */
    protected boolean mbIsArticleFeed;
    
    /** Are we paused or not? */
    protected boolean mbIsPaused;
    
    /** Are we loading a cached feed? */
    protected boolean mbIsLoadingCachedFeed;

    /** Request a feed from the meneame server */
    private RequestFeedTask mRequestFeedTask = null;
    
    public FeedActivity() {
		super();
		ApplicationMNM.addLogCat(TAG);
		ApplicationMNM.addLogCat(SYSTEM_VALUE_TAG);
		mbIsArticleFeed = true;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ApplicationMNM.logCat(TAG, getTabActivityTag()+"::onCreate()");
		
		if ( mbPrintArticleIDsOnStart )
		{
			// Form an array specifying which columns to return. 
			String[] projection = new String[] {
					FeedItemElement._ID,
					FeedItemElement.LINK_ID
					};
			
			final String[] arguments1 = new String[1];
			arguments1[0] = String.valueOf(getIndicatorStringID());
			final String where = FeedItemElement.FEEDID + "=?";
			
			// Make the query.
			Cursor cur = managedQuery(FeedItemElement.CONTENT_URI, projection, where, arguments1, null);
			
			// Print all articles we got out!
			if ( cur != null && cur.moveToFirst() )
			{
				int rowID = 0;
				do {
					ApplicationMNM.logCat(TAG, " ["+rowID+"]FeedItem: "+cur.getString(cur.getColumnIndex(FeedItemElement.LINK_ID)));
					rowID++;
				} while (cur.moveToNext() );
			}
			
			// Once we are finished close the cursor
			if ( cur != null )
			{
				cur.close();
			}
		}
		
		// Unpause
		mbIsPaused = false;
		
		// Perpare layout
		setContentView(R.layout.meneo_list);
		
		// Do final stuff
		setupViews();
		
		// Refresh if needed
		_conditionRefreshFeed();
	}
	
	@Override
	protected void onStart() {
		ApplicationMNM.logCat(TAG, getTabActivityTag()+"::onStart()");
		super.onStart();
	}
	
	@Override
	protected void onResume() {
		ApplicationMNM.logCat(TAG, getTabActivityTag()+"::onResume()");	
		super.onResume();
		
		// Restore app state if any
		restoreState();
		
		// Unpause
		mbIsPaused = false;
		
		// Set empty list textor loading if we got a task running
		TextView emptyTextView = (TextView) findViewById(android.R.id.empty);
		emptyTextView.setText(((mRequestFeedTask == null)?R.string.empty_list:R.string.refreshing_lable));
	}
	
	@Override
	protected void onRestart() {
		ApplicationMNM.logCat(TAG, getTabActivityTag()+"::onRestart()");
		super.onRestart();
	}
	
	@Override
	protected void onPause() {
		ApplicationMNM.logCat(TAG, getTabActivityTag()+"::onPause()");
		
		// Save state
		saveState();
		
		TextView emptyTextView = (TextView) findViewById(android.R.id.empty);
		emptyTextView.setText("");
		
		// Pause
		mbIsPaused = true;
		
		// Cleanup
		System.gc();
		
		super.onPause();
	}
	
	@Override
	protected void onStop() {
		ApplicationMNM.logCat(TAG, getTabActivityTag()+"::onStop()");
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		ApplicationMNM.logCat(TAG, getTabActivityTag()+"::onDestroy()");		
		// We want to close
		if ( isFinishing() )
		{
			// Stop current task if any
			if (mRequestFeedTask != null)
				mRequestFeedTask.requestStop(true);
			mRequestFeedTask = null;		
			// TODO: Save into the database our last viewed position to restore it later on		
			//setSystemValue(getFirstVisiblePositionSystemKey(), String.valueOf( mListView.getFirstVisiblePosition() ));
		}
		
		// Finish destroy
		super.onDestroy();
	}
	
	public String getFirstVisiblePositionSystemKey() {
		return "FeedActivity."+getFeedID()+".FirstVisiblePosKey";
	}
	
	/**
	 * Set a persisten system value
	 * @param key
	 * @param value
	 */
	public void setSystemValue( String key, String value ) {
		try {
			// Delete current value
			final String[] selectionArgs = new String[1];
			selectionArgs[0] = key;
			final String selection = SystemValue.KEY + "=?";
			getContentResolver().delete(SystemValue.CONTENT_URI, selection, selectionArgs);
			
			// Add new value
			final ContentValues values = new ContentValues();
			values.put(SystemValue.KEY, key);
			values.put(SystemValue.VALUE, value);
			getContentResolver().insert(SystemValue.CONTENT_URI, values);
			ApplicationMNM.logCat(SYSTEM_VALUE_TAG, "SystemValue "+key+"("+value+") set");
		} catch (SQLException e) {
			ApplicationMNM.logCat(SYSTEM_VALUE_TAG, "Failed to set system value "+key+":" + e.toString());
		}
	}
	
	/**
	 * Get a persistent system value
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	public String getSystemValue( String key, String defaultValue ) {
		String result = defaultValue;
		
		String[] projection = new String[] {
				SystemValue.VALUE
				};		
		final String[] selectionArgs = new String[1];
		selectionArgs[0] = key;
		final String selection = SystemValue.KEY + "=?";
		
		Cursor cur = getContentResolver().query(SystemValue.CONTENT_URI, projection, selection, selectionArgs, null);
		if ( cur != null && cur.moveToFirst() )
		{
			result = cur.getString(cur.getColumnIndex(SystemValue.VALUE));
			cur.close();
		}
		ApplicationMNM.logCat(SYSTEM_VALUE_TAG, "SystemValue "+key+"("+result+") recovered");
		return result;
	}
	
	/**
	 * Save state data into
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		ApplicationMNM.logCat(TAG, getTabActivityTag()+"::onSaveInstanceState()");
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);
		ApplicationMNM.logCat(TAG, getTabActivityTag()+"::onRestoreInstanceState()");	
	}
	
	/**
	 * Save the apps state into the database to be able to recover it again later
	 */
	private void saveState() {
		ApplicationMNM.logCat(TAG, getTabActivityTag()+"::saveState()");
		try {
			ApplicationMNM.logCat(TAG, " - First visible position: " + mListView.getFirstVisiblePosition());
			// Save state
		} catch( Exception e) {
			ApplicationMNM.warnCat(TAG, "Failed to save app state: "+e.toString());
		}
	}
	
	/**
	 * Restores a previously saved state into the database and will erase the cached
	 * data after restoring
	 */
	private void restoreState() {
		ApplicationMNM.logCat(TAG, getTabActivityTag()+"::restoreState()");
		try {
			// Restore state
		} catch( Exception e) {
			ApplicationMNM.warnCat(TAG, "Failed to restore app state: "+e.toString());
		}
	}
	/**
	 * IF we touch the screen and we do not have any feed and no request has been
	 * made refresh the feed from the net
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		ApplicationMNM.logCat(TAG, getTabActivityTag()+"::onTouchEvent()");
		// Only refresh on touch if no feed items there or we are not doing any feed task
		if ( !mbIsPaused &&
			 (
				 mRequestFeedTask == null &&
				 mListView != null &&
				 mListView.getAdapter() != null &&
				 mListView.getAdapter().getCount() == 0
			  )
			)
		{
			refreshFeed( false );
			return true;
		}
		return super.onTouchEvent(event);
	}
	
	/**
	 * Should we refresh in launch or not?
	 * @return
	 */
	public boolean shouldRefreshOnLaunch() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());        
        return prefs.getBoolean("pref_app_refreshonlaunch", false);
	}
	
	/**
	 * Refresh from an existing feed or should we start a new request?
	 */
	private void _conditionRefreshFeed() {
    	if ( shouldRefreshOnLaunch() )
        {
        	refreshFeed( false );
        }
	}
	
	/**
	 * Set a cursor adapter for our list
	 */
	protected void setCursorAdapter() {
		// TODO: Use: setFilterText(queryString); to set the filter.
		mListView.setAdapter(new FeedItemAdapter(
				this, 
				FeedItemElement.FEEDID+"=?",
				new String[]{String.valueOf(getFeedID())},
				getFeedItemType()));
		
		// TODO Look if we got any saved position to be set to!
		/*
		try {
			int firstVisiblePosition = Integer.parseInt(getSystemValue(getFirstVisiblePositionSystemKey(), "-1"));
			if ( firstVisiblePosition >= 0)
			{
				// Set position and reset system value
				mListView.setSelection(firstVisiblePosition);
				setSystemValue(getFirstVisiblePositionSystemKey(), "-1");
			}
		} catch ( Exception e ) {
			// Nothing to be done here
		}
		/**/
	}
	
	/**
	 * Setup view
	 */
	protected void setupViews() {
		mListView = getListView();
		
		if ( mListView != null )
		{
			// Set adapter
			setCursorAdapter();
			
			// Set basic ListView stuff
			mListView.setTextFilterEnabled(true);
			
			// Add context menu
			mListView.setOnCreateContextMenuListener( 
					new View.OnCreateContextMenuListener() {
						public void onCreateContextMenu(ContextMenu menu, View view,ContextMenu.ContextMenuInfo menuInfo) {
							menu.add(0, CONTEXT_MENU_OPEN, 0, R.string.meneo_item_open);
							if ( mbIsArticleFeed )
							{
								menu.add(0, CONTEXT_MENU_OPEN_SOURCE, 0, R.string.meneo_item_open_source);
								menu.add(0, CONTEXT_MENU_VOTE, 0, R.string.meneo_item_vote);
							}
						}
					});
		}
		else
		{
			ApplicationMNM.warnCat(TAG,"No ListView found in layout for " + this.toString());
		}
	}
	
	/**
	 * Returns the URL this feed points too
	 * @return String - FeedURL
	 */
	public String getFeedURL() {
		return mFeedURL;
	}
	
	/**
	 * Returns the tag this activity will hold in the main TabWidget
	 * @return String - TabTag
	 */
	public String getTabActivityTag() {
		return "";
	}
	
	/**
	 * String id used for the tab indicator
	 * @return
	 */
	public int getIndicatorStringID() {
		return -1;
	}
	
	/**
	 * Return the ID used for this feed tab
	 */
	public int getFeedID() {
		return getIndicatorStringID();
	}
	
	/**
	 * Returns the tag this activity will hold in the main TabWidget
	 * @return String - TabTag
	 */
	public static String static_getTabActivityTag() {
		return "";
	}
	
	/**
	 * String id used for the tab indicator
	 * @return
	 */
	public static int static_getIndicatorStringID() {
		return -1;
	}
	
	/**
	 * By default we will use articels
	 * @return
	 */
	public int getFeedItemType() {
		return FeedItemElement.TYPE_ARTICLE;
	}
	
	/**
	 * Will refresh the current feed
	 */
	public void refreshFeed( boolean bUseCache ) {		
		// Start thread if not started or not alive
		// If we are loading a cached feed to we are pause we can not start!
		if ( !mbIsPaused &&  mRequestFeedTask == null )
		{			
			mbIsLoadingCachedFeed = bUseCache;
			
			// Create all params we need for our feed request
			RequestFeedTaskParams mTaskParams = new RequestFeedTaskParams();
			mTaskParams.mMaxItems = -1;
			mTaskParams.mItemClass = "com.dcg.rss.ArticleFeedItem";
			mTaskParams.mURL = mFeedURL;
			mTaskParams.mParserClass = "com.dcg.rss.FeedParser";
			mTaskParams.mFeedListener = this;
			mTaskParams.mFeedID = getIndicatorStringID();
			
			// Create task and run it
			mRequestFeedTask = new RequestFeedTask(this);
			mRequestFeedTask.execute(mTaskParams);
			
			// Clear the current list adapter!
			setListAdapter(null);
			
			// Change empty text so that the user knows when it's all done
			TextView emptyTextView = (TextView) findViewById(android.R.id.empty);
			emptyTextView.setText(R.string.refreshing_lable);
		}
		else
		{
			// Mhh already a feed active :P
		}
	}
	
	/**
	 * Called once we finished requesting the feed
	 */
	public void onFeedFinished(Integer resultCode) {
		if ( resultCode == ApplicationMNM.ERROR_SUCCESSFULL )
		{	
			// Null task
			mRequestFeedTask = null;
			
			// Set the cursor adapter
			setCursorAdapter();
			
			// Set empty list text		
			TextView emptyTextView = (TextView) findViewById(android.R.id.empty);
			emptyTextView.setText(R.string.empty_list);
		}
	}
	
	/**
	 * Returns the class name of the list adapter we should use
	 * @return
	 */
	public String getListAdapterClassName() {
		return "com.dcg.adapter.ArticlesAdapter";
	}
	
	/**
	 * Return storage type used
	 * @return
	 */
	public String getStorageType() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());        
        return prefs.getString("pref_app_storage", "SDCard");
	}
	
	/* Creates the menu items */
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_REFRESH, 0, R.string.main_menu_refresh).setIcon(R.drawable.ic_menu_refresh);
        menu.add(1, MENU_NOTAME, 0, R.string.main_menu_notame).setIcon(android.R.drawable.ic_menu_send);
    	menu.add(1, MENU_SETTINGS, 0, R.string.main_menu_settings).setIcon(android.R.drawable.ic_menu_preferences);
    	menu.add(1, MENU_ABOUT, 0, R.string.main_menu_about).setIcon(android.R.drawable.ic_menu_info_details);
    	return true;
    }
    
    /** */
    public boolean onPrepareOptionsMenu(Menu menu) {
    	menu.setGroupEnabled(0, mRequestFeedTask == null);
    	return true;
    }
    
    /* Handles item selections */
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) 
        {
        case MENU_REFRESH:
            // Refresh !   	
        	refreshFeed( false );
            return true;
        case MENU_NOTAME:
        	// Open notame activity
        	openNotameScreen();
        	return true;
        case MENU_SETTINGS:
            // Open settitngs screen
        	openSettingsScreen();
            return true;
        case MENU_ABOUT:
        	AboutDialog aboutDialog = new AboutDialog(this);
        	aboutDialog.show();
        	return true;
        }
        return false;
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
    	final FeedItemViewHolder holder = (FeedItemViewHolder) info.targetView.getTag();

		switch (item.getItemId()) 
		{
	    	case CONTEXT_MENU_OPEN:
	        case CONTEXT_MENU_OPEN_SOURCE:
	    			String url = "";
	    			if (item.getItemId() == CONTEXT_MENU_OPEN)
	    			{
	    				url = holder.link;
	    				ApplicationMNM.showToast(getResources().getString(R.string.context_menu_open));
	    			}
	    			else
	    			{
	    				url = (String)holder.url.getText();
	    				ApplicationMNM.showToast(getResources().getString(R.string.context_menu_open_source));
	    			}
	    			try
	    			{
	    				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
	    			} catch ( Exception e )
	    			{
	    				ApplicationMNM.warnCat(TAG, "Can not open URI in browser: " + e.toString());
	    			}
	    		
	        	return true;
	    	case CONTEXT_MENU_VOTE:
	    		new MenealoTask(this).execute(holder.link_id);
	        	return true;
        }
    	return false;
    }
    
    /**
     * Open settings screen 
     */
    public void openSettingsScreen() {
    	Intent settingsActivity = new Intent( this, Preferences.class);
    	startActivityForResult(settingsActivity, SUB_ACT_SETTINGS_ID);
    }
    
    /**
     * Open notame activity
     */
    public void openNotameScreen() {
    	if ( hasNotameDataSetup() )
    	{
    		Intent notameActivity = new Intent( this, NotameActivity.class);
    		startActivityForResult(notameActivity, SUB_ACT_NOTAME_ID);
    	}
    	else
    	{
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.notame_setup_data)
				.setCancelable(false)
				.setTitle(R.string.notame_setup_data_tilte)
				.setPositiveButton(R.string.generic_ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						openSettingsScreen();
						dialog.dismiss();
					}
				})
				.setNegativeButton(R.string.generic_no, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
			AlertDialog openSettingsDialog = builder.create();
			openSettingsDialog.show();
    	}
    }
    
    /**
     * Did the user set the needed notame data or not?
     * @return
     */
    public boolean hasNotameDataSetup() {
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());        
		String userName = prefs.getString("pref_account_user", "");
		String APIKey = prefs.getString("pref_account_apikey", "");
		return userName.compareTo("") != 0 && APIKey.compareTo("") != 0;
    }
}