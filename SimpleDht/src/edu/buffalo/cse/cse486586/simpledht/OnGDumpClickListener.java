package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;


public class OnGDumpClickListener implements OnClickListener
{
	private static final String TAG = OnTestClickListener.class.getName();
	private static final String KEY_FIELD = "key";
	private static final String VALUE_FIELD = "value";

	private final TextView mTextView;
	private final ContentResolver mContentResolver;
	private final Uri mUri;

	public OnGDumpClickListener(TextView _tv, ContentResolver _cr) 
	{
		mTextView = _tv;
		mContentResolver = _cr;
		mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
	}

	private Uri buildUri(String scheme, String authority) 
	{
		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.authority(authority);
		uriBuilder.scheme(scheme);
		return uriBuilder.build();
	}

	@Override
	public void onClick(View v)
	{
		new fetchGDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private class fetchGDataTask extends AsyncTask<Void, String, Void>
	{

		@Override
		protected Void doInBackground(Void... params) 
		{
			getGDump();
			return null;
		}
		
		protected void onProgressUpdate(String...strings) 
		{
			mTextView.append(strings[0]);
			return;
		}

		private boolean getGDump()
		{
			String result = "";
			try
			{
				
				Log.d("window_shopper","asking for GDUMP");
				Cursor resultCursor = mContentResolver.query(mUri, null,"*", null, null);
				if (resultCursor == null) 
				{
					Log.e(TAG, "Result null");
					throw new Exception();
				}

				
				int keyIndex = resultCursor.getColumnIndex(KEY_FIELD);
				int valueIndex = resultCursor.getColumnIndex(VALUE_FIELD);
				

				resultCursor.moveToFirst();
				while(resultCursor.moveToNext())
				{
					result += resultCursor.getString(keyIndex)+"::";
					result += resultCursor.getString(valueIndex)+"\n";
				}
				resultCursor.close();
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
				Log.d("window_shopper","OnGDump some problems are hard to solve");
				return false;
			}
			
			publishProgress(result);
			
			return true;
		}
	}
}
