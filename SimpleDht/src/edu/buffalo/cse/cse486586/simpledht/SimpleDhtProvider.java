package edu.buffalo.cse.cse486586.simpledht;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;


/*=========================================================================
 * Class name   : SimpleDHTProvider
 * Description  : Handles the content provider for the SimpleDhtActivity
 * 					application
 * Author		: RAJARAM RABINDRANATH
 *=========================================================================*/
public class SimpleDhtProvider extends ContentProvider 
{

	final String uri = "content://edu.buffalo.cse.cse486586.simpledht.provider";
	final Uri simpleDHTURI = Uri.parse(uri);
	static final String TAG = SimpleDhtProvider.class.getName();
	/**
	 * database details
	 */
	static final String dbName 	="database_simpleDHT_sqlite";
	static final String tableName	= "data_dht_well";
	static int dbVersion = 1; // overkill -- for prj2
	private static SQLiteDatabase sqliteDB = null;	
	static dataAccess db_conduit = null; 
	static final String KEY_FIELD = "key";
	static final String VALUE_FIELD = "value";
	
	
	
	
	Context simpleDHTContext = null;
	/**
	 * comm arrangments
	 */
	int SERVER_PORT = 10000;
	String myAVDnum = null;
	node myDetails = null;	
	static final String coord = "5554";	
	

	/**
	 * Chord arrangements
	 */
	node pred_node = null,succ_node = null;
	boolean isFirstNode = false;
	ChordMaster chord = new ChordMaster(); 
	

	/**
	 * Query levels -- identifiers
	 */
	
	final String query_all = "*";
	final String query_mine = "@";
	final String query_particular = "-";
	
	final String delete_all =  query_all;
	final String delete_mine =  query_mine;
	final String delete_particular = query_particular;  
	
	/**
	 * Locks for distributed search/insert/query/delete
	 * for synchronized response to the app that requests
	 */
	Lock _GDump_Lock = null;
	Lock _insert_Lock = null; // this lock does not use the records_Requested Hashtable 
	Lock _query_Lock = null;
	Lock _GDel_Lock = null;
	Lock _delObj_Lock = null;
	
	final Integer OBJECT_DOES_NOT_EXIST = 99999;
	
	
	/*=========================================================================
	 * Class name   : Lock
	 * Description  : inner class that has template for the many locks that
	 * 					shall be used by this application to handle user requests					
	 * Author		: RAJARAM RABINDRANATH
	 *=========================================================================*/
    class Lock
	{
		Boolean isGood = Boolean.valueOf(false);
		Hashtable<String, String>records =  null;
		Integer rows_affected =  0;
		
		public Lock(){}
		
		public void setGood(Boolean goodness)
		{
			isGood = goodness;
		}
	}
	
	
	/*=========================================================================
	 * Class name   : dataAccess
	 * Description  : a inner classextends SQLiteOpenHelper 
	 * 					Sets up a database in sqlite					
	 * Author		: RAJARAM RABINDRANATH
	 *=========================================================================*/
    private class dataAccess extends SQLiteOpenHelper 
    {
    		final String sqlStatement_CreateTable = "create table "+tableName+"( key text not null,"+" value text not null);";
    		final String TAG = dataAccess.class.getName();
    		
    		public dataAccess(Context context)
    		{
    			super(context,dbName, null,dbVersion);
    		}

