package us.keithirwin.tracman;

import android.Manifest;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.emitter.Emitter;
import io.socket.client.Socket;
import io.socket.engineio.client.Transport;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.Objects;


public class LocationService extends Service implements GoogleApiClient.ConnectionCallbacks,
		GoogleApiClient.OnConnectionFailedListener, LocationListener {
	public LocationService() {}
	private String TAG = "LocationService";
	final static private int ICON_ON = 2;
	final static private int ICON_HALF = 1;
	final static private int ICON_OFF = 0;
	// Development
	final String SERVER_ADDRESS = "https://dev.tracman.org";
	// Production
//	final String SERVER_ADDRESS = "https://tracman.org";

	private Socket socket;
	private String mUserID;
	private String mUserSK;
	private SharedPreferences sharedPref;
	Location mLastLocation;
	private GoogleApiClient mGoogleApiClient;
	private LocationRequest mLocationRequest;

	synchronized void buildGoogleApiClient() {
		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.addApi(LocationServices.API)
				.build();
	}

	@Nullable
	private NotificationManager mNotificationManager;
	private final NotificationCompat.Builder mNotificationBuilder = new NotificationCompat.Builder(this);
	private void setupNotifications(Boolean persist) {
		Log.d(TAG,"setupNotification() called");
//
		if (mNotificationManager == null) {
			mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		}
		PendingIntent notificationIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, SettingsActivity.class)
						.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
				0);
		mNotificationBuilder
				.setPriority(-1)
				.setSmallIcon(R.drawable.logo_dark)
				.setCategory(NotificationCompat.CATEGORY_SERVICE)
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.setContentTitle(getText(R.string.app_name))
				.setContentIntent(notificationIntent)
				.setOngoing(persist);
	}
	private void showNotification(CharSequence text, int icon) {
		Log.d(TAG,"showNotification() called");
		mNotificationBuilder
				.setTicker(text)
				.setContentText(text);
		switch (icon) {
			case ICON_ON:
				mNotificationBuilder.setSmallIcon(R.drawable.logo_white);
				break;
			case ICON_HALF:
				mNotificationBuilder.setSmallIcon(R.drawable.logo_trans);
				break;
			case ICON_OFF:
				mNotificationBuilder.setSmallIcon(R.drawable.logo_dark);
				break;
		}
		if (mNotificationManager != null) {
			mNotificationManager.notify(1, mNotificationBuilder.build());
		}
	}

	private final BroadcastReceiver LowPowerReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			connectLocationUpdates(300, LocationRequest.PRIORITY_NO_POWER);
			Log.d(TAG, "Priority and interval lowered due to low power");
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(TAG, "onCreate called");

		// Get preferences
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

		setupNotifications(true);
		showNotification(getText(R.string.notify_connecting), ICON_OFF);
		Log.d(TAG, "Notification set up");

		buildGoogleApiClient();
		Log.d(TAG, "Google API Client built");
		mGoogleApiClient.connect();
		Log.d(TAG, "Connected to Google API Client");

		IntentFilter lowPowerFilter = new IntentFilter();
		lowPowerFilter.addAction("android.intent.action.BATTERY_LOW");
		registerReceiver(LowPowerReceiver, lowPowerFilter);
		Log.d(TAG, "LowPowerReceiver activated");

		mUserID = sharedPref.getString("loggedInUserId", null);
		mUserSK = sharedPref.getString("loggedInUserSk", null);

		try {

			// Connect to socket.io
			IO.Options opts = new IO.Options();
			opts.secure = true;
			socket = IO.socket(SERVER_ADDRESS, opts);

			showNotification(getText(R.string.notify_connected), ICON_HALF);

			// Log errors
			socket.io().on(Manager.EVENT_TRANSPORT, new Emitter.Listener() {
				@Override
				public void call(Object... args) {
					Transport transport = (Transport) args[0];
					transport.on(Transport.EVENT_ERROR, new Emitter.Listener() {
						@Override
						public void call(Object... args) {
							Exception e = (Exception) args[0];
							Log.e(TAG, "Transport error: " + e);
							e.printStackTrace();
							e.getCause().printStackTrace();
						}
					});
				}
			});

			socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
				@Override
				public void call(Object... args) {
					socket.emit("can-set", mUserID);
				}
			});

			// Listen for activation signals
			socket.on("activate", onActivate);

			socket.connect();

		} catch (URISyntaxException e) {
			showNotification(getText(R.string.server_connection_error), ICON_OFF);
			Log.e(TAG, "Failed to connect to sockets server " + SERVER_ADDRESS, e);
		}

	}

	private int getPrioritySetting() {
		return Integer.parseInt(sharedPref.getString("broadcast_priority", "100"));
	}
	private int getIntervalSetting() {
		return Integer.parseInt(
				sharedPref.getString("broadcast_frequency",
						getResources().getString(R.string.pref_default_broadcast_frequency)));
	}

	public static boolean checkLocationPermission(final Context context) {
		return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	void connectLocationUpdates(Integer interval, Integer priority) {

		// Set update parameters
		if (mLocationRequest != null) {
			mLocationRequest.setPriority(priority);
			mLocationRequest.setInterval(interval * 1000); // 1000 = 1 second
		} else{
			mLocationRequest = LocationRequest.create();
			connectLocationUpdates(getIntervalSetting(), getPrioritySetting());
		}

		// Get permission
		if (!checkLocationPermission(this)) {
			Log.d(TAG, "Location permission denied");
			//TODO: Ask the user to try again
		} else {
			Log.d(TAG, "Location permission granted");

			// Request location updates
			if (mGoogleApiClient.isConnected()) {
				LocationServices.FusedLocationApi.requestLocationUpdates(
						mGoogleApiClient,
						mLocationRequest,
						this);
			} else {
				mGoogleApiClient.connect();
			}

			// Get last location
			mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

		}

		// Set location if there is one
		if (mLastLocation != null) {
			onLocationChanged(mLastLocation);
		}

	}

	@Override
	public void onConnected(Bundle bundle) {
		Log.d(TAG, "onConnected called");

		mLocationRequest = LocationRequest.create();
		connectLocationUpdates(getIntervalSetting(), getPrioritySetting());
		showNotification(getString(R.string.occasional_updates), ICON_HALF);
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		Log.e(TAG, "onConnectionFailed: " + connectionResult);
		showNotification(getText(R.string.google_connection_error), ICON_OFF);
		buildGoogleApiClient();
	}

	private Emitter.Listener onActivate = new Emitter.Listener() {
		@Override
		public void call(final Object... args) {
			if (args[0].toString().equals("true")) {
				Log.d(TAG, "Activating realtime updates");
				connectLocationUpdates(getIntervalSetting(), getPrioritySetting());
				showNotification(getString(R.string.realtime_updates), ICON_ON);
			} else {
				Log.d(TAG, "Deactivating realtime updates");
				connectLocationUpdates(300, LocationRequest.PRIORITY_NO_POWER);
				showNotification(getString(R.string.occasional_updates), ICON_HALF);
			}
		}
	};

	@Override
	public void onLocationChanged(Location location) {

		// Make sure we're logged in...
		if (mUserID!=null && mUserSK!=null) {
			JSONObject mLocationView = new JSONObject();
			try {
				mLocationView.put("usr", mUserID);
				mLocationView.put("tok", mUserSK);
				mLocationView.put("ts", String.valueOf(System.currentTimeMillis()));
				mLocationView.put("lat", String.valueOf(location.getLatitude()));
				mLocationView.put("lon", String.valueOf(location.getLongitude()));
				mLocationView.put("dir", String.valueOf(location.getBearing()));
				mLocationView.put("spd", String.valueOf(location.getSpeed()));
			} catch (JSONException e) {
				Log.e(TAG, "Failed to put JSON data");
			}
			socket.emit("set", mLocationView);
			Log.v(TAG, "Location set: " + mLocationView.toString());
		}
		else {
			Log.v(TAG, "Can't set location because user isn't logged in.");
      stopSelf();
		}

	}

	@Override
	public void onConnectionSuspended(int i) {
		Log.d(TAG, "onConnectionSuspended called");
		showNotification(getText(R.string.google_connection_error), ICON_OFF);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy executed");

		socket.disconnect();
		socket.off("activate", onActivate);
		Log.d(TAG, "Disconnected from sockets");

		mGoogleApiClient.disconnect();
		Log.d(TAG, "Google API disconnected");

		unregisterReceiver(LowPowerReceiver);
		Log.d(TAG, "LowPowerReceiver deactivated");

		setupNotifications(false);
		showNotification(getText(R.string.disconnected), ICON_OFF);
		Log.d(TAG, "Notification changed");
	}
}