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

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.Transport;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Manager;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

public class LocationService extends Service {
//        implements GoogleApiClient.ConnectionCallbacks,
//        GoogleApiClient.OnConnectionFailedListener {
	private String TAG = "LocationService";

	// Icon styles
	final static private int ICON_ON = 2;
	final static private int ICON_HALF = 1;
	final static private int ICON_OFF = 0;

	// Development
	final String SERVER_ADDRESS = "https://dev.tracman.org";
	// Production
//	final String SERVER_ADDRESS = "https://www.tracman.org";

	private Socket mSocket;
	{
		try {
			mSocket = IO.socket(SERVER_ADDRESS);
		} catch (URISyntaxException e) {
			Log.e(TAG, "Failed to connect to sockets server " + SERVER_ADDRESS, e);
			showNotification(getText(R.string.server_connection_error), ICON_OFF);
		}
	}
	private String mUserID, mUserSK;
	private SharedPreferences sharedPref;
//    private Location mLastLocation;
	private FusedLocationProviderClient mFusedLocationClient;
	private LocationRequest mLocationRequest = new LocationRequest();
    private LocationCallback mLocationCallback = new LocationCallback() {
		@Override
		public void onLocationResult(LocationResult locationResult) {
			if (locationResult == null) {
				Log.i(TAG, "mLocationCallback called with locationResult of null");
				return;
			}
			for (Location location : locationResult.getLocations()) {
				// Update UI with location data
				Log.d(TAG, "Got location: " +
						Double.toString( location.getLatitude() ) + ", " +
						Double.toString( location.getLongitude() )
				);

				setLocation(location);
			}
		}
	};
//    private GoogleApiClient mGoogleApiClient;
//    private LocationRequest mLocationRequest;

//    synchronized void buildGoogleApiClient() {
//        mGoogleApiClient = new GoogleApiClient.Builder(this)
//                .addConnectionCallbacks(this)
//                .addOnConnectionFailedListener(this)
//                .addApi(LocationServices.API)
//                .build();
//    }

	// Setup notifications
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

	// Switch to NO_POWER on low battery
	private final BroadcastReceiver LowPowerReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			connectLocationUpdates(300, LocationRequest.PRIORITY_NO_POWER);
			Log.d(TAG, "Priority and interval lowered due to low battery");
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(TAG, "onCreate called");

		// Get preferences
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

		// Set up location service
		mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationRequest.setInterval(getIntervalSetting());
        mLocationRequest.setFastestInterval(getIntervalSetting()); // TODO: What's this?
        mLocationRequest.setPriority(getPrioritySetting());

        // Get location


        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        startLocationUpdates();

		// Setup notifications
		setupNotifications(true);
		showNotification(getText(R.string.notify_connecting), ICON_OFF);
		Log.d(TAG, "Notification set up");

//        buildGoogleApiClient();
//        Log.i(TAG, "Google API client built");
//        mGoogleApiClient.connect();
//        Log.i(TAG, "Google API client connected");

		IntentFilter lowPowerFilter = new IntentFilter();
		lowPowerFilter.addAction("android.intent.action.BATTERY_LOW");
		registerReceiver(LowPowerReceiver, lowPowerFilter);
		Log.d(TAG, "LowPowerReceiver activated");

		mUserID = sharedPref.getString("loggedInUserId", null);
		mUserSK = sharedPref.getString("loggedInUserSk", null);

//        FROM old
//            IO.Options opts = new IO.Options();
//            opts.secure = true;
//            socket = IO.socket(SERVER_ADDRESS, opts);

		showNotification(getText(R.string.notify_connected), ICON_HALF);

		// Log errors
		mSocket.io().on(Manager.EVENT_TRANSPORT, new Emitter.Listener() {
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

		// Notify server that we can set location (after connection)
		mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				mSocket.emit("can-set", mUserID, mUserSK);
			}
		});

		// Listen for activation signals
		mSocket.on("activate", onActivate);

		// Connect to socket.io
		mSocket.connect();

	}

    private void startLocationUpdates() {
	    try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback,null);
        } catch (SecurityException e) {
	        Log.e(TAG,"Can't startLocationUpdates: missing location permission");
	        // Show notification
			// TODO: Tap to request permission?
			showNotification(getText(R.string.need_location_permission), ICON_OFF);
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
//        if (mLocationRequest != null) {
//            mLocationRequest.setPriority(priority);
//            mLocationRequest.setInterval(interval * 1000); // 1000 = 1 second
//        } else{
//            mLocationRequest = LocationRequest.create();
//            connectLocationUpdates(getIntervalSetting(), getPrioritySetting());
//        }

		// Get permission
		if (!checkLocationPermission(this)) {
			Log.d(TAG, "Location permission denied");
			//TODO: Ask the user to try again
		} else {
			Log.d(TAG, "Location permission granted");

//            // Request location updates
//            if (mGoogleApiClient.isConnected()) {
//                LocationServices.FusedLocationApi.requestLocationUpdates(
//                        mGoogleApiClient,
//                        mLocationRequest,
//                        this);
//            } else {
//                mGoogleApiClient.connect();
//            }
//
//            // Get last location
//            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

		}

		// Set location if there is one
//        if (mLastLocation != null) {
//            onLocationChanged(mLastLocation);
//        }



	}

