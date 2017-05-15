package com.android.audiorecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class CallStateReceiver extends BroadcastReceiver {

	final String action = "android.intent.action.PHONE_STATE";
	final String LOG_TAG = "voicerecorder";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent != null) {
            Log.e(LOG_TAG, "in CallStateReceiver. receive intent. action = " + intent.getAction());
			if (intent.getAction().equals(action)) {
				Log.e(LOG_TAG, "in CallStateReceiver. start VoiceRecord Service.");
				Intent service = new Intent(context, VoiceRecordService.class);//AudioRecordService.class);
				context.startService(service);
			}
		}
	}

}
