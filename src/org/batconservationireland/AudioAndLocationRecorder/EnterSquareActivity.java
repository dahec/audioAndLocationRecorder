package org.batconservationireland.AudioAndLocationRecorder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class EnterSquareActivity extends Activity {
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.enter_square);
        
        final EditText edittext = (EditText) findViewById(R.id.edittext);
        edittext.requestFocus();
        if (savedInstanceState != null) {
        	edittext.setText(savedInstanceState.getString("square"));
        }
        edittext.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
            	if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
            		validateSquareAndDispatch(edittext);
            		return true;
            	}
            	return false;
            }
        });
        
        final Button button = (Button) findViewById(R.id.enterSquareButton);
        button.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	validateSquareAndDispatch(edittext);
            }
        });
    }
    
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
    	/* Save the value of the square if this identity is temporarily destroyed */
    	final EditText edittext = (EditText) findViewById(R.id.edittext);
    	savedInstanceState.putString("square",edittext.getText().toString());
    	super.onSaveInstanceState(savedInstanceState);
    }
    
   
    
    private void validateSquareAndDispatch(EditText edittext) {
    	Pattern pattern = Pattern.compile("^[A-Za-z][0-9]{2}$");
        Matcher matcher = pattern.matcher(edittext.getText().toString());
        if (matcher.find()) {
        	Intent intent =
                new Intent(EnterSquareActivity.this.getApplication(),
                			RecorderActivity.class);
        	Bundle b = new Bundle();
        	b.putString("square", edittext.getText().toString());
        	intent.putExtras(b);
            startActivity(intent);
        } else {
        	edittext.setText("");
			Toast.makeText(EnterSquareActivity.this, R.string.enterSquareValidationError, Toast.LENGTH_LONG).show();
        }
    }

}