package us.keithirwin.tracman;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.app.LoaderManager.LoaderCallbacks;

import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import android.os.Build;
import android.os.Bundle;
//import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity {//} implements
//        LoaderCallbacks<Cursor> {
	private static final String TAG = "LoginActivity";
	private static final int RC_SIGN_IN = 9001;

	// Id to identity READ_CONTACTS permission request
	//private static final int REQUEST_READ_CONTACTS = 0;

	/// Addresses, client IDs
	// Development
	private final String SERVER_ADDRESS = "https://dev.tracman.org/";
	private static final String GOOGLE_WEB_CLIENT_ID = "483494341936-hps4p2pcu3ctshjvqm3pqdbg0t0q281o.apps.googleusercontent.com";
	// Production
//	private final String SERVER_ADDRESS = "https://www.tracman.org/";
//	private static final String GOOGLE_WEB_CLIENT_ID = "483494341936-hrn0ms1tebgdtfs5f4i6ebmkt3qmo16o.apps.googleusercontent.com";


	// Keep track of the login tasks to ensure we can cancel it if requested.
	private EmailLoginTask mEmailAuthTask = null;
//    private GoogleLoginTask mGoogleAuthTask = null;

	// UI references.
	private AutoCompleteTextView mEmailView;
	private EditText mPasswordView;
	private View mProgressView;
	private View mLoginFormView;

	// OKHTTP client
	private OkHttpClient httpClient;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "created");

		// Build HTTP client
		httpClient = new OkHttpClient.Builder().build();

		// Configure sign-in to request the user's ID and basic profile, included in DEFAULT_SIGN_IN.
		// https://developers.google.com/identity/sign-in/android/sign-in#configure_google_sign-in_and_the_googlesigninclient_object
		GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
				.requestIdToken(GOOGLE_WEB_CLIENT_ID)
				.requestEmail()
				.build();
		// Build a GoogleSignInClient with the options specified by gso.
		final GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

		// From backup version:
		//mGoogleApiClient = new GoogleApiClient.Builder(this)
		//        .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
		//        .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
		//        .build();

		// Set up layout
		setContentView(R.layout.activity_login);

		// Set up form
		mEmailView = (AutoCompleteTextView) findViewById(R.id.login_email);
//        populateAutoComplete();

		mPasswordView = (EditText) findViewById(R.id.login_password);
		mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
				if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
					attemptEmailLogin();
					return true;
				}
				return false;
			}
		});

		// Set up Google sign-in button
		// https://developers.google.com/identity/sign-in/android/sign-in#add_the_google_sign-in_button_to_your_app
		SignInButton googleSignInButton = (SignInButton) findViewById(R.id.login_button_google);
		googleSignInButton.setStyle(SignInButton.SIZE_WIDE, SignInButton.COLOR_AUTO);
//        findViewById(R.id.login_button_google).setOnClickListener(this);

		// Button listeners
		Button mEmailSignInButton = (Button) findViewById(R.id.login_button);
		mEmailSignInButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				attemptEmailLogin();
			}
		});
		googleSignInButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				// https://developers.google.com/identity/sign-in/android/sign-in#start_the_sign-in_flow
				Intent signInIntent = mGoogleSignInClient.getSignInIntent();
				startActivityForResult(signInIntent, RC_SIGN_IN);
			}

		});
		//// TODO: Add fb, twitter logins
		////findViewById(R.id.login_button_facebook).setOnClickListener(this);
		////findViewById(R.id.login_button_twitter).setOnClickListener(this);

		mLoginFormView = findViewById(R.id.login_form);
		mProgressView = findViewById(R.id.login_progress);
	}

	@Override
	protected void onStart() {
		super.onStart();
		Log.d(TAG, "onStart() called");

		// Check for existing Google Sign In account, if the user is already signed in
		// the GoogleSignInAccount will be non-null.
		// https://developers.google.com/identity/sign-in/android/sign-in#check_for_an_existing_signed-in_user
		GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
		// updateUI(account);

//        GoogleSignIn.silentSignIn()
//            .addOnCompleteListener(this, new OnCompleteListener<GoogleSignInAccount>() {
//                @Override
//                public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
//                    handleGoogleSignInResult(task);
//                }
//            });

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

	// https://developers.google.com/identity/sign-in/android/sign-in#start_the_sign-in_flow
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		// Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
		if (requestCode == RC_SIGN_IN) {
			// The Task returned from this call is always completed, no need to attach
			// a listener.
			Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
			handleGoogleSignInResult(task);
		}

	}

	private void authenticateWithTracmanServer(final Request request) {

		httpClient.newCall(request).enqueue(new Callback() {
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

					String userID, userSK;
					try {
						JSONObject user = new JSONObject(userString);
						userID = user.getString("_id");
						userSK = user.getString("sk32");
						Log.v(TAG, "User retrieved with ID: " + userID);
					} catch (JSONException e) {
						Log.e(TAG, "Unable to parse user JSON: ", e);
						Log.e(TAG, "JSON String used: " + userString);
						userID = null;
						userSK = null;
					}

					// Save user as loggedInUser
					SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
					SharedPreferences.Editor editor = sharedPref.edit();
					editor.putString("loggedInUser", userString);
					editor.putString("loggedInUserId", userID);
					editor.putString("loggedInUserSk", userSK);
					editor.commit();

					startActivity(new Intent(LoginActivity.this, SettingsActivity.class));
//                    startActivityForResult(new Intent(LoginActivity.this, SettingsActivity.class), 1);
				}


			}

		});
	}

	private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
		Log.i(TAG, "handleGoogleSignInResult:");
		try {
			GoogleSignInAccount account = completedTask.getResult(ApiException.class);

			try {

				// Build request
				Request tracman_google_request = new Request.Builder()
						.url(SERVER_ADDRESS+"login/app/google?id_token="+account.getIdToken())
						.build();

				// Send to server
				authenticateWithTracmanServer(tracman_google_request);

			} catch (Exception e) {
				Log.e(TAG, "Error sending ID token to backend.", e);
			}

		} catch (ApiException e) {
			// The ApiException status code indicates the detailed failure reason.
			// Please refer to the GoogleSignInStatusCodes class reference for more information.
			Log.w(TAG, "signInResult:failed error: " + e);
			// Notify user
			showError(R.string.google_connection_error);

		}
	}

