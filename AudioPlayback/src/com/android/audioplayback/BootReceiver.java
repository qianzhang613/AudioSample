package com.android.audioplayback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

public class BootReceiver extends BroadcastReceiver {

	final String action = Intent.ACTION_BOOT_COMPLETED;
	final String action_wifi = WifiManager.NETWORK_STATE_CHANGED_ACTION;
	final String LOG_TAG = "voiceplayback";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent != null) {
            Log.e(LOG_TAG, "in BootReceiver. receive intent. action = " + intent.getAction());
			Intent service = new Intent(context, VoicePlayback.class);
			if (intent.getAction().equals(action_wifi)) {
				NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
				if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
					Log.e(LOG_TAG, "in BootReceiver. wifi is connected. start VoicePlayback Service.");
					context.startService(service);
				} else if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
					Log.e(LOG_TAG, "in BootReceiver. wifi is disconnected. stop VoicePlayback service.");
					context.stopService(service);
				}
			}
		}
	}

}
