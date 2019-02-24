package com.example.sygi.audiobookplayer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AudiobookPlayerPrefs2";
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 51531573;
    private MediaPlayer mMediaPlayer;
    private Button mStart, mStop, mRevind, mBackward, mForward;
    private static final String TAG = MainActivity.class.getSimpleName();
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private TriggerEventListener mTriggerEventListener;
    private boolean media_started = false;
    private boolean data_set = false;
    private Button mSelect;
    private SeekBar mSeek;
    private boolean mReverseTimeCounter = false;
    private Handler mHandler;
    private TextView mClock;
    private ListView mLastFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        setButtons();
        setupSensor();

        tryReadingUri();

        setTexts();
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

    private void setupSensor() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        mSensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if (sensorEvent.values[2] > 2.){
                    seek(-7);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {}
        }, mSensor, 1000 * 200);
    }

    private void setTexts() {
        mClock = findViewById(R.id.textClock);
        mClock.setText("00:00");

        mHandler = new Handler();
        MainActivity.this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if(mMediaPlayer != null){
                    int mCurrentPosition = mMediaPlayer.getCurrentPosition() / 1000;
                    mSeek.setProgress(mCurrentPosition);
                    mClock.setText(getTime(mCurrentPosition));
                }
                mHandler.postDelayed(this, 1000);
            }
        });

        mClock.setOnClickListener(new View.OnClickListener() {
                                      @Override
                                      public void onClick(View view) {
                                          mReverseTimeCounter ^= true;
                                      }
                                  }

        );

        updateList();
        if (mLastFiles != null) {
            mLastFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, final View view,
                                        int position, long id) {
                    SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                    String file_path = settings.getString(String.format("file_path%d", position), "");
                    if (!file_path.equals("")) {
                        Uri uri = Uri.parse(file_path);
                        if (!sameFile(uri)) {
                            setUri(uri);
                        }
                    }
                }
            });
        }
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
            mLastFiles = findViewById(R.id.last_files);
            mLastFiles.setAdapter(adapter);
        }
    }

    private ArrayList<String> previousFiles() {
        final ArrayList<String> list = new ArrayList<String>();
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        for (int i = 0; i < 5; ++i) {
            String file_path = settings.getString(String.format("file_path%d", i), "");
            Log.d("fp", file_path);
            if (!file_path.equals("")) {
                Uri uri = Uri.parse(file_path);
                String a = getFileName(uri);
                if ((a != null) && (!a.equals(""))) {
                    list.add(getFileName(uri));
                }
            }
        }

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
            Cursor cursor = getApplicationContext().getContentResolver().query(uri, proj, null, null, null);
            if (cursor != null && cursor.getCount() != 0) {
                int columnIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
                cursor.moveToFirst();
                String a = cursor.getString(columnIndex);
                return a;
            }
        }
        return "";
    }

    private void setButtons() {
        mStop = findViewById(R.id.button);
        mStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "stop clicked");
                if (media_started && data_set) {
                    mMediaPlayer.pause();
                    media_started = false;
                }
            }
        });
        mStart = findViewById(R.id.button2);
        mStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!data_set) {
                    Toast.makeText(getApplicationContext(), //Context
                            "Select file first",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }
                if (!media_started) {
                    mMediaPlayer.start();
                    media_started = true;
                }
            }
        });

        mRevind = findViewById(R.id.button3);
        mRevind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
             //   seek(-7);
            }
        });

        mRevind.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                seek(-1);
                return false;
            }
        });

        mBackward = findViewById(R.id.button_backward);
        mBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                seek(-60);
            }
        });

        mForward = findViewById(R.id.button_forward);
        mForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                seek(60);
            }
        });

        mSelect = findViewById(R.id.button4);
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
    }

    void seek(int difference){
        if (!data_set)
            return;
        int current = mMediaPlayer.getCurrentPosition();
        mMediaPlayer.seekTo(current + 1000 * difference);
    }

    void setUri(Uri myUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                return;
            }
        }
        try {
            mMediaPlayer.reset();
            media_started = false;
            mMediaPlayer.setDataSource(getApplicationContext(), myUri);
            Log.d(TAG, "data source set");
            mMediaPlayer.prepare();
            Log.d(TAG, "prepared");
            data_set = true;
            saveFileInfo(myUri);
            mSeek.setMax(mMediaPlayer.getDuration() / 1000);
            updateList();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean sameFile(Uri file){
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String file_path = settings.getString("file_path0", "");
        return file_path.equals(file.toString());
    }

    void saveFileInfo(Uri file){
        if (sameFile(file)) return;
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        for(int i = 5; i > 0; i--) {
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
            int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            }

            Log.d(TAG, uri.toString());
            setUri(uri);
        }
    }
}