//    private void populateAutoComplete() {
//        if (!mayRequestLocation()) {
//            return;
//        }

//        getLoaderManager().initLoader(0, null, this);
//    }

	// TODO: Location services here instead
//    private boolean mayRequestLocation() {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
//            return true;
//        }
//        if (checkSelfPermission(READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
//            return true;
//        }
//        if (shouldShowRequestPermissionRationale(READ_CONTACTS)) {
//            Snackbar.make(mEmailView, R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE)
//                    .setAction(android.R.string.ok, new View.OnClickListener() {
//                        @Override
//                        @TargetApi(Build.VERSION_CODES.M)
//                        public void onClick(View v) {
//                            requestPermissions(new String[]{READ_CONTACTS}, REQUEST_READ_CONTACTS);
//                        }
//                    });
//        } else {
//            requestPermissions(new String[]{READ_CONTACTS}, REQUEST_READ_CONTACTS);
//        }
//        return false;
//    }

	/**
	 * Callback received when a permissions request has been completed.
	 */
	// TODO: Location services here instead
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        Log.d(TAG, "onRequestPermissionsResult() called");
//        if (requestCode == REQUEST_READ_CONTACTS) {
//            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                populateAutoComplete();
//            }
//        }
//    }

	/**
	 * Attempts to sign in or register the account specified by the login form.
	 * If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual login attempt is made.
	 */
	private void attemptEmailLogin() {
		Log.d(TAG, "attemptEmailLogin() called");
		if (mEmailAuthTask != null) {
			return;
		}

		// Reset errors.
		mEmailView.setError(null);
		mPasswordView.setError(null);

		// Store values at the time of the login attempt.
		String email = mEmailView.getText().toString();
		String password = mPasswordView.getText().toString();

		boolean cancel = false;
		View focusView = null;

		// Check if the user entered a valid email and password.
		if (TextUtils.isEmpty(email)) {
			mEmailView.setError(getString(R.string.error_field_required));
			focusView = mEmailView;
			cancel = true;
		} else if (!isValidEmail(email)) {
			mEmailView.setError(getString(R.string.error_invalid_email));
			focusView = mEmailView;
			cancel = true;
		} else if (TextUtils.isEmpty(password)) {
			mPasswordView.setError(getString(R.string.error_field_required));
			focusView = mPasswordView;
			cancel = true;
		}

		if (cancel) {
			// There was an error; don't attempt login and focus the first
			// form field with an error.
			focusView.requestFocus();
		} else {
			// Show a progress spinner, and kick off a background task to
			// perform the user login attempt.
			showProgress(true);
			mEmailAuthTask = new EmailLoginTask(email, password);
			mEmailAuthTask.execute((Void) null);
		}

	}
	private boolean isValidEmail(String email) {
		Log.d(TAG, "isValidEmail() called");
		// TODO: Better email validation
		return email.contains("@");
	}

	/**
	 * Attempts to sign in or register using the current android
	 * google account
	 */
//    private void attemptGoogleLogin() {
//        Log.d(TAG, "attemptGoogleLogin() called");
//        if (mGoogleAuthTask != null) {
//            return;
//        }
//
//        boolean cancel = false;
//        View focusView = null;
//
//        if (cancel) {
//            // There was an error; don't attempt login and focus the first
//            // form field with an error.
//            focusView.requestFocus();
//        } else {
//            // Show a progress spinner, and kick off a background task to
//            // perform the user login attempt.
//            showProgress(true);
//            mGoogleAuthTask = new GoogleLoginTask();
//            mGoogleAuthTask.execute((Void) null);
//        }
//
//    }


	/**
	 * Shows the progress UI and hides the login form.
	 */
	// TODO: Implement progress bar
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	private void showProgress(final boolean show) {
		// On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
		// for very easy animations. If available, use these APIs to fade-in
		// the progress spinner.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

			mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
			mLoginFormView.animate().setDuration(shortAnimTime).alpha(
					show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
				}
			});

			mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
			mProgressView.animate().setDuration(shortAnimTime).alpha(
					show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
				}
			});
		} else {
			// The ViewPropertyAnimator APIs are not available, so simply show
			// and hide the relevant UI components.
			mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
			mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
		}
	}

