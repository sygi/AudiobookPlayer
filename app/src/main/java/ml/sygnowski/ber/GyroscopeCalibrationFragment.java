package ml.sygnowski.ber;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;

import static android.content.ContentValues.TAG;

public class GyroscopeCalibrationFragment extends DialogFragment {

    private SeekBar mSeekBar;
    private ImageView mBulb;
    private Handler mHandler;
    private Runnable mTurnOffLight;

    public interface SensivityListener {
        void onSetSensivity(DialogFragment dialog, Double level);
    }

    public void onSeek() {
        mBulb.setImageResource(R.drawable.light_311118_640);

        mHandler.removeCallbacks(mTurnOffLight);
        mHandler.postDelayed(mTurnOffLight, 1000);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout to use as dialog or embedded fragment
        getDialog().setTitle("Sensitivity");
        View layout = inflater.inflate(R.layout.fragment_gyroscope_callibration, container, false);
        mSeekBar = layout.findViewById(R.id.sensivity);
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

        mBulb = layout.findViewById(R.id.imageView);
        mHandler = new android.os.Handler();
        mTurnOffLight = new Runnable() {
            public void run() {
                Log.i("Tag", "Turning lightbulb back off");
                mBulb.setImageResource(R.drawable.lamp_311117_640);
            }
        };

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
