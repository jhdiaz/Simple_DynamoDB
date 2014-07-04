package edu.buffalo.cse.cse486586.simpledynamo;

public class KeyLocker {
	private static int arraySize = 10;
	private static String[] keys = new String[arraySize];
	private static int top = 0;
	
	public static synchronized void lockKey(String key){
		boolean haveKey = false;
		
		for(int i=0;i<keys.length;i++){
			if(keys[i].equals(key)){
				keys[i] = "";
				haveKey = true;
			}
		}
		
		if(haveKey){
			if(top==arraySize){
				arraySize *= 2;
				String[] temp = new String[arraySize];
				
				for(int i=0;i<top;i++){
					temp[i] = keys[i];
				}
				
				keys = temp;
				
				keys[top++] = key;
				
			}
			else{
				keys[top++] = key;
			}
		}
	}
	
	public static synchronized void unlockKey(String key){
		for(int i=0;i<keys.length;i++){
			if(keys[i].equals(key)){
				keys[i] = "";
				break;
			}
		}
	}
	
	public static boolean isLocked(String key){
		for(int i=0;i<keys.length;i++){
			if(key.equals(keys[i])){
				return true;
			}
		}
		return false;
	}
	
	public static void waitOnKey(String key){
		while(isLocked(key)){
			
		}
	}
}
