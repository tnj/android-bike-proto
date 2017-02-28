package sh.nothing.droidbike.location;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.io.IOException;

import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by tnj on 2/27/17.
 */

@SuppressWarnings("MissingPermission")
public class LocationManager implements GoogleApiClient.ConnectionCallbacks, LocationListener, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "LocationManager";
    private GoogleApiClient googleClient;
    private Location lastLocation;
    private LocationRequest locationRequest;
    private LocationCallback callback;
    private Geocoder geocoder;

    public LocationManager(Context context) {
        googleClient = new GoogleApiClient.Builder(context)
            .addApi(LocationServices.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build();
        geocoder = new Geocoder(context);
    }

    public void start() {
        googleClient.connect();
    }

    public void stop() {
        if (googleClient.isConnected())
            LocationServices.FusedLocationApi.removeLocationUpdates(googleClient, this);

        googleClient.disconnect();
    }

    protected void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(googleClient, builder.build());
        result.setResultCallback(locationSettingsResult -> {
                final Status status = locationSettingsResult.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        Log.d(TAG, "Location settings satisfied.");
                        requestLocationUpdates();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        Log.w(TAG, "Resolution required, aborted.");
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Log.w(TAG, "Settings change unavailable, aborted.");
                        break;
                }
            }
        );
    }

    private void requestLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(googleClient, locationRequest, this);
    }

    @Override
    public void onConnected(@Nullable Bundle connectionHint) {
        if (locationRequest != null) {
            requestLocationUpdates();
        } else {
            createLocationRequest();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;
        doCallback();
    }

    public void registerCallback(LocationCallback callback) {
        this.callback = callback;
    }

    void doCallback() {
        if (callback != null)
            callback.onLocationChanged(lastLocation);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, "Failed to connect Google API: " + connectionResult.getErrorMessage());
    }

    public Single<Address> requestGeolocation(Location location) {
        return Single.create((SingleOnSubscribe<Address>) e -> {
            try {
                e.onSuccess(geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1).get(0));
            } catch (IOException ignored) {
                e.onError(ignored);
            }
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());
    }

    public interface LocationCallback {
        void onLocationChanged(Location location);
    }
}
