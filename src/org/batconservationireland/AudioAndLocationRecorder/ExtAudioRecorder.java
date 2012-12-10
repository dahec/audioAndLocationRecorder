/* From http://i-liger.com/article/android-wav-audio-recording */

package org.batconservationireland.AudioAndLocationRecorder;

import java.io.IOException;
import java.io.RandomAccessFile;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class ExtAudioRecorder 
{
	private final static int[] sampleRates = {44100, 22050, 11025, 8000};
	
	public static void checkRecorderParameters(){
	{
		    for (int i = 0; i < sampleRates.length; ++i){
		        try {
		            Log.i(ExtAudioRecorder.class.getName(), "Indexing "+sampleRates[i]+"Hz Sample Rate");
		            int tmpBufferSize = AudioRecord.getMinBufferSize(sampleRates[i], 
		                            AudioFormat.CHANNEL_IN_MONO,
		                            AudioFormat.ENCODING_PCM_16BIT);

		            // Test the minimum allowed buffer size with this configuration on this device.
		            if(tmpBufferSize != AudioRecord.ERROR_BAD_VALUE){
		                // Seems like we have ourself the optimum AudioRecord parameter for this device.
		                AudioRecord tmpRecoder = new AudioRecord(MediaRecorder.AudioSource.MIC, 
		                                                        sampleRates[i], 
		                                                        AudioFormat.CHANNEL_IN_MONO,
		                                                        AudioFormat.ENCODING_PCM_16BIT,
		                                                        tmpBufferSize);
		                // Test if an AudioRecord instance can be initialised with the given parameters.
		                if(tmpRecoder.getState() == AudioRecord.STATE_INITIALIZED){
		                    String configResume = "initRecorderParameters(sRates) has found recorder settings supported by the device:"  
		                                        + "\nSource   = MICROPHONE"
		                                        + "\nsRate    = "+sampleRates[i]+"Hz"
		                                        + "\nChannel  = MONO"
		                                        + "\nEncoding = 16BIT";
		                    Log.i(ExtAudioRecorder.class.getName(), configResume);

		                    //+++Release temporary recorder resources and leave.
		                    tmpRecoder.release();
		                    tmpRecoder = null;

		                    return;
		                } else {
		                	tmpRecoder.release();
		                }
		            }else{
		                Log.w(ExtAudioRecorder.class.getName(), "Incorrect buffer size. Continue sweeping Sampling Rate...");
		            }
		        } catch (IllegalArgumentException e) {
		            Log.e(ExtAudioRecorder.class.getName(), "The "+sampleRates[i]+"Hz Sampling Rate is not supported on this device");
		        }
		    }
		}
	}
	
	public static ExtAudioRecorder getInstance(Boolean recordingCompressed) throws IOException
	{
		
		ExtAudioRecorder result = null;
		
		if(recordingCompressed)
		{
			result = new ExtAudioRecorder(	false, 
											MediaRecorder.AudioSource.MIC, 
											sampleRates[3], 
											AudioFormat.CHANNEL_IN_MONO,
											AudioFormat.ENCODING_PCM_16BIT);
		}
		else
		{
			int i=0;
			do
			{
				result = new ExtAudioRecorder(	true, 
												MediaRecorder.AudioSource.MIC, 
												sampleRates[i], 
												AudioFormat.CHANNEL_IN_MONO,
												AudioFormat.ENCODING_PCM_16BIT);
				
			} while((++i<sampleRates.length) & !(result.getState() == ExtAudioRecorder.State.INITIALIZING));
		}
		
		if (result.getState() == ExtAudioRecorder.State.INITIALIZING)
		{
			return result;
		} 
		else
		{
			throw new IOException(Integer.toString(R.string.ioExceptionUnknown));
		}
	}
	
	/**
	* INITIALIZING : recorder is initializing;
	* READY : recorder has been initialized, recorder not yet started
	* RECORDING : recording
	* ERROR : reconstruction needed
	* STOPPED: reset needed
	*/
	public enum State {INITIALIZING, READY, RECORDING, ERROR, STOPPED};
	
	public static final boolean RECORDING_UNCOMPRESSED = true;
	public static final boolean RECORDING_COMPRESSED = false;
	
	//The minimum amount of time (in ms) we want to listen for between writes
	//Detector listens for 320ms so listen to longer so that we can tell if there's no signal coming
	// from the detector for too long
	// (this is in ms rather than seconds so we don't have to mess around with float types)
	private static final int MIN_TIMER_INTERVAL = 640;
	
	// Toggles uncompressed recording on/off; RECORDING_UNCOMPRESSED / RECORDING_COMPRESSED
	private boolean         rUncompressed;
	
	// Recorder used for uncompressed recording
	private AudioRecord     audioRecorder = null;
	
	// Recorder used for compressed recording
	private MediaRecorder   mediaRecorder = null;
	
	// Stores current amplitude (only in uncompressed mode)
	private int             cAmplitude= 0;
	
	// Output file path
	private String          filePath = null;
	
	// Recorder state; see State
	private State          	state;
	
	// File writer (only in uncompressed mode)
	private RandomAccessFile randomAccessWriter;
		       
	// Number of channels, sample rate, sample bit depth, buffer size
	private short                    nChannels;
	private int                      sRate;
	private short                    bitDepth;
	//The size of the buffer that we write samples to. It's the number of bits read * bit depth * channels / 8 (8 bits in a byte).
	private int                      bufferSize;
	
	// Number of individual samples written to file on each output (only in uncompressed mode)
	private int                      framePeriod;
	
	// Buffer for output(only in uncompressed mode)
	private byte[]                   buffer;
	
	// Number of bytes written to file after header(only in uncompressed mode)
	// after stop() is called, this size is written to the header/data chunk in the wave file
	private int                      payloadSize;
	
	/**
	*
	* Returns the state of the recorder in a RehearsalAudioRecord.State typed object.
	* Useful, as no exceptions are thrown.
	*
	* @return recorder state
	*/
	public State getState()
	{
		return state;
	}
	
	/*
	*
	* Method used for recording.
	*
	*/
	private AudioRecord.OnRecordPositionUpdateListener updateListener = new AudioRecord.OnRecordPositionUpdateListener()
	{
		public void onPeriodicNotification(AudioRecord recorder)
		{
			if (state == State.RECORDING)
			{
				audioRecorder.read(buffer, 0, buffer.length); // Fill buffer
				try
				{ 
					randomAccessWriter.write(buffer); // Write buffer to file
					payloadSize += buffer.length;
					if (bitDepth == 16)
					{
						for (int i=0; i<buffer.length/2; i++)
						{ // 16bit bit depth
							short curSample = getShort(buffer[i*2], buffer[i*2+1]);
							if (curSample > cAmplitude)
							{ // Check amplitude
								cAmplitude = curSample;
							}
						}
					}
					else	
					{ // 8bit bit depth
						for (int i=0; i<buffer.length; i++)
						{
							if (buffer[i] > cAmplitude)
							{ // Check amplitude
								cAmplitude = buffer[i];
							}
						}
					}
				}
				catch (IOException e)
				{
					state = State.ERROR;
					throw new RuntimeException("Error writing to audio file");
				}
			}
		}
	
		public void onMarkerReached(AudioRecord recorder)
		{
			// NOT USED
		}
	};
	/** 
	 * 
	 * 
	 * Default constructor
	 * 
	 * Instantiates a new recorder, in case of compressed recording the parameters can be left as 0.
	 * In case of errors, no exception is thrown, but the state is set to ERROR
	 * 
	 */ 
	public ExtAudioRecorder(boolean uncompressed, int audioSource, int sampleRate, int channelConfig, int audioFormat) throws IOException
	{
		try
		{
			rUncompressed = uncompressed;
			if (rUncompressed)
			{ 
				// RECORDING_UNCOMPRESSED
				if (audioFormat == AudioFormat.ENCODING_PCM_16BIT)
				{
					bitDepth = 16;
				}
				else
				{
					bitDepth = 8;
				}
				
				if (channelConfig == AudioFormat.CHANNEL_IN_MONO)
				{
					nChannels = 1;
				}
				else
				{
					nChannels = 2;
				}
				
				sRate   = sampleRate;

				int minBufferSizeForDetector = (sampleRate * MIN_TIMER_INTERVAL / 1000) * 2 * bitDepth * nChannels / 8;
				bufferSize = AudioRecord.getMinBufferSize(sampleRate, 
									                        AudioFormat.CHANNEL_IN_MONO,
									                        AudioFormat.ENCODING_PCM_16BIT);
				
		        //AudioRecord.getMinBufferSize() returns an error code if the sample rate isn't supported
		        if(bufferSize != AudioRecord.ERROR_BAD_VALUE){
		        	//Adjust the buffer size to what we actually need
		        	while (bufferSize < minBufferSizeForDetector)
					{
						bufferSize *= 2;
					}
					//Reverse engineer frame period from bufferSize
					framePeriod = (bufferSize * 8) / (2 * bitDepth * nChannels);
					
					audioRecorder = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize);
	
					if (audioRecorder.getState() != AudioRecord.STATE_INITIALIZED)
					{
						audioRecorder.release();
						audioRecorder = null;
						throw new IOException(Integer.toString(R.string.ioExceptionAudioRecorder));
					} 
					audioRecorder.setRecordPositionUpdateListener(updateListener);
					audioRecorder.setPositionNotificationPeriod(framePeriod);
		        } else {
		        	throw new RuntimeException("Bad buffer size for sample rate "+sampleRate);
		        }
			} 
			else
			{ // RECORDING_COMPRESSED
				mediaRecorder = new MediaRecorder();
				mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
				mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
				mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);				
			}
			cAmplitude = 0;
			filePath = null;
			state = State.INITIALIZING;
		} catch (RuntimeException e) {
			if (audioRecorder != null)
			{
				audioRecorder.release();
			}
			if (e.getMessage() != null)
			{
				Log.e(ExtAudioRecorder.class.getName(), e.getMessage());
			}
			else
			{
				Log.e(ExtAudioRecorder.class.getName(), "Unknown error occured while initializing recording");
			}
			state = State.ERROR;
		}
	}
	
	/**
	 * Sets output file path, call directly after construction/reset.
	 *  
	 * @param output file path
	 * 
	 */
	public void setOutputFile(String argPath)
	{
		try
		{
			if (state == State.INITIALIZING)
			{
				filePath = argPath;
				if (!rUncompressed)
				{
					mediaRecorder.setOutputFile(filePath);					
				}
			}
		}
		catch (Exception e)
		{
			if (e.getMessage() != null)
			{
				Log.e(ExtAudioRecorder.class.getName(), e.getMessage());
			}
			else
			{
				Log.e(ExtAudioRecorder.class.getName(), "Unknown error occured while setting output path");
			}
			state = State.ERROR;
		}
	}
	
	/**
	 * 
	 * Returns the largest amplitude sampled since the last call to this method.
	 * 
	 * @return returns the largest amplitude since the last call, or 0 when not in recording state. 
	 * 
	 */
	public int getMaxAmplitude()
	{
		if (state == State.RECORDING)
		{
			if (rUncompressed)
			{
				int result = cAmplitude;
				cAmplitude = 0;
				return result;
			}
			else
			{
				try
				{
					return mediaRecorder.getMaxAmplitude();
				}
				catch (IllegalStateException e)
				{
					return 0;
				}
			}
		}
		else
		{
			return 0;
		}
	}
	

	/**
	 * 
	* Prepares the recorder for recording, in case the recorder is not in the INITIALIZING state and the file path was not set
	* the recorder is set to the ERROR state, which makes a reconstruction necessary.
	* In case uncompressed recording is toggled, the header of the wave file is written.
	* In case of an exception, the state is changed to ERROR
	* 	 
	*/
	public void prepare()
	{
		try
		{
			if (state == State.INITIALIZING)
			{
				if (rUncompressed)
				{
					if ((audioRecorder.getState() == AudioRecord.STATE_INITIALIZED) & (filePath != null))
					{
						// write file header
						randomAccessWriter = new RandomAccessFile(filePath, "rw");
						
						randomAccessWriter.setLength(0); // Set file length to 0, to prevent unexpected behavior in case the file already existed
						randomAccessWriter.writeBytes("RIFF");
						randomAccessWriter.writeInt(0); // Final file size not known yet, write 0 
						randomAccessWriter.writeBytes("WAVE");
						randomAccessWriter.writeBytes("fmt ");
						randomAccessWriter.writeInt(Integer.reverseBytes(16)); // Sub-chunk size, 16 for PCM
						randomAccessWriter.writeShort(Short.reverseBytes((short) 1)); // AudioFormat, 1 for PCM
						randomAccessWriter.writeShort(Short.reverseBytes(nChannels));// Number of channels, 1 for mono, 2 for stereo
						randomAccessWriter.writeInt(Integer.reverseBytes(sRate)); // Sample rate
						randomAccessWriter.writeInt(Integer.reverseBytes(sRate*bitDepth*nChannels/8)); // Byte rate, SampleRate*NumberOfChannels*BitsPerSample/8
						randomAccessWriter.writeShort(Short.reverseBytes((short)(nChannels*bitDepth/8))); // Block align, NumberOfChannels*BitsPerSample/8
						randomAccessWriter.writeShort(Short.reverseBytes(bitDepth)); // Bits per sample
						randomAccessWriter.writeBytes("data");
						randomAccessWriter.writeInt(0); // Data chunk size not known yet, write 0

						buffer = new byte[framePeriod*bitDepth/8*nChannels];

						state = State.READY;
					}
					else
					{
						Log.e(ExtAudioRecorder.class.getName(), "prepare() method called on uninitialized recorder");
						state = State.ERROR;
					}
				}
				else
				{
					mediaRecorder.prepare();
					state = State.READY;
				}
			}
			else
			{
				Log.e(ExtAudioRecorder.class.getName(), "prepare() method called on illegal state");
				release();
				state = State.ERROR;
			}
		}
		catch(Exception e)
		{
			if (e.getMessage() != null)
			{
				Log.e(ExtAudioRecorder.class.getName(), e.getMessage());
			}
			else
			{
				Log.e(ExtAudioRecorder.class.getName(), "Unknown error occured in prepare()");
			}
			state = State.ERROR;
		}
	}
	
	/**
	 * 
	 * 
	 *  Releases the resources associated with this class, and removes the unnecessary files, when necessary
	 *  
	 */
	public void release() throws IOException
	{
		if (state == State.RECORDING)
		{
			stop();
		}
		else
		{
			if ((state == State.READY) & (rUncompressed))
			{
				randomAccessWriter.close(); // Remove prepared file
			}
		}
		
		if (rUncompressed)
		{
			if (audioRecorder != null)
			{
				audioRecorder.release();
			}
		}
		else
		{
			if (mediaRecorder != null)
			{
				mediaRecorder.release();
			}
		}
	}
	

	/**
	 * 
	 * 
	 * Starts the recording, and sets the state to RECORDING.
	 * Call after prepare().
	 * 
	 */
	public void start()
	{
		if (state == State.READY)
		{
			if (rUncompressed)
			{
				payloadSize = 0;
				audioRecorder.startRecording();
				audioRecorder.read(buffer, 0, buffer.length);
			}
			else
			{
				mediaRecorder.start();
			}
			state = State.RECORDING;
		}
		else
		{
			Log.e(ExtAudioRecorder.class.getName(), "start() called on illegal state");
			state = State.ERROR;
		}
	}
	
	/**
	 * 
	 * 
	 *  Stops the recording, and sets the state to STOPPED.
	 * In case of further usage, a reset is needed.
	 * Also finalizes the wave file in case of uncompressed recording.
	 * 
	 */
	public void stop() throws IOException
	{
		if (state == State.RECORDING)
		{
			if (rUncompressed)
			{
				audioRecorder.stop();
				
				try
				{
					randomAccessWriter.seek(4); // Write size to RIFF header
					randomAccessWriter.writeInt(Integer.reverseBytes(36+payloadSize));
				
					randomAccessWriter.seek(40); // Write size to Subchunk2Size field
					randomAccessWriter.writeInt(Integer.reverseBytes(payloadSize));
				
					randomAccessWriter.close();
				}
				catch(IOException e)
				{
					state = State.ERROR;
					throw new IOException("I/O exception occured while closing output file");
				}
			}
			else
			{
				mediaRecorder.stop();
			}
			state = State.STOPPED;
		}
		else
		{
			//Log.e(ExtAudioRecorder.class.getName(), "stop() called on illegal state " + state);
			//state = State.ERROR;
			state = State.STOPPED;
		}
	}
	
	/* 
	 * 
	 * Converts a byte[2] to a short, in LITTLE_ENDIAN format
	 * 
	 */
	private short getShort(byte argB1, byte argB2)
	{
		return (short)(argB1 | (argB2 << 8));
	}
	
}

