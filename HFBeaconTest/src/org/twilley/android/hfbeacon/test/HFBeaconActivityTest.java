package org.twilley.android.hfbeacon.test;

import android.content.res.Resources;
import android.test.ActivityInstrumentationTestCase2;
import android.view.KeyEvent;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import org.twilley.android.hfbeacon.HFBeaconActivity;

public class HFBeaconActivityTest extends ActivityInstrumentationTestCase2<HFBeaconActivity> {
    private HFBeaconActivity mActivity;
    private Resources mResources;
    private Spinner mBandSpinner;
    private SpinnerAdapter mBandSpinnerAdapter;
    private TextView mBandLabelView;
    private String mBandLabelString;
	private String mBandSpinnerSelection;
	private int mBandSpinnerPos;
	public static final int INITIAL_POSITION = 0;
	public static final int TEST_POSITION = 3;
	public static final int BAND_COUNT = 5;
    
    public HFBeaconActivityTest() {
	      super("org.twilley.android.hfbeacon", HFBeaconActivity.class);
	}
	
    @Override
    protected void setUp() throws Exception {
        super.setUp();
	    setActivityInitialTouchMode(false);
	    mActivity = this.getActivity();
	    mResources = mActivity.getResources();
	    // TODO: send mock position of long=-122.084095 lat=37.422006
	    // for now, all tests must run on real device, not emulator -- sigh!!
	    mBandSpinner = (Spinner) mActivity.findViewById(org.twilley.android.hfbeacon.R.id.bandSpinner);
	    mBandSpinnerAdapter = mBandSpinner.getAdapter();
        mBandLabelView = (TextView) mActivity.findViewById(org.twilley.android.hfbeacon.R.id.bandLabel);
        mBandLabelString = mActivity.getString(org.twilley.android.hfbeacon.R.string.bandLabel);
    }
    
    /* This setup routine resets the band spinner to its initial position */
    protected void resetBandSpinner() {
		mActivity.runOnUiThread(
				new Runnable() {
					public void run() {
						mBandSpinner.requestFocus();
						mBandSpinner.setSelection(INITIAL_POSITION);
					} // end of run() method definition
				} // end of anonymous Runnable object instantiation
		    	); // end of invocation of runOnUiThread
    }
    
	public void testPreConditions() {
		assertTrue(mBandSpinner.getOnItemSelectedListener() != null);
		assertTrue(mBandSpinnerAdapter != null);
		assertEquals(mBandSpinnerAdapter.getCount(), BAND_COUNT);
        assertNotNull(mBandLabelView);
    }
	
	public void testBandSpinnerUI() {
		resetBandSpinner();
		this.sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
	    for (int i = 1; i <= TEST_POSITION; i++) {
	      this.sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
	    } // end of for loop

	    this.sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
	    mBandSpinnerPos = mBandSpinner.getSelectedItemPosition();
	    mBandSpinnerSelection = (String) mBandSpinner.getItemAtPosition(mBandSpinnerPos);

	    assertEquals(mResources.getStringArray(org.twilley.android.hfbeacon.R.array.bandArray)[TEST_POSITION], mBandSpinnerSelection);

	  }

	public void testBandSpinnerUI2() {
		resetBandSpinner();
		this.sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
	    for (int i = 1; i <= TEST_POSITION; i++) {
	      this.sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
	    } // end of for loop

	    this.sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
	    mBandSpinnerPos = mBandSpinner.getSelectedItemPosition();
	    mBandSpinnerSelection = (String) mBandSpinner.getItemAtPosition(mBandSpinnerPos);

	    assertEquals(mResources.getStringArray(org.twilley.android.hfbeacon.R.array.bandArray)[TEST_POSITION], mBandSpinnerSelection);

	  }

    public void testText() {
        assertEquals(mBandLabelString,(String)mBandLabelView.getText());
    }
}
