package org.twilley.android.hfbeacon;

import java.util.Calendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

// Change list here:
// TODO: add local offset feature
// TODO: add multiple screen size support
// TODO: add degree-minute/degree-minute-second to preferences
// TODO: default to fixed (i.e., single location sample) versus mobile
public class HFBeaconActivity extends Activity {
	private static final String TAG = "HFBeaconActivity";
	private static final String BAND = "band";
	// time values
	private static final int TEN_SECONDS_IN_MILLIS = 1000 * 10;
	private static final int ONE_MINUTE_IN_MILLIS = 1000 * 60 * 2;
	private static final int TWO_MINUTES_IN_MILLIS = 1000 * 60 * 2;
	// distance values
	private static final int HALF_MINUTE_IN_METERS = 15 * 60;
	// sigh
	private static final int MENU_INFO = 0;
	private static boolean logging = true;
	private SharedPreferences app_preferences;
	private int band;
	private int beacon;
	private int numBeacons;
	private int pastBeacon;
	private int presentBeacon;
	private int futureBeacon;
	private CharSequence[] callsigns;
	private CharSequence[] regions;
	private CharSequence[] latitudes;
	private CharSequence[] longitudes;
	private CharSequence[] bearings;
	private CharSequence[] ranges;
	private Handler handler;
	private LocationManager locmanager = null;
	private Criteria criteria = null;
	private String provider = null;
	private Location location = null;
	private Location currentBestLocation = null;
	private String GridSquare = "---";
	private String ReadableLatitude = "---";
	private String ReadableLongitude = "---";
	private Calendar currentcal;
	private IntentFilter mIntentFilter;
	private HFBeaconService mBoundService;
	private boolean mIsBound;

	/* from http://developer.android.com/guide/topics/location/obtaining-user-location.html */
	
