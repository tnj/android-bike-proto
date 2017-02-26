package sh.nothing.droidbike;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Context;
import android.databinding.DataBindingUtil;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;
import sh.nothing.droidbike.ble.CscManager;
import sh.nothing.droidbike.databinding.ActivityMainBinding;
import sh.nothing.droidbike.view.HorizontalBarGraphView;

@RuntimePermissions
public class MainActivity extends AppCompatActivity implements SensorEventListener, CscManager.CscManagerCallback {

    private static final String TAG = "MainActivity";
    private SensorManager sensorManager;
    private Sensor pressure;
    private Sensor temperature;
    private ActivityMainBinding binding;

    float basePressure = 0.0f;
    float lastPressure = 0.0f;
    float lastRawPressure = 0.0f;
    float lastTemperature = 25.0f;

    int startWheelRevolutions = -1;
    int startCrankRevolutions = -1;

    DurationCounter wheelDurationCounter = new DurationCounter();
    DurationCounter crankDurationCounter = new DurationCounter();

    // CSC data
    private CscManager cscManager;

    private ValueAnimator speedAnimator;
    private ValueAnimator cadenceAnimator;
    private Interpolator normalInterpolator = new LinearInterpolator();
    private Interpolator fastInterpolator = new DecelerateInterpolator();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        binding.fab.setOnClickListener(view -> {
            basePressure = lastRawPressure;
            Snackbar
                .make(view, "Calibrated: " + lastRawPressure, Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .show();
        });

        // Get an instance of the sensor service, and use that to get an instance of
        // a particular sensor.
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        findTemperatureSensor();
        hideSystemControls();

        cscManager = new CscManager(this);
        cscManager.registerCallback(this);

        binding.content.speedGraph.setMax(60.0f);
        binding.content.speedGraph.setMin(0.0f);
        binding.content.speedGraph.setColorResource(R.color.colorAccent);

        binding.content.cadenceGraph.setMax(150.0f);
        binding.content.cadenceGraph.setMin(0.0f);
        binding.content.cadenceGraph.setColorResource(R.color.colorPrimaryDark);

        binding.content.cadenceRpmGraph.setColorResource(R.color.colorPrimaryDark);
        binding.content.speedRpmGraph.setColorResource(R.color.colorAccent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        sensorManager.registerListener(this, pressure, SensorManager.SENSOR_DELAY_UI);
        if (temperature != null)
            sensorManager.registerListener(this, temperature, SensorManager.SENSOR_DELAY_NORMAL);

        MainActivityPermissionsDispatcher.startBleScanWithCheck(this);

        speedAnimator = initAnimator(binding.content.speed, binding.content.speedGraph);
        cadenceAnimator = initAnimator(binding.content.cadence, binding.content.cadenceGraph);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @Override
    protected void onStop() {
        super.onStop();
        sensorManager.unregisterListener(this, pressure);
        if (temperature != null)
            sensorManager.unregisterListener(this, temperature);

        stopBleScan();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == pressure) {
            lastRawPressure = event.values[0];
            if (lastPressure == 0.0f) lastPressure = lastRawPressure;
            if (basePressure == 0.0f) basePressure = lastRawPressure;
            lastPressure += (lastRawPressure - lastPressure) * 0.2;
        } else if (event.sensor == temperature) {
            lastTemperature = event.values[0];
        }
        updateView();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onUpdate(int wheelRevolutions, float wheelRpm, int crankRevolutions, float crankRpm) {
        runOnUiThread(() -> {
            if (startWheelRevolutions == -1) {
                startWheelRevolutions = wheelRevolutions;
            }
            if (startCrankRevolutions == -1) {
                startCrankRevolutions = crankRevolutions;
            }

            // distance
            double distance = (wheelRevolutions - startWheelRevolutions) * getDiameter() / 1_000_000.0;
            setFloatText(
                formatValue((float) distance),
                binding.content.distance,
                binding.content.distanceSub
            );

            // duration
            long duration = wheelDurationCounter.updateDuration(wheelRpm != 0.0);
            long durationInMilliseconds = duration / 1000000;
            binding.content.duration.setText(formatDuration(durationInMilliseconds));

            if (durationInMilliseconds > 5000) {
                // average speed
                binding.content.speedGraph.setAverage((float) (distance / durationInMilliseconds * 3600000));
            }

            long crankDuration = crankDurationCounter.updateDuration(crankRpm != 0.0);
            long crankDurationInMilliseconds = crankDuration / 1000000;
            if (crankDurationInMilliseconds > 5000) {
                // average cadence
                binding.content.cadenceGraph.setAverage((float) ((double) (crankRevolutions - startCrankRevolutions) / crankDurationInMilliseconds * 60000));
            }

            // speed
            setAnimatorValue(speedAnimator, calculateSpeed(wheelRpm));

            // cadence
            setAnimatorValue(cadenceAnimator, crankRpm);

            // wheel rpm
            binding.content.speedRpmGraph.setRpm(wheelRpm);

            // cadence rpm
            binding.content.cadenceRpmGraph.setRpm(crankRpm);
        });
    }

    private String formatDuration(long durationInMilliseconds) {
        int h = (int) (durationInMilliseconds / 3600000);
        int m = (int) (durationInMilliseconds / 60000) % 60;
        int s = (int) (durationInMilliseconds / 1000) % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    static class DurationCounter {
        private long currentDurationStartTime = 0;
        private long lastDuration = 1000;

        private long updateDuration(boolean isRunning) {
            if (isRunning) {
                if (currentDurationStartTime == 0) {
                    currentDurationStartTime = System.nanoTime();
                }
                return System.nanoTime() - currentDurationStartTime + lastDuration;
            } else {
                if (currentDurationStartTime != 0) {
                    lastDuration += System.nanoTime() - currentDurationStartTime;
                    currentDurationStartTime = 0;
                }
                return lastDuration;
            }
        }
    }


    private ValueAnimator initAnimator(TextView integerView, HorizontalBarGraphView graphView) {
        ValueAnimator animator = ValueAnimator.ofFloat(0.0f, 0.0f);
        animator.setDuration(1000);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener((animation) -> {
            integerView.setText(Integer.toString((int) (0 + (Float) animation.getAnimatedValue())));
            graphView.setCurrent((Float) animation.getAnimatedValue());
        });
        animator.start();
        return animator;
    }

    private void setAnimatorValue(ValueAnimator animator, float newValue) {
        float currentValue = (Float) animator.getAnimatedValue();
        if (Math.abs(currentValue - newValue) >= 0.01f) {
            animator.setInterpolator(normalInterpolator);
            if (currentValue > newValue) {
                if (currentValue / newValue > 1.2f) {
                    //currentValue = newValue * 1.2f;
                    animator.setInterpolator(fastInterpolator);
                }
            } else {
                if (newValue / currentValue > 1.2f) {
                    //currentValue = newValue / 1.2f;
                    animator.setInterpolator(fastInterpolator);
                }
            }
            animator.setFloatValues(currentValue, newValue);
            animator.start();
        }
    }

    private float calculateSpeed(float wheelRpm) {
        return (wheelRpm * getDiameter() * 60 / 1000 / 1000);
    }

    public int getDiameter() {
        return 2096;
    }

    @Override
    public void onConnectionStatusChanged(boolean searching, boolean found, boolean connected) {
        runOnUiThread(() -> {
            binding.content.connectionIndicator1.setImageResource(searching ? R.drawable.indicator : R.drawable.indicator_inactive);
            binding.content.connectionIndicator2.setImageResource(found ? R.drawable.indicator : R.drawable.indicator_inactive);
            binding.content.connectionIndicator3.setImageResource(connected ? R.drawable.indicator : R.drawable.indicator_inactive);
        });
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    void startBleScan() {
        if (!cscManager.connect())
            cscManager.startScan();
    }

    void stopBleScan() {
        if (!cscManager.disconnect())
            cscManager.stopScan();
    }

    private void updateView() {
        binding.content.pressure.setText(formatValue(lastPressure));
        binding.content.temperature.setText(formatValue(lastTemperature));

        String height = formatValue(calculateHeight(lastPressure, basePressure, lastTemperature));
        setFloatText(height, binding.content.height, binding.content.heightSub);
    }

    private void setFloatText(String floatString, TextView integer, TextView fraction) {
        int pointIndex = floatString.indexOf('.');
        integer.setText(floatString.substring(0, pointIndex));
        fraction.setText(floatString.substring(pointIndex));
    }

    private void hideSystemControls() {
        View decor = this.getWindow().getDecorView();
        decor.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private Sensor findTemperatureSensor() {
        List<Sensor> list = sensorManager.getSensorList(Sensor.TYPE_ALL);
        list.forEach(sensor -> {
            if ("BMP280 temperature".equals(sensor.getName())) {
                temperature = sensor;
                Log.v(TAG, sensor.getName());           // => BMP280 temperature
                Log.v(TAG, sensor.getStringType());     // => com.google.sensor.internal_temperature
                Log.v(TAG, "Type=" + sensor.getType()); // => Type=65536
            }
        });
        return temperature;
    }

    private static String formatValue(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static float calculateHeight(float lastPressure, float basePressure, float lastTemperature) {
        return (float) (((Math.pow(basePressure / lastPressure, 1 / 5.257) - 1) * (lastTemperature + 273.15)) / 0.0065);
    }
}
