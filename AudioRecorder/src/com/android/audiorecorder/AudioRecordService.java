package com.android.audiorecorder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;

import java.util.LinkedList;

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

//Record voice call audio from MIC using AudioRecord.

public class AudioRecordService extends Service {

	private final String LOG_TAG = "voicerecorder";
	private static final int SAMPLE_RATE = 44100;
	private static final double FREQUENCY = 500;
	private static final double RESOLUTION = 10;

	private static String mServerAddress = "192.168.31.119";

	private int mRecBufferSize = 0;
	private AudioRecord mRecorder;
	private AudioTrack mTrack;
	private byte[] mRecBuf;
	private boolean mIsRecord = false;

	protected DataOutputStream mDataOutStream;

	//use mInBuffer to store 2 sizes of audio data, then send to by socket. It is useful for audio quality.
	protected LinkedList<byte[]>  mInBuffer;

	//use socket to send audio data to another phone device
	private Socket mSocket;
	private int mPort = 8888;

	//AudioName: raw pcm data file.
	private static final String AudioName = "/sdcard/voice.raw";
	//NewAudioName: wav file that can play.
	private static final String NewAudioName = "/sdcard/newvoice.wav";

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

	public void initSocket() {
		try {
			Log.e(LOG_TAG, "connect to socket server: " + mServerAddress);
			mSocket = new Socket(InetAddress.getByName(mServerAddress), mPort);
			Log.e(LOG_TAG, "mSocket is " + mSocket.toString());
			mSocket.setSoTimeout(5000);
			mDataOutStream = new DataOutputStream(mSocket.getOutputStream());
		} catch(IOException e) {
			Log.d(LOG_TAG,"connect socket error ",e);
			e.printStackTrace();
		}
	}

	private class CallStateListener extends PhoneStateListener {
		private String telnumber;

		@Override
			public void onCallStateChanged(int state, String incomingNumber) {
				Log.e(LOG_TAG, "onCallStateChanged. state = " + state + ", incomingnum = " + incomingNumber);
				switch (state) {
					case TelephonyManager.CALL_STATE_IDLE:   //idle state. no calls
						telnumber = null;
						if (mRecorder != null && mIsRecord) {
							Log.e(LOG_TAG, "record ok for idle state. release recorder.");
							mRecorder.stop();
							mRecorder.release();
							mIsRecord = false;
							mRecorder = null;
						}
						break;
					case TelephonyManager.CALL_STATE_OFFHOOK:  //offhook state. call is dailing or active.
						telnumber = incomingNumber;
						Log.e(LOG_TAG, "start recording under offhook state");
						startAudioRecord();
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

	private void startAudioRecord() {
		mRecBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT);
		mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				mRecBufferSize);

		Log.e(LOG_TAG, "start audio recording thread. record buffersize = " + mRecBufferSize);
		//start recording thread.
		mRecorder.startRecording();
		mIsRecord = true;
		Thread thread = new Thread(new AudioRecordThread());
		thread.start();
	}

	private class AudioRecordThread implements Runnable{
		@Override
			public void run() {
				//send audio data to server phone by socket
				initSocket();
				sendData();

				//save audio data into a wav file on local phone for test
				//writeDateTOFile(); //write pcm raw data to file.
				//copyWaveFile(AudioName, NewAudioName); //add wav header for raw data file.
			}
	}

	private void sendData() {
		byte[] backupdata;
		byte[] audiodata = new byte[mRecBufferSize];
		int readsize = 0;

		Log.e(LOG_TAG, "start to record/send audio data. mIsRecord = " + mIsRecord);
		try {
			while (mIsRecord == true) {
				readsize = mRecorder.read(audiodata, 0, mRecBufferSize);
				backupdata = audiodata.clone();
				if (AudioRecord.ERROR_INVALID_OPERATION != readsize) {
					try {
						Log.e(LOG_TAG, "write data to socket server. readsize = " + readsize + ", backupdata.length = " + backupdata.length);
						mDataOutStream.write(backupdata, 0, backupdata.length);
						mDataOutStream.flush();
					} catch (IOException e){}
				}
				}
				mDataOutStream.close();
				mSocket.close();
				audiodata = null;
			} catch (Exception e) {
				e.printStackTrace();
			}
			Log.e(LOG_TAG, "record finished. mIsRecord = " + mIsRecord);
		}

		/**
		 * save audio raw pcm data in file. It can not be played.
		 * Should add wav header for it if wants to play.
		 */
		private void writeDateTOFile() {
			// create byte[] to store audio data.
			byte[] audiodata = new byte[mRecBufferSize];
			FileOutputStream fos = null;
			int readsize = 0;
			try {
				File file = new File(AudioName);
				if (file.exists()) {
					file.delete();
				}
				fos = new FileOutputStream(file); //create file to store byte data.
			} catch (Exception e) {
				e.printStackTrace();
			}
			Log.e(LOG_TAG, "get recording data.... mIsRecord = " + mIsRecord);
			while (mIsRecord == true) {
				readsize = mRecorder.read(audiodata, 0, mRecBufferSize);
				if (AudioRecord.ERROR_INVALID_OPERATION != readsize) {
					try {
						fos.write(audiodata);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			Log.e(LOG_TAG, "record data finished, mIsRecord = " + mIsRecord);
			try {
				fos.close(); //close file stream
			} catch (IOException e) {
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
			byte[] data = new byte[mRecBufferSize];
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
