package org.findroid.activities;

import org.findroid.data_types.MultiJoystick;
import org.finroc.core.FrameworkElement;
import org.finroc.core.RuntimeEnvironment;
import org.finroc.plugins.tcp.TCPPeer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

public class ControlActivity extends Activity {
	
	// =======================================
	// Member - Variables
	// =======================================	
	private MultiJoystick joystick;
	
	private FrameworkElement control;
	
	private TCPPeer peer;
	
	private WifiManager wifiManager;		
	private boolean wifiEnabled = false;
	private boolean wifiConnected = false;
	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			setWifiFlags();
			writeWifiInfo();
		}
	};
	
	private final int PORT = 18701;
	
	private String ip;
	
	boolean excavator_mode = false;
	boolean forklift_mode = false;

	
	// =======================================
	// ACTIVITY - SPECIFIC
	// =======================================
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	getIp();
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_l);   
        
        initFrameworkElements();
        initJoystick();
        FrameworkElement.initAll();
        
        initServer(); 
        initWifi();
        
        drawButtons(); 
    }
    
    @Override
    public void onStart() {
    	super.onStart();
    	getIp();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_findroid_control, menu);
        return true;
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	this.unregisterReceiver(broadcastReceiver);
    	peer.managedDelete();
    	control.managedDelete();
    }
    
    @Override
    public void onBackPressed() {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Quit the FinDroid Control-Part?");
        
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
        	public void onClick(DialogInterface dialog, int id) {
        		finish();
        	}
        });
        
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
        	public void onClick(DialogInterface dialog, int id) {
        		// nothing!
        	}
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    
    // =======================================
  	// BUTTON - EVENTS
  	// =======================================
    public void toggleExcavatorMode(View v) {
    	excavator_mode = ((ToggleButton) v).isChecked();
    	if (excavator_mode) {
    		joystick.setNumberOfSticks(2);
    		joystick.setExcavatorMode(true);
        	joystick.setReset(true);
        	joystick.invalidate();
        	toggleIncDecButtons(false);
    	} else {
    		joystick.setExcavatorMode(false);
    		joystick.setReset(true);
    		joystick.invalidate();
    		joystick.setPitchLock(false);
    		toggleIncDecButtons(true);
    	}
    	findViewById(R.id.control_togglebutton_inverse).setEnabled(excavator_mode);
    	findViewById(R.id.control_togglebutton_pitchlock).setEnabled(excavator_mode);
    	findViewById(R.id.control_togglebutton_forklift).setEnabled(!excavator_mode);
    }
    
    public void toggleForkliftMode(View view) {
    	forklift_mode = ((ToggleButton) view).isChecked();
    	if (forklift_mode) {
    		joystick.setNumberOfSticks(1);
    		joystick.setForkliftMode(true);
    		joystick.setReset(true);
    		joystick.invalidate();
    		toggleIncDecButtons(false);
    	} else {
    		joystick.setForkliftMode(false);
    		joystick.setReset(true);
    		joystick.invalidate();
    		joystick.setPitchLock(false);
    		toggleIncDecButtons(true);
    	}
    	findViewById(R.id.control_togglebutton_inverse).setEnabled(forklift_mode);
    	findViewById(R.id.control_togglebutton_excavator).setEnabled(!forklift_mode);
    }
    
    public void toggleInverseControl(View view) {
    	boolean temp = ((ToggleButton) view).isChecked();
    	
    	joystick.invertSticks();
 	
    	joystick.setInverse(temp);
    	joystick.invalidate();
    	
    	if (temp) {
    		Drawable img_toggle_left = getBaseContext().getResources().getDrawable(R.drawable.arrow_left);
     		ToggleButton right = ((ToggleButton) findViewById(R.id.control_togglebutton_inverse));
     		img_toggle_left.setBounds(0, 0, 50, 50);
     		right.setCompoundDrawables( img_toggle_left, null, null, null );
    	} else {
    		Drawable img_toggle_right = getBaseContext().getResources().getDrawable(R.drawable.arrow_right);
     		ToggleButton right = ((ToggleButton) findViewById(R.id.control_togglebutton_inverse));
     		img_toggle_right.setBounds(0, 0, 50, 50);
     		right.setCompoundDrawables( img_toggle_right, null, null, null );
    	}
    }
    
    public void toggleLockPitch(View view) {
    	joystick.setPitchLock(((ToggleButton) view).isChecked());
    }
    
    public void incrementSticks(View v) {
    	joystick.setNumberOfSticks(joystick.getNumberOfSticks()+1);
    	joystick.setReset(true);
    	joystick.invalidate();
    }
    
    public void decrementSticks(View v) {
    	if (joystick.getNumberOfSticks() > 1) {
    		joystick.setNumberOfSticks(joystick.getNumberOfSticks()-1);
    		joystick.setReset(true);
    		joystick.invalidate();
    	}
    }
    
    
    // =======================================
 	// FRAMEWORK - SPECIFIC
 	// =======================================
    public void initFrameworkElements() {
    	control = new FrameworkElement(RuntimeEnvironment.getInstance(), "Control");
    }
      
 	public void initServer() {
         new Thread(new Runnable() {
             public void run() {
            	 
            	 peer = new TCPPeer(ip, "", TCPPeer.Mode.FULL, PORT, TCPPeer.GUI_FILTER, false);
            	 
            	 peer.init();
            	 try {
					peer.connect(ip);
				} catch (Exception e) {
					e.printStackTrace();
				}
            	 
             }
         }).start();
 	}
 	
 	
    // =======================================
  	// WIFI - SPECIFIC
  	// =======================================
 	public void writeWifiInfo() {
		TextView textView = (TextView) findViewById(R.id.control_textview_server);
		String msg;
		if (wifiEnabled && wifiConnected) msg = "IP: " + 
				intToip(wifiManager.getDhcpInfo().ipAddress) + ":" + PORT + '\n';
		else if (wifiEnabled && !wifiConnected) msg = "This device isn't connected to a wifi network.\n";
		else msg = "WiFi isn't enabled on this device.\n";
		msg = msg + "Connect-To IP: " + ip;
		textView.setText(msg);
	}
 	
 	public void initWifi() {
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		
		this.registerReceiver(broadcastReceiver,
		         new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}
 	
 	public void setWifiFlags() {
		if (wifiManager.isWifiEnabled()) {
		   	wifiEnabled = true;
		    wifiConnected = (wifiManager.getConnectionInfo().getNetworkId() != -1);
		} else {
		    wifiEnabled = false;
		    wifiConnected = false;
		}
	}
 	
 	
    // =======================================
 	// MISCELLANEOUS
 	// =======================================
 	public String intToip(int i) {
        return (i & 0xFF) + "." +
               ((i >> 8) & 0xFF) + "." +
               ((i >> 16) & 0xFF) + "." +
               ((i >> 24) & 0xFF);
 	}
 	
 	public void toggleIncDecButtons(boolean act) {
 		Button button = (Button) findViewById(R.id.control_button_sticks_dec);
 		button.setEnabled(act);
 		button = (Button) findViewById(R.id.control_button_sticks_inc);
 		button.setEnabled(act);		
 	}
 	
 	public void getIp() {
 		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ip = prefs.getString("Control-IP", "131.246.160.97:4444");
 	}
 	
 	public void drawButtons() {
 		Drawable img_toggle_right = getBaseContext().getResources().getDrawable(R.drawable.arrow_right);
 		ToggleButton inverse = ((ToggleButton) findViewById(R.id.control_togglebutton_inverse));
 		img_toggle_right.setBounds(0, 0, 50, 50);
 		inverse.setCompoundDrawables(img_toggle_right, null, null, null );
 		
 		Drawable img_lock = getBaseContext().getResources().getDrawable(R.drawable.lock);
 		ToggleButton lock = ((ToggleButton) findViewById(R.id.control_togglebutton_pitchlock));
 		img_lock.setBounds(0, 0, 50, 50);
 		lock.setCompoundDrawables(img_lock, null, null, null );
 	}

 	public void initJoystick() {
 		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
 		joystick = (MultiJoystick) findViewById(R.id.control_joystick);
        joystick.setXMax(Float.parseFloat(prefs.getString("X-Max", "8.0")));
        joystick.setXMin(Float.parseFloat(prefs.getString("X-Min", "0.0")));
        joystick.setYMax(Float.parseFloat(prefs.getString("Y-Max", "6.0")));
        joystick.setYMin(Float.parseFloat(prefs.getString("Y-Min", "-6.0")));
        joystick.setParentElement(control);
        joystick.setPortXId(prefs.getString("Port X", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X"));
        joystick.setPortYId(prefs.getString("Port Y", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp Z"));
        joystick.setPortAngleId(prefs.getString("Port Angle", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp Pitch"));       
        joystick.initPorts();
 	}
}
