package us.keithirwin.tracman;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
//import java.security.KeyStore;
//import java.util.Arrays;

//import javax.net.ssl.TrustManager;
//import javax.net.ssl.TrustManagerFactory;
//import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Callback;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity implements
		GoogleApiClient.OnConnectionFailedListener,
		View.OnClickListener {
	private static final String TAG = "LoginActivity";
	private static final int RC_SIGN_IN = 9001;
	static final int SIGN_OUT = 1;
	private static boolean DONT_LOG_IN = false;

	// Development
	private final String SERVER_ADDRESS = "https://dev.tracman.org/";
	private static final String GOOGLE_WEB_CLIENT_ID = "483494341936-hps4p2pcu3ctshjvqm3pqdbg0t0q281o.apps.googleusercontent.com";
	// Production
//	private final String SERVER_ADDRESS = "https://tracman.org/";
//	private static final String GOOGLE_WEB_CLIENT_ID = "483494341936-hrn0ms1tebgdtfs5f4i6ebmkt3qmo16o.apps.googleusercontent.com";

	private GoogleApiClient mGoogleApiClient;
	private ProgressDialog mProgressDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG, "created");

		// Set up layout
		setContentView(R.layout.activity_login);
		setTitle(R.string.login_name);
		TextView loginDescription = (TextView) findViewById(R.id.login_description);
		TextView forgotPassword = (TextView) findViewById(R.id.login_forgot_password);
		loginDescription.setMovementMethod(LinkMovementMethod.getInstance());
		forgotPassword.setMovementMethod(LinkMovementMethod.getInstance());

		// Configure sign-in to request the user's ID and basic profile, included in DEFAULT_SIGN_IN.
		GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
				.requestIdToken(GOOGLE_WEB_CLIENT_ID)
				.requestEmail()
				.build();

		// Build a GoogleApiClient with access to the Google Sign-In API and the
		// options specified by gso.
		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
				.addApi(Auth.GOOGLE_SIGN_IN_API, gso)
				.build();

		// Set up buttons
		SignInButton signInButton = (SignInButton) findViewById(R.id.login_button_google);
		signInButton.setStyle(SignInButton.SIZE_WIDE, SignInButton.COLOR_AUTO);

		// Button listeners
		findViewById(R.id.login_button).setOnClickListener(this);
		findViewById(R.id.login_button_google).setOnClickListener(this);
