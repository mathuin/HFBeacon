package org.twilley.android.hfbeacon;

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
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
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

public class HFBeaconActivity extends Activity {
	private static final String TAG = "HFBeaconActivity";
	private static final String USEDMS = "UseDMS";
	// time values
	private static final int ONE_MINUTE_IN_MILLIS = 1000 * 60 * 2;
	private static final int TWO_MINUTES_IN_MILLIS = 1000 * 60 * 2;
	// distance values
	private static final int HALF_SECOND_IN_METERS = 15 * 60;
	private static final int HALF_MINUTE_IN_METERS = 900;
	// sigh
	private static final int MENU_INFO = 0;
	private static final int MENU_PREFERENCES = 1;
	private int band;
	private int offset; 
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
	private String northAbbrev;
	private String southAbbrev;
	private String eastAbbrev;
	private String westAbbrev;
	private CharSequence[] maidenheadField;
	private CharSequence[] maidenheadDigit;
	private CharSequence[] maidenheadSub;	
	private LocationManager locmanager = null;
	private Criteria criteria = null;
	private String provider = null;
	private Location location = null;
	private Location currentBestLocation = null;
	private String GridSquare = null;
	private String ReadableLatitude = null;
	private String ReadableLongitude = null;
	private SharedPreferences app_preferences;
	private boolean useDMS;
	private OnSharedPreferenceChangeListener listener;
	private HFBeaconService mBoundService;
	private boolean mIsBound;
	private TextView maidenheadValue;
	private TextView latitudeValue;
	private TextView longitudeValue;
	private TextView pastBearing;
	private TextView pastRange;
	private TextView presentBearing;
	private TextView presentRange;
	private TextView futureRange;
	private TextView futureBearing;
	private TextView pastCallsign;
	private TextView pastRegion;
	private TextView presentCallsign;
	private TextView presentRegion;
	private TextView futureCallsign;
	private TextView futureRegion;
	private TextView offsetValue;

	/* from http://developer.android.com/guide/topics/location/obtaining-user-location.html */
	
	/** Determines whether one Location reading is better than the current Location fix
	  * @param location  The new Location that you want to evaluate
	  * @param currentBestLocation  The current Location fix, to which you want to compare the new one
	  */
	protected boolean isBetterLocation(Location location, Location currentBestLocation) {
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

			Log.v(TAG, "entered onLocationChanged");
			if (isBetterLocation(location, currentBestLocation)) {
				Log.i(TAG, "new location is better than current best location");
				currentBestLocation = location;
				
				// update variables
				GridSquare = gridSquare(location);
				ReadableLatitude = readableLatitude(location);
				ReadableLongitude = readableLongitude(location);

				// calculate range/bearing
				for (int i = 0; i < numBeacons; i++) {
					Location.distanceBetween(location.getLatitude(), location.getLongitude(), Location.convert(latitudes[i].toString()), Location.convert(longitudes[i].toString()), results);
					ranges[i] = "" + (int) (results[0] / 1000);
					int rawBearing = (360 + (int) results[1]) % 360;
					bearings[i] = ((rawBearing < 100) ? "0" : "") + ((rawBearing < 10) ? "0" : "") + rawBearing;
				}
				updateLocation(location);
			}
		}

		@Override
		public void onProviderDisabled(String oldprovider) {
			Log.v(TAG, "entered onProviderDisabled");
			if (oldprovider == provider) {
				getBestProvider();
			}
		}

		@Override
		public void onProviderEnabled(String newprovider) {
			Log.v(TAG, "entered onProviderEnabled");
			if (newprovider != provider) {
				getBestProvider();
			}
		}

