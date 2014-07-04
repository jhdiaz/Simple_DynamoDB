package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import android.util.Log;

public class Sender implements Runnable{
	private String message;
	private int destinationPort;
	private boolean sendSuccessful = false;
	
	public Sender(String message, int destinationPort){
		this.message = message;
		this.destinationPort = destinationPort;
	}
	
	public void run(){
		try {
			InetAddress address = InetAddress.getByAddress(new byte[]{10,0,2,2});
			Socket socket = new Socket(address, destinationPort);
			OutputStream os = socket.getOutputStream();
			os.write(message.getBytes());
			//If the other party does not ping back within 100 milliseconds it is considered down.
			Thread.sleep(100);
			InputStream is = socket.getInputStream();
			int isAlive = is.read();
			
			if(isAlive==1){
				sendSuccessful = true;
			}
			
			socket.close();
		} catch (IOException e) {
			Log.v("Sender Exception", "IOException in Sender");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	//Checks whether message was successfully sent to destination.
	//Is used for failure-handling.
	public boolean didSend(){
		return sendSuccessful;
	}
}
