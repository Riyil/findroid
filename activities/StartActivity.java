package org.findroid.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

public class StartActivity extends Activity {

	// =======================================
	// Member - Variables
	// =======================================
	
	
	// =======================================
	// Activity - Specific
	// =======================================
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) 
        	setContentView(R.layout.activity_start_p);
        else
        	setContentView(R.layout.activity_start_l);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_findroid_start, menu);
        return true;
    }
    
    	
	// =======================================
	// Button - Events
	// =======================================
	public void activateControlPart(View view) {
		Intent intent = new Intent(this, ControlActivity.class);
		startActivity(intent);
	}
	
	public void activateSensorPart(View view) {
		Intent intent = new Intent(this, SensorActivity.class);
		startActivity(intent);
	}		
	
	public void setPreferences(View view) {
		Intent intent = new Intent(this, PreferencesActivity.class);
		startActivity(intent);
	}
}
