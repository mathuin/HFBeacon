package org.twilley.android.hfbeacon;

import android.preference.PreferenceActivity;
import android.os.Bundle;

public class HFBeaconPreferenceActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
    }
    
}