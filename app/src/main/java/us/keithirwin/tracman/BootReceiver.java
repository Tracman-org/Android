package us.keithirwin.tracman;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class BootReceiver extends BroadcastReceiver {
	public BootReceiver() {}

	@Override
	public void onReceive(Context context, Intent intent) {
		// Starts location service on boot
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getBoolean("pref_start_boot", true)) {
			context.startService(new Intent(context, LocationService.class));
		}
	}
}