//		findViewById(R.id.login_button_facebook).setOnClickListener(this);
//		findViewById(R.id.login_button_twitter).setOnClickListener(this);
	}

	@Override
	public void onStart() {
		super.onStart();
		Log.v(TAG, "onStart() called");

		// Try to sign in
		if (!DONT_LOG_IN) {
			Log.v(TAG, "Trying to sign in...");
			OptionalPendingResult<GoogleSignInResult> opr = Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);
			if (opr.isDone()) {
				// If the user's cached credentials are valid, the OptionalPendingResult will be "done"
				// and the GoogleSignInResult will be available instantly.
				Log.d(TAG, "Got cached sign-in");
				GoogleSignInResult result = opr.get();
				handleGoogleSignInResult(result);
			} else {
				// If the user has not previously signed in on this device or the sign-in has expired,
				// this asynchronous branch will attempt to sign in the user silently.  Cross-device
				// single sign-on will occur in this branch.
				showProgressDialog();
				opr.setResultCallback(new ResultCallback<GoogleSignInResult>() {
					@Override
					public void onResult(GoogleSignInResult googleSignInResult) {
					hideProgressDialog();
					handleGoogleSignInResult(googleSignInResult);
					}
				});
			}
		}

	}

	/**
	 * if onResume() is called, the user has returned from SettingsActivity.
	 * Their user account must be disassociated and the LocationService must be stopped.
	 */
	@Override
	public void onResume() {
		super.onResume();

		// Get sharedPrefs
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		SharedPreferences.Editor editor = sharedPref.edit();

		// Stop LocationService
		Log.v(TAG, "Stopping location service...");
		stopService(new Intent(LoginActivity.this, LocationService.class));
		editor.putBoolean("gps_switch",false);

		// Remove saved loggedInUser
		Log.v(TAG, "Removing saved user...");
		editor.remove("loggedInUser");
		editor.remove("loggedInUserId");
		editor.remove("loggedInUserName");
		editor.remove("loggedInUserSk");
		editor.apply();

	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Log.v(TAG, "onActivityResult() called");

		// Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
		if (requestCode == RC_SIGN_IN) {
			Log.v(TAG, "requestCode was RC_SIGN_IN");
			GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
			handleGoogleSignInResult(result);
		}
		// User just logged out.  Don't log in again, stupid
		else if (requestCode == SIGN_OUT) {
			Log.v(TAG, "requestCode was SIGN_OUT");
			DONT_LOG_IN = true;
		}

	}

	private void authenticateWithTracmanServer(final Request request) throws Exception {
		// Needed to support TLS 1.1 and 1.2
//		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
//				TrustManagerFactory.getDefaultAlgorithm());
//		trustManagerFactory.init((KeyStore) null);
//		TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
//		if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
//			throw new IllegalStateException("Unexpected default trust managers:"
//					+ Arrays.toString(trustManagers));
//		}
//		X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

		OkHttpClient client = new OkHttpClient.Builder()
//				.sslSocketFactory(new TLSSocketFactory(), trustManager)
				.build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				Log.e(TAG, "Failed to connect to Tracman server!");
				showError(R.string.server_connection_error);
				e.printStackTrace();
			}

			@Override
			public void onResponse(Call call, Response res) throws IOException {
				if (!res.isSuccessful()) {
					showError(R.string.login_no_user_error);
					res.body().close();
					throw new IOException("Unexpected code: " + res);
				} else {
					Log.d(TAG, "Response code: " + res.code());
					String userString = res.body().string();
					System.out.println("Full response: " + userString);

					String userID, userName, userSK;
					try {
						JSONObject user = new JSONObject(userString);
						userID = user.getString("_id");
						userName = user.getString("name");
						userSK = user.getString("sk32");
						Log.v(TAG, "User retrieved with ID: " + userID);
					} catch (JSONException e) {
						Log.e(TAG, "Unable to parse user JSON: ", e);
						Log.e(TAG, "JSON String used: " + userString);
						userID = null;
						userName = null;
						userSK = null;
					}

					// Save user as loggedInUser
					SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
					SharedPreferences.Editor editor = sharedPref.edit();
					editor.putString("loggedInUser", userString);
					editor.putString("loggedInUserId", userID);
					editor.putString("loggedInUserName", userName);
					editor.putString("loggedInUserSk", userSK);
					editor.commit();

					startActivityForResult(new Intent(LoginActivity.this, SettingsActivity.class), SIGN_OUT);
				}


			}

		});
	}

	private void handleGoogleSignInResult(GoogleSignInResult result) {
		Log.d(TAG, "handleSignInResult:" + result.isSuccess());
		if (result.isSuccess()) { // Signed in successfully
			GoogleSignInAccount acct = result.getSignInAccount();
			try {

				// Build request
				Request request = new Request.Builder()
					.url(SERVER_ADDRESS+"login/app/google?id_token="+acct.getIdToken())
					.build();

				// Send to server
				authenticateWithTracmanServer(request);

			} catch (Exception e) {
				Log.e(TAG, "Error sending ID token to backend.", e);
			}
		} else {
			Log.e(TAG, "Failed to log in: "+result.getStatus().getStatusCode());
			if (result.getStatus().getStatusCode()!=4) {
				showError(R.string.google_connection_error);
			}
		}
	}

	public void signInWithPassword() {
		Log.d(TAG, "signInWithPassword() called");

		// Get params from form
		EditText emailText = (EditText)findViewById(R.id.login_email);
		String email = emailText.getText().toString();
		EditText passwordText = (EditText)findViewById(R.id.login_password);
		String password = passwordText.getText().toString();

		// Build formdata
		RequestBody formData = new FormBody.Builder()
			.add("email", email)
			.add("password", password)
			.build();

		// Build request
		Request request = new Request.Builder()
			.url(SERVER_ADDRESS+"login/app")
			.post(formData)
			.build();

		// Send formdata to endpoint
		try {
			Log.v(TAG, "Sending local login POST to server...");
			authenticateWithTracmanServer(request);
		} catch (Exception e) {
			Log.e(TAG, "Error sending local login to backend:",e);
		}

	}

	public void signInWithGoogle() {
		Log.v(TAG, "signInWithGoogle() called");
		Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
		startActivityForResult(signInIntent, RC_SIGN_IN);
	}

//	private void signInWithFacebook() {
//		Log.v(TAG, "signInWithFacebook() called");
//
//		//TODO: Facebook login to /login/app/facebook
//
//	}

//	private void signInWithTwitter() {
//		Log.v(TAG, "signInWithTwitter() called");
//
//		//TODO: Twitter login to /login/app/twitter
//
//	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		showError(R.string.disconnected);
		Log.d(TAG, "onConnectionFailed:" + connectionResult);
	}

	private void showProgressDialog() {
		if (mProgressDialog == null) {
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setMessage(getString(R.string.loading));
			mProgressDialog.setIndeterminate(true);
		}
		mProgressDialog.show();
	}

	private void hideProgressDialog() {
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.hide();
		}
	}

	private void showError(final int errorText) {
		final TextView errorTextView = (TextView)findViewById(R.id.login_error);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				errorTextView.setText(getText(errorText).toString());
			}
		});
	}

	@Override
	public void onClick(View v) {
		Log.v(TAG, "onClick() called");
		switch (v.getId()) {
			case R.id.login_button:
				Log.v(TAG, "Password login button pressed");
				signInWithPassword();
				break;
			case R.id.login_button_google:
				Log.v(TAG, "Google login button pressed");
				signInWithGoogle();
				break;
//			case R.id.login_button_facebook:
//				Log.v(TAG, "Facebook login button pressed");
//				signInWithFacebook();
//				break;
//			case R.id.login_button_twitter:
//				Log.v(TAG, "Twitter login button pressed");
//				signInWithTwitter();
//				break;
		}
	}

}