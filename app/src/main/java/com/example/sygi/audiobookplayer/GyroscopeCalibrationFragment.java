package com.example.sygi.audiobookplayer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;

import static android.content.ContentValues.TAG;

public class GyroscopeCalibrationFragment extends DialogFragment {

    private Button mSeekBar;

    public interface SensivityListener {
        void onSetSensivity(DialogFragment dialog, Double level);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout to use as dialog or embedded fragment
        View layout = inflater.inflate(R.layout.fragment_gyroscope_callibration, container, false);
        SeekBar mSeekBar= layout.findViewById(R.id.sensivity);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                listener.onSetSensivity(GyroscopeCalibrationFragment.this, 5. - progress / 200.);
            }
        });
        mSeekBar.setMax(1000);

        Bundle bundle = getArguments();
        Double startingSensitivity = bundle.getDouble("SENSITIVITY",2.0);
        Log.d(TAG, "starting sensitivity " + startingSensitivity.toString());

        Double progressLevel = (5. - startingSensitivity) * 200;
        mSeekBar.setProgress(progressLevel.intValue());
        return layout;
    }

    // Use this instance of the interface to deliver action events
    SensivityListener listener;

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            listener = (SensivityListener) context;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException("The activity doesn't implement GyroscopeCalibrationFragment.SensivityListenter interface.");
        }
    }
}
