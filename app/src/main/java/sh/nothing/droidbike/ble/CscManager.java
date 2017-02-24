package sh.nothing.droidbike.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Created by tnj on 2/19/17.
 */

public class CscManager {

    private static final String TAG = "BLEManager";

    private static final UUID SERVICE_UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("00002A5B-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final byte WHEEL_REVOLUTIONS_DATA_PRESENT = 0x01; // 1 bit
    private static final byte CRANK_REVOLUTION_DATA_PRESENT = 0x02; // 1 bit

    private Context context;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothDevice device;

    private ScanCallback callback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            if (device == null) {
                Log.v("BLEScan", result.toString());
                initWithDevice(result.getDevice());
                bluetoothLeScanner.stopScan(this);
            }
        }
    };
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic characteristic;

    private int lastWheelRevolutions;
    private int lastWheelEventTime;
    private int lastCrankRevolutions;
    private int lastCrankEventTime;
    private boolean scanning;
    private boolean connected;
    private boolean found;

    public CscManager(Context context) {
        this.context = context;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    public boolean supported() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public void startScan() {
        ScanSettings settings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build();

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder()
            .setServiceUuid(new ParcelUuid(SERVICE_UUID))
            .build());

        Log.v(TAG, "startScan");
        bluetoothLeScanner.startScan(filters, settings, callback);
        setScanning(true);
    }

    public void stopScan() {
        bluetoothLeScanner.stopScan(callback);
        setScanning(false);
    }

    private void initWithDevice(BluetoothDevice device) {
        this.device = device;
        gatt = device.connectGatt(context, false, new GattCallback());
        setFound(true);
    }

    private CscManagerCallback cscCallback;
    public void registerCallback(CscManagerCallback callback) {
        cscCallback = callback;
    }

    void setScanning(boolean scanning) {
        this.scanning = scanning;
        doConnectionStatusCallback();
    }

    void setConnected(boolean connected) {
        this.connected = connected;
        doConnectionStatusCallback();
    }

    void setFound(boolean found) {
        this.found = found;
        doConnectionStatusCallback();
    }

    private void doConnectionStatusCallback() {
        if (cscCallback != null)
            cscCallback.onConnectionStatusChanged(scanning, found, connected);
    }

    class GattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.v("GattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    setConnected(true);
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("GattCallback", "STATE_DISCONNECTED");
                    setConnected(false);
                    break;
                default:
                    Log.e("GattCallback", "newState=" + newState);
            }
            super.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service != null) {
                characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                gatt.setCharacteristicNotification(characteristic, true);
                final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
            }
        }


        // Heavily borrowed from https://github.com/NordicSemiconductor/Android-nRF-Toolbox/blob/0b2e3aba170e784ccb1d4ff7eed3212a7f6a084b/app/src/main/java/no/nordicsemi/android/nrftoolbox/csc/CSCManager.java

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            // Decode the new data
            int offset = 0;
            final int flags = characteristic.getValue()[offset]; // 1 byte
            offset += 1;

            final boolean wheelRevPresent = (flags & WHEEL_REVOLUTIONS_DATA_PRESENT) > 0;
            final boolean crankRevPreset = (flags & CRANK_REVOLUTION_DATA_PRESENT) > 0;

            float wheelRpm = 0.0f;
            float crankRpm = 0.0f;
            if (wheelRevPresent) {
                final int wheelRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, offset);
                offset += 4;

                final int wheelEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset); // 1/1024 s
                offset += 2;

                if (lastWheelRevolutions == 0) {
                    lastWheelRevolutions = wheelRevolutions;
                    lastWheelEventTime = wheelEventTime;
                }

                wheelRpm = rpm(wheelRevolutions, lastWheelRevolutions, wheelEventTime, lastWheelEventTime);
                int length = 2096; // mm
                Log.d(TAG, "Wheel: #" + wheelRevolutions
                    + " @ " + String.format(Locale.US, "%.2f", (float) wheelEventTime / 1024) + "s / "
                    + String.format(Locale.US, "%.2f", wheelRpm) + "RPM / "
                    + String.format(Locale.US, "%.2f", ((wheelRpm * length) * 60 / 1000 / 1000)) + "km/h");

                lastWheelRevolutions = wheelRevolutions;
                lastWheelEventTime = wheelEventTime;

            }

            if (crankRevPreset) {
                final int crankRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;

                final int crankEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;

                if (lastCrankRevolutions == 0) {
                    lastCrankRevolutions = crankRevolutions;
                    lastCrankEventTime = crankEventTime;
                }

                crankRpm = rpm(crankRevolutions, lastCrankRevolutions, crankEventTime, lastCrankEventTime);
                Log.d(TAG, "Crank: #" + crankRevolutions
                    + " @ " + String.format(Locale.US, "%.2f", (float) crankEventTime / 1024) + "s / "
                    + String.format(Locale.US, "%.2f", crankRpm) + "RPM");

                lastCrankRevolutions = crankRevolutions;
                lastCrankEventTime = crankEventTime;
            }

            Log.d(TAG, "Gear Ratio: " + String.format(Locale.US, "%.2f", wheelRpm / crankRpm));

            if (cscCallback != null)
                cscCallback.onUpdate(lastWheelRevolutions, wheelRpm, lastCrankRevolutions, crankRpm);
        }
    }

    static float rpm(int newRevolutions, int lastRevolutions, int newTime, int lastTime) {
        int diff = timeDiff(newTime, lastTime);
        if (diff == 0)
            return 0.0f;
        return (float) (newRevolutions - lastRevolutions) / diff * 1024.0f * 60.0f;
    }

    static int timeDiff(int newTime, int lastTime) {
        return newTime - lastTime + (newTime < lastTime ? 65535 : 0);
    }

    public interface CscManagerCallback {
        void onUpdate(int wheelRevolutions, float wheelRpm, int cranksRevolutions, float crankRpm);
        void onConnectionStatusChanged(boolean searching, boolean found, boolean connected);
    }
}
