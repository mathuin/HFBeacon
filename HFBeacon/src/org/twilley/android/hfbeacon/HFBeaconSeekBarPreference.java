package org.twilley.android.hfbeacon;

// This class and related content is based on SeekBarPreference by Amir Sadrinia <amir.sadrinia@gmail.com> 
// and permission has been received from Amir to release this code under the same license as the rest of the project.

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class HFBeaconSeekBarPreference extends Preference implements OnSeekBarChangeListener {
	public static int realMaximum = 30000;
	public static int realMinimum = -realMaximum;
	public static int realRange = realMaximum - realMinimum;
	public static int realInterval = 250;
	public static int realDefault = 0;
	public static int seekBarMax = realRange/realInterval; // must evenly divide
	public static int seekBarMin = 0; // all seek bars have a minimum of zero
	private int oldRealValue = realDefault;
	final LinearLayout layout;
	final LinearLayout textbar;
	final TextView label;
	final TextView value;
	final LinearLayout buttonbar;
	final Button minus;
	final SeekBar bar;
	final Button plus;

	public int fromBartoReal(int barValue) {
		int realValue = (barValue*realInterval)+realMinimum;
		return realValue;
	}
	
	public int fromRealtoBar(int realValue) {
		int barValue = (realValue-realMinimum)/realInterval; 
		return barValue;
	}
	
	public HFBeaconSeekBarPreference(Context context) {
		super(context);
		layout = new LinearLayout(context);
		textbar = new LinearLayout(context);
		label = new TextView(context);
		value = new TextView(context);
		buttonbar = new LinearLayout(context);
		minus = new Button(context);
		bar = new SeekBar(context);
		plus = new Button(context);
		textbar.addView(label);
		textbar.addView(value);
		buttonbar.addView(minus);
		buttonbar.addView(bar);
		buttonbar.addView(plus);
		layout.addView(textbar);
		layout.addView(buttonbar);
	}

	public HFBeaconSeekBarPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		layout = new LinearLayout(context);
		textbar = new LinearLayout(context);
		label = new TextView(context);
		value = new TextView(context);
		buttonbar = new LinearLayout(context);
		minus = new Button(context);
		bar = new SeekBar(context);
		plus = new Button(context);
		textbar.addView(label);
		textbar.addView(value);
		buttonbar.addView(minus);
		buttonbar.addView(bar);
		buttonbar.addView(plus);
		layout.addView(textbar);
		layout.addView(buttonbar);
	}

	public HFBeaconSeekBarPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		layout = new LinearLayout(context);
		textbar = new LinearLayout(context);
		label = new TextView(context);
		value = new TextView(context);
		buttonbar = new LinearLayout(context);
		minus = new Button(context);
		bar = new SeekBar(context);
		plus = new Button(context);
		textbar.addView(label);
		textbar.addView(value);
		buttonbar.addView(minus);
		buttonbar.addView(bar);
		buttonbar.addView(plus);
		layout.addView(textbar);
		layout.addView(buttonbar);
	}

	@Override
	protected View onCreateView(ViewGroup parent){
		super.onCreateView(parent);

		LinearLayout.LayoutParams textbarParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.FILL_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		textbarParams.gravity = Gravity.TOP;

		LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		labelParams.gravity = Gravity.LEFT;
		
		LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.FILL_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		valueParams.gravity = Gravity.RIGHT;
		
		LinearLayout.LayoutParams buttonbarParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.FILL_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		
		LinearLayout.LayoutParams minusParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		
		LinearLayout.LayoutParams seekbarParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		seekbarParams.weight = 3.0f;
		
		LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		
		// was horizontal
		layout.setPadding(15, 5, 10, 5);
		layout.setOrientation(LinearLayout.VERTICAL);

		textbar.setOrientation(LinearLayout.HORIZONTAL);
		textbar.setLayoutParams(textbarParams);

		label.setText(R.string.offsetLabel);
		label.setTextSize(18);
		label.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
		label.setGravity(Gravity.LEFT);
		label.setLayoutParams(labelParams);

		value.setTextSize(18);
		value.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
		value.setGravity(Gravity.RIGHT);
		value.setLayoutParams(valueParams);
		
		buttonbar.setOrientation(LinearLayout.HORIZONTAL);
		buttonbar.setLayoutParams(buttonbarParams);

		minus.setText("-");
		minus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	if (oldRealValue > realMinimum) {
                    updateOffset(oldRealValue - realInterval);
            	}
            }
        });
		minus.setLayoutParams(minusParams);

		bar.setLayoutParams(seekbarParams);
		bar.setMax(seekBarMax);
		bar.setOnSeekBarChangeListener(this);
		bar.setPadding(5, 5, 5, 0);

		plus.setText("+");
		plus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	if (oldRealValue < realMaximum) {
                    updateOffset(oldRealValue + realInterval);
            	}
            }
        });
		plus.setLayoutParams(plusParams);
		
		layout.setId(android.R.id.widget_frame);

		updateOffset(oldRealValue);
		
		return layout; 
	}
	
	public void updateOffset(int newOffset) {
		oldRealValue = newOffset;
		bar.setProgress(fromRealtoBar(oldRealValue));
		// not going to extract.  milliseconds are milliseconds.
		value.setText(((oldRealValue < 0) ? "" : "+") + oldRealValue + " ms");
		updatePreference(oldRealValue);
	}
	
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {

		if(!callChangeListener(progress)){
			seekBar.setProgress(fromRealtoBar(oldRealValue)); 
			return; 
		}

		updateOffset(fromBartoReal(progress));

		notifyChanged();
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}

	@Override 
	protected Object onGetDefaultValue(TypedArray ta,int index){
		int dValue = (int)ta.getInt(index, realDefault);

		return validateValue(dValue);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
		int temp = restoreValue ? getPersistedInt(realDefault) : (Integer)defaultValue;

		if(!restoreValue)
			persistInt(temp);

		updateOffset(temp);
	}

	private int validateValue(int value){
		if(value > realMaximum)
			value = realMinimum;
		else if(value < realMinimum)
			value = realMinimum;
		else if(value % realInterval != 0)
			value = Math.round(((float)value)/realInterval)*realInterval;  
		return value;  
	}

	private void updatePreference(int newValue){
		SharedPreferences.Editor editor =  getEditor();
		editor.putInt(getKey(), newValue);
		editor.commit();
	}
}