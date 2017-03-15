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
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.TlsVersion;

public class LoginActivity extends AppCompatActivity implements
		GoogleApiClient.OnConnectionFailedListener,
		View.OnClickListener {
//	private static final String TAG = "LoginActivity";
	private static final int RC_SIGN_IN = 9001;

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
//		Log.v(TAG, "Created...");

		// Set up layout
		setContentView(R.layout.activity_login);
		setTitle(R.string.login_name);
		TextView tv = (TextView) findViewById(R.id.login_description);
		tv.setMovementMethod(LinkMovementMethod.getInstance());

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
		SignInButton signInButton = (SignInButton) findViewById(R.id.google_sign_in_button);
		signInButton.setStyle(SignInButton.SIZE_WIDE, SignInButton.COLOR_AUTO);

		// Button listeners
		findViewById(R.id.google_sign_in_button).setOnClickListener(this);
	}

	@Override
	public void onStart() {
		super.onStart();
//		Log.v(TAG, "Started. Checking for intent method");

		if (getIntent().hasExtra("method")) {
//			Log.v(TAG, "Intent has method extra");
			if (getIntent().getStringExtra("method").equals("signOut")) {
//				Log.d(TAG, "Got intent to sign out");
			}
		} else { // Try to sign in
//			Log.v(TAG, "Trying to sign in...");
			OptionalPendingResult<GoogleSignInResult> opr = Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);
			if (opr.isDone()) {
				// If the user's cached credentials are valid, the OptionalPendingResult will be "done"
				// and the GoogleSignInResult will be available instantly.
//				Log.d(TAG, "Got cached sign-in");
				GoogleSignInResult result = opr.get();
				handleSignInResult(result);
			} else {
				// If the user has not previously signed in on this device or the sign-in has expired,
				// this asynchronous branch will attempt to sign in the user silently.  Cross-device
				// single sign-on will occur in this branch.
				showProgressDialog();
				opr.setResultCallback(new ResultCallback<GoogleSignInResult>() {
					@Override
					public void onResult(GoogleSignInResult googleSignInResult) {
						hideProgressDialog();
						handleSignInResult(googleSignInResult);
					}
				});
			}
		}

	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		// Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
		if (requestCode == RC_SIGN_IN) {
			GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
			handleSignInResult(result);
		}
	}

	private void AuthenticateGoogle(final String token) throws Exception {

		// Needed to support TLS 1.1 and 1.2
		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
				TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init((KeyStore) null);
		TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
		if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
			throw new IllegalStateException("Unexpected default trust managers:"
					+ Arrays.toString(trustManagers));
		}
		X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

		OkHttpClient client = new OkHttpClient.Builder()
				.sslSocketFactory(new TLSSocketFactory(), trustManager)
				.build();

		Request request = new Request.Builder()
				.url(SERVER_ADDRESS+"auth/google/idtoken?id_token="+token)
				.build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
//				Log.e(TAG, "Failed to connect to server: " + SERVER_ADDRESS + "auth/google/idtoken?id_token=" + token);
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
//					Log.d(TAG, "Response code: " + res.code());
					String userString = res.body().string();
					System.out.println("Full response: " + userString);

					String userID, userName, userSK;
					try {
						JSONObject user = new JSONObject(userString);
						userID = user.getString("_id");
						userName = user.getString("name");
						userSK = user.getString("sk32");
//						Log.v(TAG, "User retrieved with ID: " + userID);
					} catch (JSONException e) {
//						Log.e(TAG, "Unable to parse user JSON: ", e);
//						Log.e(TAG, "JSON String used: " + userString);
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

					startActivity(new Intent(getBaseContext(), SettingsActivity.class));
			}
		}

		});

	}

	private void handleSignInResult(GoogleSignInResult result) {
//		Log.d(TAG, "handleSignInResult:" + result.isSuccess());
		if (result.isSuccess()) { // Signed in successfully
			GoogleSignInAccount acct = result.getSignInAccount();
			String googleToken = acct.getIdToken();
//			Log.v(TAG, "Google token: " + googleToken);
			try {
				AuthenticateGoogle(acct.getIdToken());
			}  catch (Exception e) {
//				Log.e(TAG, "Error sending ID token to backend.", e);
			}
		} else {
//			Log.e(TAG, "Failed to log in: "+result.getStatus().getStatusCode());
			if (result.getStatus().getStatusCode()!=4) {
				showError(R.string.google_connection_error);
			}
		}
	}

	private void signIn() {
		Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
		startActivityForResult(signInIntent, RC_SIGN_IN);
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		showError(R.string.disconnected);
//		Log.d(TAG, "onConnectionFailed:" + connectionResult);
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
		switch (v.getId()) {
			case R.id.google_sign_in_button:
				signIn();
				break;
		}
	}

}