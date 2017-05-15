package com.android.audioplayback;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaRecorder;
import android.media.AudioTrack;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioManager;
import android.os.Environment;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

//playback recording audio from client phone

public class VoicePlayback extends Service {

	private final String LOG_TAG = "voiceplayback";
	private static final int SAMPLE_RATE = 44100;
	private static final double FREQUENCY = 500;
	private static final double RESOLUTION = 10;

	private static String mServerAddress = "192.168.31.119";

	private int mPlayBufferSize = 0;
	private AudioTrack mTrack;
	byte[] mRecBuf;
	boolean mIsRecord = false;

	//use socket to send audio data to another phone device
	private ServerSocket mSrvSocket;
	private int mPort = 8888;
	//private InputStream mInputStream;
	private DataInputStream mDataInputStream;

	//AudioName: raw pcm data file.
	private static final String AudioName = "/sdcard/voice_srv.raw";
	//NewAudioName: wav file that can play.
	private static final String NewAudioName = "/sdcard/newvoice_srv.wav";

	@Override
	public void onCreate() {
		super.onCreate();
		Log.e(LOG_TAG, "onCreate()..");
		Log.e(LOG_TAG, "start server socket");
		Thread thread = new Thread(new AudioPlayThread());
		thread.start();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.e(LOG_TAG, "onDestroy()..");
		/*Log.e(LOG_TAG, "close server socket");
		try {
			if (mSrvSocket != null)
				mSrvSocket.close();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}*/
	}

	private void startAudioTrack() {
		mPlayBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
				AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT); //*2;
		mTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
				SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
				mPlayBufferSize, AudioTrack.MODE_STREAM);

		Log.e(LOG_TAG, "start to playback. play buffersize = " + mPlayBufferSize);

		mTrack.play();
		mIsRecord = true;
	}

	private class AudioPlayThread implements Runnable{
		@Override
		public void run() {
			receiveData();
		}
	}

	private void receiveData() {
		//FileOutputStream fos = null;  //for test
		try {
			Log.e(LOG_TAG, "init socket server.");
			mSrvSocket = new ServerSocket(mPort);   //listen for all ip addresses
			//mSrvSocket = new ServerSocket(mPort, 0, InetAddress.getByName(mServerAddress));
			//mSrvSocket.bind(new InetSocketAddress(mServerAddress, mPort));
			Log.e(LOG_TAG, "mSrvSocket = " + mSrvSocket.toString());
			while (mSrvSocket != null && !mSrvSocket.isClosed()) {
				Socket client = null;
				try {
					byte [] backupdata = null ;
					client = mSrvSocket.accept();
					Log.e(LOG_TAG, "accept client socket. " + client);
					startAudioTrack();
					/*
					//test start: save audio data into file
					File file = new File(AudioName);
					if (file.exists()) {
					file.delete();
					}
					fos = new FileOutputStream(file);
					//test end
					 */
					mDataInputStream = new DataInputStream(client.getInputStream());
					int len = 0;
					byte[] audiodata = new byte[mPlayBufferSize];
					while ((len = mDataInputStream.read(audiodata)) != -1) {						
						// fos.write(audiodata); // for test
						backupdata = audiodata.clone() ;
						mTrack.write(backupdata, 0, backupdata.length);
						Log.e(LOG_TAG, "receive audio data. len = " + len + ", backupdata.length = " + backupdata.length);
					}
				} catch (Exception e) {
					System.out.println(e.getMessage());
					e.printStackTrace();
				} finally {
					if (client != null) {
						client.close();
						//fos.close();   //for test
						mDataInputStream.close();
						mTrack.stop();
						mTrack.release();
						mTrack = null;
						Log.e(LOG_TAG, "play finished. close client socket");
					}
					//copyWaveFile(AudioName, NewAudioName);   //for test
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// get wav file that can be played.
	private void copyWaveFile(String inFilename, String outFilename) {
		FileInputStream in = null;
		FileOutputStream out = null;
		long totalAudioLen = 0;
		long totalDataLen = totalAudioLen + 36;
		long longSampleRate = SAMPLE_RATE;
		int channels = 1;
		long byteRate = 16 * SAMPLE_RATE * channels / 8;
		byte[] data = new byte[mPlayBufferSize];
		try {
			in = new FileInputStream(inFilename);
			out = new FileOutputStream(outFilename);
			totalAudioLen = in.getChannel().size();
			totalDataLen = totalAudioLen + 36;
			WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
					longSampleRate, channels, byteRate);
			while (in.read(data) != -1) {
				out.write(data);
			}
			in.close();
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * add wav header for pcm raw data, then it can be played normally.
	 */
	private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
			long totalDataLen, long longSampleRate, int channels, long byteRate)
		throws IOException {
			byte[] header = new byte[44];
			header[0] = 'R'; // RIFF/WAVE header
			header[1] = 'I';
			header[2] = 'F';
			header[3] = 'F';
			header[4] = (byte) (totalDataLen & 0xff);
			header[5] = (byte) ((totalDataLen >> 8) & 0xff);
			header[6] = (byte) ((totalDataLen >> 16) & 0xff);
			header[7] = (byte) ((totalDataLen >> 24) & 0xff);
			header[8] = 'W';
			header[9] = 'A';
			header[10] = 'V';
			header[11] = 'E';
			header[12] = 'f'; // 'fmt ' chunk
			header[13] = 'm';
			header[14] = 't';
			header[15] = ' ';
			header[16] = 16; // 4 bytes: size of 'fmt ' chunk
			header[17] = 0;
			header[18] = 0;
			header[19] = 0;
			header[20] = 1; // format = 1
			header[21] = 0;
			header[22] = (byte) channels;
			header[23] = 0;
			header[24] = (byte) (longSampleRate & 0xff);
			header[25] = (byte) ((longSampleRate >> 8) & 0xff);
			header[26] = (byte) ((longSampleRate >> 16) & 0xff);
			header[27] = (byte) ((longSampleRate >> 24) & 0xff);
			header[28] = (byte) (byteRate & 0xff);
			header[29] = (byte) ((byteRate >> 8) & 0xff);
			header[30] = (byte) ((byteRate >> 16) & 0xff);
			header[31] = (byte) ((byteRate >> 24) & 0xff);
			header[32] = (byte) (2 * 16 / 8); // block align
			header[33] = 0;
			header[34] = 16; // bits per sample
			header[35] = 0;
			header[36] = 'd';
			header[37] = 'a';
			header[38] = 't';
			header[39] = 'a';
			header[40] = (byte) (totalAudioLen & 0xff);
			header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
			header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
			header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
			out.write(header, 0, 44);
		}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		Log.e(LOG_TAG, "onBind()..");
		return null;
	}

}