//    @Override
//    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
//        return new CursorLoader(this,
//                // Retrieve data rows for the device user's 'profile' contact.
//                Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
//                        ContactsContract.Contacts.Data.CONTENT_DIRECTORY), ProfileQuery.PROJECTION,
//
//                // Select only email addresses.
//                ContactsContract.Contacts.Data.MIMETYPE +
//                        " = ?", new String[]{ContactsContract.CommonDataKinds.Email
//                .CONTENT_ITEM_TYPE},
//
//                // Show primary email addresses first. Note that there won't be
//                // a primary email address if the user hasn't specified one.
//                ContactsContract.Contacts.Data.IS_PRIMARY + " DESC");
//    }

//    @Override
//    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
//        List<String> emails = new ArrayList<>();
//        cursor.moveToFirst();
//        while (!cursor.isAfterLast()) {
//            emails.add(cursor.getString(ProfileQuery.ADDRESS));
//            cursor.moveToNext();
//        }
//
//        addEmailsToAutoComplete(emails);
//    }

//    @Override
//    public void onLoaderReset(Loader<Cursor> cursorLoader) {
//
//    }

//    private void addEmailsToAutoComplete(List<String> emailAddressCollection) {
//        //Create adapter to tell the AutoCompleteTextView what to show in its dropdown list.
//        ArrayAdapter<String> adapter =
//                new ArrayAdapter<>(LoginActivity.this,
//                        android.R.layout.simple_dropdown_item_1line, emailAddressCollection);
//
//        mEmailView.setAdapter(adapter);
//    }


//    private interface ProfileQuery {
//        String[] PROJECTION = {
//                ContactsContract.CommonDataKinds.Email.ADDRESS,
//                ContactsContract.CommonDataKinds.Email.IS_PRIMARY,
//        };
//
//        int ADDRESS = 0;
//        int IS_PRIMARY = 1;
//    }

	/**
	 * Represents an asynchronous login/registration task used to authenticate
	 * the user.
	 */
	public class EmailLoginTask extends AsyncTask<Void, Void, Boolean> {

		private final String mEmail;
		private final String mPassword;

		EmailLoginTask(String email, String password) {
			mEmail = email;
			mPassword = password;
		}

		@Override
		protected Boolean doInBackground(Void... params) {

			// Build formdata
			RequestBody formData = new FormBody.Builder()
					.add("email", mEmail)
					.add("password", mPassword)
					.build();

			// Build request
			Request tracman_email_request = new Request.Builder()
					.url(SERVER_ADDRESS+"login/app")
					.post(formData)
					.build();

			// Send formdata to endpoint
			try {
				Log.i(TAG, "Sending local login POST to server...");
				authenticateWithTracmanServer(tracman_email_request);
				return true;
			} catch (Exception e) {
				Log.e(TAG, "Error sending local login to backend:",e);
				return false;
			}
		}

//        @Override
//        protected void onPostExecute(final Boolean success) {
//            mEmailAuthTask = null;
//            showProgress(false);
//
//            if (success) {
//                finish();
//            } else {
//                mPasswordView.setError(getString(R.string.error_incorrect_password));
//                mPasswordView.requestFocus();
//            }
//        }
//
//        @Override
//        protected void onCancelled() {
//            mEmailAuthTask = null;
//            showProgress(false);
//        }
	}

	/**
	 * Represents an asynchronous login/registration task used to authenticate
	 * the user.
	 */
//    public class GoogleLoginTask extends AsyncTask<Void, Void, Boolean> {
//
//        @Override
//        protected Boolean doInBackground(Void... params) {
//
//            // TODO: attempt authentication against a google servers servers.
//
//            // TODO: register the new account
//
//            return true;
//        }
//
//        @Override
//        protected void onPostExecute(final Boolean success) {
//            mGoogleAuthTask = null;
//            showProgress(false);
//
//            if (success) {
//                finish();
//            }
//        }
//
//        @Override
//        protected void onCancelled() {
//            mGoogleAuthTask = null;
//            showProgress(false);
//        }
//    }
//
//    void updateUI (GoogleSignInAccount account) {
//        if  (account!=null) {
//            Log.i(TAG, "updateUI() called with account");
//            // TODO: Go to Settings activity
//
//        }
//        else {
//            Log.i(TAG, "updateUI() called with no account");
//            // TODO: Tell the user something didn't work
//            ////showError(R.string.name_of_error_string);
//        }
//    }

}

