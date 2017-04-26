package us.keithirwin.tracman;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class ConnectionReceiver extends BroadcastReceiver {
	private String TAG = "ConnectionReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG,"onReceive() called");

		// Get connection information
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = cm.getActiveNetworkInfo();

		// Prepare intent
		Intent locationServiceIntent = new Intent(context, LocationService.class);

		// Check connection
		if (networkInfo!=null) {
			Log.d(TAG, "Connected");
			context.startService(locationServiceIntent);
		}
		else {
			Log.d(TAG,"Disconnected");
			context.stopService(locationServiceIntent);
		}

	}
}
