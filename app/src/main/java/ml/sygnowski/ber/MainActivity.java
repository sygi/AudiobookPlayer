package ml.sygnowski.ber;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.support.constraint.ConstraintLayout;
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.AppCompatButton;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.ViewOverlay;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements GyroscopeCalibrationFragment.SensivityListener {

    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    private static final String PREFS_NAME = "BookEnRoutePrefs";
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 51531573;
    private MediaPlayer mMediaPlayer = null;
    private AppCompatButton mStartStop, mRevind, mFastForward, mCalibration, mSelect;
    private static final String TAG = MainActivity.class.getSimpleName();
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private boolean media_started = false;
    private boolean data_set = false;
    private SeekBar mSeek;
    private boolean mReverseTimeCounter = false;
    private Handler mHandler;
    private TextView mClock;
    private ListView mLastFiles;
    private ImageView mOverlay;
    private SensorEventListener mSensorListener;
    private ActionBar mActionBar;
    private Double mGyroscopeSensitivity = 2.0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler();

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                int length = mMediaPlayer.getDuration() / 1000;
                mSeek.setProgress(length);
                mClock.setText(getTime(length));
                stopPlayback();
            }
        });

        setButtons();

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        Double sensitivity = Double.valueOf(settings.getFloat("sensitivity", 2.f));
        setupSensor(sensitivity);

        tryReadingUri();

        setTexts();


        Boolean overlayDismissed = settings.getBoolean("overlay_dismissed", false);
        if (overlayDismissed){
            mOverlay.setVisibility(View.GONE);

        } else {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Animatable a = (Animatable) mOverlay.getDrawable();
                    a.start();
                }
            }, 1000);
        }
    }

    private void tryReadingUri() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String file_path = settings.getString("file_path0", "");
        if (file_path != "") {
            Log.d("Opening", file_path);
            Uri uri = Uri.parse(file_path);
            setUri(uri);
        }
    }

    private void setupSensor(Double sensitivityLevel) {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (mSensorListener != null) {
            Log.d(TAG, "unregistering listener");
            mSensorManager.unregisterListener(mSensorListener);
        }

        mSensorListener = getSensorListener(sensitivityLevel);
        mSensorManager.registerListener(mSensorListener, mSensor, 1000 * 200);

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putFloat("sensitivity", sensitivityLevel.floatValue());
        editor.commit();
    }

    private SensorEventListener getSensorListener(final Double sensivityLevel) {
        mGyroscopeSensitivity = sensivityLevel;
        return new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if (sensorEvent.values[2] > sensivityLevel){
                    if (!sendSeekToFragment()) {
                        // seek only when there's no fragment for choosing sensitivity.
                        seek(-7);
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {}
        };
    }

    private boolean sendSeekToFragment(){
        GyroscopeCalibrationFragment fragment = (GyroscopeCalibrationFragment) getSupportFragmentManager(
        ).findFragmentByTag("Gyroscope Calibration");
        if (fragment != null) {
            Log.d(TAG, "Found the fragment");
            fragment.onSeek();
            return true;
        } else {
            Log.d(TAG, "No fragment with that tag.");
            return false;
        }
    }

    private void setTexts() {
        mClock = findViewById(R.id.textClock);
        mClock.setText("0:00:00");

        MainActivity.this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (data_set){
                    int mCurrentPosition = mMediaPlayer.getCurrentPosition() / 1000;
                    // what is the max? run the following onCompletion
                    mSeek.setProgress(mCurrentPosition);
                    mClock.setText(getTime(mCurrentPosition));
                }
                mHandler.postDelayed(this, 1000);
            }
        });

        mClock.setOnClickListener( new View.OnClickListener() {
                                      @Override
                                      public void onClick(View view) {
                                          mReverseTimeCounter ^= true;
                                      }
                                  }

        );

        updateList();
        mLastFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, final View view,
                                    int position, long id) {
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                String file_path = settings.getString(String.format("file_path%d", position), "");
                if (!file_path.equals("")) {
                    Uri uri = Uri.parse(file_path);
                    setUri(uri);
                }
            }
        });

        mActionBar = getSupportActionBar();
        mActionBar.setIcon(R.mipmap.icon_book);  // showing the icon to your action bar
        mActionBar.setTitle(" Book En Route");
        mActionBar.setDisplayShowHomeEnabled(true);
    }

    private String getTime(int current){
        if (mReverseTimeCounter){
            current = mMediaPlayer.getDuration() / 1000 - current;
        }
        int minutes = current / 60;
        int hours = minutes / 60;
        minutes %= 60;
        int seconds = current % 60;
        String current_time = String.format("%d:%02d:%02d", hours, minutes, seconds);
        if (mReverseTimeCounter)
            return "-" + current_time;
        else
            return " " + current_time;
    }

    private void updateList() {
        ArrayList<String> values = previousFiles();
        if (values.size() > 0){
            ArrayAdapter adapter = new ArrayAdapter(getApplicationContext(), R.layout.row_text_view, values);
            mLastFiles.setAdapter(adapter);
        }
    }

    private ArrayList<String> previousFiles() {
        final ArrayList<String> list = new ArrayList<String>();
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        for (int i = 0; i <= 6; ++i) {
            String file_id = String.format("file_path%d", i);
            String file_path = settings.getString(file_id, "");
            Log.d("fp", file_path);
            if (!file_path.equals("")) {
                Uri uri = Uri.parse(file_path);
                // TODO: fix this hack
                int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                if (checkCallingOrSelfUriPermission(uri, takeFlags) == PackageManager.PERMISSION_DENIED){
                    Log.d(TAG, "I don't have permissions for a file, removing it from the list");
                    editor.putString(file_id, "");
                } else {
                    String a = getFileName(uri);  // This throws an error when I don't have the URI permission (even if I have the storage permission)
                    if ((a != null) && (!a.equals(""))) {
                        list.add(getFileName(uri));
                    }
                }
            }
        }

        editor.commit();
        Log.d("previous list", list.toString());
        return list;
    }

    private String getFileName(Uri uri){
        Log.d("looking for uri", uri.toString());
        String scheme = uri.getScheme();
        if (scheme.equals("file")) {
            return uri.getLastPathSegment();
        }
        else if (scheme.equals("content")) {
            String[] proj = { OpenableColumns.DISPLAY_NAME };
            ContentResolver cr = getApplicationContext().getContentResolver();
            Cursor cursor = cr.query(uri, proj, null, null, null);
            if (cursor != null && cursor.getCount() != 0) {
                int columnIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
                cursor.moveToFirst();
                String a = cursor.getString(columnIndex);
                cursor.close();
                return a;
            }
        }
        return "";
    }

    private void setButtons() {
        mStartStop = findViewById(R.id.start_stop);
        mStartStop.setBackgroundResource(R.drawable.ic_play_circle_outline_black_24dp);
        mStartStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!media_started) {
                    startPlayback();
                } else {
                    stopPlayback();
                }
            }
        });

        mRevind = findViewById(R.id.rewind);
        mRevind.setBackgroundResource(R.drawable.ic_fast_rewind_black_24dp);
        mRevind.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                seek(-1);
                return false;
            }
        });

        mFastForward = findViewById(R.id.fast_forward);
        mFastForward.setBackgroundResource(R.drawable.ic_fast_forward_black_24dp);
        mFastForward.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                seek(1);
                return false;
            }
        });

        mCalibration = findViewById(R.id.menu);
        mCalibration.setBackgroundResource(R.drawable.ic_menu_black_24dp);
        mCalibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCalibrationDialog();
            }
        });

        mSelect = findViewById(R.id.select);
        mSelect.setBackgroundResource(R.drawable.ic_open_in_browser_black_24dp);
        mSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent mediaIntent = new Intent();
                if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    mediaIntent.setAction(Intent.ACTION_GET_CONTENT);
                } else {
                    mediaIntent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                    mediaIntent.addCategory(Intent.CATEGORY_OPENABLE);
                }
                mediaIntent.setType("audio/*"); //set mime type as per requirement
                startActivityForResult(mediaIntent, 1);
            }
        });

        mSeek = findViewById(R.id.seekBar);
        mSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(mMediaPlayer != null && data_set && fromUser){
                    mMediaPlayer.seekTo(progress * 1000);
                }
            }
        });

        mOverlay = findViewById(R.id.overlay);
        mOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mOverlay.setVisibility(View.GONE);
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean("overlay_dismissed", true);
                editor.commit();
            }
        });

        mLastFiles = findViewById(R.id.last_files);
    }

    void seek(int difference){
        if (!data_set)
            return;
        int current = mMediaPlayer.getCurrentPosition();
        mMediaPlayer.seekTo(current + 1000 * difference);
    }

    void startPlayback() {
        if (!data_set) {
            Toast.makeText(getApplicationContext(), //Context
                    "Select file first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }
        mMediaPlayer.start();
        media_started = true;
        mStartStop.setBackgroundResource(R.drawable.ic_pause_circle_outline_black_48dp);
        mStartStop.setContentDescription("Pause");
    }

    void stopPlayback() {
        if (!data_set)
            return;
        mMediaPlayer.pause();
        media_started = false;
        mStartStop.setBackgroundResource(R.drawable.ic_play_circle_outline_black_24dp);
        mStartStop.setContentDescription("Start");
    }

    void openCalibrationDialog() {
        DialogFragment newFragment = new GyroscopeCalibrationFragment();
        newFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogWithTitle);

        Bundle bundle = new Bundle();
        bundle.putDouble("SENSITIVITY", mGyroscopeSensitivity);
        newFragment.setArguments(bundle);
        newFragment.show(getSupportFragmentManager(), "Gyroscope Calibration");
    }

    void setUri(Uri myUri) {
        saveFileInfo(myUri);
        updateList();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                return;
            }
        }
        try {
            stopPlayback();
            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(getApplicationContext(), myUri);
            Log.d(TAG, "data source set");
            mMediaPlayer.prepare();
            Log.d(TAG, "prepared");
            data_set = true;
            mSeek.setMax(mMediaPlayer.getDuration() / 1000);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int fileIndexOnTheList(Uri file) {
        for(int i = 0; i <= 6; i++) {
            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            String file_path = settings.getString(String.format("file_path%d", i), "");
            if (file_path.equals(file.toString()))
                return i;
        }
        return -1;
    }

    void saveFileInfo(Uri file){
        int index = fileIndexOnTheList(file);
        if (index == 0)
            return;

        if (index < 0)
            index = 6;

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();

        for(int i = index; i > 0; i--) {
            String next_query = String.format("file_path%d", i);
            String previous_query = String.format("file_path%d", i - 1);

            String previous_path = settings.getString(previous_query, "");
            if (!previous_path.equals("")) {
                editor.putString(next_query, previous_path);
            }
        }
        editor.putString("file_path0", file.toString());
        editor.commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            }

            Log.d(TAG, uri.toString());
            setUri(uri);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        Log.d(TAG, "on request permissions result");
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                    String file_path = settings.getString("file_path0", "");
                    if (file_path != "") {
                        Log.d("Opening", file_path);
                        Uri uri = Uri.parse(file_path);
                        setUri(uri);
                    }
                } else {
                    // TODO: handle permission denial.
                }
                return;
            }
        }
    }

    @Override
    public void onSetSensivity(DialogFragment dialog, Double level) {
        Log.d(TAG, "setting sensivity level " + level.toString());
        setupSensor(level);
    }

    protected void onPause() {
        super.onPause();
    }

    protected void onStop() {
        super.onStop();
    }

    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        stopPlayback();
        super.onBackPressed();
    }
}
