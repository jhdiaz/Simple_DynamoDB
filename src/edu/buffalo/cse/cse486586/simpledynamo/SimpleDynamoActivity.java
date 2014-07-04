package edu.buffalo.cse.cse486586.simpledynamo;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.database.Cursor;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class SimpleDynamoActivity extends Activity {
	private static final Uri uri = Uri.parse("content://edu.buffalo.cse.cse486586.simpledynamo.provider");
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_simple_dynamo);
		
		final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        Button lDump = (Button) findViewById(R.id.button1);
        
        lDump.setOnClickListener(new OnClickListener(){
        	@Override
            public void onClick(View v)
            {
                tv.append("LDUMP!\n");
                Cursor cursor = getContentResolver().query(uri, null, "@", null, null);
                int count = 0;
                if(cursor.moveToFirst()){
                	do{
                		String key = cursor.getString(cursor.getColumnIndex("key"));
                		String value = cursor.getString(cursor.getColumnIndex("value"));
                		tv.append(Integer.toString(count)+"\n"+"key = "+key+"\n"+"value = "+value+"\n");
                		count++;
                	} while(cursor.moveToNext());
                }
            }
        });
        
        Button gDump = (Button) findViewById(R.id.button2);
        gDump.setOnClickListener(new OnClickListener(){
        	@Override
            public void onClick(View v)
            {
                tv.append("GDUMP!\n");
                Cursor cursor = getContentResolver().query(uri, null, "*", null, null);
                int count = 0;
                if(cursor.moveToFirst()){
                	do{
                		String key = cursor.getString(cursor.getColumnIndex("key"));
                		String value = cursor.getString(cursor.getColumnIndex("value"));
                		tv.append(Integer.toString(count)+"\n"+"key = "+key+"\n"+"value = "+value+"\n");
                		count++;
                	} while(cursor.moveToNext());
                }
            }
        });
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.simple_dynamo, menu);
		return true;
	}

}
