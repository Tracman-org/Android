package us.keithirwin.tracman;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.DrawerLayout;

import values.AboutFragment;
import values.MainFragment;

public class MainActivity extends AppCompatActivity implements
		NavigationDrawerFragment.NavigationDrawerCallbacks,
		MainFragment.OnMapButtonPressedListener,
		AboutFragment.OnBackButtonPressedListener {

	private static final String TAG = "MainActivity";
	private CharSequence mTitle;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (BuildConfig.DEBUG){ Log.d(TAG,"Started in debug mode"); }

		NavigationDrawerFragment mNavigationDrawerFragment = (NavigationDrawerFragment)
				getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
		mTitle = getTitle();

		// Set up the drawer.
		mNavigationDrawerFragment.setUp(
			R.id.navigation_drawer,
			(DrawerLayout) findViewById(R.id.drawer_layout));

		// Check if gps enabled and start location service
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		if (sharedPref.getBoolean("gps_switch", false)) {
			Log.d(TAG, "Starting LocationService");
			this.startService(new Intent(this, LocationService.class));
		}
	}

	@Override
	public void onNavigationDrawerItemSelected(int position) {
		// update the main content by replacing fragments
		Log.v(TAG, "onNavigationDrawerItemSelected() called");

		Fragment fragment;
		FragmentManager fragmentManager = getSupportFragmentManager();

		switch(position) {
			default:
			case 0:
				Log.d(TAG, "Sending intent to go to main fragment");

				// Get user ID and name
				SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
				final String mUserID = sharedPref.getString("loggedInUserId", null);
				final String mUserName = sharedPref.getString("loggedInUserName", null);
				if (mUserID == null) {
					startActivity(new Intent(this, LoginActivity.class)
							.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
				}

				fragment = MainFragment.newInstance(mUserName, mUserID);
				break;
			case 1:
				Log.v(TAG, "Sending intent to go to Settings Activity");
				fragment = null;
				startActivity(new Intent(this, SettingsActivity.class)
						.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
				break;
			case 2:
				Log.v(TAG, "Sending intent to go to about fragment");
				fragment = AboutFragment.newInstance();
				break;
			case 3:
				Log.v(TAG, "Sending intent to go to logout fragment");
				fragment = null;
				Log.d(TAG, "Sending intent to log out");

				// Stop LocationService
				stopService(new Intent(this, LocationService.class));

				// Send back to login screen
				startActivity(new Intent(this, LoginActivity.class)
						.putExtra("method", "signOut")
						.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
				break;
		}

		if (fragment!=null) {
			fragmentManager.beginTransaction()
					.replace(R.id.container, fragment)
					.commit();
		}
	}

	public void onSectionAttached(int number) {
		switch (number) {
			case 0:
				mTitle = getString(R.string.main_name);
//				Toast.makeText(this, "main", Toast.LENGTH_SHORT).show();
				break;
			case 1:
//				mTitle = getString(R.string.settings_name);
//				Log.d(TAG, "Sending intent to go to settings");
//				startActivity(new Intent(this, SettingsActivity.class)
//						.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
				break;
			case 2:
				mTitle = getString(R.string.about_name);
				break;
//			case 3:
//				break;
		}
	}

	public void goBack() {
		onNavigationDrawerItemSelected(0);
	}

	public void showMap(String UserId) {
		String url = "https://tracman.org/trac/id/" + UserId;
		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setData(Uri.parse(url));
		startActivity(i);
	}

//	public void restoreActionBar() {
//		ActionBar actionBar = getActionBar();
//		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
//		actionBar.setDisplayShowTitleEnabled(true);
//		actionBar.setTitle(mTitle);
//	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		private static final String ARG_SECTION_NUMBER = "section_number";

		public PlaceholderFragment() {}

		/**
		 * Returns a new instance of this fragment for the given section
		 * number.
		 */
		public static PlaceholderFragment newInstance(int sectionNumber) {
			PlaceholderFragment fragment = new PlaceholderFragment();
			Bundle args = new Bundle();
			args.putInt(ARG_SECTION_NUMBER, sectionNumber);
			fragment.setArguments(args);
			return fragment;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			return inflater.inflate(R.layout.fragment_main, container, false);
		}

		@Override
		public void onAttach(Context context) {
			super.onAttach(context);
			((MainActivity) context).onSectionAttached(getArguments().getInt(ARG_SECTION_NUMBER));
		}

	}

}
