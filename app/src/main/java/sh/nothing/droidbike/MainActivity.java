package sh.nothing.droidbike;

import android.Manifest;
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

import java.util.List;
import java.util.Locale;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;
import sh.nothing.droidbike.ble.BleManager;
import sh.nothing.droidbike.databinding.ActivityMainBinding;

@RuntimePermissions
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "MainActivity";
    private SensorManager sensorManager;
    private Sensor pressure;
    private Sensor temperature;
    private ActivityMainBinding binding;

    float basePressure = 0.0f;
    float lastPressure = 0.0f;
    float lastRawPressure = 0.0f;
    float lastTemperature = 25.0f;

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

    }

    @Override
    protected void onStart() {
        super.onStart();
        sensorManager.registerListener(this, pressure, SensorManager.SENSOR_DELAY_UI);
        if (temperature != null)
            sensorManager.registerListener(this, temperature, SensorManager.SENSOR_DELAY_NORMAL);

        MainActivityPermissionsDispatcher.startBleScanWithCheck(this);
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    void startBleScan() {
        new BleManager(this).startScan();
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
