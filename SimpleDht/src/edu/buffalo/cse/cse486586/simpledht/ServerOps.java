package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

import android.content.ContentResolver;
import android.os.AsyncTask;
import android.util.Log;


/*=========================================================================
 * Class name   : SeverOps
 * Description  : Opens a socket and listens to incoming requests
 * Author		: RAJARAM RABINDRANATH
 *========================================================================*/
public class ServerOps extends AsyncTask<serverOps_params,Void, Void> 
{
	static String TAG = ServerOps.class.getName();
	
	protected Void doInBackground(serverOps_params... params) 
	{
		Message in_msg = null;
		Socket clientConnx = null;
		
		ServerSocket serverSocket = params[0].serverSocket;
		SimpleDhtProvider dhtProvider = params[0].dhtProvider;
		
		try
		{
			while(true) // for each message the client shall make a new connection therefore while(true)
			{
				try
		    	{
		    		clientConnx = serverSocket.accept();
			    	ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(clientConnx.getInputStream()));  
					in_msg = (Message)ois.readObject();  

					ois.close();
			    	clientConnx.close();
					// Create a new thread to process this message
					new MessageProcessor(in_msg,dhtProvider).start();
			    }
		    	catch(ClassNotFoundException ex)
		    	{
		    		ex.printStackTrace();
		    		Log.e(TAG,"received object is not of type message");
		    	}
		    }
		}
		catch(IOException ex)
		{
			Log.e(TAG,"Problems with server socket");
			ex.printStackTrace();
		}
		return null;
	}

}

/*=========================================================================
 * Class name   : SeverOps_params
 * Description  : An aux class that shall facilitate the creation of params
 * 					that are later passed to the ServerOps class
 * Author's		: RAJARAM RABINDRANATH
 *========================================================================*/
class serverOps_params
{
	ServerSocket serverSocket = null;
	SimpleDhtProvider dhtProvider = null;
	
	public serverOps_params(ServerSocket serverSocket, SimpleDhtProvider dhtProvider)
	{
		this.serverSocket =serverSocket;
		this.dhtProvider = dhtProvider;
	}
}
