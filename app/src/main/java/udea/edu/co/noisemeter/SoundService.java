package udea.edu.co.noisemeter;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

public class SoundService extends IntentService {

    public static final int MSG_REGISTER_CLIENT = 1;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_DB_UPDATE = 3;
    public static final int MSG_KILL_SERVICE = 4;
    public static final int MSG_START_ALERTING = 5;
    public static final int MSG_STOP_ALERTING = 6;
    public static final int MSG_UPDATE_THRESHOLD = 7;
    public static final int MSG_UPDATE_ALERTING_STATE = 8;

    private static boolean isRunning = false;

    Messenger mMessenger = new Messenger(new IncomingHandler());

    private MediaRecorder mediaRecorder = null;
    private boolean listening = true;
    private boolean playing = false;
    private boolean alerting = false;
    private double maxLevel;
    private int maxBreaks = 3;
    private int breakCount = 0;
    private long lastBreak = -1;
    private long minTimeInMils = 3000;
    private int delay = 400;
    Messenger mClient;

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case MSG_REGISTER_CLIENT:
                    mClient = msg.replyTo;
                    changeAlertingState(alerting);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClient = null;
                    break;
                case MSG_KILL_SERVICE:
                    stop();
                    break;
                case MSG_START_ALERTING:
                    changeAlertingState(true);
                    break;
                case MSG_STOP_ALERTING:
                    changeAlertingState(false);
                    break;
                case MSG_UPDATE_THRESHOLD:
                    updatePreferences();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    public SoundService() {
        super("sound-service");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if(mediaRecorder == null){
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile("/dev/null");

            try {
                mediaRecorder.prepare();
            } catch (Exception e) {
                Log.e("error", e.getMessage());
            }

            mediaRecorder.start();
        }

        updatePreferences();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    private void updatePreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        maxLevel = Double.valueOf(preferences.getString("maxLevel", "-20"));
    }

    @Override
    public void onDestroy() {
        this.listening = false;
        if(mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
        }
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        isRunning = true;
        while (listening){
            SystemClock.sleep(delay);
            Double amp = 20 * Math.log10(getAmplitude() / 32768.0);

            if(!Double.isInfinite(amp)){
                if (amp > maxLevel && alerting) {
                    if (playing) {
                        playing = false;
                    } else {
                        long time = System.currentTimeMillis();
                        if (lastBreak != -1) {
                            if ((time - lastBreak) < minTimeInMils) {
                                breakCount++;
                                if (breakCount >= maxBreaks) {
                                    makeAlert();
                                    lastBreak = -1;
                                    breakCount = 0;
                                }
                            } else {
                                lastBreak = -1;
                                breakCount = 0;
                            }
                        } else {
                            lastBreak = time;
                        }
                    }
                }
                reportData(amp);
            }
        }
        isRunning = false;
    }

    private void reportData(Double amp) {
        if(mClient != null) {
            try {
                Message msg = Message.obtain(null, MSG_DB_UPDATE, breakCount, 0);
                Bundle b = new Bundle();
                b.putDouble("DB", amp);
                msg.setData(b);
                mClient.send(msg);
            } catch (Exception e) {
                mClient = null;
            }
        }
    }

    public void makeAlert(){
        playing = true;
        MediaPlayer mp = MediaPlayer.create(this, R.raw.alarm);
        mp.start();
    }

    private double getAmplitude() {
        if(mediaRecorder != null){
            return mediaRecorder.getMaxAmplitude();
        }else{
            return 0.0;
        }
    }

    public void changeAlertingState(boolean state){
        alerting = state;
        if(mClient != null) {
            try {
                Message msg = Message.obtain(null, MSG_UPDATE_ALERTING_STATE, (alerting ? 1 : 0), 0);
                mClient.send(msg);
            } catch (Exception e) {
                mClient = null;
            }
        }
    }

    public void stop(){
        onDestroy();
    }

    public static boolean isRunning(){
        return isRunning;
    }

}
