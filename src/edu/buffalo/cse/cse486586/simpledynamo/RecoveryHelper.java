package edu.buffalo.cse.cse486586.simpledynamo;

import android.util.Log;

public class RecoveryHelper implements Runnable{
	private static int recoveriesInProgress = 0;
	private int currentNode;
	private int[] nodeArray;
	
	public RecoveryHelper(){
		
	}
	
	public RecoveryHelper(int currentNode, int[] nodeArray){
		Log.v("In recovery helper for", Integer.toString(currentNode));
		
		this.currentNode = currentNode;
		this.nodeArray = nodeArray;
	}
	
	//Begins the recovery process.
	public void run(){
		for(int i=0;i<nodeArray.length;i++){
        	if(currentNode!=nodeArray[i]){
        		int destination = SimpleDynamoProvider.getPort(nodeArray[i]);
        		Sender sender = new Sender("$recovery$ "+Integer.toString(currentNode), destination);
        		sender.run();
        		
        		if(sender.didSend()){
        			recoveriesInProgress++;
        		}
        	}
        }
	}
	
	//Partition that called this finished one instance of recovery.
	//Recovery takes multiple instances because requests are sent to multiple partitions.
	public synchronized static void finishedRecovery(){
		Log.v("Finished recovery", Integer.toString(recoveriesInProgress));
		
		recoveriesInProgress--;
	}
	
	//Checks whether a recovery is in progress.
	public static boolean isRecovering(){
		return recoveriesInProgress>0;
	}
	
	//Stops any functionality within the thread that calls this while there are still recoveries going on.
	public static void lockWhileRecovering(){
		
		while(isRecovering()){
			
		}
	}
	
	public static int getRecoveries(){
		return recoveriesInProgress;
	}
}
