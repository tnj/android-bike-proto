package sh.nothing.droidbike;

import android.app.Application;

import com.deploygate.sdk.DeployGate;

/**
 * Created by tnj on 2/28/17.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DeployGate.install(this, null, false);
    }
}
