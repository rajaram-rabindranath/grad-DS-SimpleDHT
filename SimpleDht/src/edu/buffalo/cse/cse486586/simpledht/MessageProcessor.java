package edu.buffalo.cse.cse486586.simpledht;

import java.util.Hashtable;



import android.util.Log;

/*=========================================================================
 * Class   	  : MessageProcessors
 * Description: A thread that is kick started in the ServerOps class to
 * 				handle incoming request/responses
 * Author	  : RAJARAM RABINDRANATH
 *=========================================================================*/
public class MessageProcessor extends Thread 
{
	Message msg = null;
	SimpleDhtProvider dhtProvider = null;
	static String TAG = MessageProcessor.class.getName();
	// query string
	static String query_all = "*";
	static String query_particular = "-";
	
	public MessageProcessor(Message msg,SimpleDhtProvider dhtProvider)
	{
		this.msg = msg;
		this.dhtProvider = dhtProvider;
	}
	
	
	/*=========================================================================
     * Function   : run
     * Description: handles a received message and takes the appropriate actions
     * Parameters : void
     * Return	  : void 
     *=========================================================================*/
	public void run()
	{
		Log.d(TAG,"Message recvd is::"+msg.msgType+"::"+msg.msgOrigin);
		switch(msg.msgType)
		{
			case joinMessage:
				_joinChord();
				break;

			case peerListMessage:
				_setPeerList();
				break;
			
			case GDumpQueryMessage:
				_GDumpQuery();
				break;
			
			case objectInsertMessage:
				_ObjInsert();
				break;
			
			case objectQueryMessage:
				if(msg.msgOrigin.equals(dhtProvider.myAVDnum))
				{
					Log.e(TAG,"I have got my own request -- nobody can be as stupid as me");
				}
				else
				{
					_ObjQuery();
				}
				break;
			
			case GDumpQueryResponseMessage:
				synchronized (dhtProvider._GDump_Lock) 
				{
					dhtProvider._GDump_Lock.records = (Hashtable<String, String>)msg.payload;
					dhtProvider._GDump_Lock.notify();
				}
				break;
			
			case objectQueryResponseMessage:
				synchronized (dhtProvider._query_Lock)
				{
					dhtProvider._query_Lock.records = (Hashtable<String, String>)msg.payload;
					dhtProvider._query_Lock.notify();
				}
				break;
				
			case objectInsertResponseMessage:
				synchronized (dhtProvider._insert_Lock)
				{
					Log.e(TAG,"the object have been inserted!");
					dhtProvider._insert_Lock.setGood(true);
					dhtProvider._insert_Lock.notify();
				}
				break;
			case GDelReqMessage:
				_dhtDel();
				break;
			case GDelReqResponseMessage:
				synchronized (dhtProvider._GDel_Lock)
				{
					dhtProvider._GDel_Lock.rows_affected = (Integer)msg.payload;
					dhtProvider._GDel_Lock.notify();
				}
				break;
				
			case ObjectDelReqMessage:
				_ObjDel();
				break;
			
			case ObjectDelReqResponseMessage:
				synchronized (dhtProvider._delObj_Lock)
				{
					dhtProvider._delObj_Lock.rows_affected = (Integer)msg.payload;
					dhtProvider._delObj_Lock.notify();
				}
				break;
			
			default:
				Log.e(TAG,"life can be cruel sometimes");
					break;
		}
	}
	
	
	/*=========================================================================
     * Function   : _ObjQuery()
     * Description: handles the object query request from predecessor
     * Parameters : void
     * Return	  : void 
     *=========================================================================*/
	private void _ObjQuery()
	{
		Hashtable<String, String> queryResult = dhtProvider.query(msg.query,msg.payload);
		
		if(queryResult!=null) // end the request chain
		{
			Log.d(TAG,"The size of queryResult ="+queryResult.size());
			Message.sendMessage(MessageType.objectQueryResponseMessage,null,dhtProvider.myAVDnum,queryResult,msg.originPort);
		}
		else // i don't have it sending to successor
		{
			msg.forwardMessage(dhtProvider.succ_node.portNum);
		}
	}
	
