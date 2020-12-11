package com.gyorog.filepusher;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

public class SmbShareDataActivity extends AppCompatActivity {
    static String TAG = "com.gyorog.filepusher.EnterShareDataActivity";
    String username;
    String password;
    String hostname;
    String sharename;
    String remotepath;
    int show_selection_id = 0;
    int save_selection_id = 0;

    Button browse_button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_share_data);

        browse_button = findViewById(R.id.btn_browse_path);

        Intent args_intent = getIntent();
        username = args_intent.getStringExtra("str-username");
        if(username != null) {
            Log.d(TAG, "Found intent. Filling in form details.");
            ((TextInputEditText) findViewById(R.id.target_username)).setText( username );
            password = args_intent.getStringExtra("str-password");
            ((TextInputEditText) findViewById(R.id.target_password)).setText( password );
            hostname = args_intent.getStringExtra("str-hostname");
            ((TextInputEditText) findViewById(R.id.target_hostname)).setText( hostname );
            sharename = args_intent.getStringExtra("str-sharename");
            ((TextInputEditText) findViewById(R.id.target_sharename)).setText( sharename );
            remotepath = args_intent.getStringExtra("str-remotepath");
            SetPathString(remotepath);
        } else {
            // If no share information was passed in, we set the path to ""
            browse_button.setTag("");
        }

        browse_button.setOnClickListener(v -> {
            username = ((TextInputEditText) findViewById(R.id.target_username)).getText().toString();
            password = ((TextInputEditText) findViewById(R.id.target_password)).getText().toString();
            hostname = ((TextInputEditText) findViewById(R.id.target_hostname)).getText().toString();
            sharename = ((TextInputEditText) findViewById(R.id.target_sharename)).getText().toString();
            remotepath = (String) v.getTag();

            Log.d(TAG, "browse_button clicked with path='" + remotepath + "'");

            Intent intent = new Intent(v.getContext(), BrowseRemotePathActivity.class);
            intent.putExtra("str-username", username);
            intent.putExtra("str-password", password);
            intent.putExtra("str-hostname", hostname);
            intent.putExtra("str-sharename", sharename);
            intent.putExtra("str-remotepath", remotepath );
            startActivityForResult(intent, MainActivity.CODE_GET_REMOTE_PATH);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MainActivity.CODE_GET_REMOTE_PATH) {
            switch (resultCode) {
                case MainActivity.CODE_FAILURE:
                    Log.d(TAG, "Error code returned");
                    break;
                case MainActivity.CODE_SUCCESS:
                    Log.d(TAG, "Success code returned");
                    SetPathString( data.getStringExtra("str-remotepath") );
                    break;
                default:
                    Log.e(TAG, "Received unexpected result (expected " + MainActivity.CODE_FAILURE + " or " + MainActivity.CODE_SUCCESS + " but got " + resultCode + ")");
            }
        } else {
            Log.e(TAG, "Received unexpected requestCode (expected " + MainActivity.CODE_GET_REMOTE_PATH + " but got " + requestCode + ")");
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void SetPathString(String selected_path){
        LinearLayout layout = findViewById(R.id.enter_share_layout);
        Context context = layout.getContext();

        TextView show_selection;
        if( show_selection_id == 0 ) {
            show_selection = new TextView(context);
            show_selection_id = View.generateViewId();
            show_selection.setId(show_selection_id);
            layout.addView(show_selection);
        } else {
            show_selection = findViewById(show_selection_id);
        }
        show_selection.setText(getString(R.string.smb_path,
                ((TextInputEditText) findViewById(R.id.target_hostname)).getText().toString(),
                ((TextInputEditText) findViewById(R.id.target_sharename)).getText().toString(),
                selected_path
        ));

        Button save_selection;
        if ( save_selection_id == 0 ) {
            save_selection = new Button(context);
            save_selection_id = View.generateViewId();
            save_selection.setId(save_selection_id);
            save_selection.setOnClickListener(v -> SaveShareData() );
            save_selection.setText(R.string.save_share_data);
            layout.addView(save_selection);
        } else {
            save_selection = findViewById(save_selection_id);
        }
        save_selection.setTag(selected_path);

        // This will be used if they browse again:
        browse_button.setTag(selected_path);
    }

    private void SaveShareData(){
        String username = ((TextInputEditText) findViewById(R.id.target_username)).getText().toString();
        String password = ((TextInputEditText) findViewById(R.id.target_password)).getText().toString();
        String hostname = ((TextInputEditText) findViewById(R.id.target_hostname)).getText().toString();
        String sharename = ((TextInputEditText) findViewById(R.id.target_sharename)).getText().toString();
        String remotepath = browse_button.getTag().toString();

        Intent returnIntent = new Intent();
        returnIntent.putExtra("str-username", username);
        returnIntent.putExtra("str-password", password);
        returnIntent.putExtra("str-hostname", hostname);
        returnIntent.putExtra("str-sharename", sharename);
        returnIntent.putExtra("str-remotepath", remotepath);
        setResult(MainActivity.CODE_SUCCESS, returnIntent);
        finish();

    }

}