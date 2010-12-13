package org.twilley.android.hfbeacon.test;

import android.test.ActivityInstrumentationTestCase2;
import android.widget.TextView;

import org.twilley.android.hfbeacon.HFBeacon;

public class HFBeaconTest extends ActivityInstrumentationTestCase2<HFBeacon> {
    private HFBeacon mActivity;
    private TextView mView;
    private String resourceString;
    
    public HFBeaconTest() {
	      super("org.twilley.android.hfbeacon", HFBeacon.class);
	}
	
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = this.getActivity();
        mView = (TextView) mActivity.findViewById(org.twilley.android.hfbeacon.R.id.bandLabel);
        resourceString = mActivity.getString(org.twilley.android.hfbeacon.R.string.bandLabel);
    }
    
    public void testPreconditions() {
        assertNotNull(mView);
    }
    
    public void testText() {
        assertEquals(resourceString,(String)mView.getText());
    }
}
