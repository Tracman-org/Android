package us.keithirwin.tracman;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

public class LocationService extends Service implements GoogleApiClient.ConnectionCallbacks,
		GoogleApiClient.OnConnectionFailedListener, LocationListener {
	public LocationService() {}
	private String TAG = "LocationService";

	private Socket mSocket;
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
		if (mNotificationManager == null) {
			mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		}
		PendingIntent notificationIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, SettingsActivity.class)
						.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
				0);
		mNotificationBuilder
				.setPriority(-1)
				.setSmallIcon(R.drawable.logo_white)
//				.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.logo_by))
				.setCategory(NotificationCompat.CATEGORY_SERVICE)
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.setContentTitle(getText(R.string.app_name))
//				.setWhen(System.currentTimeMillis())
				.setContentIntent(notificationIntent)
				.setOngoing(persist);
	}
	private void showNotification(CharSequence text, Boolean active) {
		mNotificationBuilder
				.setTicker(text)
				.setContentText(text);
		if (active) {
			mNotificationBuilder.setSmallIcon(R.drawable.logo_white);
		} else {
			mNotificationBuilder.setSmallIcon(R.drawable.logo_trans);
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
		showNotification(getText(R.string.connecting), false);
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
		mUserSK = sharedPref.getString("loggedInUserId", null);
		final String SERVER_ADDRESS = "https://tracman.org/";

		// Connect to socket
		try {
			mSocket = IO.socket(SERVER_ADDRESS);
			mSocket.on("activate", onActivate);
			mSocket.connect();
			mSocket.emit("room", "app-"+mUserID);
			Log.d(TAG, "Connected to socket.io server "+SERVER_ADDRESS);
		} catch (URISyntaxException e) {
			showNotification(getText(R.string.server_connection_error), false);
			Log.e(TAG, "Failed to connect to sockets server " + SERVER_ADDRESS, e);
		}
		showNotification(getText(R.string.connected), false);
	}

	private int getPrioritySetting() {
		return Integer.parseInt(sharedPref.getString("broadcast_priority", "100"));
	}

	private int getIntervalSetting() {
		return Integer.parseInt(
				sharedPref.getString("broadcast_frequency",
						getResources().getString(R.string.pref_default_broadcast_frequency)));
	}

	void connectLocationUpdates(Integer interval, Integer priority) {
		if (mLocationRequest != null) {
			mLocationRequest.setPriority(priority);
			mLocationRequest.setInterval(interval * 1000); // 1000 = 1 second
		} else{
			mLocationRequest = LocationRequest.create();
			connectLocationUpdates(getIntervalSetting(), getPrioritySetting());
		}

		if (mGoogleApiClient.isConnected()) {
			LocationServices.FusedLocationApi.requestLocationUpdates(
					mGoogleApiClient,
					mLocationRequest,
					this);
		} else {
			mGoogleApiClient.connect();
		}
		mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

		if (mLastLocation != null) {
			onLocationChanged(mLastLocation);
		}
	}

	@Override
	public void onConnected(Bundle bundle) {
		Log.d(TAG, "onConnected called");

		mLocationRequest = LocationRequest.create();
		connectLocationUpdates(getIntervalSetting(), getPrioritySetting());
		showNotification(getString(R.string.realtime_updates), true);
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		Log.e(TAG, "onConnectionFailed: " + connectionResult);
		showNotification(getText(R.string.google_connection_error), false);
		buildGoogleApiClient();
	}

	private Emitter.Listener onActivate = new Emitter.Listener() {
		@Override
		public void call(final Object... args) {
			if (args[0].toString().equals("true")) {
				Log.d(TAG, "Activating realtime updates");
				connectLocationUpdates(getIntervalSetting(), getPrioritySetting());
				showNotification(getString(R.string.realtime_updates), true);
			} else {
				Log.d(TAG, "Deactivating realtime updates");
				connectLocationUpdates(300, LocationRequest.PRIORITY_NO_POWER);
				showNotification(getString(R.string.occasional_updates), false);
			}
		}
	};

	@Override
	public void onLocationChanged(Location location) {
		JSONObject mLocationView = new JSONObject();
		try {
			mLocationView.put("usr", mUserID);
			mLocationView.put("tok", mUserSK);
			mLocationView.put("lat", String.valueOf(location.getLatitude()));
			mLocationView.put("lon", String.valueOf(location.getLongitude()));
			mLocationView.put("dir", String.valueOf(location.getBearing()));
			mLocationView.put("spd", String.valueOf(location.getSpeed()));
		} catch (JSONException e) {
			Log.e(TAG, "Failed to put JSON data");
		}
		mSocket.emit("app", mLocationView);
//		Log.v(TAG, "Location updated: " + mLocationView.toString());
	}

	@Override
	public void onConnectionSuspended(int i) {
		Log.d(TAG, "onConnectionSuspended called");
		showNotification(getText(R.string.google_connection_error), false);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy executed");

		mSocket.disconnect();
		Log.d(TAG, "Disconnected from sockets");

		mGoogleApiClient.disconnect();
		Log.d(TAG, "Google API disconnected");

		unregisterReceiver(LowPowerReceiver);
		Log.d(TAG, "LowPowerReceiver deactivated");

		setupNotifications(false);
		showNotification(getText(R.string.disconnected), false);
		Log.d(TAG, "Notification changed");
	}
}