	/** Determines whether one Location reading is better than the current Location fix
	  * @param location  The new Location that you want to evaluate
	  * @param currentBestLocation  The current Location fix, to which you want to compare the new one
	  */
	protected boolean isBetterLocation(Location location, Location currentBestLocation) {
		if (logging == true)
			Log.v(TAG, "entered isBetterLocation");

		if (currentBestLocation == null) {
	        // A new location is always better than no location
	        return true;
	    }

	    // Check whether the new location fix is newer or older
	    long timeDelta = location.getTime() - currentBestLocation.getTime();
	    boolean isSignificantlyNewer = timeDelta > TWO_MINUTES_IN_MILLIS;
	    boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES_IN_MILLIS;
	    boolean isNewer = timeDelta > 0;

	    // If it's been more than two minutes since the current location, use the new location
	    // because the user has likely moved
	    if (isSignificantlyNewer) {
	        return true;
	    // If the new location is more than two minutes older, it must be worse
	    } else if (isSignificantlyOlder) {
	        return false;
	    }

	    // Check whether the new location fix is more or less accurate
	    int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
	    boolean isLessAccurate = accuracyDelta > 0;
	    boolean isMoreAccurate = accuracyDelta < 0;
	    boolean isSignificantlyLessAccurate = accuracyDelta > 200;

	    // Check if the old and new location are from the same provider
	    boolean isFromSameProvider = isSameProvider(location.getProvider(),
	            currentBestLocation.getProvider());

	    // Determine location quality using a combination of timeliness and accuracy
	    if (isMoreAccurate) {
	        return true;
	    } else if (isNewer && !isLessAccurate) {
	        return true;
	    } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
	        return true;
	    }
	    return false;
	}

	/** Checks whether two providers are the same */
	private boolean isSameProvider(String provider1, String provider2) {
		if (logging == true)
			Log.v(TAG, "entered isSameProvider");
	    if (provider1 == null) {
	      return provider2 == null;
	    }
	    return provider1.equals(provider2);
	}
	
	// WHEE
	private final LocationListener loclistener = new LocationListener() {
		@Override
		public void onLocationChanged(Location location) {
			float[] results = new float[2];

			if (logging == true)
				Log.v(TAG, "entered onLocationChanged");
			if (isBetterLocation(location, currentBestLocation)) {
				if (logging == true)
					Log.d(TAG, "new location is better than current best location");
				currentBestLocation = location;
				
				// update variables
				GridSquare = gridSquare(location);
				ReadableLatitude = readableLatitude(location);
				ReadableLongitude = readableLongitude(location);

				// calculate range/bearing
				for (int i = 0; i < callsigns.length; i++) {
					Location.distanceBetween(location.getLatitude(), location.getLongitude(), Location.convert(latitudes[i].toString()), Location.convert(longitudes[i].toString()), results);
					ranges[i] = "" + (int) (results[0] / 1000);
					int rawBearing = (360 + (int) results[1]) % 360;
					bearings[i] = ((rawBearing < 100) ? "0" : "") + ((rawBearing < 10) ? "0" : "") + rawBearing;
				}
				if (logging == true)
					Log.d(TAG, "calling updateLocation from onLocationChanged");
				updateLocation(location);
			}
		}

		@Override
		public void onProviderDisabled(String oldprovider) {
			if (logging == true)
				Log.v(TAG, "entered onProviderDisabled");
			if (oldprovider == provider) {
				getBestProvider();
			}
		}

		@Override
		public void onProviderEnabled(String newprovider) {
			if (logging == true)
				Log.v(TAG, "entered onProviderEnabled");
			if (newprovider != provider) {
				getBestProvider();
			}
		}

		@Override
		public void onStatusChanged(String oldprovider, int status, Bundle extras) {
			if (logging == true)
				Log.v(TAG, "entered onStatusChanged");
			if ((oldprovider == provider && (status == LocationProvider.OUT_OF_SERVICE || 
											 status == LocationProvider.TEMPORARILY_UNAVAILABLE)) ||
				(oldprovider != provider && status == LocationProvider.AVAILABLE))
				getBestProvider();
		}
	};

	// TODO: http://code.google.com/p/android/issues/detail?id=7849 <-- does this affect me?
	
	/** Gets the current best location service provider */
	private void getBestProvider() {
		if (logging == true)
			Log.v(TAG, "entered getBestProvider");
		
		// TODO: move manager and criteria elsewhere to some other function? 
		// manager
		if (locmanager == null)
			locmanager = (LocationManager) getSystemService(LOCATION_SERVICE);
		
		// criteria
		if (criteria == null) {
			criteria = new Criteria();
			criteria.setAccuracy(Criteria.ACCURACY_COARSE);
		}
		
		// provider
		provider = locmanager.getBestProvider(criteria, true);
		if (provider == null) {
			if (logging == true)
				Log.d(TAG, "no provider enabled at all");
			showLocationDisabled(this);
		} else {
			if (logging == true)
				Log.d(TAG, "best provider is " + provider);
		}
	}

	/** Listens for service messages */
	private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
		@Override
	    public void onReceive(Context context, Intent intent) {
			if (logging == true)
				Log.v(TAG, "entered onReceive");
			
			String action = intent.getAction();
			Bundle extras = intent.getExtras();
			if (logging == true)
				Log.d(TAG, "action: " + action + ", extras: " + extras);
			
			if (action.equals(HFBeaconService.UPDATEBEACONS)) {
				if (logging == true)
					Log.d(TAG, "received intent to update beacons");
				
				// what beacons are they
				int beacons[] = extras.getIntArray(HFBeaconService.CONTENTS);
				int pastBeacon = beacons[0]; 
				int presentBeacon = beacons[1]; 
				int futureBeacon = beacons[2]; 
				if (logging == true)
					Log.d(TAG, "beacons = " + pastBeacon + ", " + presentBeacon + ", " + futureBeacon);

				// set the textviews and stuff for band and beacons
				if (logging == true)
					Log.d(TAG, "running setBeacons from onReceive");
				// TODO: actually set beacons from here!
				//setBeacons();
			} else {
		    	if (logging == true)
		    		Log.d(TAG, "received unknown intent: " + action);
			}
	    }
	};

	/** Runs every ten seconds updating beacons */
	private final Runnable updateBeacons = new Runnable() {
		public void run() {
			if (logging == true)
				Log.v(TAG, "entered run");

			// calculate next event
			// TODO: apply local offset here!
			long now = System.currentTimeMillis();
			long next = ((now / TEN_SECONDS_IN_MILLIS) + 1) * TEN_SECONDS_IN_MILLIS;

			// set the current band (and the beacons!)
			currentcal = Calendar.getInstance();
			beacon = (((currentcal.get(Calendar.MINUTE) % 3) * 60 + currentcal.get(Calendar.SECOND)) / 10) - band;
			pastBeacon = (numBeacons + beacon - 1) % numBeacons;
			presentBeacon = (numBeacons + beacon) % numBeacons;
			futureBeacon = (numBeacons + beacon + 1) % numBeacons;
			if (logging == true) 
				Log.d(TAG, "beacons = " + pastBeacon + ", " + presentBeacon + ", " + futureBeacon);

			// set the textviews and stuff for band and beacons
			if (logging == true)
				Log.d(TAG, "running setBeacons from updateBeacons");
			setBeacons();
			
			// reset handler for next event
			handler.removeCallbacks(this);
			handler.postDelayed(this, next - System.currentTimeMillis());
		}
	};

	/** Converts location into Maidenhead grid square as described in http://en.wikipedia.org/wiki/Maidenhead_Locator_System */
	private String gridSquare(Location location) {
		if (logging == true)
			Log.v(TAG, "entered gridSquare");

		final String[] field = { "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R" };
		final String[] digit = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" };
		final String[] sub = { "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x" }; 

		// Make longitude and latitude both positive
		double myLongitude = location.getLongitude() + 180;
		double myLatitude = location.getLatitude() + 90;

		// field consists of two uppercase letters, one letter per ten degrees of latitude or twenty degrees of longitude
		int longField = (int) myLongitude / 20;
		int latField = (int) myLatitude / 10;
		// square consists of two digits, one digit per one degree of latitude or two degrees of longitude
		int longDigit = (int) (myLongitude - (longField * 20)) / 2;
		int latDigit = (int) (myLatitude - (latField * 10)) / 1;
		// subsquare consists of two lowercase letters, one letter per 2.5 minutes of latitude or 5 minutes of longitude 
		int longSub = (int) (myLongitude * 60 / 5) % 24;
		int latSub = (int) (myLatitude * 60 / 2.5) % 24;
		
		return field[longField] + field[latField] +	digit[longDigit] + digit[latDigit] + sub[longSub] + sub[latSub];
	}
	
	/** Converts raw latitude string value to a more user-friendly format */
	private String readableLatitude(Location location) {
		if (logging == true) 
			Log.v(TAG, "entered readableLatitude");
		
		String[] latitude = Location.convert(location.getLatitude(), Location.FORMAT_SECONDS).split(":");

		// what format do I want to use?
		// 12<deg>34<min>56.789<sec> [N|S]
		return latitude[0].replaceFirst("-", "") + '\u00B0' + latitude[1] + '\'' + latitude[2] + "\" " + ((latitude[0].startsWith("-")) ? "S" : "N");
	}
	
	/** Converts raw longitude string value to a more user-friendly format */
	private String readableLongitude(Location location) {
		if (logging == true)
			Log.v(TAG, "entered readableLongitude");
		
		String[] longitude = Location.convert(location.getLongitude(), Location.FORMAT_SECONDS).split(":");

		// what format do I want to use?
		// 12<deg>34<min>56.789<sec> [E|W]
		return longitude[0].replaceFirst("-", "") + '\u00B0' + longitude[1] + '\'' + longitude[2] + "\" " + ((longitude[0].startsWith("-")) ? "W" : "E");
	}
	
	/** Updates location and recalculates range and bearing */
	private void updateLocation(Location location) {
		if (logging == true)
			Log.v(TAG, "entered updateLocation");
		
		// calculate Maidenhead grid square and store in textview
		TextView maidenheadValue = (TextView) this.findViewById(R.id.maidenheadValue);
		maidenheadValue.setText(GridSquare);

		// store location in textview
		TextView latitudeValue = (TextView) this.findViewById(R.id.latitudeValue);
		latitudeValue.setText(ReadableLatitude);
		TextView longitudeValue = (TextView) this.findViewById(R.id.longitudeValue);
		longitudeValue.setText(ReadableLongitude);
		
		// update beacon ranges and bearings
		TextView pastBearing = (TextView) this.findViewById(R.id.pastBearing);
		pastBearing.setText(bearings[pastBeacon]);
		TextView pastRange = (TextView) this.findViewById(R.id.pastRange);
		pastRange.setText(ranges[pastBeacon]);
		TextView presentBearing = (TextView) this.findViewById(R.id.presentBearing);
		presentBearing.setText(bearings[presentBeacon]);
		TextView presentRange = (TextView) this.findViewById(R.id.presentRange);
		presentRange.setText(ranges[presentBeacon]);
		TextView futureBearing = (TextView) this.findViewById(R.id.futureBearing);
		futureBearing.setText(bearings[futureBeacon]);
		TextView futureRange = (TextView) this.findViewById(R.id.futureRange);
		futureRange.setText(ranges[futureBeacon]);		
	}

	/** Sets past, present, and future beacons based on current band and time */
	private void setBeacons() {
		if (logging == true) 
			Log.v(TAG, "entered setBeacons");

		// display past, present and future values
		TextView pastCallsign = (TextView) this.findViewById(R.id.pastCallsign);
		pastCallsign.setText(callsigns[pastBeacon]);
		TextView pastRegion = (TextView) this.findViewById(R.id.pastRegion);
		pastRegion.setText(regions[pastBeacon]);
		TextView pastBearing = (TextView) this.findViewById(R.id.pastBearing);
		pastBearing.setText(bearings[pastBeacon]);
		TextView pastRange = (TextView) this.findViewById(R.id.pastRange);
		pastRange.setText(ranges[pastBeacon]);
		TextView presentCallsign = (TextView) this.findViewById(R.id.presentCallsign);
		presentCallsign.setText(callsigns[presentBeacon]);
		TextView presentRegion = (TextView) this.findViewById(R.id.presentRegion);
		presentRegion.setText(regions[presentBeacon]);
		TextView presentBearing = (TextView) this.findViewById(R.id.presentBearing);
		presentBearing.setText(bearings[presentBeacon]);
		TextView presentRange = (TextView) this.findViewById(R.id.presentRange);
		presentRange.setText(ranges[presentBeacon]);
		TextView futureCallsign = (TextView) this.findViewById(R.id.futureCallsign);
		futureCallsign.setText(callsigns[futureBeacon]);
		TextView futureRegion = (TextView) this.findViewById(R.id.futureRegion);
		futureRegion.setText(regions[futureBeacon]);
		TextView futureBearing = (TextView) this.findViewById(R.id.futureBearing);
		futureBearing.setText(bearings[futureBeacon]);
		TextView futureRange = (TextView) this.findViewById(R.id.futureRange);
		futureRange.setText(ranges[futureBeacon]);
	}

	private ServiceConnection mConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
			if (logging == true)
				Log.v(TAG, "entered onServiceConnected");
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        mBoundService = ((HFBeaconService.HFBeaconBinder)service).getService();

	        // Tell the user about this for our demo.
	        // Toast.makeText(Binding.this, R.string.local_service_connected, Toast.LENGTH_SHORT).show();
	    }

	    public void onServiceDisconnected(ComponentName className) {
			if (logging == true)
				Log.v(TAG, "entered onServiceDisconnected");
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        // Because it is running in our same process, we should never
	        // see this happen.
	        mBoundService = null;
	        // Toast.makeText(HFBeaconActivity.this, R.string.local_service_disconnected, Toast.LENGTH_SHORT).show();
	    }
	};

	void doBindService() {
		if (logging == true)
			Log.v(TAG, "entered doBindService");
	    // Establish a connection with the service.  We use an explicit
	    // class name because we want a specific service implementation that
	    // we know will be running in our own process (and thus won't be
	    // supporting component replacement by other applications).
		//startService(new Intent(HFBeaconActivity.this, HFBeaconService.class));
	    bindService(new Intent(HFBeaconActivity.this, HFBeaconService.class), mConnection, Context.BIND_AUTO_CREATE);
	    mIsBound = true;
	}

	void doUnbindService() {
		if (logging == true)
			Log.v(TAG, "entered doUnbindService");
	    if (mIsBound) {
	        // Detach our existing connection.
	        unbindService(mConnection);
	        stopService(new Intent(HFBeaconActivity.this, HFBeaconService.class));
	        mIsBound = false;
	    }
	}

	/** Called when the activity is first created. */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		if (logging == true) 
			Log.v(TAG, "entered onCreate");

		// open preferences
		app_preferences = PreferenceManager.getDefaultSharedPreferences(this);

		// check the bundle for stored values
		// if the bundle is empty, check preferences
		// TODO: add DM/DMS display to preferences
		if (savedInstanceState != null) {
			band = savedInstanceState.getInt(BAND);
		} else {
			band = app_preferences.getInt(BAND, 0);
		}

		// import resources
		Resources res = getResources();
		callsigns = res.getTextArray(R.array.callsign);
		regions = res.getTextArray(R.array.region);
		latitudes = res.getTextArray(R.array.latitude);
		longitudes = res.getTextArray(R.array.longitude);
		numBeacons = callsigns.length;
		bearings = new CharSequence[numBeacons];
		ranges = new CharSequence[numBeacons];

		// assemble the spinner
		Spinner spinner = (Spinner) this.findViewById(R.id.bandSpinner);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.bandArray, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setSelection(band);
		spinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				if (logging == true) 
					Log.v(TAG, "entering onItemSelected");
				
				// spinner is arg0
				band = arg0.getSelectedItemPosition();
				if (logging == true) 
					Log.d(TAG, "running setBeacons from onItemSelected");
				setBeacons();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
		
		// define intent and intent filter
		mIntentFilter = new IntentFilter();
		mIntentFilter.addAction(HFBeaconService.UPDATEBEACONS);
		if (logging == true)
			Log.d(TAG, "mIntentFilter is" + mIntentFilter);
	}

	/** Called when the activity is becoming visible to the user. */
	@Override
	protected void onStart() {
		super.onStart();
		if (logging == true) 
			Log.v(TAG, "entered onStart");

		// provider
		if (provider == null)
			getBestProvider();
		
		// try to get the last known location
		location = locmanager.getLastKnownLocation(provider);
		if (location == null) {
			if (logging == true) 
				Log.d(TAG, "null - no last known location");
			// display "waiting for location" page
			showWaiting(this);
			return;
		}

		// start service
		if (logging == true)
			Log.d(TAG, "binding service");
	    doBindService();

	    // update location with new values
		if (logging == true)
			Log.d(TAG, "calling updateLocation from onStart");
		//updateLocation(location);

		// set the current band (and the beacons!)
		if (logging == true) 
			Log.d(TAG, "running setBeacons from onStart");
		//setBeacons();
	}

	/** Called after your activity has been stopped, prior to it being started again. */
	@Override
	protected void onRestart() {
		super.onRestart();
		if (logging == true) 
			Log.v(TAG, "entered onRestart");
	}

	/** Called when the activity will start interacting with the user. */
	@Override
	protected void onResume() {
		super.onResume();
		if (logging == true) 
			Log.v(TAG, "entered onResume");

		// start the location listener

		// Speaking of which, how often should I be getting location updates?
		// Wikipedia says no two points in the same grid square can be further
		// apart than 12km.  If I'm displaying in degrees-minutes, I want to get
		// updates at the half-minute level, or 900m.  Displaying in
		// degrees-minutes-seconds would require half-second updates which is
		// 15m.  I think I'll stick to degrees-minutes at first and add
		// degrees-minutes-seconds with a warning of battery usage.	
		
		// TODO: fix the magic values here
		// first number is minTime in milliseconds
		// documentation recommends against values under 60000 -- I was using FIVE_MINUTES
		// second number is minDistance in meters 
		// the two values for this are 900 and 15 depending on whether DM or DMS is used for display
		locmanager.requestLocationUpdates(provider, ONE_MINUTE_IN_MILLIS, HALF_MINUTE_IN_METERS, loclistener);

		// calculate next event
		// TODO: add local offset here too
		long now = System.currentTimeMillis();
		long next = ((now / TEN_SECONDS_IN_MILLIS) + 1) * TEN_SECONDS_IN_MILLIS;

		// register receiver
		if (logging == true)
			Log.d(TAG, "registering receiver");
		registerReceiver(mIntentReceiver, mIntentFilter, null, null);
		
		// configure handler
		// handler = new Handler();
		// handler.removeCallbacks(updateBeacons);
		// handler.postDelayed(updateBeacons, next - System.currentTimeMillis());
	}

	/** Called when the system is about to start resuming a previous activity. */
	@Override
	protected void onPause() {
		super.onPause();
		if (logging == true) 
			Log.v(TAG, "entered onPause");
		
		// store band in preferences
		// TODO: add DM/DMS display to preferences
		SharedPreferences.Editor editor = app_preferences.edit();
		editor.putInt(BAND, band);
		editor.commit();
		
		// deregister receiver
		unregisterReceiver(mIntentReceiver);
		
		// kill update events
		//handler.removeCallbacks(updateBeacons);
		locmanager.removeUpdates(loclistener);
	}

	/** Called when the activity is no longer visible to the user. */
	@Override
	protected void onStop() {
		super.onStop();
		if (logging == true) 
			Log.v(TAG, "entered onStop");

		// stop service
		if (logging == true)
			Log.d(TAG, "stopping service");
	    doUnbindService();
	}

	/** The final call you receive before your activity is destroyed. */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (logging == true) 
			Log.v(TAG, "entered onDestroy");
	}

	/** Store temporary state (such as the content of a text field) */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (logging == true) 
			Log.v(TAG, "entered onSaveInstanceState");

		// store band in bundle
		outState.putInt(BAND, band);
	}
	
	/** build options menu during OnCreate */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		if (logging == true) 
			Log.v(TAG, "entered onCreateOptionsMenu");
		
		// add info
		menu.add(0, MENU_INFO, 0, R.string.menu_info);
		
		return true;
	}
	
	/** invoked when user selects item from menu */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		if (logging == true) 
			Log.v(TAG, "entered onOptionsItemSelected");
		
		// display info
		switch (item.getItemId()) {
		case MENU_INFO:
			// display info
			showAbout(this);
			return true;
		default:
			// should never reach here...
			return false;
		}
	}

	/** displays "about..." page */
	private void showAbout(final Activity activity) {
		if (logging == true) 
			Log.v(TAG, "entered showAbout");
		
		final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		PackageManager pm = getPackageManager();
		String versionName;
		try {
			versionName = pm.getPackageInfo(getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			if (logging == true)
				Log.d(TAG, "PackageManager threw NameNotFoundException");
			versionName = "";
		}

		builder.setTitle(getString(R.string.about_title) + " " + getString(R.string.app_name) + " " + versionName);
		builder.setMessage(R.string.about_body);
		builder.setPositiveButton(R.string.about_dismiss_button, 
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						;
					}
		});
		builder.create().show();
	}
	
	/** displays "waiting for position" page */
	private void showWaiting(final Activity activity) {
		if (logging == true)
			Log.v(TAG, "entered showWaiting");
		
		final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		
		builder.setTitle(R.string.waiting_title);
		builder.setMessage(R.string.waiting_body);
		builder.setPositiveButton(R.string.waiting_dismiss_button, 
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						;
					}
		});
		builder.create().show();
	}
	
	/** displays "location disabled" page */
	private void showLocationDisabled(final Activity activity) {
		if (logging == true)
			Log.v(TAG, "entered showLocationDisabled");
		
		final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		
		builder.setTitle(R.string.locationdisabled_title);
		builder.setMessage(R.string.locationdisabled_body);
		builder.setPositiveButton(R.string.locationdisabled_positive_button, 
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
						intent.addCategory(Intent.CATEGORY_LAUNCHER);
						intent.setAction(Settings.ACTION_SECURITY_SETTINGS);
						intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.SecuritySettings"));
						intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(intent);
					}});
		builder.setNegativeButton(R.string.locationdisabled_negative_button, 
 				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
		});
		builder.create().show();
	}
}