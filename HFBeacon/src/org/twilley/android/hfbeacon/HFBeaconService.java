package org.twilley.android.hfbeacon;

import java.util.Calendar;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class HFBeaconService extends Service {
	private static final String TAG = "HFBeaconService";
	public static final String UPDATEBEACONS = "org.twilley.android.hfbeacon.UPDATEBEACONS";
	public static final String CONTENTS = "org.twilley.android.hfbeacon.beacons";
	private static boolean logging = true;
	private static final int TEN_SECONDS_IN_MILLIS = 1000 * 10;
	private int offset = 1000 * 5;
	private Handler handler;
	private Context context = this;

	public class HFBeaconBinder extends Binder {
        HFBeaconService getService() {
            return HFBeaconService.this;
        }
    }
	
	private final IBinder mBinder = new HFBeaconBinder();

	// TODO: figure out how to take messages from activity
	// here's what we need to know:
	// what kind of offset (clocksync, apache, manual)
	// if manual, how much
	// what band the user has selected
	// here's when we need to know it:
	// at start time, and when it changes
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (logging == true)
			Log.v(TAG, "entered onCreate");

		// calculate next event
		// TODO: add local offset here too
		long now = System.currentTimeMillis();
		long next = ((now / TEN_SECONDS_IN_MILLIS) + 1) * TEN_SECONDS_IN_MILLIS + offset;
		if (logging == true)
			Log.d(TAG, "now: " + now + ", next: " + next);

		// configure handler
		handler = new Handler();
		if (logging == true)
			Log.d(TAG, "removing callbacks");
		handler.removeCallbacks(broadcastBeacon);
		if (logging == true)
			Log.d(TAG, "postDelayed in " + (next - System.currentTimeMillis()) + "milliseconds");
		handler.postDelayed(broadcastBeacon, next - System.currentTimeMillis());
		
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (logging == true)
			Log.v(TAG, "entered onStartCommand");
		
		if (logging == true)
			Log.d(TAG, "intent: " + intent + ", startId: " + startId);
		
	    return START_NOT_STICKY;
	}
	
	@Override
	public void onDestroy() {
		if (logging == true)
			Log.v(TAG, "entered onDestroy");
		// drop the handler stuff
		handler.removeCallbacks(broadcastBeacon);
	}
	
	/** Runs every ten seconds updating beacons */
	private final Runnable broadcastBeacon = new Runnable() {
		public void run() {
			if (logging == true)
				Log.v(TAG, "entered run");

			// calculate next event
			// TODO: apply local offset here!
			long now = System.currentTimeMillis();
			long next = ((now / TEN_SECONDS_IN_MILLIS) + 1) * TEN_SECONDS_IN_MILLIS + offset;

			// set the current band (and the beacons!)
			Calendar currentcal = Calendar.getInstance();
			// TODO: band needs to be set!
			int band = 0;
			// TODO: numbeacons needs to be set!
			int numBeacons = 18;
			int beacon = (((currentcal.get(Calendar.MINUTE) % 3) * 60 + currentcal.get(Calendar.SECOND)) / 10) - band;
			int pastBeacon = (numBeacons + beacon - 1) % numBeacons;
			int presentBeacon = (numBeacons + beacon) % numBeacons;
			int futureBeacon = (numBeacons + beacon + 1) % numBeacons;
			int beacons[] = {pastBeacon, presentBeacon, futureBeacon};
			if (logging == true) 
				Log.d(TAG, "beacons = " + beacons);

			// set the textviews and stuff for band and beacons
			// We are sending this to a specific recipient, so we will
			// only specify the recipient class name. 
			Intent intent = new Intent(UPDATEBEACONS);
			intent.putExtra(CONTENTS, beacons);
			if (logging == true)
				Log.d(TAG, "sending broadcast" + intent);
			sendBroadcast(intent);
			
			// reset handler for next event
			handler.removeCallbacks(this);
			handler.postDelayed(this, next - System.currentTimeMillis());
		}
	};
}
