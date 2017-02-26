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

        binding.content.cadenceGraph.setMax(120.0f);
        binding.content.cadenceGraph.setMin(0.0f);
        binding.content.cadenceGraph.setColorResource(R.color.colorPrimaryDark);

        binding.content.gearRatio.setMax(4.6f);
        binding.content.gearRatio.setMin(1.2f);
        binding.content.gearRatio.setColorResource(R.color.colorPrimary);

        binding.content.cadenceRpmGraph.setColorResource(R.color.colorPrimaryDark);
    }

    int fpscount = 0;
    long fpsstart = 0;

    @Override
    protected void onStart() {
        super.onStart();
        sensorManager.registerListener(this, pressure, SensorManager.SENSOR_DELAY_UI);
        if (temperature != null)
            sensorManager.registerListener(this, temperature, SensorManager.SENSOR_DELAY_NORMAL);

        MainActivityPermissionsDispatcher.startBleScanWithCheck(this);

        speedAnimator = initAnimator(binding.content.speed);
        cadenceAnimator = initAnimator(binding.content.cadence);
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
    public void onUpdate(int wheelRevolutions, float wheelRpm, int cranksRevolutions, float crankRpm) {
        runOnUiThread(() -> {
            setAnimatorValue(speedAnimator, calculateSpeed(wheelRpm));
            setAnimatorValue(cadenceAnimator, crankRpm);
            binding.content.cadenceRpmGraph.setRpm(crankRpm);
            binding.content.gearRatio.setCurrent(wheelRpm / crankRpm);
        });
    }

    private ValueAnimator initAnimator(TextView view) {
        ValueAnimator animator = ValueAnimator.ofFloat(0.0f, 0.0f);
        animator.setDuration(1000);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener((animation) -> {
            view.setText(String.format(Locale.US, "%.0f", (Float) animation.getAnimatedValue()));
            if (view == binding.content.speed)
                binding.content.speedGraph.setCurrent((Float) animation.getAnimatedValue());
            else if (view == binding.content.cadence)
                binding.content.cadenceGraph.setCurrent((Float) animation.getAnimatedValue());

            fpscount++;
            long current = System.nanoTime();
            long diff = current - fpsstart;
            if (diff > 1_000_000_000) {
                Log.v(TAG, "fps=" + String.format(Locale.US, "%.1f", (float) fpscount / (diff / 1_000_000_000.0)));
                fpscount = 0;
                fpsstart = current;
            }
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
        int pointIndex = height.indexOf('.');
        binding.content.height.setText(height.substring(0, pointIndex));
        binding.content.heightSub.setText(height.substring(pointIndex));
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