	/*=========================================================================
     * Function   : _ObjDel()
     * Description: handles the object delete request from predecessor
     * Parameters : void
     * Return	  : void 
     *=========================================================================*/
	private void _ObjDel()
	{
		// just a debug check
		if(msg.msgOrigin.equals(dhtProvider.myAVDnum))
		{
			Log.e(TAG,"nobody can be more stupid than me");
		}
		else // pred has asked me to get the job done
		{
			Integer retVal = dhtProvider.delete(msg.query, (String)msg.payload);
			if(retVal > 0) // delete was successful
			{
				Message.sendMessage(MessageType.ObjectDelReqResponseMessage,null,dhtProvider.myAVDnum,(Object)retVal, msg.originPort);
			}
			else // did not find key -- forwarding request to sender
			{
				msg.forwardMessage(dhtProvider.succ_node.portNum);
			}
		}
	}
	
	
	/*=========================================================================
     * Function   : dhtDel
     * Description: handles the delete all propagation request from the predecessor
     * Parameters : void
     * Return	  : void 
     *=========================================================================*/
	private void _dhtDel()
	{		
		// another bebug check
		if(msg.msgOrigin.equals(dhtProvider.myAVDnum))
		{
			Log.e(TAG,"nobody can be more stupid than me");
		}
		else // pred has asked me to get the job done
		{
			Integer retVal = dhtProvider.delete(msg.query,null);
			Integer newPayload =  retVal + (Integer)msg.payload;
			
			// put in the highest value
			if(dhtProvider.succ_node.avdNum.equals(msg.msgOrigin))
			{
				Message.sendMessage(MessageType.GDelReqResponseMessage,null,dhtProvider.myAVDnum,(Object)newPayload, msg.originPort);
			}
			else
			{
				msg.forwardMessage(dhtProvider.succ_node.portNum);
			}
		}
	}
	
	
	/*=========================================================================
     * Function   : _ObjInsert()
     * Description: handles object insert request from the predecessor
     * Parameters : void
     * Return	  : void 
     *=========================================================================*/
	private void _ObjInsert()
	{
		if(dhtProvider.insert((String[])msg.payload))
			Message.sendMessage(MessageType.objectInsertResponseMessage,null, dhtProvider.myAVDnum,null,msg.originPort);
		else
			msg.forwardMessage(dhtProvider.succ_node.portNum);
			
		return;
	}
	

	/*=========================================================================
     * Function   : _joinChord()
     * Description: handles the chord join request from a new node this shall 
     * 				be executed only on the coord AVD 
     * Parameters : void
     * Return	  : void 
     *=========================================================================*/
	private void _joinChord()
	{
		/**
		 * insert nodes into chord and then proceed to resolving
		 * resolve predecessors and successors of all nodes in chord 
		 * and communicate this information to the respective nodes  
		 **/
		node newNode = new node(msg.originPort,msg.msgOrigin);
		dhtProvider.chord.insert(newNode.node_id,newNode);
		dhtProvider.chord.printChord();
		dhtProvider.chord.sendAdjNodesMsg();
		return;
	}
	
	/*=========================================================================
     * Function   : _setPeerList()
     * Description: When a peer list message is received from the coord
     * 				does the needful
     * Parameters : void
     * Return	  : void
     *=========================================================================*/
	private void _setPeerList()
	{
		/**
		 * coord has told me who my peers shall be
		 */
		dhtProvider.pred_node = ((node[])(msg.payload))[0];
		dhtProvider.succ_node = ((node[])(msg.payload))[1];
		return;
	}
	
	/*=========================================================================
     * Function   : _GDumpQuery()
     * Description: handles the Global Dump query request
     * Parameters : void
     * Return	  : void 
     *=========================================================================*/
	private void _GDumpQuery()
	{
		Hashtable<String, String> result = null;
		// sanity check
		if(msg.msgOrigin != dhtProvider.myAVDnum)
		{			
			/**
			 * get my dump
			 * check if successor is the originator -- 
			 * if yes send queryResponse message 
			 */
			result  = dhtProvider.query(query_all,msg.payload);
			if(dhtProvider.succ_node.avdNum.equals(msg.msgOrigin))
			{
				Message.sendMessage(MessageType.GDumpQueryResponseMessage,null,dhtProvider.myAVDnum,result,dhtProvider.succ_node.portNum);
			}
			else // forward message to successor for GDump 
			{
				msg.payload = result;
				msg.forwardMessage(dhtProvider.succ_node.portNum);
			}
		}
		return;
	}				
}