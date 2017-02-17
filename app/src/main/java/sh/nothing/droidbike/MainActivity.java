package sh.nothing.droidbike;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import sh.nothing.droidbike.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor pressure;
    private Sensor temperature;
    private ActivityMainBinding binding;

    float basePressure = 1013.25f;
    float lastPressure = 1000.0f;
    float lastTemperature = 15.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        setSupportActionBar(binding.toolbar);
        binding.fab.setOnClickListener(view -> {
            basePressure = lastPressure;
            Snackbar
                .make(view, "Calibrated: " + lastPressure, Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .show();
        });

        // Get an instance of the sensor service, and use that to get an instance of
        // a particular sensor.
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        temperature = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        sensorManager.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, temperature, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onStop() {
        super.onStop();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()) {
            case Sensor.TYPE_PRESSURE:
                lastPressure = event.values[0];
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                lastTemperature = event.values[0];
                break;
        }

        String text = Float.toString(lastPressure) + "hPa\n" +
            Float.toString(lastTemperature) + "℃\n" +
            calculateHeight(lastPressure, basePressure, lastTemperature) + "m";

        binding.content.helloWorld.setText(text);
    }

    private float calculateHeight(float lastPressure, float basePressure, float lastTemperature) {
        return (float) (((Math.pow(basePressure / lastPressure, 1 / 5.257) - 1) * (lastTemperature + 273.15)) / 0.0065);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