		@Override
		public void onStatusChanged(String oldprovider, int status, Bundle extras) {
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
		Log.v(TAG, "entered getBestProvider");
		
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
			Log.w(TAG, "no provider enabled at all");
			showLocationDisabled(this);
		} else {
			Log.d(TAG, "best provider is " + provider);
		}
	}

	/** Listens for service messages */
	private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
		@Override
	    public void onReceive(Context context, Intent intent) {
			Log.v(TAG, "entered onReceive");
			
			String action = intent.getAction();
			Bundle extras = intent.getExtras();
			
			if (action.equals(HFBeaconService.UPDATEBEACONS)) {
				Log.i(TAG, "received intent to update beacons");
				
				// what beacons are they
				int beacons[] = extras.getIntArray(HFBeaconService.CONTENTS);
				pastBeacon = beacons[0]; 
				presentBeacon = beacons[1]; 
				futureBeacon = beacons[2]; 
				Log.d(TAG, "beacons = " + pastBeacon + ", " + presentBeacon + ", " + futureBeacon);

				// set the textviews and stuff for band and beacons
				setBeacons();
			} else {
				Log.d(TAG, "received unknown intent: " + action);
			}
	    }
	};
	
	/** Converts location into Maidenhead grid square as described in http://en.wikipedia.org/wiki/Maidenhead_Locator_System */
	private String gridSquare(Location location) {
		Log.v(TAG, "entered gridSquare");

		String result = "";
		
		// Make longitude and latitude both positive
		double myLongitude = location.getLongitude() + 180;
		double myLatitude = location.getLatitude() + 90;

		// field consists of two uppercase letters, one letter per ten degrees of latitude or twenty degrees of longitude
		int longField = (int) myLongitude / 20;
		int latField = (int) myLatitude / 10;
		result += maidenheadField[longField];
		result += maidenheadField[latField];
		
		// square consists of two digits, one digit per one degree of latitude or two degrees of longitude
		int longDigit = (int) (myLongitude - (longField * 20)) / 2;
		int latDigit = (int) (myLatitude - (latField * 10)) / 1;
		result += maidenheadDigit[longDigit];
		result += maidenheadDigit[latDigit];
		
		// subsquare consists of two lowercase letters, one letter per 2.5 minutes of latitude or 5 minutes of longitude 
		int longSub = (int) (myLongitude * 60 / 5) % 24;
		int latSub = (int) (myLatitude * 60 / 2.5) % 24;
		result += maidenheadSub[longSub];
		result += maidenheadSub[latSub];
		
		return result;
	}
	
	/** Converts raw latitude string value to a more user-friendly format */
	private String readableLatitude(Location location) {
		Log.v(TAG, "entered readableLatitude");
		
		String[] latitude = Location.convert(location.getLatitude(), Location.FORMAT_SECONDS).split(":|\\.|,");
		String degree = latitude[0].replaceFirst("-", "") + '\u00B0';
		String minute = latitude[1] + '\'';
		String second = latitude[2] + "\" ";

		String result = degree + minute;
		if (useDMS) {
			result += second;
		}
		
		result += " " + ((latitude[0].startsWith("-")) ? southAbbrev : northAbbrev);
		
		return result;
	}
	
	/** Converts raw longitude string value to a more user-friendly format */
	private String readableLongitude(Location location) {
		Log.v(TAG, "entered readableLongitude");
		
		String[] longitude = Location.convert(location.getLongitude(), Location.FORMAT_SECONDS).split(":|\\.|,");
		String degree = longitude[0].replaceFirst("-", "") + '\u00B0';
		String minute = longitude[1] + '\'';
		String second = longitude[2] + "\" ";

		String result = degree + minute;
		if (useDMS) {
			result += second;
		}

		result += " " + ((longitude[0].startsWith("-")) ? westAbbrev : eastAbbrev);
		
		return result;
	}
	
	/** Updates location and recalculates range and bearing */
	private void updateLocation(Location location) {
		Log.v(TAG, "entered updateLocation");
		
		// calculate Maidenhead grid square and store in textview
		maidenheadValue.setText(GridSquare);

		// store location in textview
		latitudeValue.setText(ReadableLatitude);
		longitudeValue.setText(ReadableLongitude);
		
		// update beacon ranges and bearings
		pastBearing.setText(bearings[pastBeacon] + "\u00B0");
		pastRange.setText(ranges[pastBeacon] + " km");
		presentBearing.setText(bearings[presentBeacon] + "\u00B0");
		presentRange.setText(ranges[presentBeacon] + " km");
		futureBearing.setText(bearings[futureBeacon] + "\u00B0");
		futureRange.setText(ranges[futureBeacon] + " km");		
	}

	/** Sets past, present, and future beacons based on current band and time */
	private void setBeacons() {
		Log.v(TAG, "entered setBeacons");
		
		// display past, present and future values
		pastCallsign.setText(callsigns[pastBeacon]);
		pastRegion.setText(regions[pastBeacon]);
		pastBearing.setText(bearings[pastBeacon] + "\u00B0");
		pastRange.setText(ranges[pastBeacon] + " km");
		presentCallsign.setText(callsigns[presentBeacon]);
		presentRegion.setText(regions[presentBeacon]);
		presentBearing.setText(bearings[presentBeacon] + "\u00B0");
		presentRange.setText(ranges[presentBeacon] + " km");
		futureCallsign.setText(callsigns[futureBeacon]);
		futureRegion.setText(regions[futureBeacon]);
		futureBearing.setText(bearings[futureBeacon] + "\u00B0");
		futureRange.setText(ranges[futureBeacon] + " km");
		
		// update offset value here
		// milliseconds are milliseconds
		offsetValue.setText((( offset < 0 ) ? "" : "+") + offset + " ms");
	}

	private ServiceConnection mConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	    	Log.v(TAG, "entered onServiceConnected");
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        mBoundService = ((HFBeaconService.HFBeaconBinder)service).getService();

	        // now set the offset value!
	        Bundle offsetValues = new Bundle();
	        offsetValues.putInt(HFBeaconService.HFBeaconBinder.VALUEKEY, offset);
	        Parcel offsetData = Parcel.obtain();
	        offsetData.writeBundle(offsetValues);
	        try {
	        	Log.d(TAG, "sending offset transact request to service");
				mBoundService.mBinder.transact(HFBeaconService.HFBeaconBinder.SETOFFSET, offsetData, null, 0);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	    public void onServiceDisconnected(ComponentName className) {
	    	Log.v(TAG, "entered onServiceDisconnected");
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        // Because it is running in our same process, we should never
	        // see this happen.
	        mBoundService = null;
	    }
	};
	
	void doBindService() {
		Log.v(TAG, "entered doBindService");
	    // Establish a connection with the service.  We use an explicit
	    // class name because we want a specific service implementation that
	    // we know will be running in our own process (and thus won't be
	    // supporting component replacement by other applications).
	    bindService(new Intent(HFBeaconActivity.this, HFBeaconService.class), mConnection, Context.BIND_AUTO_CREATE);
	    mIsBound = true;
	}

	void doUnbindService() {
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
		Log.v(TAG, "entered onCreate");

		// import resources
		Resources res = getResources();
		callsigns = res.getTextArray(R.array.callsign);
		regions = res.getTextArray(R.array.region);
		latitudes = res.getTextArray(R.array.latitude);
		longitudes = res.getTextArray(R.array.longitude);
		numBeacons = callsigns.length;
		bearings = new CharSequence[numBeacons];
		ranges = new CharSequence[numBeacons];
		northAbbrev = res.getString(R.string.northAbbrev);
		southAbbrev = res.getString(R.string.southAbbrev);
		eastAbbrev = res.getString(R.string.eastAbbrev);
		westAbbrev = res.getString(R.string.westAbbrev);
		maidenheadField = res.getTextArray(R.array.maidenheadField);
		maidenheadDigit = res.getTextArray(R.array.maidenheadDigit);
		maidenheadSub = res.getTextArray(R.array.maidenheadSub);

		// open preferences
		app_preferences = PreferenceManager.getDefaultSharedPreferences(this);
		// band may be useful for spinner
		band = app_preferences.getInt(HFBeaconService.BAND, 0);
		offset = app_preferences.getInt(HFBeaconService.OFFSET, 0);
		useDMS = app_preferences.getBoolean(USEDMS, true);
		listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
					String key) {
				if (key.equals(HFBeaconService.OFFSET)) {
					Log.d(TAG, "received new offset from shared preferences");
			        // now set the offset value!
					offset = sharedPreferences.getInt(key, 0);
					Bundle offsetValues = new Bundle();
			        offsetValues.putInt(HFBeaconService.HFBeaconBinder.VALUEKEY, offset);
			        Parcel offsetData = Parcel.obtain();
			        offsetData.writeBundle(offsetValues);
			        if (mBoundService == null) {
		        		Log.e(TAG, "mBoundService is null, service must have disconnected, possibly orientation");
		        	} else {
		        		try {
		        			Log.d(TAG, "sending offset transact request to service");
		        			mBoundService.mBinder.transact(HFBeaconService.HFBeaconBinder.SETOFFSET, offsetData, null, 0);
		        		} catch (RemoteException e) {
		        			// TODO Auto-generated catch block
		        			e.printStackTrace();
		        		}
		        	}
				} else if (key.equals(USEDMS)) {
					Log.d(TAG, "received new UseDMS from shared preferences");
					useDMS = sharedPreferences.getBoolean(key, true);

					int minDist;
					
					// kill update events
					locmanager.removeUpdates(loclistener);

					// start the location listener
					if (useDMS)
						minDist = HALF_SECOND_IN_METERS;
					else
						minDist = HALF_MINUTE_IN_METERS;
					
					locmanager.requestLocationUpdates(provider, ONE_MINUTE_IN_MILLIS, minDist, loclistener);
				}
			}
		};
		app_preferences.registerOnSharedPreferenceChangeListener(listener);

		// define the textviews
		maidenheadValue = (TextView) this.findViewById(R.id.maidenheadValue);
		latitudeValue = (TextView) this.findViewById(R.id.latitudeValue);
		longitudeValue = (TextView) this.findViewById(R.id.longitudeValue);
		pastCallsign = (TextView) this.findViewById(R.id.pastCallsign);
		pastRegion = (TextView) this.findViewById(R.id.pastRegion);
		pastBearing = (TextView) this.findViewById(R.id.pastBearing);
		pastRange = (TextView) this.findViewById(R.id.pastRange);
		presentCallsign = (TextView) this.findViewById(R.id.presentCallsign);
		presentRegion = (TextView) this.findViewById(R.id.presentRegion);
		presentBearing = (TextView) this.findViewById(R.id.presentBearing);
		presentRange = (TextView) this.findViewById(R.id.presentRange);
		futureCallsign = (TextView) this.findViewById(R.id.futureCallsign);
		futureRegion = (TextView) this.findViewById(R.id.futureRegion);
		futureBearing = (TextView) this.findViewById(R.id.futureBearing);
		futureRange = (TextView) this.findViewById(R.id.futureRange);
		offsetValue = (TextView) this.findViewById(R.id.offsetValue);
		
		// assemble the spinner
		Spinner spinner = (Spinner) this.findViewById(R.id.bandSpinner);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.bandArray, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setSelection(band);
		spinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				Log.v(TAG, "entering onItemSelected");
				
		        // now set the band value!
		        Bundle bandValues = new Bundle();
		        bandValues.putInt(HFBeaconService.HFBeaconBinder.VALUEKEY, arg0.getSelectedItemPosition());
		        Parcel bandData = Parcel.obtain();
		        bandData.writeBundle(bandValues);
	        	if (mBoundService == null) {
	        		Log.e(TAG, "mBoundService is null, service must have disconnected, possibly orientation");
	        	} else {
	        		try {
	        			Log.d(TAG, "sending band transact request to service");
	        			mBoundService.mBinder.transact(HFBeaconService.HFBeaconBinder.SETBAND, bandData, null, 0);
	        		} catch (RemoteException e) {
	        			// TODO Auto-generated catch block
	        			e.printStackTrace();
	        		}
	        	}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
	}

	/** Called when the activity is becoming visible to the user. */
	@Override
	protected void onStart() {
		super.onStart();
		Log.v(TAG, "entered onStart");

		// provider
		if (provider == null)
			getBestProvider();
		if (provider == null)
			return;
		
		// try to get the last known location
		location = locmanager.getLastKnownLocation(provider);
		if (location == null) {
			Log.w(TAG, "null - no last known location");
			// display "waiting for location" page
			showWaiting(this);
			return;
		}

		// start service
		Log.d(TAG, "binding service");
	    doBindService();
	}

	/** Called after your activity has been stopped, prior to it being started again. */
	@Override
	protected void onRestart() {
		super.onRestart();
		Log.v(TAG, "entered onRestart");
	}

	/** Called when the activity will start interacting with the user. */
	@Override
	protected void onResume() {
		super.onResume();
		Log.v(TAG, "entered onResume");

		int minDist;

		// start the location listener
		if (useDMS)
			minDist = HALF_SECOND_IN_METERS;
		else
			minDist = HALF_MINUTE_IN_METERS;
		
		if (provider==null)
			getBestProvider();
		if (provider==null)
			return;
		else
			locmanager.requestLocationUpdates(provider, ONE_MINUTE_IN_MILLIS, minDist, loclistener);

		// register receiver
		registerReceiver(mIntentReceiver, new IntentFilter(HFBeaconService.UPDATEBEACONS), null, null);
	}

	/** Called when the system is about to start resuming a previous activity. */
	@Override
	protected void onPause() {
		super.onPause();
		Log.v(TAG, "entered onPause");
		
		try {
			// deregister receiver
			unregisterReceiver(mIntentReceiver);
			// kill update events\
			locmanager.removeUpdates(loclistener);
		} catch (IllegalArgumentException e) {
			;
		}
		
	}

	/** Called when the activity is no longer visible to the user. */
	@Override
	protected void onStop() {
		super.onStop();
		Log.v(TAG, "entered onStop");

		// stop service
		Log.d(TAG, "stopping service");
	    doUnbindService();
	}

	/** The final call you receive before your activity is destroyed. */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.v(TAG, "entered onDestroy");

		// update preferences
		try {
			app_preferences.unregisterOnSharedPreferenceChangeListener(listener);
		} catch (IllegalArgumentException e) {
			;
		}
		SharedPreferences.Editor editor = app_preferences.edit();
		editor.putInt(HFBeaconService.BAND, band);
		editor.putInt(HFBeaconService.OFFSET, offset);
		editor.putBoolean(USEDMS, useDMS);
		editor.commit();
	}

	/** build options menu during OnCreate */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		Log.v(TAG, "entered onCreateOptionsMenu");
		
		// add info
		menu.add(0, MENU_INFO, 0, R.string.menu_info);
		menu.add(1, MENU_PREFERENCES, 1, R.string.menu_preferences);
		
		return true;
	}
	
	/** invoked when user selects item from menu */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		Log.v(TAG, "entered onOptionsItemSelected");
		
		// display info
		switch (item.getItemId()) {
		case MENU_INFO:
			// display info
			showAbout(this);
			return true;
		case MENU_PREFERENCES:
			Intent intent = new Intent(this, HFBeaconPreferenceActivity.class);
			startActivity(intent);
		default:
			// should never reach here...
			return false;
		}
	}

	/** displays "about..." page */
	private void showAbout(final Activity activity) {
		Log.v(TAG, "entered showAbout");
		
		final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		PackageManager pm = getPackageManager();
		String versionName;
		try {
			versionName = pm.getPackageInfo(getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			Log.w(TAG, "PackageManager threw NameNotFoundException");
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