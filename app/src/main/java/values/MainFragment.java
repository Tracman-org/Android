package values;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import us.keithirwin.tracman.R;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link MainFragment.OnMapButtonPressedListener} interface
 * to handle interaction events.
 * Use the {@link MainFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MainFragment extends Fragment {
	private static final String USER_NAME = "UserName";
	private static final String USER_ID = "UserId";
	private String mUserName, mUserId;

	private OnMapButtonPressedListener mButtonPressedListener;

	/**
	 * Use this factory method to create a new instance of
	 * this fragment using the provided parameters.
	 *
	 * @param userName Name of the logged-in user.
	 * @param userID ID of the logged-in user.
	 * @return A new instance of fragment MainFragment.
	 */
	public static MainFragment newInstance(String userName, String userID) {
		MainFragment fragment = new MainFragment();
		Bundle args = new Bundle();
		args.putString(USER_NAME, userName);
		args.putString(USER_ID, userID);
		fragment.setArguments(args);
		return fragment;
	}	public MainFragment() {}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			mUserName = getArguments().getString(USER_NAME);
			mUserId = getArguments().getString(USER_ID);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_main, container, false);

		if (mUserName!=null) {
			TextView tv = (TextView) view.findViewById(R.id.named_welcome);
			tv.setText(getString(R.string.named_welcome, " "+mUserName));
		}

		view.findViewById(R.id.tracpage_button).setOnClickListener( new View.OnClickListener() {
			public void onClick(View v) {
				showMap();
			}
		});

		return view;
	}

	public void showMap () {
		if (mButtonPressedListener != null) {
			mButtonPressedListener.showMap(mUserId);
		}
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		if (context instanceof OnMapButtonPressedListener) {
			mButtonPressedListener = (OnMapButtonPressedListener) context;
		} else {
			throw new RuntimeException(context.toString() + " must implement OnFragmentInteractionListener");
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mButtonPressedListener = null;
	}

	/**
	 * This interface must be implemented by activities that contain this
	 * fragment to allow an interaction in this fragment to be communicated
	 * to the activity and potentially other fragments contained in that
	 * activity.
	 * <p/>
	 * See the Android Training lesson <a href=
	 * "http://developer.android.com/training/basics/fragments/communicating.html"
	 * >Communicating with Other Fragments</a> for more information.
	 */
	public interface OnMapButtonPressedListener {
		void showMap(String UserId);
	}
}
