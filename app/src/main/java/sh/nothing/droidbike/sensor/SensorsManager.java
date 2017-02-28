package sh.nothing.droidbike.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;

/**
 * Created by tnj on 2/27/17.
 */

public class SensorsManager implements SensorEventListener {
    public static final String TAG = "SensorsManager";

    private SensorManager sensorManager;
    private Sensor pressure;
    private Sensor accelerometer;
    private Sensor magneticField;
    private int screenRotation;

    private long lastSensorUpdate = 0L;
    private float lastPressure = Float.NaN;
    private float lastPitch = Float.NaN;
    private float lastAzimuth = Float.NaN;

    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private final float[] remappedRotationMatrix = new float[9];

    private SensorsManagerCallback callback;

    public SensorsManager(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void setScreenRotation(int rotation) {
        screenRotation = rotation;
    }

    public void start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL);
        lastSensorUpdate = System.nanoTime();
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    public void registerCallback(SensorsManagerCallback callback) {
        this.callback = callback;
    }

    private void doCallback() {
        if (callback != null)
            callback.onSensorUpdate(lastPressure, lastPitch, lastAzimuth);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == pressure) {
            if (Float.isNaN(lastPressure))
                lastPressure = event.values[0];
            lastPressure = lastPressure * 0.9f + event.values[0] * 0.1f;
        } else if (event.sensor == accelerometer) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.length);
        } else if (event.sensor == magneticField) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.length);
        }

        // limit to 60 calls/sec
        if (System.nanoTime() - lastSensorUpdate >= 16_666_666) {
            lastSensorUpdate = System.nanoTime();
            processSensorReadings();
            doCallback();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void processSensorReadings() {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
        int axisX = SensorManager.AXIS_X;
        int axisY = SensorManager.AXIS_Y;
        switch (screenRotation) {
            case Surface.ROTATION_90:
                axisX = SensorManager.AXIS_Y;
                axisY = SensorManager.AXIS_MINUS_X;
                break;
            case Surface.ROTATION_180:
                axisX = SensorManager.AXIS_MINUS_X;
                axisY = SensorManager.AXIS_MINUS_Y;
                break;
            case Surface.ROTATION_270:
                axisX = SensorManager.AXIS_MINUS_Y;
                axisY = SensorManager.AXIS_X;
                break;
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedRotationMatrix);
        SensorManager.getOrientation(remappedRotationMatrix, orientationAngles);

        float pitch = (float) Math.tan(-orientationAngles[1]);
        if (Float.isNaN(lastPitch))
            lastPitch = pitch;
        lastPitch = lastPitch * 0.99f + pitch * 0.01f;

        if (Float.isNaN(lastAzimuth))
            lastAzimuth = orientationAngles[0];
        lastAzimuth = lastAzimuth * 0.95f + orientationAngles[0] * 0.05f;
    }

    public interface SensorsManagerCallback {
        void onSensorUpdate(float pressure, float pitch, float azimuth);
    }
}
