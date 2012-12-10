package org.batconservationireland.AudioAndLocationRecorder;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class RecorderActivity extends Activity {
	
	private String square = null;
	private ExtAudioRecorder recorder = null; 
    private boolean recordingInProgress = false;
    private LocationManager locationManager = null;
    private PrintWriter locationFileWriter = null;
    private long recordingStartTime = 0;
    private TextView statusDisplay = null;
    private Button button = null;
    private ProgressDialog waitingForLocationDialog = null;
    private LocationUpdateListener locationUpdateListener = null;
    
    

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.recorder);
	    statusDisplay = (TextView) findViewById(R.id.status);
	    button = (Button) findViewById(R.id.recordButton);
	    
	    this.locationUpdateListener = new LocationUpdateListener();
	    IntentFilter intentFilter = new IntentFilter();
	    intentFilter.addAction("org.batconservationireland.AudioAndLocationRecorder.UPDATE_LOCATION_ACTION");
        registerReceiver(locationUpdateListener,intentFilter);
	    
	    //Restore state if necessary
	    if (savedInstanceState != null) {
	    	square = savedInstanceState.getString("square");
        	recordingInProgress = savedInstanceState.getBoolean("recordingInProgress");
        	recordingStartTime = savedInstanceState.getLong("recordingStartTime");
  	    	if (recordingInProgress) {
  	    		button.setText(R.string.recordButtonStop);
  	    		if (getLastNonConfigurationInstance() != null) {
  	        		Object[] retainedObjects = (Object[]) getLastNonConfigurationInstance();
  	    	    	locationFileWriter = (PrintWriter)retainedObjects[0];
  	    	    	recorder = (ExtAudioRecorder)retainedObjects[1];
  	    	    	locationManager = (LocationManager)retainedObjects[2];
  	    	    	waitingForLocationDialog = (ProgressDialog)retainedObjects[3];
        	    	
                	if (recordingStartTime == 0)
                	{
                		statusDisplay.setText(R.string.recorderStatusInitialising);
                		waitingForLocationDialog.show();
                	}
                	else
        	    	{
        	    		statusDisplay.setText(R.string.recorderStatusRecording);
        	    		statusDisplay.append(" " + square);
        	    	}
        	    } else {
            		Toast.makeText(RecorderActivity.this, "There has been an error after a configuration change (screen rotation or sliding the keyboard in/out).", Toast.LENGTH_LONG).show();
            		this.finish();
            	}
  	    	} else {
        	    	statusDisplay.setText(R.string.recorderStatusReady);
                	statusDisplay.append(" " + square);
                	recordingInProgress = false;
                	recordingStartTime = 0;
            }
        } else {
        	square = getIntent().getExtras().getString("square");
        	statusDisplay.append(" " + square);
        }
	    
	    if (waitingForLocationDialog == null) {
	    	waitingForLocationDialog = new ProgressDialog(RecorderActivity.this);
    		waitingForLocationDialog.setMessage("Waiting for location fix. DO NOT DRIVE until this message disappears.");
	    }
	    	
        
        button.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	try {
            		if (!recordingInProgress) {
                		startRecording();
	                } else {
	                	stopRecording();
	               	}
            		recordingInProgress = !recordingInProgress;
            	} catch (IOException e) {
            		Toast.makeText(RecorderActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
            		recordingInProgress = false;
            		stopRecording();
            	}
            }
        });
	}
	
	@Override
	public void onDestroy()
	{
		unregisterReceiver(locationUpdateListener);
		super.onDestroy();
	}
	
	@Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
    	savedInstanceState.putBoolean("recordingInProgress",recordingInProgress);
    	savedInstanceState.putLong("recordingStartTime",recordingStartTime);
    	savedInstanceState.putString("square",square);
    	super.onSaveInstanceState(savedInstanceState);
    }
	
	@Override
	public Object onRetainNonConfigurationInstance() 
	{
		if (recordingInProgress)
		{
			if (waitingForLocationDialog.isShowing())
			{
				waitingForLocationDialog.dismiss();
			}
			waitingForLocationDialog.dismiss();
			Object[] retainedObjects = new Object[4];
			retainedObjects[0] = locationFileWriter;
			retainedObjects[1] = recorder;
			retainedObjects[2] = locationManager;
			retainedObjects[3] = waitingForLocationDialog;
			return retainedObjects;
		} else {
			return null;
		}
	}
	
	private void startRecording() throws IOException { 
		String state = android.os.Environment.getExternalStorageState();
	    if(!state.equals(android.os.Environment.MEDIA_MOUNTED))  {
	        throw new IOException(this.getString(R.string.ioExceptionSD));
	    }
	    
	    button.setText(R.string.recordButtonStop);
		statusDisplay.setText(R.string.recorderStatusInitialising);
		waitingForLocationDialog.show();
	    
		//Get the date and time to create filenames with
	    Calendar cal = Calendar.getInstance();
	    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
	    String outputFileRoot = sdf.format(cal.getTime()).toString();
	    File audioFile = new File(getExternalFilesDir(null), square.toUpperCase() + "-" + outputFileRoot + ".wav");
	    File locationFile = new File(getExternalFilesDir(null), square.toUpperCase() + "-" + outputFileRoot + ".csv");
    	locationFileWriter = new PrintWriter(locationFile);
	    locationFileWriter.println("Seconds since recording started,Latitude,Longitude,Accuracy (m),,Speed (m/s),Altitude (m)");
	    locationFileWriter.flush();
	    
	    	
	    //LOCATION
	    // Acquire a reference to the system Location Manager
	    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

	    // Define a listener that responds to location updates
	    LocationListener locationListener = new LocationListener() {
	        public void onLocationChanged(Location location) {
	        	Intent intent =
	                new Intent("org.batconservationireland.AudioAndLocationRecorder.UPDATE_LOCATION_ACTION");
	        	Bundle b = new Bundle();
	        	b.putString("locationString", 	location.getLatitude() + "," +
							  					location.getLongitude() + "," +
							  					location.getAccuracy() + "," +
							  					location.getSpeed() + "," +
							  					location.getAltitude());
	        	intent.putExtras(b);
	        	sendBroadcast(intent);
	        }
    	    public void onStatusChanged(String provider, int status, Bundle extras) {}
    	    public void onProviderEnabled(String provider) {}
    	    public void onProviderDisabled(String provider) {}
   	  };

   	  // Register the listener with the Location Manager to receive location updates
   	  locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
   	  locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, locationListener);
    	
   	  //AUDIO
   	  recorder = ExtAudioRecorder.getInstance(false);
   	  recorder.setOutputFile(audioFile.getAbsolutePath());
   	  recorder.prepare();
    }

	private void stopRecording() 
	{
		try {
			recorder.release();
		} catch (Exception e) {
			//Do nothing
		}
	    recorder = null;
	    recordingStartTime = 0;
	    button.setText(R.string.recordButtonStart);
    	statusDisplay.setText(R.string.recorderStatusReady);
    	statusDisplay.append(" " + square);
    	locationFileWriter.close();
    }
	
	public class LocationUpdateListener extends BroadcastReceiver {
		 @Override
		 public void onReceive(Context context, Intent intent) {
	         Bundle bundle = intent.getExtras();
	         String locationString = bundle.getString("locationString");
	         if (recordingStartTime == 0) {
	    		waitingForLocationDialog.dismiss();
	    		statusDisplay.setText(R.string.recorderStatusRecording);
	    		statusDisplay.append(" " + square);
	    		recordingStartTime = System.currentTimeMillis();
	    		recorder.start();
	         }
	         long msSinceStart = System.currentTimeMillis() - recordingStartTime;
	         // Called when a new location is found by the network location provider.
	         locationFileWriter.println(Double.toString((double)msSinceStart / 1000) + "," + locationString);
		 }
	}

    /*private class RecorderStartAsyncTask extends AsyncTask<Context, Void, Void> {

        // automatically done on worker thread (separate from UI thread)
        protected Void doInBackground(Context... params) {
        	recorder.start();
        	
       	  	return null;
        }
     }
    
    private class RecorderStopAsyncTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... unused) {
        	recorder.release();
    	    recorder = null;
    	    recordingStartTime = 0;
        	return null;
        }
    }*/
}

