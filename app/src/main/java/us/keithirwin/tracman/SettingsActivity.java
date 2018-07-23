package us.keithirwin.tracman;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;

import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
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

			} else if (preference instanceof RingtonePreference) {
				// For ringtone preferences, look up the correct display value
				// using RingtoneManager.
				if (TextUtils.isEmpty(stringValue)) {
					// Empty values correspond to 'silent' (no ringtone).
					preference.setSummary(R.string.pref_ringtone_silent);

				} else {
					Ringtone ringtone = RingtoneManager.getRingtone(
							preference.getContext(), Uri.parse(stringValue));

					if (ringtone == null) {
						// Clear the summary if there was a lookup error.
						preference.setSummary(null);
					} else {
						// Set the summary to reflect the new ringtone display
						// name.
						String name = ringtone.getTitle(preference.getContext());
						preference.setSummary(name);
					}
				}

			} else {
				// For all other preferences, set the summary to the value's
				// simple string representation.
				preference.setSummary(stringValue);
			}
			return true;
		}
	};

	/**
	 * Binds a preference's summary to its value. More specifically, when the
	 * preference's value is changed, its summary (line of text below the
	 * preference title) is updated to reflect the value. The summary is also
	 * immediately updated upon calling this method. The exact display format is
	 * dependent on the type of preference.
	 *
	 * @see #sBindPreferenceSummaryToValueListener
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

	// Ask for location permissions (can't be done in service, only activity)
	private void getLocationPermission() {
		if(!LocationService.checkLocationPermission(SettingsActivity.this)) {
			ActivityCompat.requestPermissions(
				SettingsActivity.this,
				new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
				MY_FINE_LOCATION_PERMISSION
			);
		}
	};

	/**
	 * A preference value change listener that restarts the location service
	 * after something relevant is changed.
	 */
    private Preference.OnPreferenceChangeListener sRestartLocationServiceOnChangeListener = new Preference.OnPreferenceChangeListener() {

    	// Restart LocationService when preferences change
        @Override
        public boolean onPreferenceChange(Preference preference, Object obj) {

			restartLocationService();

            return true;

        }
    };

    private void restartLocationService(){
		// Get preferences
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);

		// Stop LocationService, if running
		stopService(new Intent(SettingsActivity.this, LocationService.class));

		// Check if it should be reactivated
		if (sharedPref.getBoolean("gps_switch", false)) {

			getLocationPermission();

			// Start LocationService
			Log.d(TAG, "Starting LocationService");
			startService(new Intent(SettingsActivity.this, LocationService.class));

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


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
		setupActionBar();

        // Restart LocationService when any related preference is changed
		//findPreference("gps_switch").setOnPreferenceChangeListener(sRestartLocationServiceOnChangeListener);

        // Get User ID
        // FROM old
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String mUserID = sharedPref.getString("loggedInUserId", null);
        if (mUserID == null) {
            startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }

        // Start location service, if needed
		restartLocationService();

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

	// FROM old
//    @Override
//    protected void onStop() {
//        Log.d(TAG, "onStop called");
//        super.onStop();
//        // Get updated preferences
//        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
//
//        // Save updated preferences
//        SharedPreferences.Editor editor = sharedPref.edit();
////		editor.putBoolean("pref_start_boot", );
//        editor.apply();
//
//        // Restart service so settings can take effect
//        stopService(new Intent(this, LocationService.class));
//        if (sharedPref.getBoolean("gps_switch", false)) {
//
//            // Ask for location permissions (can't be done in service, only activity)
//            if (!LocationService.checkLocationPermission(this)) {
//                ActivityCompat.requestPermissions(
//                        this,
//                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                        MY_FINE_LOCATION_PERMISSION);
//            }
//
//            // Start location tracking service
//            Log.d(TAG, "Starting LocationService");
//            startService(new Intent(this, LocationService.class));
//
//        }
//
//    }

//    FROM old
//    @Override
//    public void onBackPressed() {
//        Log.v(TAG,"onBackPressed() called");
//
//        // Return to LoginActivity and don't sign back in again
//        setResult(SIGN_OUT, new Intent());
//
//        super.onBackPressed();
//    }

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

			// Restart LocationService when any related preference is changed
//			findPreference("gps_switch").setOnPreferenceChangeListener(sRestartLocationServiceOnChangeListener);

			// Bind the summary of preferences to their value
			bindPreferenceSummaryToValue(findPreference("broadcast_frequency"));
			bindPreferenceSummaryToValue(findPreference("broadcast_priority"));



		}

		// FROM blank
//		@Override
//		public boolean onOptionsItemSelected(MenuItem item) {
//			int id = item.getItemId();
//			if (id == android.R.id.home) {
//				startActivity(new Intent(getActivity(), SettingsActivity.class));
//				return true;
//			}
//			return super.onOptionsItemSelected(item);
//		}
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
//
//		@Override
//		public boolean onOptionsItemSelected(MenuItem item) {
//			int id = item.getItemId();
//			if (id == android.R.id.home) {
//				startActivity(new Intent(getActivity(), SettingsActivity.class));
//				return true;
//			}
//			return super.onOptionsItemSelected(item);
//		}
//	}

	/**
	 * This fragment shows data and sync preferences only. It is used when the
	 * activity is showing a two-pane settings UI.
	 */
//	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
//	public static class DataSyncPreferenceFragment extends PreferenceFragment {
//		@Override
//		public void onCreate(Bundle savedInstanceState) {
//			super.onCreate(savedInstanceState);
//			addPreferencesFromResource(R.xml.pref_data_sync);
//			setHasOptionsMenu(true);
//
//			// Bind the summaries of EditText/List/Dialog/Ringtone preferences
//			// to their values. When their values change, their summaries are
//			// updated to reflect the new value, per the Android Design
//			// guidelines.
//			bindPreferenceSummaryToValue(findPreference("sync_frequency"));
//		}
//
//		@Override
//		public boolean onOptionsItemSelected(MenuItem item) {
//			int id = item.getItemId();
//			if (id == android.R.id.home) {
//				startActivity(new Intent(getActivity(), SettingsActivity.class));
//				return true;
//			}
//			return super.onOptionsItemSelected(item);
//		}
//	}
}
