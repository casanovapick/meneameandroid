package com.dcg.meneame;

import java.io.File;

import com.dcg.app.ApplicationMNM;
import com.dcg.dialog.VersionChangesDialog;
import com.dcg.meneame.R;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

/**
 * Our preference activity
 * @author Moritz Wundke (b.thax.dcg@gmail.com)
 */
public class Preferences extends PreferenceActivity {
	/** Class tag used for it's logs */
	private static final String TAG = "Preferences";
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ApplicationMNM.addLogCat(TAG);        
        ApplicationMNM.logCat(TAG, "onCreate()");
		
		// Add prefs from xml
		addPreferencesFromResource(R.xml.preferences);
		
		// Set version title!
		PreferenceScreen prefScreen = getPreferenceScreen();
		if ( prefScreen != null )
		{
			PreferenceGroup appPrefernce = (PreferenceGroup)prefScreen.getPreference(1);
			if ( appPrefernce != null )
			{
				Preference versionPrefernce = appPrefernce.getPreference(0);
				if ( versionPrefernce != null )
				{
					int resID = getResources().getIdentifier("version_change_v"+ApplicationMNM.getVersionNumber()+"_title", "string", "com.dcg.meneame");
					versionPrefernce.setTitle(resID);
				}
			}
		}
		
		// Set callback to know when we change our storage method
		Preference storagePref = findPreference("pref_app_storage");
		storagePref.setOnPreferenceChangeListener( new OnPreferenceChangeListener() {

			public boolean onPreferenceChange(Preference preference,
					Object newValue) {
				boolean bResult = true;
				String testValue = newValue.toString();
				
				// To use the SD card cache we need to have access to the SD card.
				if ( testValue.compareTo("SDCard") == 0 )
				{
					try
					{
						File directory = new File(ApplicationMNM.getRootSDcardFolder());
						bResult = directory.canWrite();
					} catch( Exception e ) {
						bResult = false;
					}
					if ( !bResult )
					{
						// Not writeable
						ApplicationMNM.showToast(R.string.clear_cache_sdcard_notwritebale);
					}
				}
				if ( bResult )
				{
					clearFeedCache();
				}
				return bResult;
			}
			
		});
	}
	
	/**
	 * Return storage type used
	 * @return
	 */
	public String getStorageType() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());        
        return prefs.getString("pref_app_storage", "SDCard");
	}
	
	public void  onContentChanged()
	{
		ApplicationMNM.logCat(TAG, "onContentChanged()");
		super.onContentChanged();
	}
	
	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		ApplicationMNM.logCat(TAG, "onContentChanged()");
		if ( preference.getKey().compareTo("pref_app_version_number") == 0 )
		{
			VersionChangesDialog versionDialog = new VersionChangesDialog(this);
        	versionDialog.show();
		}
		else if ( preference.getKey().compareTo("pref_app_clearcache") == 0 )
		{
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.confirm_clear_cache)
				.setCancelable(false)
				.setPositiveButton(R.string.generic_ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						clearFeedCache();
						dialog.dismiss();
					}
				})
				.setNegativeButton(R.string.generic_no, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
			AlertDialog clearCacheDialog = builder.create();
			clearCacheDialog.show();
		}
		// TODO Auto-generated method stub
		return super.onPreferenceTreeClick(preferenceScreen, preference);
	}
	
	/**
	 * Worker method that clears the current feed cache
	 * @return
	 */
	public boolean clearFeedCacheWorker() {
		boolean bResult = false;
		if ( getStorageType().compareTo("Internal") == 0 )
		{
			MeneameDbAdapter dBHelper = new MeneameDbAdapter(this);
			dBHelper.open();		
			bResult = dBHelper.deleteCompleteFeedCache();
			dBHelper.close();
		}
		else
		{
			bResult = ApplicationMNM.clearFeedCache();
		}
		return bResult;
	}
	
	/**
	 * Clear feed cache, file or DB.
	 */
	public void clearFeedCache() {		
		if ( clearFeedCacheWorker() )
		{
			ApplicationMNM.logCat(TAG, "Cache has been cleared!");
			ApplicationMNM.showToast(R.string.clear_cache_successfull);
		}
		else
		{
			ApplicationMNM.logCat(TAG, "Failed to clear cache!");
			ApplicationMNM.showToast(R.string.clear_cache_failed);
		}
	}

}