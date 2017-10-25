package org.findroid.activities;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.findroid.data_types.CameraPreview;
import org.findroid.data_types.CompressedImage;
import org.findroid.data_types.Image;
import org.finroc.core.FrameworkElement;
import org.finroc.core.RuntimeEnvironment;
import org.finroc.core.datatype.CoreBoolean;
import org.finroc.core.datatype.CoreNumber;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.Port;
import org.finroc.core.port.PortCreationInfo;
import org.finroc.core.port.PortFlags;
import org.finroc.core.port.PortListener;
import org.finroc.core.port.cc.PortNumeric;
import org.finroc.plugins.tcp.TCPPeer;
import org.finroc.plugins.tcp.TCPServer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

public class SensorActivity extends Activity implements SensorEventListener, 
	PortListener<CoreBoolean>, Camera.PreviewCallback {

	
	// =======================================
	// MEMBER - VARIABLES
	// =======================================	
	// to check whether WiFi is enabled or not
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
	
	// necessary for acquiring GPS data
	private LocationManager locationManager;
	private LocationListener locationListener;
	
	// necessary for acquiring sensor data
	private SensorManager sensorManager;
	private Sensor sensor_acc;
	private Sensor sensor_rot;
	
	// necessary for acquiring camera data
	private Camera camera;
	private CameraPreview cameraPreview;
	
	// necessary for binding sensors to ports and making their data available
	private TCPPeer peer;
	private final int PORT = 18700;
	private FrameworkElement[] sensors;
	
	private boolean acc_activated = false;
	private Port<CoreBoolean> acc_enabled_port;
	private PortNumeric<Float> acceleration_x;
	private PortNumeric<Float> acceleration_y;
	private PortNumeric<Float> acceleration_z;
	private float[][] acc_data;
	private float[] acc_data_filtered;
	private int acc_counter = 0;
	
	private boolean rot_activated = false;
	private Port<CoreBoolean> rot_enabled_port;
	private PortNumeric<Float> rotation_x;
	private PortNumeric<Float> rotation_y;
	private PortNumeric<Float> rotation_z;
	private float[][] rot_data;
	private float[] rot_data_filtered;
	private int rot_counter = 0;
	
	private boolean gps_activated = false;
	private Port<CoreBoolean> gps_enabled_port;
	private PortNumeric<Double> gps_altitude;
	private PortNumeric<Double> gps_latitude;
	private PortNumeric<Double> gps_longitude;
	private PortNumeric<Integer> gps_satellites;
	
	private boolean camera_activated = false;
	private Port<CoreBoolean> camera_enabled_port;
	private Port<CoreBoolean> camera_use_jpeg;
	private Port<Image> camera_image;
	private Port<CompressedImage> camera_comp_image;
	private boolean useJPEG = false;
	private final byte JPEG_QUALITY = 80;
	
	private final int ROUNDING_DIGITS = 4;
	private final int FILTER_WINDOW = 15;
	private final int DATA_DIMENSION = 3;
	
	private String ip;
	private String acceleration_x_uid;
	private String acceleration_y_uid;
	private String acceleration_z_uid;
	private String acceleration_enable_uid;
	private String rotation_x_uid;
	private String rotation_y_uid;
	private String rotation_z_uid;
	private String rotation_enable_uid;
	private String gps_altitude_uid;
	private String gps_latitude_uid;
	private String gps_longitude_uid;
	private String gps_satellites_uid;
	private String gps_enable_uid;
	private String camera_image_uid;
	private String camera_compimage_uid;
	private String camera_enable_uid;
	private String camera_jpeg_uid;
		
	
	// =======================================
	// ACTIVITY - SPECIFIC
	// =======================================
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
        	setContentView(R.layout.activity_sensor_p);
        else
        	setContentView(R.layout.activity_sensor_l);
        
        getConfig();
        
        // Initializes all FrameworkElements and Input/Output Ports
        initFrameworkElements();
        initPorts();
        FrameworkElement.initAll();
        
        // Initializes all Sensors and binds them to member-variables
        initSensors();
        
        // Initializes wifiManager to notify if WiFi-state has changed
        initWifi();
                
        // Initializes locationManager and GPS-Listener
        initGPS();
        
        // Enables the CheckBox for camera if a camera is available in this device
        checkCamera(this);
                
        // Initializes a TCP Server, offering ports and displays its IP + port
        initServer();
        
        // Initializes arrays for filtering purposes
        initFilter();
        
        // Restore previous state (i.e. orientation has changed)
        if (savedInstanceState != null) {
        	restoreState(savedInstanceState);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_findroid_sensor, menu);
        return true;
    }
    
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	peer.managedDelete();
    	for (FrameworkElement element : sensors) {
    		element.managedDelete();
    	}
    	triggerAcceleration(false);
    	triggerRotation(false);
    	triggerGPS(false);
    	if (locationListener != null) locationManager.removeUpdates(locationListener);
    	triggerCamera(false);
    	if (camera != null) camera.release();
    	this.unregisterReceiver(broadcastReceiver);
    }
    
    @Override
    public void onBackPressed() {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Quit the FinDroid Sensor-Part?");
        
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
    
    public void onSaveInstanceState(Bundle bundle) {
    	super.onSaveInstanceState(bundle);
    	bundle.putBoolean("acceleration", acc_activated);
    	bundle.putBoolean("rotation", rot_activated);
    	bundle.putBoolean("gps", gps_activated);
    	bundle.putBoolean("camera", camera_activated);
    	bundle.putBoolean("format", useJPEG);
    }
    
    public void restoreState(Bundle bundle) {
    	triggerAcceleration(bundle.getBoolean("acceleration"));
    	triggerRotation(bundle.getBoolean("rotation"));
    	triggerGPS(bundle.getBoolean("gps"));
    	triggerCamera(bundle.getBoolean("camera"));
    	if (camera != null) changeFormat(bundle.getBoolean("format"));
    }
    
    
    // =======================================
 	// BUTTON - EVENTS
 	// =======================================
    public void triggerSensors(View view) {
    	triggerAcceleration(checked(R.id.sensor_checkbox_acc));
    	triggerRotation(checked(R.id.sensor_checkbox_rot));
    	triggerGPS(checked(R.id.sensor_checkbox_gps));
    	triggerCamera(checked(R.id.sensor_checkbox_camera));
    }
    
	public void triggerFormat(View view) {
		changeFormat(((ToggleButton) view).isChecked());
	}
    
    
 	// =======================================
    // SENSOR - RELATED
    // =======================================
    @Override
    public void onSensorChanged(SensorEvent event) {
    	switch (event.sensor.getType()) {
    		case Sensor.TYPE_ACCELEROMETER:
    			System.arraycopy(event.values, 0, acc_data[acc_counter], 0, 3);
    			acc_data_filtered = filter(acc_data, ROUNDING_DIGITS);
    	        acceleration_x.publish(acc_data_filtered[0]);
    	        acceleration_y.publish(acc_data_filtered[1]);
    	       	acceleration_z.publish(acc_data_filtered[2]);
    	       	acc_counter = (acc_counter == FILTER_WINDOW-1) ? 0 : acc_counter+1;
    	       	break;
    		case Sensor.TYPE_ROTATION_VECTOR:
    			System.arraycopy(event.values, 0, rot_data[rot_counter], 0, 3);
       		    rot_data_filtered = filter(rot_data, ROUNDING_DIGITS);
    			rotation_x.publish(rot_data_filtered[0]);
    	       	rotation_y.publish(rot_data_filtered[1]);
    	       	rotation_z.publish(rot_data_filtered[2]);
    	       	rot_counter = (rot_counter == FILTER_WINDOW-1) ? 0 : rot_counter+1;
    	       	break;
    	}
    }   

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		
	}
	
	// Create a sensorManager and tries to bind desired sensors if they're available on the device
	public void initSensors() {
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
		if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
			sensor_acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			enableCheckBox(R.id.sensor_checkbox_acc, true);
		}
		if (sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null) {
			sensor_rot = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
			enableCheckBox(R.id.sensor_checkbox_rot, true);
		}
	}
	
	public void triggerAcceleration(boolean enable) {
		if (enable && !acc_activated) {
			sensorManager.registerListener(this, sensor_acc, SensorManager.SENSOR_DELAY_FASTEST);
			acc_activated = true;
		} else if (!enable && acc_activated) {
			sensorManager.unregisterListener(this, sensor_acc);
			acc_activated = false;
		}
	}
	
	public void triggerRotation(boolean enable) {
		if (enable && !rot_activated) {
			sensorManager.registerListener(this, sensor_rot, SensorManager.SENSOR_DELAY_FASTEST);
			rot_activated = true;
		} else if (!enable && rot_activated){
			sensorManager.unregisterListener(this, sensor_rot);
			rot_activated = false;
		}
	}
	
	public void initFilter() {
		acc_data = new float[FILTER_WINDOW][DATA_DIMENSION];
		acc_data_filtered = new float[DATA_DIMENSION];
		rot_data = new float[FILTER_WINDOW][DATA_DIMENSION];
		rot_data_filtered = new float[DATA_DIMENSION];
	}
	
	public float[] filter(float[][] data, int digits) {
		float[] filtered = new float[DATA_DIMENSION];
		for (int i = 0; i < DATA_DIMENSION; i++) {
			filtered[i] = 0;
			for (int j = 0; j < FILTER_WINDOW; j++) {
				filtered[i] += data[j][i];
			}
			filtered[i] /= FILTER_WINDOW;
		}
	
		for (int i  = 0; i < DATA_DIMENSION; i++) {
			filtered[i] *= Math.pow(10, digits);
			filtered[i] = (int) filtered[i];
			filtered[i] = (float) (filtered[i] / Math.pow(10, digits));
		}
		
		return filtered;
	}
	
	
	// =======================================
	// GPS - Related
	// =======================================
	// Creates manager for location-services and GPS-listener
	public void initGPS() {
		if (this.getSystemService(Context.LOCATION_SERVICE) != null) {
			locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
	        
	        locationListener = new LocationListener() {
	            @Override
	            public void onStatusChanged(String provider, int status, Bundle extras) {
	            	switch(status) {
	            		case LocationProvider.AVAILABLE:
	            			writeGPSInfo("GPS Provider is enabled and available.");
	            			break;
	            		case LocationProvider.TEMPORARILY_UNAVAILABLE:
	            			writeGPSInfo("GPS Provider is enabled but temporarily unavailable.");
	            			break;
	            		case LocationProvider.OUT_OF_SERVICE:
	            			writeGPSInfo("GPS Provider is enabled but out of service");
	            	}
	            }
	            
	            @Override
	            public void onProviderEnabled(String provider) {
	            	writeGPSInfo("GPS Provider is enabled.");
	            }
	            
	            @Override
	            public void onProviderDisabled(String provider) {
	            	writeGPSInfo("GPS Provider is disabled.");
	            }
	            
	            @Override // Callback when the GPS location has changed, publish on ports HERE
	            public void onLocationChanged(Location location) {
	            	if (gps_activated) {
	            		gps_latitude.publish(location.getLatitude());
		                gps_longitude.publish(location.getLongitude());
		                gps_altitude.publish(location.getAltitude());
		                gps_satellites.publish(location.getExtras().getInt("satellites"));
		                
		                /*TextView view = (TextView) findViewById(R.id.sensor_textview_bottom);
		                view.setText("Latitude: " + location.getLatitude() + '\n' + 
		                		"Longitude: " + location.getLongitude());*/
	            	}  
	            }
	        };
	        
	        enableCheckBox(R.id.sensor_checkbox_gps, true);
	        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
	        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) 
	        	writeGPSInfo("GPS-Provider is enabled.");
	        else writeGPSInfo("GPS-Provider is disabled.");
		}
	}
	
	public void triggerGPS(boolean enable) {
		gps_activated = enable;
	}
	
	public void writeGPSInfo(String msg) {
		TextView textView = (TextView) findViewById(R.id.sensor_textview_gps);
		textView.setText(msg);
	}
	
	// =======================================
	// WiFi - RELATED
	// =======================================
	// Create a WifiManager, which allows to check whether WiFi is enabled or not
	public void initWifi() {
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		
		this.registerReceiver(broadcastReceiver,
		         new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}
	
	public void writeWifiInfo() {
		TextView textView = (TextView) findViewById(R.id.sensor_textview_server);
		String msg;
		if (wifiEnabled && wifiConnected) msg = "IP: " + 
				intToIp(wifiManager.getDhcpInfo().ipAddress) + ":" + PORT + '\n' + 
				"Connect-To IP: " + ip + '\n';
		else if (wifiEnabled && !wifiConnected) msg = "This device isn't connected to a WiFi network.\n";
		else msg = "WiFi isn't enabled on this device.\n";
		textView.setText(msg);
	}
	
	public void setWifiFlags() {
		if (wifiManager.isWifiEnabled()) {
		   	wifiEnabled = true;
		    wifiConnected = wifiManager.getConnectionInfo().getNetworkId() != -1;
		} else {
		    wifiEnabled = false;
		    wifiConnected = false;
		}
	}
	
	// =======================================
	// CAMERA - RELATED
	// =======================================
	public Camera getCamera(){
	    Camera camera = null;
	    try {
	        camera = Camera.open();
	    } catch (Exception e) {
	    	
	    }
	    return camera;
	}
	
	public void checkCamera(Context context) {
		if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {	
			enableCheckBox(R.id.sensor_checkbox_camera, true);
			cameraPreview = new CameraPreview(this);
			FrameLayout preview = (FrameLayout) findViewById(R.id.sensor_framelayout_preview);
			preview.addView(cameraPreview);
		}
	}
	
	public void triggerCamera(boolean enable) {
		if (enable && !camera_activated) {
			camera = getCamera();
			camera.setPreviewCallback(this);
			try {
				camera.setPreviewDisplay(cameraPreview.getHolder());
			} catch (IOException e) {
				e.printStackTrace();
			}
			camera.startPreview();
			camera_activated = true;
			findViewById(R.id.sensor_togglebutton_jpeg).setEnabled(true);
		} else if (!enable && camera_activated) {
			camera.setPreviewCallback(null);
			camera.stopPreview();
			camera.release();
			camera_activated = false;
			findViewById(R.id.sensor_togglebutton_jpeg).setEnabled(false);
		}
	}
	
	public boolean supportsJPEG(Camera camera) {
		for (int i : camera.getParameters().getSupportedPreviewFormats()) {
			if (i == ImageFormat.JPEG) return true;
		}
		return false;
	}
	
	public void changeFormat(boolean toJPEG) {
		this.useJPEG = toJPEG;
		Camera.Parameters parameters;
		if (useJPEG && supportsJPEG(camera)) {
			parameters = camera.getParameters();
			parameters.setPreviewFormat(ImageFormat.JPEG);
		} else {
			parameters = camera.getParameters();
			parameters.setPreviewFormat(ImageFormat.NV21);
		}
		camera.setParameters(parameters);
	}
	
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {		
		if (useJPEG) {
			CompressedImage image_comp;
			byte[] camera_data;
			if (supportsJPEG(camera)) {
				image_comp = camera_comp_image.getUnusedBuffer();
				camera_data = data;
			} else {
				Size size = camera.getParameters().getPreviewSize();
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
		    	YuvImage image = new YuvImage(data, camera.getParameters().getPreviewFormat(), 
		    			size.width, size.height, null);
		    	Rect rect = new Rect(0, 0, size.width, size.height);
		    	image.compressToJpeg(rect, JPEG_QUALITY, stream);
		    	image_comp = camera_comp_image.getUnusedBuffer();
				camera_data = stream.toByteArray();
			}
			image_comp.setBuffer(camera_data);
			camera_comp_image.publish(image_comp);
		} else {
			Image image = camera_image.getUnusedBuffer();
			Size size = camera.getParameters().getPreviewSize();
			image.setContent(size.width, size.height, data);
			camera_image.publish(image);
		}
	}
	
	
	// =======================================
	// FINROC - RELATED
	// =======================================
	// Create a TCP-Server, which makes the output-ports visible
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
                writeWifiInfo();
            }
        }).start();
	}
	
	// Initializes all FrameworkElements; should be called BEFORE initPorts()
	public void initFrameworkElements() {
		sensors = new FrameworkElement[5];
		sensors[0] = new FrameworkElement(RuntimeEnvironment.getInstance(), "Sensors");
		sensors[1] = new FrameworkElement(sensors[0], "Acceleration");
		sensors[2] = new FrameworkElement(sensors[0], "Rotation-Vector");
		sensors[3] = new FrameworkElement(sensors[0], "GPS");
		sensors[4] = new FrameworkElement(sensors[0], "Camera");
	}
	
	// Initializes all Ports, should only be called AFTER initFrameworkElements()
	public void initPorts() {
		// Acceleration - Ports
		acceleration_x = new PortNumeric<Float>(new PortCreationInfo("Acceleration X", 
    			sensors[1], CoreNumber.TYPE, PortFlags.SHARED_OUTPUT_PORT));
		acceleration_x.connectTo(acceleration_x_uid);
		acceleration_y = new PortNumeric<Float>(new PortCreationInfo("Acceleration Y", 
    			sensors[1], CoreNumber.TYPE, PortFlags.SHARED_OUTPUT_PORT));
		acceleration_y.connectTo(acceleration_y_uid);
		acceleration_z = new PortNumeric<Float>(new PortCreationInfo("Acceleration Z", 
    			sensors[1], CoreNumber.TYPE, PortFlags.SHARED_OUTPUT_PORT));
		acceleration_z.connectTo(acceleration_z_uid);
		acc_enabled_port = new Port<CoreBoolean>(new PortCreationInfo("Acceleration Enable", 
				sensors[1], CoreBoolean.TYPE, PortFlags.SHARED_INPUT_PORT));
		acc_enabled_port.connectTo(acceleration_enable_uid);
		acc_enabled_port.addPortListener(this);
		
		// Rotation-Vector - Ports
		rotation_x = new PortNumeric<Float>(new PortCreationInfo("Rotation X", 
    			sensors[2], CoreNumber.TYPE, PortFlags.SHARED_OUTPUT_PORT));
		rotation_x.connectTo(rotation_x_uid);
		rotation_y = new PortNumeric<Float>(new PortCreationInfo("Rotation Y", 
    			sensors[2], CoreNumber.TYPE, PortFlags.SHARED_OUTPUT_PORT));
		rotation_y.connectTo(rotation_y_uid);
		rotation_z = new PortNumeric<Float>(new PortCreationInfo("Rotation Z", 
    			sensors[2], CoreNumber.TYPE, PortFlags.SHARED_OUTPUT_PORT));
		rotation_z.connectTo(rotation_z_uid);
		rot_enabled_port = new Port<CoreBoolean>(new PortCreationInfo("Rotation Enable", 
				sensors[2], CoreBoolean.TYPE, PortFlags.SHARED_INPUT_PORT));
		rot_enabled_port.connectTo(rotation_enable_uid);
		rot_enabled_port.addPortListener(this);
		
		// GPS - Ports
		gps_altitude = new PortNumeric<Double>(new PortCreationInfo("GPS Altitude", 
    			sensors[3], CoreNumber.TYPE, PortFlags.SHARED_OUTPUT_PORT));
		gps_altitude.connectTo(gps_altitude_uid);
		gps_latitude = new PortNumeric<Double>(new PortCreationInfo("GPS Latitude", 
    			sensors[3], CoreNumber.TYPE, PortFlags.SHARED_OUTPUT_PORT));
		gps_latitude.connectTo(gps_latitude_uid);
		gps_longitude = new PortNumeric<Double>(new PortCreationInfo("GPS Longitude", 
    			sensors[3], CoreNumber.TYPE, PortFlags.SHARED_OUTPUT_PORT));
		gps_longitude.connectTo(gps_longitude_uid);
		gps_satellites = new PortNumeric<Integer>(new PortCreationInfo("GPS Satellites", 
    			sensors[3], CoreNumber.TYPE, PortFlags.SHARED_OUTPUT_PORT));
		gps_satellites.connectTo(gps_satellites_uid);
		gps_enabled_port = new Port<CoreBoolean>(new PortCreationInfo("GPS Enable", 
				sensors[3], CoreBoolean.TYPE, PortFlags.SHARED_INPUT_PORT));
		gps_enabled_port.connectTo(gps_enable_uid);
		gps_enabled_port.addPortListener(this);
		
		// Camera - Ports
		camera_image = new Port<Image>(new PortCreationInfo("Camera Image", 
				sensors[4], Image.TYPE, PortFlags.SHARED_OUTPUT_PORT));
		camera_image.connectTo(camera_image_uid);
		/*camera_comp_image = new Port<CompressedImage>(new PortCreationInfo("Camera Comp. Image",
				sensors[4], CompressedImage.TYPE, PortFlags.SHARED_OUTPUT_PORT));
		camera_comp_image.connectTo(camera_compimage_uid);*/
		camera_enabled_port = new Port<CoreBoolean>(new PortCreationInfo("Camera Enable", 
				sensors[4], CoreBoolean.TYPE, PortFlags.SHARED_INPUT_PORT));
		camera_enabled_port.connectTo(camera_enable_uid);
		camera_use_jpeg = new Port<CoreBoolean>(new PortCreationInfo("Camera Use JPEG", 
				sensors[4], CoreBoolean.TYPE, PortFlags.SHARED_INPUT_PORT));
		camera_use_jpeg.connectTo(camera_jpeg_uid);
		camera_enabled_port.addPortListener(this);
		camera_use_jpeg.addPortListener(this);
	}
	
	@Override // Callback if one of the boolean input ports has changed
	public void portChanged(AbstractPort origin, final CoreBoolean value) {
		// Acceleration-Enabled Port has changed
		if (origin == acc_enabled_port.getWrapped()) triggerAcceleration(value.get());
		
		// RotationVector-Enabled Port has changed
		if (origin == rot_enabled_port.getWrapped()) triggerRotation(value.get());
		
		// GPS-Enabled Port has changed
		if (origin == gps_enabled_port.getWrapped()) triggerGPS(value.get());
		
		// Camera-Enabled Port has changed
		if (origin == camera_enabled_port.getWrapped()) {
			runOnUiThread(new Runnable() {
				public void run() {
					triggerCamera(value.get());
				}
			});
		}
		
		// Camera-Use-JPEG Port has changed
		if (origin == camera_use_jpeg.getWrapped()) changeFormat(value.get());
	}

	
	// =======================================
	// Miscellaneous
	// =======================================
	// True if CheckBox with given parameter id is checked
	public boolean checked(int id) {
		CheckBox check = (CheckBox) findViewById(id);
		return check.isChecked();
	}
	
	public void enableCheckBox(int id, boolean state) {
		CheckBox checkBox = (CheckBox) findViewById(id);
		checkBox.setEnabled(state);
	}	
	
	public String intToIp(int i) {
        return (i & 0xFF) + "." +
               ((i >> 8) & 0xFF) + "." +
               ((i >> 16) & 0xFF) + "." +
               ((i >> 24) & 0xFF);
    }
	
	public void getConfig() {
 		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
 		ip = prefs.getString("Sensor-IP", "131.246.160.97:4444");
 		acceleration_enable_uid = prefs.getString("Acceleration-Enable", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		acceleration_x_uid = prefs.getString("Acceleration-X", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		acceleration_y_uid = prefs.getString("Acceleration-Y", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		acceleration_z_uid = prefs.getString("Acceleration-Z", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		rotation_enable_uid = prefs.getString("Rotation-Enable", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		rotation_x_uid = prefs.getString("Rotation-X", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		rotation_y_uid = prefs.getString("Rotation-Y", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		rotation_z_uid = prefs.getString("Rotation-Z", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		gps_altitude_uid = prefs.getString("GPS-Altitude", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		gps_latitude_uid = prefs.getString("GPS-Latitude", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		gps_longitude_uid = prefs.getString("GPS-Longitude", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		gps_satellites_uid = prefs.getString("GPS-Satellites", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		gps_enable_uid = prefs.getString("GPS-Enable", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		camera_image_uid = prefs.getString("Camera-Image", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		camera_compimage_uid = prefs.getString("Camera-CompImage", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		camera_enable_uid = prefs.getString("Camera-Enable", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
 		camera_jpeg_uid = prefs.getString("Camera-JPEG", "/Main Thread/ExcavatorControl/Controller Input/Desired Tcp X");
	}
	
}

