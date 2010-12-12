package org.twilley.android.hfbeacon;

import java.util.Calendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
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

public class HFBeacon extends Activity {
	private static final String BAND = "band";
	private static final String TAG = "HFBeacon";
	private static final int TEN_SECONDS_IN_MILLIS = 10000;
	private static final int MENU_INFO = 0;
	private static final String[] field = { "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R" };
	private static final String[] digit = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" };
	private static final String[] sub = { "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x" }; 
	private static boolean logging = false;
	private SharedPreferences app_preferences;
	private int band;
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

	private final LocationListener loclistener = new LocationListener() {
		@Override
		public void onLocationChanged(Location location) {
			if (logging == true)
				Log.v(TAG, "entered onLocationChanged");
			updateLocation(location);
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
	
	/** Gets the current best location service provider */
	private void getBestProvider() {
		if (logging == true)
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
			if (logging == true)
				Log.v(TAG, "no provider enabled at all");
			showLocationDisabled(this);
		} else {
			if (logging == true)
				Log.v(TAG, "best provider is " + provider);
		}
	}

	/** Runs every ten seconds updating beacons */
	private final Runnable updateBeacons = new Runnable() {
		public void run() {
			if (logging == true)
				Log.v(TAG, "entered run");

			// calculate next event
			long now = System.currentTimeMillis();
			long next = ((now / TEN_SECONDS_IN_MILLIS) + 1) * TEN_SECONDS_IN_MILLIS;

			// set the current band (and the beacons!)
			if (logging == true)
				Log.v(TAG, "running setBeacons from updateBeacons");
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
		float[] results = new float[2];
		
		// calculate Maidenhead grid square and store in textview
		TextView maidenheadValue = (TextView) this.findViewById(R.id.maidenheadValue);
		maidenheadValue.setText(gridSquare(location));

		// store location in textview
		TextView latitudeValue = (TextView) this.findViewById(R.id.latitudeValue);
		latitudeValue.setText(readableLatitude(location));
		TextView longitudeValue = (TextView) this.findViewById(R.id.longitudeValue);
		longitudeValue.setText(readableLongitude(location));

		// calculate range/bearing
		for (int i = 0; i < callsigns.length; i++) {
			Location.distanceBetween(location.getLatitude(), location.getLongitude(), Location.convert(latitudes[i].toString()), Location.convert(longitudes[i].toString()), results);
			ranges[i] = "" + (int) (results[0] / 1000);
			int rawBearing = (360 + (int) results[1]) % 360;
			bearings[i] = ((rawBearing < 100) ? "0" : "") + ((rawBearing < 10) ? "0" : "") + rawBearing;
		}
	}

	/** Sets past, present, and future beacons based on current band and time */
	private void setBeacons() {
		if (logging == true) 
			Log.v(TAG, "entered setBeacons");

		Calendar now = Calendar.getInstance();
		int raw = (((now.get(Calendar.MINUTE) % 3) * 60 + now.get(Calendar.SECOND)) / 10) - band;
		int numBeacons = callsigns.length;
		int pastIndex = (numBeacons + raw - 1) % numBeacons;
		int presentIndex = (numBeacons + raw) % numBeacons;
		int futureIndex = (numBeacons + raw + 1) % numBeacons;
		if (logging == true) 
			Log.v(TAG, "beacons = " + pastIndex + ", " + presentIndex + ", " + futureIndex);

		// display past, present and future values
		TextView pastCallsign = (TextView) this.findViewById(R.id.pastCallsign);
		pastCallsign.setText(callsigns[pastIndex]);
		TextView pastRegion = (TextView) this.findViewById(R.id.pastRegion);
		pastRegion.setText(regions[pastIndex]);
		TextView pastBearing = (TextView) this.findViewById(R.id.pastBearing);
		pastBearing.setText(bearings[pastIndex]);
		TextView pastRange = (TextView) this.findViewById(R.id.pastRange);
		pastRange.setText(ranges[pastIndex]);
		TextView presentCallsign = (TextView) this.findViewById(R.id.presentCallsign);
		presentCallsign.setText(callsigns[presentIndex]);
		TextView presentRegion = (TextView) this.findViewById(R.id.presentRegion);
		presentRegion.setText(regions[presentIndex]);
		TextView presentBearing = (TextView) this.findViewById(R.id.presentBearing);
		presentBearing.setText(bearings[presentIndex]);
		TextView presentRange = (TextView) this.findViewById(R.id.presentRange);
		presentRange.setText(ranges[presentIndex]);
		TextView futureCallsign = (TextView) this.findViewById(R.id.futureCallsign);
		futureCallsign.setText(callsigns[futureIndex]);
		TextView futureRegion = (TextView) this.findViewById(R.id.futureRegion);
		futureRegion.setText(regions[futureIndex]);
		TextView futureBearing = (TextView) this.findViewById(R.id.futureBearing);
		futureBearing.setText(bearings[futureIndex]);
		TextView futureRange = (TextView) this.findViewById(R.id.futureRange);
		futureRange.setText(ranges[futureIndex]);
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
		bearings = new CharSequence[callsigns.length];
		ranges = new CharSequence[callsigns.length];

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
					Log.v(TAG, "running setBeacons from onItemSelected");
				setBeacons();
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
		if (logging == true) 
			Log.v(TAG, "entered onStart");

		// set up provider, exit if null
		if (provider == null)
			getBestProvider();
		if (provider == null)
			return;
		
		// location
		Location location = new Location(provider);

		// try to get the last known location
		location = locmanager.getLastKnownLocation(provider);
		if (location == null) {
			if (logging == true) 
				Log.v(TAG, "null - no last known location");
			// display "waiting for location" page
			showWaiting(this);
			return;
		}

		// update location with new values
		updateLocation(location);

		// set the current band (and the beacons!)
		if (logging == true) 
			Log.v(TAG, "running setBeacons from onStart");
		setBeacons();
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

		// calculate next event
		long now = System.currentTimeMillis();
		long next = ((now / TEN_SECONDS_IN_MILLIS) + 1) * TEN_SECONDS_IN_MILLIS;

		// start the location listener
		if (provider == null)
			getBestProvider();
		else
			locmanager.requestLocationUpdates(provider, 300000, 1000, loclistener);

		// configure handler
		handler = new Handler();
		handler.removeCallbacks(updateBeacons);
		handler.postDelayed(updateBeacons, next - System.currentTimeMillis());
	}

	/** Called when the system is about to start resuming a previous activity. */
	@Override
	protected void onPause() {
		super.onPause();
		if (logging == true) 
			Log.v(TAG, "entered onPause");
		
		// store band in preferences
		SharedPreferences.Editor editor = app_preferences.edit();
		editor.putInt(BAND, band);
		editor.commit();
		
		// kill update events
		handler.removeCallbacks(updateBeacons);
		locmanager.removeUpdates(loclistener);
	}

	/** Called when the activity is no longer visible to the user. */
	@Override
	protected void onStop() {
		super.onStop();
		if (logging == true) 
			Log.v(TAG, "entered onStop");
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
				Log.v(TAG, "PackageManager threw NameNotFoundException");
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