    		/*
    		 *get called when getWriteable database is called
    		 */
    		public void onCreate(SQLiteDatabase sqliteDB) 
    		{
    			sqliteDB.execSQL(sqlStatement_CreateTable); // creates a table
    			Log.e("pigtail","CREATING TABLE");
    		}

    		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) 
    		{
    			Log.e(TAG,"I have been asked to upgrade .. don't know what to do");
    		}    	
    }

	
    /*=========================================================================
     * Function   : onCreate()
     * Description: setsup the SimpleDhtProvider
     * Parameters : void
     * Return	  : boolean
     *=========================================================================*/
    public boolean onCreate() 
    {
		simpleDHTContext = getContext();
		db_conduit = new dataAccess(simpleDHTContext);
		
		// init all locks
		_GDump_Lock  = new Lock();
		_insert_Lock = new Lock();
		_query_Lock = new Lock();
		_GDel_Lock = new Lock();
		_delObj_Lock = new Lock();
		
		// permissions to be writable
		sqliteDB = db_conduit.getWritableDatabase();
		
		if(sqliteDB == null)
		{
			Log.e(TAG,"COULD NOT CREATE DATABASE!");
			return false;
		}
		
		
		// Who am i ? .. well
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        myAVDnum = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(myAVDnum) * 2));
        
        // need to wrap all my details in a node object for self awareness
        myDetails =  new node(myPort,myAVDnum);
        
        Log.d(TAG,"I am <avdnum> ::"+myAVDnum);
        // send join requests
	    Message.sendMessage(MessageType.joinMessage,null,myAVDnum,null,Integer.toString((Integer.parseInt(coord))*2));
		
		
		/**
		 * Start server thread
		 */
		try
		{
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			
            new ServerOps().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new serverOps_params(serverSocket,this));
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
			Log.e(TAG,"Cannot create serversocket");
		}
		return true;	
    }

 
        
    /*=========================================================================
     * Function   : getType()
     * Description: NONE
     * Parameters : Uri
     * Return	  : String
     *=========================================================================*/
    public String getType(Uri uri) 
    {
        return null;
    }

    
    /*=========================================================================
     * Function   : doesChordExist()
     * Description: indicates the existence of a chord or otherwise
     * Parameters : void
     * Return	  : boolean
     *=========================================================================*/
    private boolean doesChordExist()
    {
    	if(succ_node ==  null) return false;
    	if(myDetails.avdNum.equals(succ_node.avdNum)) return false;
    	return true;
    }
    
    
    /*=========================================================================
     * Function   : is_inMyDomain()
     * Description: checks if the hashedKey received as input belongs
     * 				this node partition
     * Parameters : String hashedKey (Hashed using SHA-1)
     * Return	  : boolean 
     *=========================================================================*/
    private boolean is_inMyDomain(String hashedKey)
    {
    	
    	String my_node_id = myDetails.node_id;
    	
    	// takes care of special case -- am i the only node on chord
    	if(!doesChordExist())
		{
    		Log.e(TAG,"there is no chord");
    		return true;
		}
    
    	/**
    	 * I am not the only node on the chord 
    	 */
		if((hashedKey.compareTo(pred_node.node_id)>0) && (hashedKey.compareTo(my_node_id)<=0))
    	{
    		return true;
    	}
    	else if(isFirstNode()) //special cases
    	{
    		Log.e(TAG,"I am first node --- kick ass");
    		/* object fall between 0 and pred*/
    		if(hashedKey.compareTo(pred_node.node_id) >0)return true;
    		/* object falls between 0 and me */
    		else if(hashedKey.compareTo(my_node_id) <=0)return true;
    	}
	    
    	return false;
    }
    
    
    /*=========================================================================
     * Function   : isFirstNode()
     * Description: verifies if it is the first node
     * Parameters : void
     * Return	  : boolean 
     *=========================================================================*/
    private boolean isFirstNode()
    {
    	// if my pred's node_id is > than mine then --- i am first node
    	if(myDetails.node_id.compareTo(pred_node.node_id) < 0)
    	{
    		return true;
    	}
    	return false;
    }
    
    
    /*=========================================================================
     * Function   : insert
     * Description: this is an overloaded method which insets the givn KV_pair
     * 				into the sqlite table-- shall be called programmatically
     * Parameters : String[] KV_pair
     * Return	  : boolean
     *=========================================================================*/
    public boolean insert(String[] KV_pair)
    {
    	boolean reslt = false;
    	
    	ContentValues newValues = null;
    	
    	// has the key and check if the node belongs 
    	try
    	{
    		String hashedKey =	ChordMaster.genHash(KV_pair[0]);
			
			// does this object belong to my domain
			if(is_inMyDomain(hashedKey))
			{
				newValues = new ContentValues();
				newValues.put(KEY_FIELD,KV_pair[0]);
				newValues.put(VALUE_FIELD,KV_pair[1]);
			
				
				// i am responsible for this entry
				long rowID = sqliteDB.insert(tableName, "", newValues);
		        if(rowID < 0)
		        {
		        	Log.e(TAG+" insert","INSERT FAIL");
		        	return reslt; //FIXME-- interpretation problems
		        }
		        Log.v(TAG, "ins:"+KV_pair[0]+"::"+KV_pair[1]+" ---  "+rowID); // notify user debug statement
		        reslt = true;
			}
    	}
    	catch(NoSuchAlgorithmException nsa)
    	{
    		nsa.printStackTrace();
    		Log.e(TAG,"no such algorithms");
    		return false; //FIXME -- interpretation problems
    	}
    	return reslt;
    }
    
    /*=========================================================================
     * Function   : insert
     * Description: Inserts given KV_pair into the sqlite database
     * Parameters : Uri uri, ContentValues values
     * Return	  : Uri 
     *=========================================================================*/
    public Uri insert(Uri uri, ContentValues values) 
    {
    	String[] KV_pair ={(String)values.get(KEY_FIELD),(String)values.get(VALUE_FIELD)};
    	
    	// am i this Object's owner
		if(insert(KV_pair));
		else // forward this <key,value> pair to successor
		{
			synchronized (_insert_Lock)
			{
				Message.sendMessage(MessageType.objectInsertMessage,null, myAVDnum,KV_pair,succ_node.portNum);
				Log.d(TAG,"the object given does not belong to my domain");
				try
				{
					// waiting for some peer to say -- I have inserted it
					_insert_Lock.wait();
				}
				catch(InterruptedException iex)
				{
					Log.e(TAG,"have encountered an exception when waiting");
					iex.printStackTrace();
				}
				_insert_Lock.setGood(false);; //have to set it false for next run
			} // synchronized blk
		}// else
		getContext().getContentResolver().notifyChange(uri, null);
        return simpleDHTURI;
    }

    
    
    /*=========================================================================
     * Function   : delete
     * Description: handles delete requests generated programmatically, requests
     * 				coming from the predecessor
     * Parameters : String del_lvl,String _param
     * Return	  : Uri 
     *=========================================================================*/
    public Integer delete(String del_lvl,String _param)
    {
    	int retVal = 0;
    	String hashedKey = null;
    	
    	if(del_lvl.equals(delete_all)) 
    	{
    		retVal =  sqliteDB.delete(tableName,null,null);
    	}
    	else if(del_lvl.equals(delete_particular))
    	{
    		try
    		{
    			hashedKey = ChordMaster.genHash(_param) ;
    			if(is_inMyDomain(hashedKey))
	    		{
	    			String[] delArgs={_param};
	    			retVal =  sqliteDB.delete(tableName,"key=?",delArgs);
	    			if(retVal == 0) retVal = OBJECT_DOES_NOT_EXIST;
	    		}
    		}
    		catch(NoSuchAlgorithmException nex)
    		{
    			nex.printStackTrace();
    		}
    	}
    	else
    	{
    		Log.e(TAG,"wrong del_lvl sent"+del_lvl);
    	}
    	return retVal;
    }
    
    /*=========================================================================
     * Function   : query()
     * Description: Query for an object in the database -- overloaded method
     * Parameters : String queryLevel,Object _param
     * Return	  : Hashtable<String,String> 
     *=========================================================================*/
    public Hashtable<String,String> query(String queryLevel,Object _param)
    {
    	Hashtable<String, String> queryResult = null;
    	String hashedKey = null;
    	String query = null;
    	
    	if(queryLevel.equals(query_all)) 
    	{
    		query ="select * from "+tableName;
    		Cursor cursor =  sqliteDB.rawQuery(query,null);
    		queryResult = unpack_cursor(cursor);
    		
    		
    		// need to append only if both are not null
    		if(_param != null && queryResult != null)
    		{
	    		appendRecords(queryResult,(Hashtable<String, String>)_param);
	    		queryResult = (Hashtable<String, String>)_param;
    		}
    		else if(queryResult == null) queryResult = (Hashtable<String, String>)_param;
    	}
    	else if(queryLevel.equals(query_particular))
    	{
    		
    		try
    		{
    			hashedKey = ChordMaster.genHash((String)_param) ;
    			if(is_inMyDomain(hashedKey))
	    		{
	    			String[] queryArgs={(String)_param};
	    			Log.d(TAG,"Looking for::"+queryArgs[0]);
	    			query = "select * from "+tableName+" where key=?";
	    			Cursor cursor =  sqliteDB.rawQuery(query,queryArgs);
	    			if(cursor == null) Log.e(TAG,"I am fucked!");
	    			else
	    			{
	    				queryResult = unpack_cursor(cursor);
	    			}
	    		}
    		}
    		catch(NoSuchAlgorithmException nex)
    		{
    			nex.printStackTrace();
    		}
    	}
    	else
    	{
    		Log.e(TAG,"wrong queryLevel send"+queryLevel);
    	}
		return queryResult;
    }
    
    /*=========================================================================
     * Function   : query
     * Description: Query the database to fetch the requested items
     * Parameters : Uri uri, String[] projection, String selection, 
     * 				String[] selectionArgs,String sortOrder
     * Return	  : Cursor 
     *=========================================================================*/
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,String sortOrder) 
    {
    	SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
		queryBuilder.setTables(tableName);
        sqliteDB = db_conduit.getReadableDatabase();
        Cursor queryResult = null;
        String query = null;
        //String hashedKey = null;
        String[] colnames={"key","value"};
        MatrixCursor matCursor =  null;
        
        // debug mandi
        String countSize = "select * from "+tableName;
    	Cursor n = sqliteDB.rawQuery(countSize,null);
    	Log.d(TAG,"rowCount::"+n.getCount());
    	
    	// if there are no other nodes in the dht
    	if(!doesChordExist())
    	{
    		if(selection.equals(query_all))selection = query_mine;
    	}
    	
    	if(selection.equals(query_all))
        {
    		//Log.d(TAG,"someone asked for GDUMP ? inside query method");
    		query = "select * from "+tableName;
        	queryResult = sqliteDB.rawQuery(query,null);
        	queryResult.moveToFirst();
        	
        	_GDump_Lock.records = unpack_cursor(queryResult);
        	
        	// send GDUMP request message to successor
        	Message.sendMessage(MessageType.GDumpQueryMessage,query_all,myDetails.avdNum,_GDump_Lock.records,succ_node.portNum);
        	
    		synchronized (_GDump_Lock) 
    		{
    			try
        		{
					Log.d(TAG,"--- waiting for the GDUMP ----");
    				_GDump_Lock.wait();// wait for successor to respond
    			}
        		catch(InterruptedException iex)
        		{
        			iex.printStackTrace();
        			Log.e(TAG,"was waiting on GDUMP");
        		}
    			Log.d(TAG,"---- Have recvd GDUMP ----");
    			matCursor = construct_MatrixCursor(_GDump_Lock.records,colnames);
    		}
		}
        else if(selection.equals(query_mine))
        {
        	query = "select * from "+tableName;
        	queryResult = sqliteDB.rawQuery(query,null);
        	matCursor = construct_MatrixCursor(queryResult,colnames);
        }
        else // query for a particular object
        {	
        	
        	Log.d(TAG,"Tester looking for::"+selection);
        	query = "select * from "+tableName+" where key=?";
        	
        	try
        	{
        		String hashedKey  =  ChordMaster.genHash(selection);
	        	
	    		if(is_inMyDomain(hashedKey))
	        	{
	    			String[] queryArgs = {selection};
	        		queryResult = sqliteDB.rawQuery(query,queryArgs);
	        		Log.e(TAG,"queryResult size"+queryResult.getCount());
	        		matCursor =  construct_MatrixCursor(queryResult, colnames);
	        	}
	        	else // this object does not belong to my domain 
	        	{
	        		Message.sendMessage(MessageType.objectQueryMessage,query_particular,myAVDnum,selection,succ_node.portNum);
	        		synchronized (_query_Lock)
	        		{
	        			try
	        			{
	        				Log.d(TAG,"query for a key waiting on successor");
	        				_query_Lock.wait();
	        			}
	        			catch(InterruptedException iex)
	        			{
	        				iex.printStackTrace();
	        				Log.e(TAG,"Interrupted Exception waiting on object query return");
	        			}
	        			Log.d(TAG,"successor has responded");
	        			Log.d(TAG,"size of hash returned query"+_query_Lock.records.size());
	        			matCursor =  construct_MatrixCursor(_query_Lock.records, colnames);
					}// synchronized
	        	}//
        	}
        	catch(NoSuchAlgorithmException nex)
        	{
        		nex.printStackTrace();
        	}
    	}
        
    	// debug code that tell us state
       if(matCursor == null) 
       {
	    	Log.d(TAG,"Query Failure ? -- matcursor null");
	    	return null;
       }   
       else
       {
    	   matCursor.moveToFirst();
           Log.d(TAG,"num rows returned_returned cursor::"+matCursor.getCount());
           Log.d(TAG,"=========== MAT CUSOR ========");
           printCursor(matCursor);
           matCursor.moveToFirst();
       }
        					
       if(queryResult == null)
       {
    	   Log.d(TAG,"Query Failure ? -- queryResult null");
       }
       else
       {
    	   queryResult.moveToFirst();
           Log.d(TAG,"num rows returned_my cursor::"+queryResult.getCount());
           Log.d(TAG,"=========== MY CUSOR ========");
           printCursor(queryResult);
        }
        
        // make sure that potential listeners are getting notified
        matCursor.setNotificationUri(getContext().getContentResolver(), uri);
        Log.v(TAG+" query", selection);
        return matCursor;
    }
    
    
    /*=========================================================================
     * Function   : printCursor
     * Description: Prints the cursor object's contents
     * Parameters : Cursor
     * Return	  : void
     *=========================================================================*/
    private void printCursor(Cursor cursor)
    {
    	if(cursor == null) return;
    	if(cursor.getCount() == 0) return;
    	
    	cursor.moveToFirst();
    	int index = 1;
    	int keyIndex = cursor.getColumnIndex(KEY_FIELD);
		int valueIndex = cursor.getColumnIndex(VALUE_FIELD);

		Log.d(TAG,"row_"+index+"::"+cursor.getString(keyIndex));
		index++;
		while(cursor.moveToNext())
    	{
    		Log.d(TAG,"row_"+index+"::"+cursor.getString(keyIndex)+":"+cursor.getString(valueIndex));
    		index++;
    	}
    	cursor.moveToFirst();
    	cursor.close();
    	return;
    }
    
    /*=========================================================================
     * Function   : appendRecords()
     * Description: Appends records from one hashtable to another
     * Parameters : Hashtables from <my LDUMP> & to <GDUMP from predecessor>
     * Return	  : void
     *=========================================================================*/
    void appendRecords(Hashtable<String,String> LDUMP,Hashtable<String,String> GDUMP)
    {	
    	/**
		 * GDUMP from pred and MY LDUMP are null -- do-nothing
		 * My LDUMP is null -- nothing to append
		 * GDUMP is null -- just make "to" <LDUMP> = "from"  <GDUMP>
     	*/	
    	Set<String> keys = LDUMP.keySet();
    	for(String key:keys)
    	{
    		GDUMP.put(key,LDUMP.get(key));
    	}
	
    	return;
    }
    
    /*=========================================================================
     * Function   : update
     * Description: 
     * Parameters : Uri uri, String[] projection, String selection, 
     * 				String[] selectionArgs,String sortOrder
     * Return	  : Cursor 
     *=========================================================================*/
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) 
    {
        return 0;
    }

    
       
    /*=========================================================================
	 * Function   : delete
	 * Description: deletes the KV_pair from the sqlite database
	 * Parameters : Uri uri, String selection,String[] selectionArgs
	 * Return	  : int
	 *=========================================================================*/
	public int delete(Uri uri, String selection, String[] selectionArgs) 
	{
		int retVal = 0;
		
		if(!doesChordExist())
		{
			if(selection.equals(delete_all))selection = delete_mine;
		}
		
		if(selection.equals(delete_all)) // delete all in DHT
		{
		 	retVal = sqliteDB.delete(tableName, null, null);
		 	
		 	Message.sendMessage(MessageType.GDelReqMessage,delete_all,myAVDnum,null,succ_node.portNum);
		 	
		 	synchronized (_GDel_Lock) 
		 	{
		 		try
		 		{
		 			_GDel_Lock.wait();
		 		}
		 		catch(InterruptedException iex)
		 		{
		 			iex.printStackTrace();
		 		}
		 		retVal = _GDel_Lock.rows_affected;
		 		Log.d(TAG,"All peers have deleted");
			}
		}
		else if(selection.equals(delete_mine)) // delete all of mine
		{
	    	retVal = sqliteDB.delete(tableName,null, null);
		}
		else // delete_particular
		{
			try
			{
				Log.v(TAG,"deleting::"+selection);
				String queryArgs[]={selection};
				String hashedKey = ChordMaster.genHash(selection);
				if(is_inMyDomain(hashedKey))
				{
					retVal = sqliteDB.delete(tableName,"key=?",queryArgs);
				}
				else // not in my domain
				{
					Message.sendMessage(MessageType.ObjectDelReqMessage,delete_particular,myAVDnum,selection, succ_node.portNum);
					synchronized (_delObj_Lock)
					{
						try
	    				{
	    					Log.v(TAG,"Waiting on successor");
	    					_delObj_Lock.wait();
	    				}
	    				catch(InterruptedException iex)
	    				{
	    					iex.printStackTrace();
	    				}
	    				
	    				retVal = _delObj_Lock.rows_affected;
	    				if(retVal == OBJECT_DOES_NOT_EXIST)
	    					Log.v(TAG,"CANNOT DELETE OBJ DOES NOT EXIST"+retVal);        					
	    				else
	    					Log.v(TAG,"Stop waiting deleted"+retVal);
	    			}
				}
			}
			catch(NoSuchAlgorithmException nex)
			{
				nex.printStackTrace();
			}
			
		}
		return retVal;
	}



	/*=========================================================================
     * Function   : unpack_cursor
     * Description: copies the contents of a Cursor object into a Hashtable
     * Parameters : 
     * Return	  : Hashtable<String,String> 
     *=========================================================================*/
    Hashtable<String, String> unpack_cursor(Cursor cursor)
    {
    	Hashtable<String, String> result = null;
    	if(cursor == null) return null;
    	else if(cursor.getCount() == 0) return null;
    	else
    	{
    		result= new Hashtable<String, String>();
	    	cursor.moveToFirst(); 
	    	int keyIndex = cursor.getColumnIndex(KEY_FIELD);
			int valueIndex = cursor.getColumnIndex(VALUE_FIELD);
			result.put(cursor.getString(keyIndex), cursor.getString(valueIndex));
			while (cursor.moveToNext()) 
	    	{
	    		result.put(cursor.getString(keyIndex), cursor.getString(valueIndex));
	    	}
    	}
    	return result;
    }
    
    /*=========================================================================
     * Function   : unpack_cursor (overloaded method)
     * Description: copies the content of a cursor object into the given 
     * 				Hashtable<String,String>
     * Parameters : Cursor cursor, Hashtable<String,String> result
     * Return	  : Hashtable<String,String> 
     *=========================================================================*/
    Hashtable<String, String> unpack_cursor(Cursor cursor,Hashtable<String, String> result)
    {
    	if(cursor == null) return null;
    	cursor.moveToFirst(); 
    	if(result == null)
    	result = new Hashtable<String, String>();
    	
    	int keyIndex = cursor.getColumnIndex(KEY_FIELD);
		int valueIndex = cursor.getColumnIndex(VALUE_FIELD);
		while (cursor.moveToNext()) 
    	{
    		result.put(cursor.getString(keyIndex), cursor.getString(valueIndex));
    	}
    	return result;
    }
    
    
    /*=========================================================================
     * Function   : construct_MatrixCursor
     * Description: Given a cursor  constructs a MatrixCursor with contents of
     * 				the former
     * Parameters : Cursor records,String[] columnNames
     * Return	  : MatrixCursor
     *=========================================================================*/
    private MatrixCursor construct_MatrixCursor(Cursor records,String[] columnNames)
    {
    	if(records == null || records.getCount() == 0) return null;
    	String[] rowItem = new String[2];
    	MatrixCursor matCursor = new MatrixCursor(columnNames);
    	records.moveToFirst();
    	
    	int keyIndex = records.getColumnIndex(KEY_FIELD);
		int valueIndex = records.getColumnIndex(VALUE_FIELD);
		
		rowItem[0] = records.getString(keyIndex);
		rowItem[1] = records.getString(valueIndex);
		matCursor.addRow(rowItem);
		
		while(records.moveToNext())
    	{
    		rowItem[0] = records.getString(keyIndex);
    		rowItem[1] = records.getString(valueIndex);
    		matCursor.addRow(rowItem);
    	}
		matCursor.moveToFirst();
		records.moveToFirst();
		return matCursor;
    }
    
    /*=========================================================================
     * Function   : construct_MatrixCursor(overloaded method)
     * Description: Given a Hashtable construct a matrix cursor with content
     * 					of the former
     * Parameters : Hashtable<String, String> records,String[] columnNames
     * Return	  : Cursor 
     *=========================================================================*/
    private MatrixCursor construct_MatrixCursor(Hashtable<String, String> records,String[] columnNames)
    {
    	if(records == null) return null;
    	MatrixCursor matCursor = new MatrixCursor(columnNames);
    	String[] rowItem = new String[2];
    	Set<String> keys = records.keySet();
    	for(String key:keys)
    	{
    		rowItem[0] = key;
    		rowItem[1] = records.get(rowItem[0]);
    		matCursor.addRow(rowItem);
    	}
    	return matCursor;
    }
}