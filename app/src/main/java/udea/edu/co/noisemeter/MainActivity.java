package udea.edu.co.noisemeter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;

public class MainActivity extends AppCompatActivity {

    private TextView txtLevel;
    private TextView txtAlertStatus;
    private TextView txtBreakCount;
    private ProgressBar progressBar;

    SharedPreferences preferences;
    private double minLevel;
    private double maxLevel;

    boolean isBound = false;
    Messenger mService = null;
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    // Context Menu Constants
    static int PREFERENCES_GROUP_ID = 0;
    static final int PREF_CALIBRATE = 5;
    static final int PREF_START_SERVICE = 6;
    static final int PREF_KILL_SERVICE = 7;
    static final int PREF_RESTORE_DEFAULT = 8;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = new Messenger(service);
            try {
                Message msg = Message.obtain(null, SoundService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            }catch (Exception e){
                Log.v("SOUND", e.getMessage());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SoundService.MSG_DB_UPDATE:
                    Double amp = msg.getData().getDouble("DB");
                    txtLevel.setText(getLevelOfNoise(amp));
                    txtBreakCount.setText(String.valueOf(msg.arg1));
                    updateProgressFromDB(amp);
                    break;
                case SoundService.MSG_UPDATE_ALERTING_STATE:
                    updateAlertingState(msg.arg1);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private void updateAlertingState(int state) {
        if(state == 1){
            txtAlertStatus.setText(R.string.alerting_status_on);
            txtAlertStatus.setTextColor(Color.GREEN);
        }else{
            txtAlertStatus.setText(R.string.alerting_status_off);
            txtAlertStatus.setTextColor(Color.GRAY);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txtLevel = (TextView) findViewById(R.id.txtLevel);
        txtAlertStatus = (TextView) findViewById(R.id.txtAlertStatus);
        txtBreakCount = (TextView) findViewById(R.id.txtBreakCount);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setProgress(0);
        progressBar.setMax(100);

        if(!SoundService.isRunning()){
            doStartService();
        }

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        minLevel = Double.valueOf(preferences.getString("minLevel", "-40"));
        maxLevel = Double.valueOf(preferences.getString("maxLevel", "-20"));

        findViewById(R.id.calibrationLayout).setVisibility(View.INVISIBLE);
    }

    public void updateProgressFromDB(Double amp){
        int progress = (int) Math.round(((minLevel - amp) / (minLevel - maxLevel)) * 100);
        if(progress > 100){
            progressBar.setProgress(100);
            progressBar.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        }else{
            progressBar.setProgress(progress);
            if(progress > 70){
                progressBar.getProgressDrawable().setColorFilter(Color.rgb(255, 140, 0), PorterDuff.Mode.SRC_IN);
            }else{
                progressBar.getProgressDrawable().setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    @Override
    protected void onResume() {
        doBindService();
        super.onResume();
    }

    @Override
    protected void onStop() {
        doUnbindService();
        super.onStop();
    }

    public void startAlerting(View view){
        doStartAlerting();
    }

    public void doStartAlerting(){
        try {
            Message msg = Message.obtain(null, SoundService.MSG_START_ALERTING);
            mService.send(msg);
        }catch (Exception e){
            Log.v("SOUND", e.getMessage());
        }
    }

    public void stopAlerting(View view){
        doStopAlerting();
    }

    private void doStopAlerting(){
        try {
            Message msg = Message.obtain(null, SoundService.MSG_STOP_ALERTING);
            mService.send(msg);
        }catch (Exception e){
            Log.v("SOUND", e.getMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(PREFERENCES_GROUP_ID, PREF_CALIBRATE, 0, "Calibrar").setIcon(android.R.drawable.ic_input_add);
        menu.add(PREFERENCES_GROUP_ID, PREF_START_SERVICE, 0, "Iniciar Servicio").setIcon(android.R.drawable.ic_input_add);
        menu.add(PREFERENCES_GROUP_ID, PREF_KILL_SERVICE, 0, "Detener Servicio").setIcon(android.R.drawable.ic_delete);
        menu.add(PREFERENCES_GROUP_ID, PREF_RESTORE_DEFAULT, 0, "Restaurar Configuracion").setIcon(android.R.drawable.ic_delete);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case PREF_CALIBRATE:
                doCalibrateOptions();
                break;
            case PREF_START_SERVICE:
                doStartService();
                break;
            case PREF_KILL_SERVICE:
                doStopService();
                break;
            case PREF_RESTORE_DEFAULT:
                preferences.edit().clear().apply();
                maxLevel = Double.valueOf(preferences.getString("maxLevel", "-20"));
                updateTolerance(0);
                break;
        }
        return true;
    }

    private void doCalibrateOptions() {
        doStopAlerting();
        findViewById(R.id.calibrationLayout).setVisibility(View.VISIBLE);
    }

    public void endCalibrateOptions(View view){
        doEndCalibrateOptions();
    }

    private void doEndCalibrateOptions(){
        findViewById(R.id.calibrationLayout).setVisibility(View.INVISIBLE);
        doStartAlerting();
    }

    public void lessTolerance(View view){
        updateTolerance(-1);
    }

    public void moreTolerance(View view){
        updateTolerance(+1);
    }

    public void updateTolerance(int change){
        maxLevel += change;
        preferences.edit().putString("maxLevel", String.valueOf(maxLevel)).apply();
        try {
            Message msg = Message.obtain(null, SoundService.MSG_UPDATE_THRESHOLD);
            mService.send(msg);
        }catch (Exception e){
            Log.v("SOUND", e.getMessage());
        }
    }

    public void postMessage(String msg){
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    public void doStartService() {
        if(!SoundService.isRunning()) {
            startService(new Intent(this, SoundService.class));
            doBindService();
        }
    }

    public void doStopService() {
        if(SoundService.isRunning()) {
            doUnbindService();
            stopService(new Intent(this, SoundService.class));
        }
    }

    public void doBindService(){
        if(!isBound){
            bindService(new Intent(this, SoundService.class), mConnection, Context.BIND_AUTO_CREATE);
            isBound = true;
        }
    }

    public void doUnbindService(){
        if(isBound){
            unbindService(mConnection);
            isBound = false;
        }
    }

    public String getLevelOfNoise(Double amp){
        return "Nivel de Ruido: " + String.valueOf(round((minLevel * -1) + amp, 2));
    }

    /**
     * Utility function for rounding decimal values
     */
    public double round(double d, int decimalPlace) {
        // see the Javadoc about why we use a String in the constructor
        // http://java.sun.com/j2se/1.5.0/docs/api/java/math/BigDecimal.html#BigDecimal(double)
        BigDecimal bd = new BigDecimal(Double.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.doubleValue();
    }

}
