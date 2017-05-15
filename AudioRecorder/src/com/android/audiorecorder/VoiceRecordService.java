package com.android.audiorecorder;

import java.io.File;  
import java.io.IOException;  
import java.text.SimpleDateFormat;  
import java.util.Date;  
  
import android.app.Service;  
import android.content.ContentResolver;  
import android.content.Context;  
import android.content.Intent;  
import android.database.Cursor;  
import android.media.MediaRecorder;  
import android.os.Environment;  
import android.os.IBinder;  
import android.provider.ContactsContract;  
import android.telephony.PhoneStateListener;  
import android.telephony.TelephonyManager;  
import android.util.Log;


//Record voice audio from MIC  using MediaRecord.

public class VoiceRecordService extends Service {
	
	private final String LOG_TAG = "voicerecorder";

	@Override
	public void onCreate() {
		super.onCreate();
		Log.e(LOG_TAG, "onCreate()..");
		TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
		tm.listen(new CallStateListener(), PhoneStateListener.LISTEN_CALL_STATE);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.e(LOG_TAG, "onDestroy()..");
	}

	private class CallStateListener extends PhoneStateListener {
		private String telnumber;
		private MediaRecorder recorder;
		private boolean isRecord = false;
		private File file;

		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			Log.e(LOG_TAG, "onCallStateChanged. state = " + state + ", incomingnum = " + incomingNumber);
			switch (state) {
				case TelephonyManager.CALL_STATE_IDLE:   //idle state. no calls
					telnumber = null;
					if (recorder != null && isRecord) {
						Log.e(LOG_TAG, "record ok for idle state. release recorder.");
						recorder.stop();
						recorder.reset();
						recorder.release();
						isRecord = false;
					}
				    break;
				case TelephonyManager.CALL_STATE_OFFHOOK:  //offhook state. call is dailing or active. 
					telnumber = incomingNumber;
					Log.e(LOG_TAG, "recording under offhook state");
					file = new File(Environment.getExternalStorageDirectory(), 
							telnumber + "_" + System.currentTimeMillis() + ".amr");
					recorder = new MediaRecorder();
					Log.e(LOG_TAG, "set audiosource to VOICE_CALL(not MIC).");    //voice_uplink means voice for near end. voice_downlink means voice for far end.
					recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL);//MIC);
					recorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);//THREE_GPP);
					recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
				    recorder.setOutputFile(file.getAbsolutePath());
					recorder.setAudioSamplingRate(8000);
					try {
						recorder.prepare();
					    recorder.start();
					    isRecord = true;
					} catch (IllegalStateException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
					break;
				case TelephonyManager.CALL_STATE_RINGING:  //ringing state for incoming call
					telnumber = incomingNumber;
					Log.e(LOG_TAG, "ringing state. get telnumber: " + telnumber);
					break;
				default:
					break;
			}

		}

	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		Log.e(LOG_TAG, "onBind()..");
		return null;  
	}  

}
