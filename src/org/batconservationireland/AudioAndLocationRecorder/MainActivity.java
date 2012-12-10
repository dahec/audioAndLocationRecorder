package org.batconservationireland.AudioAndLocationRecorder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;
import android.os.CountDownTimer;
import android.media.ToneGenerator;
import android.media.AudioManager;

public class MainActivity extends Activity {
	
	/**
	* STOPPED : ready to enter square
	* READY : square entered, ready to record
	* INITIALISING : initialising recorders
	* RECORDING : recording in progress
	*/
	public enum State {STOPPED, READY, INITIALISING, RECORDING};
	
	private State state;
	private EditText squareInput = null;
	private Dialog recorderReadyDialog = null;
	private ProgressDialog recorderRecordingDialog = null;
	private Dialog areyousureDialog = null;
	private ProgressDialog waitingForLocationDialog = null;
	private Dialog errorDialog = null;
	private String currentOutputFileRoot = "";
	private AudioRecorder audioRecorder = null;
	private LocationRecorder locationRecorder = null;
	private StartAudioRecorderListener startAudioRecorderListener = null;
	private CountDownTimer timer = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        squareInput = (EditText) findViewById(R.id.edittext);
        
        if (timer != null) {
        	timer.cancel();
        }
       	restoreState(savedInstanceState);
        setupDialogs();
        setupBasePageButtonListeners();
        setupBroadcastReceivers();
        restoreDialogs();
    }
    
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) 
    {
    	if (timer != null) {
        	timer.cancel();
        }
    	savedInstanceState.putString("square",squareInput.getText().toString());
    	savedInstanceState.putString("state", state.name());
    	savedInstanceState.putString("currentOutputFileRoot", currentOutputFileRoot);
    	super.onSaveInstanceState(savedInstanceState);
    }
    
    @Override
    public void onDestroy()
    {
    	if (timer != null) {
        	timer.cancel();
        }
    	stopRecorders();
    	unregisterReceiver(startAudioRecorderListener);
   		super.onDestroy();
    }
   
    private void beep()
    {
    	final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
        tg.startTone(ToneGenerator.TONE_PROP_BEEP);	
    }
    
    private void beep2()
    {
    	final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
        tg.startTone(ToneGenerator.TONE_PROP_BEEP2);	
    }
    
    private void ack()
    {
    	final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
        tg.startTone(ToneGenerator.TONE_PROP_ACK);	
    }
    
    private void showRecorderReadyDialog() {
    	Pattern pattern = Pattern.compile("^[A-Za-z][0-9]{2}$");
        Matcher matcher = pattern.matcher(squareInput.getText().toString());
        if (matcher.find()) {
        	state = State.READY;
        	recorderReadyDialog.show();
        } else {
        	squareInput.setText("");
			Toast.makeText(MainActivity.this, R.string.enterSquareValidationError, Toast.LENGTH_LONG).show();
			squareInput.requestFocus();
        }
    }
    
    
    private void setWaitingForLocationDialogMessage()
    {
    	waitingForLocationDialog.setMessage("Waiting for location fix. DO NOT DRIVE until this message changes to 'Recording ...'.");
        waitingForLocationDialog.show();
		timer = new CountDownTimer(60000, 1000) {
    		public void onTick(long millisUntilFinished) {}
    		public void onFinish() {
    			waitingForLocationDialog.setMessage("Still waiting for location fix after 1 minute. There may be a problem with the device. If this message does not change to \"Recording ...\" soon you should try re-starting your device by turning it off, taking out the battery, putting the battery back in and then turning the device back on.");
    		}
    	}.start();	
    }
    
    private void startRecorders()
    {
    	String sdState = android.os.Environment.getExternalStorageState();
	    if(!sdState.equals(android.os.Environment.MEDIA_MOUNTED))  {
	    	ack();
    		final TextView tv = (TextView) errorDialog.findViewById(R.id.errorText);
	    	tv.setText(R.string.sdError);
	    	errorDialog.show();
	    } else {
		    
		    state = State.INITIALISING;
		    setWaitingForLocationDialogMessage();
		    
			//Get the date and time to create filenames with
		    Calendar cal = Calendar.getInstance();
		    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
		    currentOutputFileRoot = squareInput.getText().toString() + "-";
		    currentOutputFileRoot +=  sdf.format(cal.getTime()).toString(); 
		        
		    try {
		    	locationRecorder.start(currentOutputFileRoot);
		    } catch (FileNotFoundException e) {
		    	state = State.READY;
		    	waitingForLocationDialog.dismiss();
		    	if (timer != null) {
		        	timer.cancel();
		        }
		    	stopRecorders();
		    	ack();
		    	final TextView tv = (TextView) errorDialog.findViewById(R.id.errorText);
		    	tv.setText(R.string.locationWriteError);
		    	errorDialog.show();
		    }
		    
	    }
    }

    private class AudioRecorder {

    	private ExtAudioRecorder recorder = null;
    	
	    public void start(String outputFileRoot) throws Exception {
	    	File audioFile = new File(getExternalFilesDir(null), outputFileRoot + ".wav");
    		recorder = ExtAudioRecorder.getInstance(false);
    		recorder.setOutputFile(audioFile.getAbsolutePath());
    	   	recorder.prepare();
	    	recorder.start();
	    }
	    
	    public void stop() throws Exception
	    {
	    	if (recorder != null) {
	    		try {
	    			recorder.release();
	    		} catch (Exception e) {
	    			throw e;
	    		}
	    	}
	    }
	}

    private class LocationRecorder  {

    	private LocationManager locationManager = null;
    	private LocationListener locationListener = null;
    	private PrintWriter locationFileWriter = null;
    	private long recordingStartTime  = 0;
		
    	
    	public void start(String outputFileRoot) throws FileNotFoundException
    	{
    		File locationFile = new File(getExternalFilesDir(null), outputFileRoot + ".csv");
   	    	locationFileWriter = new PrintWriter(locationFile);
    		recordingStartTime = 0;
    		locationFileWriter.println("Seconds since recording started,Latitude,Longitude,Accuracy (m),Speed (m/s),Altitude (m)");
    	    locationFileWriter.flush();
    	    	
    	    // Acquire a reference to the system Location Manager
    	    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

    	    // Define a listener that responds to location updates
    	    locationListener = new LocationListener() {
    	        public void onLocationChanged(Location location) {
    	        	if (recordingStartTime == 0) {
    	        		recordingStartTime = System.currentTimeMillis();
    	        		sendBroadcast(new Intent("org.batconservationireland.AudioAndLocationRecorder.START_AUDIO_RECORDER_ACTION"));
    	        	}
    	        	long msSinceStart = System.currentTimeMillis() - recordingStartTime;
    	        	locationFileWriter.println(Double.toString((double)msSinceStart / 1000) + "," +
					    	        			location.getLatitude() + "," +
							  					location.getLongitude() + "," +
							  					location.getAccuracy() + "," +
							  					location.getSpeed() + "," +
							  					location.getAltitude());
    	        	locationFileWriter.flush();
    	        }
        	    public void onStatusChanged(String provider, int status, Bundle extras) {}
        	    public void onProviderEnabled(String provider) {}
        	    public void onProviderDisabled(String provider) {}
       	  	};

       	  	// Register the listener with the Location Manager to receive location updates
       	  	locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
       	  	locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
    	}
    	
    	public void stop()
    	{
    		if (locationManager instanceof LocationManager) {
    			locationManager.removeUpdates(locationListener);
    		}
    		if (locationFileWriter instanceof PrintWriter) {
    			locationFileWriter.close();
    		}
    	}
	}
    
    public class StartAudioRecorderListener extends BroadcastReceiver {
		 @Override
		 public void onReceive(Context context, Intent intent) {
			 state = State.RECORDING;
			 recorderReadyDialog.dismiss();
			 recorderRecordingDialog.show();
			 waitingForLocationDialog.dismiss();
	         audioRecorder = new AudioRecorder();
	         try {
	        	 audioRecorder.start(currentOutputFileRoot);
	        	 beep2();
	         } catch (Exception e) {
	        	 ack();
	        	 locationRecorder.stop();
	        	 state = State.READY;
	        	 final TextView tv = (TextView) errorDialog.findViewById(R.id.errorText);
	        	 tv.setText(R.string.audioWriteError);
	        	 errorDialog.show();
	         }
	         if (timer != null) {
	         	timer.cancel();
	         }
		 }
	}
    
    private void stopRecorders()
    {
    	if (locationRecorder != null) {
    		locationRecorder.stop();
    	}
    	try {
	    	if (audioRecorder != null) {
	    		audioRecorder.stop();
	    	}
	    	state = State.READY;
    	} catch (Exception e) {
    		ack();
    		final TextView tv = (TextView) errorDialog.findViewById(R.id.errorText);
	    	tv.setText(R.string.finalizeError);
	    	errorDialog.show();
	    	state = State.STOPPED;
    	}
    	
    }
    
    
    private void restoreState(Bundle savedInstanceState)
    {
    	//Restore instance state
        if (savedInstanceState != null) {
        	squareInput.setText(savedInstanceState.getString("square"));
        	state = State.valueOf(savedInstanceState.getString("state"));
        	currentOutputFileRoot = savedInstanceState.getString("currentOutputFileRoot");
        	if (getLastNonConfigurationInstance() != null) {
        		Object[] retainedObjects = (Object[]) getLastNonConfigurationInstance();
    	    	locationRecorder = (LocationRecorder)retainedObjects[0];
    	    	audioRecorder = (AudioRecorder)retainedObjects[1];
    	    } else {
    	    	locationRecorder = new LocationRecorder();
            	audioRecorder = new AudioRecorder();
    	    }
        	
        } else {
        	state = State.STOPPED;
        	locationRecorder = new LocationRecorder();
        	audioRecorder = new AudioRecorder();
        }
        
    }
    
    private void setupDialogs()
    {
    	errorDialog = new Dialog(this);
    	errorDialog.setContentView(R.layout.error_dialog);
    	errorDialog.setCancelable(false);
    	errorDialog.setTitle(R.string.error);
        Button ok_btn = (Button) errorDialog.findViewById(R.id.errorOkButton);
        ok_btn.setOnClickListener(new View.OnClickListener() 
        {
        	public void onClick(View view)
        	{
        		beep();
        		stopRecorders();
        		waitingForLocationDialog.dismiss();
        		recorderReadyDialog.dismiss();
        		recorderRecordingDialog.dismiss();
        		areyousureDialog.dismiss();
        		errorDialog.dismiss();
        	}
        }); 
        
    	
        recorderReadyDialog = new Dialog(this);
        recorderReadyDialog.setContentView(R.layout.recorder_dialog);
        recorderReadyDialog.setCancelable(false);
        recorderReadyDialog.setTitle(R.string.recorderStatusReady);
        Button cancel_btn = (Button) recorderReadyDialog.findViewById(R.id.recorderCancelButton);
        cancel_btn.setText(R.string.cancelButtonText);
        cancel_btn.setOnClickListener(new View.OnClickListener() 
        {
        	public void onClick(View view)
        	{
        		beep();
        		stopRecorders();
        		recorderReadyDialog.dismiss();
        	}
        }); 
        Button recordstart_btn = (Button) recorderReadyDialog.findViewById(R.id.recordButton);
        recordstart_btn.setText(R.string.recordButtonStart);
        recordstart_btn.setOnClickListener(new View.OnClickListener() 
        {
        	public void onClick(View view)
        	{
        		beep();
        		startRecorders();
        	}
        });
        
        recorderRecordingDialog = new ProgressDialog(this);
        recorderRecordingDialog.setMessage("Recording ...");
        recorderRecordingDialog.setCancelable(false);
        recorderRecordingDialog.setButton("Stop recording", new DialogInterface.OnClickListener() 
        {
            public void onClick(DialogInterface dialog, int which) 
            {
            	beep();
            	areyousureDialog.show();
            	timer = new CountDownTimer(5000, 1000) {
            		public void onTick(long millisUntilFinished) {}
            		public void onFinish() {
            			recorderRecordingDialog.show();
            			areyousureDialog.dismiss();
            		}
            	}.start();

            }
        });
        
        
        areyousureDialog = new Dialog(this);
        areyousureDialog.setContentView(R.layout.areyousure_dialog);
        areyousureDialog.setTitle(R.string.areYouSure);
        areyousureDialog.setCancelable(false);
        Button stoprecording_btn = (Button) areyousureDialog.findViewById(R.id.areyousureProceed);
        stoprecording_btn.setOnClickListener(new View.OnClickListener() 
        {
        	public void onClick(View view)
        	{
        		beep();
        		stopRecorders();
        		state = State.STOPPED;
        		areyousureDialog.dismiss();
        		recorderRecordingDialog.dismiss();
        		if (!errorDialog.isShowing()) {
        			recorderReadyDialog.show();
        		}
        		if (timer != null) {
                	timer.cancel();
                }
        	}
        }); 
        Button cancelstoprecording_btn = (Button) areyousureDialog.findViewById(R.id.areyousureCancel);
        cancelstoprecording_btn.setOnClickListener(new View.OnClickListener() 
        {
        	public void onClick(View view)
        	{
        		beep();
        		recorderRecordingDialog.show();
        		areyousureDialog.dismiss();
        		if (timer != null) {
                	timer.cancel();
                }
        	}
        });
        
        waitingForLocationDialog = new ProgressDialog(this);
        waitingForLocationDialog.setCancelable(false);
        waitingForLocationDialog.setButton("Cancel", new DialogInterface.OnClickListener() 
        {
            public void onClick(DialogInterface dialog, int which) 
            {
            	beep();
            	stopRecorders();
            	waitingForLocationDialog.dismiss();
            }
        });
    }
    

    
    private void setupBasePageButtonListeners()
    {
    	//Set listener for enter value for square input and button
        squareInput.setOnKeyListener(new OnKeyListener() {
	        public boolean onKey(View v, int keyCode, KeyEvent event) {
	        	if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
	        		beep();
	        		showRecorderReadyDialog();
	        		return true;
	        	}
	        	return false;
	        }
        });
        Button continueButton = (Button) findViewById(R.id.enterSquareButton);
        continueButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	beep();
            	showRecorderReadyDialog();
            }
        });
        //Set listener for exit button
        Button exitButton = (Button) findViewById(R.id.exitButton);
        exitButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	beep();
            	finish();
            }
        });
    }
    
    private void setupBroadcastReceivers()
    {
        startAudioRecorderListener = new StartAudioRecorderListener();
	    IntentFilter intentFilter = new IntentFilter();
	    intentFilter.addAction("org.batconservationireland.AudioAndLocationRecorder.START_AUDIO_RECORDER_ACTION");
        registerReceiver(startAudioRecorderListener,intentFilter);
    }
    
    
    
    
    private void restoreDialogs()
    {
    	switch (state) {
        
	    	case STOPPED:
	    		squareInput.requestFocus();
	    		break;
	    		
	    	case READY:
	    		recorderReadyDialog.show();
	    		break;
	    	
	    	case INITIALISING:
	    		setWaitingForLocationDialogMessage();
	    		waitingForLocationDialog.show();
	    		break;
	    		
	    	case RECORDING:
	    		recorderRecordingDialog.show();
	    		break;
    	}
    }
    
	public Object onRetainNonConfigurationInstance() 
	{
		if (recorderReadyDialog.isShowing())
		{
			recorderReadyDialog.dismiss();
		}
		if (recorderRecordingDialog.isShowing())
		{
			recorderRecordingDialog.dismiss();
		}
		if (waitingForLocationDialog.isShowing())
		{
			waitingForLocationDialog.dismiss();
		}
		if (areyousureDialog.isShowing())
		{
			areyousureDialog.dismiss();
		}
		if (errorDialog.isShowing())
		{
			errorDialog.dismiss();
		}
		Object[] retainedObjects = new Object[2];
		retainedObjects[0] = locationRecorder;
		retainedObjects[1] = audioRecorder;
    	return retainedObjects;
	}
}