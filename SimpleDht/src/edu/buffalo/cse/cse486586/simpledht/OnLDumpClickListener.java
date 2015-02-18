package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class OnLDumpClickListener implements OnClickListener 
{

	private static final String TAG = OnTestClickListener.class.getName();
	private static final int TEST_CNT = 50;
	private static final String KEY_FIELD = "key";
	private static final String VALUE_FIELD = "value";

	private final TextView mTextView;
	private final ContentResolver mContentResolver;
	private final Uri mUri;

	public OnLDumpClickListener(TextView _tv, ContentResolver _cr) 
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
		new fetchLDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private class fetchLDataTask extends AsyncTask<Void, String, Void>
	{

		@Override
		protected Void doInBackground(Void... params) 
		{
			return null;
		}
		
		protected void onProgressUpdate(String...strings) 
		{
			mTextView.append(strings[0]);
			return;
		}

		private boolean testQuery()
		{
			try
			{
				Cursor resultCursor = mContentResolver.query(mUri, null,"@", null, null);
				if (resultCursor == null) 
				{
					Log.e(TAG, "Result null");
					throw new Exception();
				}

				int keyIndex = resultCursor.getColumnIndex(KEY_FIELD);
				int valueIndex = resultCursor.getColumnIndex(VALUE_FIELD);
					
				if (keyIndex == -1 || valueIndex == -1)
				{
					//Log.e(TAG, "Wrong columns");
					Log.e("window_shopper","Wrong columns");
					resultCursor.close();
					throw new Exception();
				}

				resultCursor.moveToFirst();

				// more number of rows than should be -- right
				if (!(resultCursor.isFirst() && resultCursor.isLast()))
				{
					Log.e(TAG, "Wrong number of rows");
					resultCursor.close();
					throw new Exception();
				}
				resultCursor.close();
			} 
			catch (Exception e) 
			{
				return false;
			}
			return true;
		}
	}
}
