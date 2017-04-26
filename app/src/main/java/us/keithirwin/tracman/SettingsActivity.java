package us.keithirwin.tracman;

import android.Manifest;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;

import java.util.List;

import static us.keithirwin.tracman.LoginActivity.SIGN_OUT;


/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {
	private static final String TAG = "SettingsActivity";
	private static final int MY_FINE_LOCATION_PERMISSION = 425;

	/**
	 * A preference value change listener that updates the preference's summary
	 * to reflect its new value.
	 */
	private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			String stringValue = value.toString();

			if (preference instanceof ListPreference) {
				// For list preferences, look up the correct display value in
				// the preference's 'entries' list.
				ListPreference listPreference = (ListPreference) preference;
				int index = listPreference.findIndexOfValue(stringValue);

				// Set the summary to reflect the new value.
				preference.setSummary(
						index >= 0
								? listPreference.getEntries()[index]
								: null);
			} else {
				// For all other preferences, set the summary to the value's
				// simple string representation.
				preference.setSummary(stringValue);
			}
			return true;
		}
	};

	/**
	 * A preference value change listener that restarts the location service
	 * after something relevant is changed.
	 */
	private Preference.OnPreferenceChangeListener sRestartLocationServiceOnChangeListener = new Preference.OnPreferenceChangeListener() {

		@Override
		public boolean onPreferenceChange(Preference preference, Object obj) {
			SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);

			stopService(new Intent(SettingsActivity.this, LocationService.class));

			if (sharedPref.getBoolean("gps_switch", false)) {

				// Ask for location permissions (can't be done in service, only activity)
				if (!LocationService.checkLocationPermission(SettingsActivity.this)) {
					ActivityCompat.requestPermissions(
							SettingsActivity.this,
							new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
							MY_FINE_LOCATION_PERMISSION);
				}

				Log.d(TAG, "Starting LocationService");
				startService(new Intent(SettingsActivity.this, LocationService.class));

			}

			return true;
		}
	};

	/**
	 * Helper method to determine if the device has an extra-large screen. For
	 * example, 10" tablets are extra-large.
	 */
	private static boolean isXLargeTablet(Context context) {
		return (context.getResources().getConfiguration().screenLayout
				& Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
	}

	/**
	 * Binds a preference's summary to its value. More specifically, when the
	 * preference's value is changed, its summary (line of text below the
	 * preference title) is updated to reflect the value. The summary is also
	 * immediately updated upon calling this method. The exact display format is
	 * dependent on the type of preference.
	 *
	 */
	private static void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

		// Trigger the listener immediately with the preference's
		// current value.
		sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
				PreferenceManager
						.getDefaultSharedPreferences(preference.getContext())
						.getString(preference.getKey(), ""));
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setupActionBar();
		Log.d(TAG, "activity onCreate called");

		// Restart LocationService when any related preference is changed
//		findPreference("gps_switch").setOnPreferenceChangeListener(sRestartLocationServiceOnChangeListener);

		// Get User ID
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		String mUserID = sharedPref.getString("loggedInUserId", null);
		if (mUserID == null) {
			startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
		}
	}

	@Override
	protected void onStop() {
		Log.d(TAG, "onStop called");
		super.onStop();
		// Get updated preferences
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

		// Save updated preferences
		SharedPreferences.Editor editor = sharedPref.edit();
//		editor.putBoolean("pref_start_boot", );
		editor.apply();

		// Restart service so settings can take effect
		stopService(new Intent(this, LocationService.class));
		if (sharedPref.getBoolean("gps_switch", false)) {

			// Ask for location permissions (can't be done in service, only activity)
			if (!LocationService.checkLocationPermission(this)) {
				ActivityCompat.requestPermissions(
						this,
						new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
						MY_FINE_LOCATION_PERMISSION);
			}

			// Start location tracking service
			Log.d(TAG, "Starting LocationService");
			startService(new Intent(this, LocationService.class));

		}

	}

	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	private void setupActionBar() {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			// Show the Up button in the action bar.
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onIsMultiPane() {
		return isXLargeTablet(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void onBuildHeaders(List<Header> target) {
		loadHeadersFromResource(R.xml.pref_headers, target);
	}

	@Override
	public void onBackPressed() {
		//Log.v(TAG,"onBackPressed() called");

		// Return to LoginActivity and don't sign back in again
		setResult(SIGN_OUT, new Intent());

		super.onBackPressed();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId()==android.R.id.home) {
			// Respond to the action bar's Up/Home button
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * This method stops fragment injection in malicious applications.
	 * Make sure to deny any unknown fragments here.
	 */
	protected boolean isValidFragment(String fragmentName) {
		return PreferenceFragment.class.getName().equals(fragmentName)
				|| GeneralPreferenceFragment.class.getName().equals(fragmentName);
	}

	/**
	 * This fragment shows general preferences only. It is used when the
	 * activity is showing a two-pane settings UI.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class GeneralPreferenceFragment extends PreferenceFragment {

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_general);
			setHasOptionsMenu(true);

//			// Restart LocationService when any related preference is changed
//			findPreference("gps_switch").setOnPreferenceChangeListener(sRestartLocationServiceOnChangeListener);

			// Bind the summary of preferences to their value
			bindPreferenceSummaryToValue(findPreference("broadcast_frequency"));
			bindPreferenceSummaryToValue(findPreference("broadcast_priority"));

		}


	}

	/**
	 * This fragment shows notification preferences only. It is used when the
	 * activity is showing a two-pane settings UI.
	 */
//	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
//	public static class NotificationPreferenceFragment extends PreferenceFragment {
//		@Override
//		public void onCreate(Bundle savedInstanceState) {
//			super.onCreate(savedInstanceState);
//			addPreferencesFromResource(R.xml.pref_notification);
//			setHasOptionsMenu(true);
//
//			// Bind the summaries of EditText/List/Dialog/Ringtone preferences
//			// to their values. When their values change, their summaries are
//			// updated to reflect the new value, per the Android Design
//			// guidelines.
//			bindPreferenceSummaryToValue(findPreference("notifications_new_message_ringtone"));
//		}
//	}

	/**
	 * This fragment shows map preferences only. It is used when the
	 * activity is showing a two-pane settings UI.
	 */
//	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
//	public static class MapPreferenceFragment extends PreferenceFragment {
//		@Override
//		public void onCreate(Bundle savedInstanceState) {
//			super.onCreate(savedInstanceState);
//			addPreferencesFromResource(R.xml.pref_map);
//			setHasOptionsMenu(true);
//
//			// Bind the summaries of EditText/List/Dialog/Ringtone preferences
//			// to their values. When their values change, their summaries are
//			// updated to reflect the new value, per the Android Design
//			// guidelines.
//			bindPreferenceSummaryToValue(findPreference("sync_frequency"));
//		}
//	}
}
