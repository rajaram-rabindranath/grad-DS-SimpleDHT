package edu.buffalo.cse.cse486586.simpledht;


/*=========================================================================
 * enum name   : MessageType
 * Description : a set of constants representing message types
 * Author	   : RAJARAM RABINDRANATH
 *=========================================================================*/
public enum MessageType
{
	joinMessage,
	peerListMessage,
	
	objectQueryMessage,
	objectQueryResponseMessage,
	
	GDumpQueryMessage,
	GDumpQueryResponseMessage,
	
	GDelReqMessage,
	GDelReqResponseMessage,
	
	ObjectDelReqMessage,
	ObjectDelReqResponseMessage,
	
	objectInsertMessage,
	objectInsertResponseMessage;
}
