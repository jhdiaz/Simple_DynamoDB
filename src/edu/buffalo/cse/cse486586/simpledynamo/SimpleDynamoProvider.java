package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDynamoProvider extends ContentProvider {
	private static SQLiteDatabase db;
	private static int currID = 0;
	private static int succID = 0;
	private static int[] nodeArray = {5562, 5556, 5554, 5558, 5560};
	private static String messageFromListener = "$empty$";
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		//Log.v("Before lock", "delete");
		RecoveryHelper.lockWhileRecovering();
		//Log.v("After lock", "delete");
		
		if(selection.equals("*")){//All data will be deleted from every partition
			db.execSQL("DELETE FROM data");
			
			//Send a delete request to all other nodes.
			for(int i=0;i<nodeArray.length;i++){
				if(nodeArray[i]!=currID){
					Sender sender = new Sender("$globalDelete$", getPort(nodeArray[i]));
					Thread senderThread = new Thread(sender);
					senderThread.start();
				}
			}
		}
		else if(selection.equals("@")){//Delete all local data
			db.execSQL("DELETE FROM data");
		}
		else{//Delete certain key from all partitions.
			//Log.v("Deleting", selection);
			db.execSQL("DELETE FROM data WHERE key='"+selection+"'");
			
			for(int i=0;i<nodeArray.length;i++){
				if(currID!=nodeArray[i]){
					Sender sender = new Sender("$delete$ "+selection, getPort(nodeArray[i]));
					Thread senderThread = new Thread(sender);
					senderThread.start();
				}
			}
		}
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values){
		//Log.v("Before lock", "insert");
		RecoveryHelper.lockWhileRecovering();
		//Log.v("After lock", "insert");
		
		String key = values.getAsString("key");
		String value = values.getAsString("value");
		int partition = 0;
		
		//Checking which partition the key belongs in.
		for(int i=0;i<nodeArray.length;i++){
			if(belongsInPartition(nodeArray[i], key)){
				partition = nodeArray[i];
				break;
			}
		}
		
		if(currID==partition){
			//First the key and value are inserted into the current node,
			//then, replication is implemented by sending a replication request to
			//the next two nodes in the chain.
			boolean keyExists = db.rawQuery("SELECT * FROM data WHERE key='"+key+"'", null).moveToFirst();
			
			if(!keyExists){
				db.execSQL("INSERT INTO data (key, value) " +
						"VALUES('"+key+"', '"+value+"');");
			}
			else{
				update(null, null, key+":"+value, null);
			}
			
			//A replicate request is sent to the next two partitions.
			String replicateRequest = "$insert$ "+key+":"+value;
			Sender sender = new Sender(replicateRequest, getPort(succID));
			Thread senderThread = new Thread(sender);
			senderThread.start();

			Sender sender1 = new Sender(replicateRequest, getPort(getSucc(succID)));
			Thread senderThread1 = new Thread(sender1);
			senderThread1.start();
			
			Log.v("insert", Integer.toString(currID));
			Log.v(key, value);
		}
		else{
			//Does not belong in local partition. Sending to correct partitions.
			//Including partitions that need to replicate this key.
			String insertRequest = "$insert$ "+key+":"+value;
			Sender sender = new Sender(insertRequest, getPort(partition));
			Thread senderThread = new Thread(sender);
			senderThread.start();
			
			Sender sender1 = new Sender(insertRequest, getPort(getSucc(partition)));
			Thread senderThread1 = new Thread(sender1);
			senderThread1.start();
			
			Sender sender2 = new Sender(insertRequest, getPort(getSucc(getSucc(partition))));
			Thread senderThread2 = new Thread(sender2);
			senderThread2.start();
		}

		return null;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder){
		
		//Log.v("Before lock", "query");
		RecoveryHelper.lockWhileRecovering();
		//Log.v("After lock", "query");
		
		if(selection.equals("*")){//All data will be pulled from every partition
			Cursor cursor = db.rawQuery("SELECT * FROM data", null);
			boolean empty = !cursor.moveToFirst();
			String key_values = "";
			
			if(empty){
				return cursor;
			}
			
			do{
				key_values += cursor.getString(cursor.getColumnIndex("key"))+":";
				key_values += cursor.getString(cursor.getColumnIndex("value"))+"/";
			} while (cursor.moveToNext());
			
			String globalQueryRequest = "$globalQuery$ 2 "+Integer.toString(currID)+" "+key_values;
			int destination = currID;
			Sender sender;
			
			//Attempt to send will be made to node next in chain until the request is sent successfully.
			do{
				destination = getSucc(destination);
				sender = new Sender(globalQueryRequest, getPort(destination));
				sender.run();
			} while(!sender.didSend());
			
			//Wait for message retrieval in server.
			while(messageFromListener.equals("$empty$")){
				
			}
			
			MatrixCursor mc = new MatrixCursor(new String[]{"key", "value"});
			String[] keyValues = messageFromListener.split("/");
			
			for(int i=0;i<keyValues.length;i++){
				String key = keyValues[i].split(":")[0];
				String value = keyValues[i].split(":")[1];
				mc.addRow(new String[]{key, value});
			}
			
			setMessage("$empty$");
			
			return mc;
		}
		else if(selection.equals("@")){//All local data will be pulled.
			Cursor cursor = db.rawQuery("SELECT * FROM data", null);
			return cursor;
		}
		else{//Data selection based on key.
			//Log.v("Querying", selection);
			
			Cursor cursor = db.rawQuery("SELECT * FROM data WHERE key='"+selection+"'", null);
				
			//If this key exists in the database then return the cursor.
			if(cursor.moveToFirst()){
				Log.v("Found locally", selection);
				return cursor;
			}
			//If the key does not exist in the database then forward the query
			//request to successor.
			else{
				//Log.v("Not found locally", selection);
				
				String queryRequest = "$query$ "+selection+" "+Integer.toString(currID);
				int destination = currID;
				Sender sender;
				
				//Attempt to send will be made to node next in chain until the request is sent successfully.
				do{
					destination = getSucc(destination);
					sender = new Sender(queryRequest, getPort(destination));
					sender.run();
				} while(!sender.didSend());
				
				//This loop will first check whether a message was received in the server.
				//The loop will then check to make sure that the message is for the
				//correct thread.
				
				String value = "";

				while(true){
					if(!messageFromListener.equals("$empty$")){
						if(selection.equals(messageFromListener.split(":")[0])){
							value = messageFromListener.split(":")[1];
							break;
						}	
					}
				}
				
				MatrixCursor mc = new MatrixCursor(new String[]{"key", "value"});
				mc.addRow(new String[]{selection, value});
				setMessage("$empty$");
				
				return mc;
			}
		}
	}
		
	@Override
	public boolean onCreate() {
		try{
			//Getting machine address for communication
	    	TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
	        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
	        currID = Integer.parseInt(portStr);
			
			//Initializing database.
			//Have to initialize database before server to avoid null pointer exception.
			//Insert requests can possibly be received in server before database is initialized.
	        DBOpenHelper oh = new DBOpenHelper(this.getContext());
	        db = oh.getWritableDatabase();
			
	        //Initializing array that contains the ID's of all nodes.
	        updateArray();
	        
	        //Starting server.
			ServerSocket serverSocket = new ServerSocket(10000);
	        new Thread(new Listener(serverSocket)).start();
	        
	        Log.v("Starting emulator", Integer.toString(currID));
	        Log.v("VERSION", "76");
	        
	        RecoveryHelper recoveryHelper = new RecoveryHelper(currID, nodeArray);
	        Thread recoveryThread = new Thread(recoveryHelper);
	        recoveryThread.start();
	        
	        return true;
		}
		catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private class Listener implements Runnable{
		private ServerSocket serverSocket;
		
		public Listener(ServerSocket serverSocket){
			this.serverSocket = serverSocket;
		}
		
		public void run(){
			try {
				//This is our server which handles received messages.
				while(true){
					//Listener is waiting for a ping.
					Socket clientSocket = serverSocket.accept();
					
					//Pinging back socket to let sender know this node is not down.
					OutputStream os = clientSocket.getOutputStream();
					os.write(1);
					
					//Reading message from socket.
					InputStream is = clientSocket.getInputStream();
					InputStreamReader isr = new InputStreamReader(is);
					BufferedReader br = new BufferedReader(isr);
					String message = "";
					String line = null;
					
					while( (line=br.readLine()) != null){
						message += line;
					}
					
					//Log.v("Listener", Integer.toString(currID));
					
					//Handing request off to request handler for improved performance.
					//The performance is improved because the server only haves to worry about receiving the
					//request and not also handling the logic of the request. This allows for multiple threads
					//to handle requests while the server freely receives more requests instead of multiple
					//requests stacking up on the server's buffer.
					RequestHandler requestHandler = new RequestHandler(message);
					Thread requestThread = new Thread(requestHandler);
					requestThread.start();
					
					clientSocket.close();
				}
			} catch (IOException e) {
			}
		}
	}
	
	//This class handles all requests received in Listener.
	private class RequestHandler implements Runnable{
		String request;
		
		public RequestHandler(String request){
			this.request = request;
		}
		
		public void run(){
			String[] arguments = request.split(" ");
			
			//Log.v("In RequestHandler", Integer.toString(currID));

			//If there is at least one ongoing recovery, and the current request isn't a recovery request,
			//then all functionality will be put on hold until the recovery process is complete.
			//Log.v("Before lock", request);
			if(RecoveryHelper.isRecovering() && !arguments[0].equals("$returnedRecovery$")){
				RecoveryHelper.lockWhileRecovering();
			}
			//Log.v("After lock", request);
			
			if(arguments.length==2 && arguments[0].equals("$insert$")){
				String key = arguments[1].split(":")[0];
				String value = arguments[1].split(":")[1];
				Cursor cursor = db.rawQuery("SELECT * FROM data WHERE key='"+key+"'", null);
				boolean keyExists = cursor.moveToFirst();
				
				if(!keyExists){
					db.execSQL("INSERT INTO data (key, value) " +
							"VALUES('"+key+"', '"+value+"');");
				}
				else{
					update(null, null, key+":"+value, null);
				}
			}
			else if(arguments.length==3 && arguments[0].equals("$query$")){
				String key = arguments[1];
				String requester = arguments[2];
				Cursor cursor = db.rawQuery("SELECT * FROM data WHERE key='"+key+"'", null);
				
				//Query was successful and will be returned to requester.
				if(cursor.moveToFirst()){
					String value = cursor.getString(cursor.getColumnIndex("value"));
					String returnedQuery = "$returnedQuery$ "+key+":"+value;
					Sender sender = new Sender(returnedQuery, getPort(Integer.parseInt(requester)));
					Thread senderThread = new Thread(sender);
					senderThread.start();
				}
				//Query was not successful and will be propagated to next partition.
				else{
					String queryRequest = "$query$ "+key+" "+requester;
					int destination = currID;
					Sender sender;
					
					//Attempt to send will be made to node next in chain until the request is sent successfully.
					do{
						destination = getSucc(destination);
						sender = new Sender(queryRequest, getPort(destination));
						sender.run();
					} while(!sender.didSend());
				}
			}
			else if(arguments.length==2 && arguments[0].equals("$returnedQuery$")){
				setMessage(arguments[1]);
			}
			else if(arguments.length==4 && arguments[0].equals("$globalQuery$")){
				String requester = arguments[2];
				int n = Integer.parseInt(arguments[1])-1;
				Cursor cursor = db.rawQuery("SELECT * FROM data", null);
				String keyValues = arguments[3];
				boolean empty = !cursor.moveToFirst();
				
				if(!empty){
					do{
						keyValues += cursor.getString(cursor.getColumnIndex("key"))+":";
						keyValues += cursor.getString(cursor.getColumnIndex("value"))+"/";
					} while (cursor.moveToNext());
				}
					
				//Keep propagating global query request until it is n partitions after the requester.
				if(n>0){
					String x = Integer.toString(n);
					String globalQueryRequest = "$globalQuery$ "+x+" "+requester+" "+keyValues;
					int destination = currID;
					Sender sender;
					
					//Attempt to send will be made to node next in chain until the request is sent successfully.
					do{
						destination = getSucc(destination);
						sender = new Sender(globalQueryRequest, getPort(destination));
						sender.run();
					} while(!sender.didSend());
				}
				//This is the final partition and it should send the queries back to
				//the original requester.
				else{
					Sender sender = new Sender("$returnedQuery$ "+keyValues, getPort(Integer.parseInt(requester)));
					Thread senderThread = new Thread(sender);
					senderThread.start();
				}
			}
			else if(arguments.length==1 && arguments[0].equals("$globalDelete$")){
				db.execSQL("DELETE FROM data");
			}
			else if(arguments.length==2 && arguments[0].equals("$recovery$")){
				int requester = Integer.parseInt(arguments[1]);
				int requesterPort = getPort(requester);
				Cursor cursor = db.rawQuery("SELECT * FROM data", null);
				String key_values = "";
				
				if(cursor.moveToFirst()){
					do{
						String key = cursor.getString(cursor.getColumnIndex("key"));
						String value = cursor.getString(cursor.getColumnIndex("value"));
						key_values += key+":"+value+"/";
					} while(cursor.moveToNext());
				}
				
				//If no keys were added, then a filler character will be appended to the string
				//in order to allow the message to still be processed in the server.
				if(key_values.equals("")){
					key_values += "/";
				}
				
				Sender sender = new Sender("$returnedRecovery$ "+key_values+" "+Integer.toString(currID), requesterPort);
				Thread senderThread = new Thread(sender);
				senderThread.start();
			}
			else if(arguments.length==3 && arguments[0].equals("$returnedRecovery$")){
				String[] key_values = arguments[1].split("/");
				int partition = Integer.parseInt(arguments[2]);

				//If the partition that sent me their keys for recovery is either one of the two nodes
				//after me in the chain, I will only insert the keys that I replicated into said partition.
				if(partition==getSucc(currID) || partition==getSucc(getSucc(currID))){
					for(int i=0;i<key_values.length;i++){
						String key = key_values[i].split(":")[0];
						
						if(belongsInPartition(currID, key)){
							String value = key_values[i].split(":")[1];
							Cursor cursor = db.rawQuery("SELECT * FROM data WHERE key='"+key+"'", null);
							boolean keyExists = cursor.moveToFirst();
							
							if(!keyExists){
								db.execSQL("INSERT INTO data (key, value) " +
										"VALUES('"+key+"', '"+value+"');");
							}
							else{
								update(null, null, key+":"+value, null);
							}
						}
					}
				}
				//Otherwise, I will only insert keys that I was supposed to replicate from the previous
				//two partitions.
				else{
					for(int i=0;i<key_values.length;i++){
						String key = key_values[i].split(":")[0];
						
						if(belongsInPartition(partition, key)){
							String value = key_values[i].split(":")[1];
							Cursor cursor = db.rawQuery("SELECT * FROM data WHERE key='"+key+"'", null);
							boolean keyExists = cursor.moveToFirst();
							
							if(!keyExists){
								db.execSQL("INSERT INTO data (key, value) " +
										"VALUES('"+key+"', '"+value+"');");
							}
							else{
								update(null, null, key+":"+value, null);
							}
						}
					}
				}
				
				RecoveryHelper.finishedRecovery();
			}
			else if(arguments.length==2 && arguments[0].equals("$delete$")){
				String key = arguments[1];
				db.execSQL("DELETE FROM data WHERE key='"+key+"'");
			}
		}
	}
	
	private void updateArray(){
		succID = getSucc(currID);
	}
	
	public static int getPort(int nodeID){
		return 2*nodeID;
	}
	
	private int getSucc(int nodeID){
		int nodeIndex = -1;
		
		//Finding position of current node in array
		for(int i=0;i<nodeArray.length;i++){
			if(nodeID==nodeArray[i]){
				nodeIndex = i;
				break;
			}
		}
		
		//Case for the current node being the lowest node in the chain.
		if(nodeIndex==0){
			return 5556;
		}
		//Case for the current node being the highest node in the chain.
		else if(nodeIndex==nodeArray.length-1){
			return 5562;
		}
		else{
			return nodeArray[nodeIndex+1];
		}
	}

	private int getPred(int nodeID){
		int nodeIndex = -1;
		
		//Finding position of current node in array
		for(int i=0;i<nodeArray.length;i++){
			if(nodeID==nodeArray[i]){
				nodeIndex = i;
				break;
			}
		}
		
		//Case for the current node being the lowest node in the chain.
		if(nodeIndex==0){
			return 5560;
		}
		//Case for the current node being the highest node in the chain.
		else if(nodeIndex==nodeArray.length-1){
			return 5558;
		}
		else{
			return nodeArray[nodeIndex-1];
		}
	}
	
	private boolean belongsInPartition(int partition, String key){
		try{
			String keyHash = genHash(key);
			String predIDHash = genHash(Integer.toString(getPred(partition)));
			String currIDHash = genHash(Integer.toString(partition));
			
			boolean flag1 = (predIDHash.compareTo(currIDHash)>0) 
					&& (keyHash.compareTo(predIDHash)>0);
			boolean flag2 = (keyHash.compareTo(currIDHash)<=0) 
					&& (keyHash.compareTo(predIDHash)>0);
			boolean flag3 = (keyHash.compareTo(currIDHash)<=0) 
				&& (predIDHash.compareTo(currIDHash)>0);
			
			if(flag1 || flag2 || flag3){
				return true;
			}
			
			return false;
		}
		catch(NoSuchAlgorithmException e){
			return false;
		}
	}
	
	public synchronized void setMessage(String newMessage){
		messageFromListener = newMessage;
	}
	
	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		
		String key = selection.split(":")[0];
		String value = selection.split(":")[1];
		
		db.execSQL("UPDATE data SET value='"+value+"' WHERE key='"+key+"'");
		
		return 0;
	}

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        @SuppressWarnings("resource")
		Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
}