//    @Override
	public void onConnected(Bundle bundle) {
		Log.d(TAG, "onConnected called");

//        mLocationRequest = LocationRequest.create();
		connectLocationUpdates(getIntervalSetting(), getPrioritySetting());
		showNotification(getString(R.string.occasional_updates), ICON_HALF);
	}


//    @Override
//    public void onConnectionFailed(ConnectionResult connectionResult) {
//        Log.e(TAG, "onConnectionFailed: " + connectionResult);
//        showNotification(getText(R.string.google_connection_error), ICON_OFF);
//        buildGoogleApiClient();
//    }

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

	private void setLocation(Location location) {
		Log.i(TAG, "setLocation() called");
		Log.v(TAG, "mUserID: "+mUserID);
		Log.v(TAG, "mUserSK: "+mUserSK);

		// Make sure we're logged in...
        if (mUserID!=null && mUserSK!=null) {
            JSONObject mLocationView = new JSONObject();
            try {
                mLocationView.put("ts", String.valueOf(System.currentTimeMillis()));
                mLocationView.put("lat", String.valueOf(location.getLatitude()));
                mLocationView.put("lon", String.valueOf(location.getLongitude()));
                mLocationView.put("dir", String.valueOf(location.getBearing()));
                mLocationView.put("spd", String.valueOf(location.getSpeed()));
            } catch (JSONException e) {
                Log.e(TAG, "Failed to put JSON data");
            }
            mSocket.emit("set", mLocationView);
            Log.v(TAG, "Location set: " + mLocationView.toString());
        }
        else {
            Log.e(TAG, "Can't set location because user isn't logged in.");
            stopSelf();
			// TODO: Return user to LoginActivity
        }

	}

//    @Override
//    public void onLocationChanged(Location location) {
//        Log.v(TAG, "onLocationChanged() called");
//
//        // Make sure we're logged in...
//        if (mUserID!=null && mUserSK!=null && mUserVeh!=null) {
//            JSONObject mLocationView = new JSONObject();
//            try {
//                mLocationView.put("ts", String.valueOf(System.currentTimeMillis()));
//                mLocationView.put("`lat", String.valueOf(location.getLatitude()));
//                mLocationView.put("lon", String.valueOf(location.getLongitude()));
//                mLocationView.put("dir", String.valueOf(location.getBearing()));
//                mLocationView.put("spd", String.valueOf(location.getSpeed()));
//            } catch (JSONException e) {
//                Log.e(TAG, "Failed to put JSON data");
//            }
//            mSocket.emit("set", mLocationView);
//            Log.v(TAG, "Location set: " + mLocationView.toString());
//        }
//        else {
//            Log.v(TAG, "Can't set location because user isn't logged in.");
//            stopSelf();
//        }
//
//    }

//    @Override
//    public void onConnectionSuspended(int i) {
//        Log.d(TAG, "onConnectionSuspended called");
//        showNotification(getText(R.string.google_connection_error), ICON_OFF);
//    }

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy() called");

		mSocket.disconnect();
		mSocket.off("activate", onActivate);
		Log.i(TAG, "Disconnected socket.io");

//        mGoogleApiClient.disconnect();
//        Log.i(TAG, "Google Location Services API disconnected");

		unregisterReceiver(LowPowerReceiver);
		Log.i(TAG, "LowPowerReceiver deactivated");

		setupNotifications(false);
		showNotification(getText(R.string.disconnected), ICON_OFF);
		Log.d(TAG, "Notification changed");

	}

	@Override
	// Required for LocationService
	public IBinder onBind(Intent intent) {
		// TODO: Return the communication channel to the service.
		throw new UnsupportedOperationException("Not yet implemented");
	}

}
