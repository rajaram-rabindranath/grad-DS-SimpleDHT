package edu.buffalo.cse.cse486586.simpledht;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import android.util.Log;


/*=========================================================================
 * Class name   : node
 * Description  : A template for a node object
 * Author		: RAJARAM RABINDRANATH
 *=========================================================================*/
public class ChordMaster implements Serializable
{
	private static final long serialVersionUID = 1L;
	private TreeMap<String, node> simpleChord = null;
	static String TAG = ChordMaster.class.getName();	
	
	public ChordMaster()
	{
		simpleChord = new TreeMap<String,node>();
	}
	
	
	/*=========================================================================
     * Function   : sendAdjNodesMsg()
     * Description: Sends peerlist to all nodes currently present in the chord
     * Parameters : void
     * Return	  : void  
     *=========================================================================*/
	public void sendAdjNodesMsg()
    {
		node toNode =  null;
		Set<String> ids = this._get_keySet();
		
		for(String node_id:ids)
		{
			toNode = this._get(node_id);
			Message.sendMessage(MessageType.peerListMessage,null,toNode.avdNum,this.get_adjNodes(toNode),toNode.portNum);
		}
    }

	
	/*=========================================================================
     * Function   : _get_keySet
     * Description: returns the keySet of the component simpleChord
     * Parameters : 
     * Return	  : Set<String> 
     *=========================================================================*/
	public Set<String> _get_keySet()
	{
		return simpleChord.keySet();
	}
	
	public node _get_firstNode()
	{
		return simpleChord.get(simpleChord.firstKey());
	}
	
	/*=========================================================================
     * Function   : genHash
     * Description: given a node_id <String> returns a node object
     * Parameters : String node_id
     * Return	  : node 
     *=========================================================================*/
	public node _get(String node_id)
	{
		return simpleChord.get(node_id);
	}
	
	
	
	/*=========================================================================
     * Function   : genHash
     * Description: 
     * Parameters : Uri uri, String[] projection, String selection, 
     * 				String[] selectionArgs,String sortOrder
     * Return	  : Cursor 
     *=========================================================================*/
	public void insert(String node_id,node n)
	{
		simpleChord.put(node_id,n);
	}
	
	/*=========================================================================
     * Function   : genHash
     * Description: Given a string input generates the hashkey for the input
     * 				using SHA-1
     * Parameters : String input
     * Return	  : String has value of "input"
     *=========================================================================*/
    public static String genHash(String input) throws NoSuchAlgorithmException 
    {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        int index = 0;
        for (byte b : sha1Hash)
        {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
    
	
    /*=========================================================================
     * Function   : printChord
     * Description: Lists all the members of the chord
     * Parameters : void
     * Return	  : void 
     *=========================================================================*/
    public void printChord()
    {
    	Set<String> ids = simpleChord.keySet();
    	Log.d(TAG,"num of elements ="+simpleChord.size());
    	for(String node_id:ids)
    	{
    		Log.e(TAG,"chord avds:: "+node_id+"::"+simpleChord.get(node_id).avdNum);
    	}
    }
    
    /*=========================================================================
     * Function   : adjNodes
     * Description: given a node_id fetches the predecessor and successor nodes
     * 				of the said node
     * Parameters : node n
     * Return	  : node[] 
     *=========================================================================*/
    public node[] get_adjNodes(node n)
    {
    	int pred=0, succ=1; // index of pred and succ in the node[] that shall be sent across to other nodes
    	int i = 1;
    	boolean found = false;
    	node[] adjNodes = {null,null};
    	Iterator<String> iter =simpleChord.keySet().iterator();
    	
    	String currNode_id = null;
    	
    	if(simpleChord.size() == 1)
	    {
    		adjNodes[pred] = adjNodes[succ] = simpleChord.get(iter.next());
    		return adjNodes;
	    }
	    	
    	// find pred and succ of node 
    	while(iter.hasNext())
    	{
    		currNode_id = iter.next();
    		
    		
    		if(n.node_id.equals(currNode_id))
    		{
    			found = true;
    			/**
    			 * we already know the predecessor
    			 * lets set the successor
    			 */
    			if(!iter.hasNext()) // if this is the last key
    			{
    				adjNodes[succ] = simpleChord.firstEntry().getValue();
    			}
    			else // go ahead set succ and handle special case
    			{
    				adjNodes[succ] = simpleChord.get(iter.next());
    				if(i == 1)adjNodes[pred]=(simpleChord.lastEntry()).getValue();
    			}
    			break;
    		}
    		adjNodes[pred] = simpleChord.get(currNode_id);
    		i++;
    	}
    	return adjNodes;
    }
}
