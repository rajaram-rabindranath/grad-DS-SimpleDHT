package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import android.util.Log;


/*=========================================================================
 * Class name   : ClientOps
 * Description  : A thread that is used to send messages to successor
 * Author		: RAJARAM RABINDRANATH
 *=========================================================================*/
public class ClientOps extends Thread
{
	sendMsgParams msgParams = null;
	static final String TAG = ClientOps.class.getName();
	
	
	ClientOps(sendMsgParams msgParams)
	{
		this.msgParams = msgParams;
	}
	
	
	public void run()
	{
		Message out_msg =  msgParams.msg;
		String toWhom = msgParams.receiverPortNum;
		
		try
        {
			// need to give myself enough time to join the dht -- post boot up	
			if((out_msg.msgType == MessageType.joinMessage) && (!out_msg.msgOrigin.equals(SimpleDhtProvider.coord)))
			Thread.sleep(1000);
			
			Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),Integer.parseInt(toWhom));
			BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(out_msg);  
			bos.flush();
			oos.flush();
			
			oos.close();
			bos.close();
			socket.close();
			Log.d(TAG,"sent msg::"+out_msg.msgType+"::"+(Integer.parseInt(toWhom)/2));	
		}
        catch (UnknownHostException e) 
        {
        	e.printStackTrace();
            Log.e(TAG, "ClientTask UnknownHostException");
        } 
    	catch(StreamCorruptedException ex)
    	{
    		ex.printStackTrace();
    		Log.d(TAG," stream corrupted bull crap"+ex.getLocalizedMessage());
    	}
		catch(InterruptedException iex)
		{
			iex.printStackTrace();
			Log.d(TAG,"wait causing problems");
		}
        catch (IOException e) 
        {
        	e.printStackTrace();
            Log.e(TAG, "ClientTask socket IOException");
        }
	}
}


/*=========================================================================
 * Class name   : sendMsgParams
 * Description  : A aux class that helps build the parameters for the 
 * 					ClientOps class
 * Authors		: Rajaram Rabindranath
 *=========================================================================*/
class sendMsgParams
{
	Message msg =null;
	String receiverPortNum = null;
	
	public sendMsgParams(Message msg,String receiverPortNum)
	{
		this.msg = msg;
		this.receiverPortNum = receiverPortNum;
	}
}