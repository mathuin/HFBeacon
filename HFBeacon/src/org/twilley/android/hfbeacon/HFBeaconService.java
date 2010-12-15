package org.twilley.android.hfbeacon;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.preference.PreferenceManager;
import android.util.Log;

public class HFBeaconService extends Service {
	private static final String TAG = "HFBeaconService";
	// TODO: move these strings to the resources directory
	public static final String UPDATEBEACONS = "org.twilley.android.hfbeacon.UPDATEBEACONS";
	public static final String CONTENTS = "org.twilley.android.hfbeacon.beacons";
	private static final String BAND = "band";
	private static final String OFFSET = "offset";
	private static final int TEN_SECONDS_IN_MILLIS = 1000 * 10;
	private int band;
	private int offset;
	private int numBeacons = 18;
	private Handler mHandler;
	private SharedPreferences app_preferences;

	public class HFBeaconBinder extends Binder {
		// TODO: move these strings to the resources directory
		public static final int SETBAND = 0;
		public static final int SETOFFSET = 1;
		public static final String TYPEKEY = "type";
		public static final String VALUEKEY = "value";
		public static final int OFFSETMANUAL = 0;
		public static final int OFFSETNTP = 1;
		public static final int OFFSETCLOCKSYNC = 2;

		HFBeaconService getService() {
            return HFBeaconService.this;
        }
        
    	@Override
    	protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
    		Log.v(TAG, "entering onTransact");
    		
			long now = System.currentTimeMillis();
    		boolean returnCode = false;
			Bundle values;
			int newValue;
			int newType;
   		
			switch(code) {
    		case SETBAND:
    			values = data.readBundle();
    			newValue = values.getInt(VALUEKEY);
    			Log.i(TAG, "request to change band from " + band + " to " + newValue);
    			band = newValue;
    			// run the beacon update thing
    			broadcastBeacon(now);
    			returnCode = true;
    			break;
    		case SETOFFSET:
    			values = data.readBundle();
    			newType = values.getInt(TYPEKEY);
				switch(newType) {
    			case OFFSETMANUAL:
    				newValue = values.getInt(VALUEKEY);
    				Log.i(TAG, "request to change offset from " + offset + " to " + newValue);
    				offset = newValue;
    				returnCode = true;
    				break;
    			case OFFSETNTP:
    				Log.e(TAG, "NTP offset not yet implemented");
    				returnCode = false;
    				break;
    			case OFFSETCLOCKSYNC:
    				Log.e(TAG, "ClockSync offset not yet implemented");
    				returnCode = false;
    				break;
    			default:
    				Log.e(TAG, "invalid offset type received: " + newType);
    				returnCode = false;
    			}
				if (returnCode == true) {
					long next = broadcastBeacon(now);
					Log.d(TAG, "removing callbacks");
					mHandler.removeCallbacks(beaconBroadcaster);
					Log.d(TAG, "postDelayed in " + (next - System.currentTimeMillis()) + " milliseconds");
					mHandler.postDelayed(beaconBroadcaster, next - System.currentTimeMillis());
					returnCode = true;
				}
				break;
    		default:
    			Log.e(TAG, "invalid transaction code received: " + code);
    		}
    		
    		return returnCode;
    	  }
    }
	
	final IBinder mBinder = new HFBeaconBinder();

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.v(TAG, "entered onCreate");

		// calculate next event
		long next = ((System.currentTimeMillis() / TEN_SECONDS_IN_MILLIS) + 1) * TEN_SECONDS_IN_MILLIS + (offset % TEN_SECONDS_IN_MILLIS);
		
		// open preferences
		Log.d(TAG, "opening preferences");
		app_preferences = PreferenceManager.getDefaultSharedPreferences(this);
		band = app_preferences.getInt(BAND, 0);
		offset = app_preferences.getInt(OFFSET, 0);
		
		// configure mHandler
		mHandler = new Handler();
		Log.d(TAG, "postDelayed in " + (next - System.currentTimeMillis()) + " milliseconds");
		mHandler.postDelayed(beaconBroadcaster, next - System.currentTimeMillis());
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.v(TAG, "entered onStartCommand");
		
		Log.d(TAG, "intent: " + intent + ", startId: " + startId);
		
	    return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		Log.v(TAG, "entered onDestroy");

		// okay, we're storing and retrieving band and offset to preferences
		// eventually we'll have to store offset type and value
		Log.d(TAG, "saving preferences");
		SharedPreferences.Editor editor = app_preferences.edit();
		editor.putInt(BAND, band);
		editor.putInt(OFFSET, offset);
		editor.commit();
		
		// drop the mHandler stuff
		Log.d(TAG, "removing callbacks");
		mHandler.removeCallbacks(beaconBroadcaster);
	}

	/** Actually broadcast the beacon and return the next time */
	private long broadcastBeacon(long now) {
		Log.v(TAG, "entered broadcastBeacon");

		Log.d(TAG, "band = " + band);
		Log.d(TAG, "offset = " + offset);
		int beacon = (int) ((now - offset) % (numBeacons * TEN_SECONDS_IN_MILLIS) / TEN_SECONDS_IN_MILLIS) - band;
		int pastBeacon = (numBeacons + beacon - 1) % numBeacons;
		int presentBeacon = (numBeacons + beacon) % numBeacons;
		int futureBeacon = (numBeacons + beacon + 1) % numBeacons;
		Log.d(TAG, "beacons = " + pastBeacon + ", " + presentBeacon + ", " + futureBeacon);
		int beacons[] = {pastBeacon, presentBeacon, futureBeacon};

		Intent intent = new Intent(UPDATEBEACONS);
		intent.putExtra(CONTENTS, beacons);
		Log.i(TAG, "sending broadcast" + intent);
		sendBroadcast(intent);
				
		return ((now / TEN_SECONDS_IN_MILLIS) + 1) * TEN_SECONDS_IN_MILLIS + (offset % TEN_SECONDS_IN_MILLIS);
	}
	
	/** Runs every ten seconds updating beacons */
	private final Runnable beaconBroadcaster = new Runnable() {
		public void run() {
			Log.v(TAG, "entered run");

			// actually broadcast the beacons
			long now = System.currentTimeMillis();
			long next = broadcastBeacon(now);
			
			// reset mHandler for next event
			Log.d(TAG, "removing callbacks");
			mHandler.removeCallbacks(this);
			Log.d(TAG, "postDelayed in " + (next - System.currentTimeMillis()) + " milliseconds");
			mHandler.postDelayed(this, next - System.currentTimeMillis());
		}
	};
}
