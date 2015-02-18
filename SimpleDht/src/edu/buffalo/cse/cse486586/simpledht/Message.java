package edu.buffalo.cse.cse486586.simpledht;

import java.io.Serializable;
import java.util.Set;

import android.os.Parcel;
import android.os.Parcelable;

/*=========================================================================
 * Class name   : Message
 * Description  : A template for a message object
 * Author		: RAJARAM RABINDRANATH
 *=========================================================================*/
public class Message implements Serializable
{
	
	private static final long serialVersionUID = -6893921450147855832L;
	
	MessageType msgType =  null;
	String msgOrigin =  null;
	String query = null;
	Object payload = null;
	String originPort = null;
	
	private Message(MessageType msgType,String query, String myAvdNum, Object payload)
	{
		this.msgType = msgType;
		this.query = query;
		this.msgOrigin =  myAvdNum;
		this.originPort = Integer.toString((Integer.parseInt(this.msgOrigin)*2));
		this.payload =  payload;
	}
	
	/*=========================================================================
     * Function   : forwardMessage()
     * Description: Forward the request from predecessor to successor
     * Parameters : void
     * Return	  : String toPort 
     *=========================================================================*/
    public void forwardMessage(String toPort)
	{
		ClientOps forwardRequest = new ClientOps(new sendMsgParams(this,toPort));
		forwardRequest.start();
	}
	
    /*=========================================================================
     * Function   : sendMessage()
     * Description: Sends a message to successor -- could be anyone of the 12 
     * 				message types defined in MessageType enum
     * Parameters : a lot of them
     * Return	  : void
     *=========================================================================*/
    public static void sendMessage(MessageType msgType,String query,String avdNum,Object payload,String toPort)
	{
		Message respMsg = new Message(msgType,query,avdNum,payload);
		ClientOps sendRequest = new ClientOps(new sendMsgParams(respMsg,toPort));
		sendRequest.start();
	}
}

