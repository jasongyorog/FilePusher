package com.gyorog.filepusher;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;

public class EditScheduleActivity extends AppCompatActivity {
    public static final String TAG = "com.gyorog.filepusher.EditScheduleActivity";
    private Context context;
    private PendingIntent pendingResult;

    private String sched_name;
    private String localpath;
    private String username;
    private String password;
    private String hostname;
    private String sharename;
    private String remotepath;
    private String schedule;
    private String only_when_charging;

    private Boolean local_path_set = false;
    private Boolean remote_path_set = false;
    private int save_button_id = 0;
    private int save_and_run_button_id = 0;

    public EditScheduleActivity() {
        super();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();

        setContentView(R.layout.activity_edit_schedule);

        Spinner schedule_view = findViewById(R.id.spinner_schedule);
        String[] schedule_choices = new String[]{"daily 03:00", "daily 05:00", "daily 07:00", "daily 09:00", "daily 11:00", "daily 13:00", "daily 15:00", "daily 17:00", "daily 19:00", "daily 21:00", "daily 23:00", "daily 01:00" };
        // add support for "weekly 03:00 Sunday" after testing

        ArrayAdapter<String> schedule_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, schedule_choices);
        schedule_view.setAdapter( schedule_adapter );

        Intent args_intent = getIntent();
        sched_name = args_intent.getStringExtra("str-sched_name");
        if (sched_name != null) { // If sched_name is there, we assume all of them are there.
            // Log.d(TAG, "sched_name found in Intent. Filling in form details.");
            localpath = args_intent.getStringExtra("str-localpath");
            username = args_intent.getStringExtra("str-username");
            password = args_intent.getStringExtra("str-password");
            hostname = args_intent.getStringExtra("str-hostname");
            sharename = args_intent.getStringExtra("str-sharename");
            remotepath = args_intent.getStringExtra("str-remotepath");
            schedule = args_intent.getStringExtra("str-schedule");
            only_when_charging = args_intent.getStringExtra("str-onlywhencharging");

            ((TextInputEditText) findViewById(R.id.textinput_schedule_name)).setText( sched_name );

            ((TextView) findViewById(R.id.textview_localpath)).setText( localpath );
            local_path_set = true;

            ((TextView) findViewById(R.id.textview_remotepath)).setText( FormatRemotePath(args_intent) );
            remote_path_set = true;

            int selection_index = Arrays.asList(schedule_choices).indexOf(schedule);
            if ( selection_index != -1 ) {
                schedule_view.setSelection(Arrays.asList(schedule_choices).indexOf(schedule));
            } else {
                Log.d(TAG, "Found schedule of " + schedule + " which is no longer in the available list. Setting back to default.");
                schedule_view.setSelection(0);
                schedule = schedule_choices[0];
            }

            ((CheckBox) findViewById(R.id.checkbox_when_charging)).setChecked( "true".equals(only_when_charging) );
        } else {
            schedule_view.setSelection(0);
            schedule = schedule_choices[0];
        }

        Log.d(TAG, "Schedule set to " + schedule);

        findViewById(R.id.btn_browse_local).setOnClickListener(v -> {
            boolean gotStoragePermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            if (gotStoragePermission) {

                Intent intent = new Intent(context, BrowseLocalPathActivity.class);
                if (sched_name != null) {
                    // We got an intent, so use it.
                    intent.putExtra("str-localpath", localpath);
                }
                startActivityForResult(intent, MainActivity.CODE_GET_LOCAL_PATH);
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MainActivity.CODE_GET_STORAGE_PERMISSIONS);
            }
        });

        findViewById(R.id.btn_browse_remote).setOnClickListener(v -> {
            Intent intent = new Intent(context, SmbShareDataActivity.class);
            if (sched_name != null) {
                // We got an intent, so use it.
                intent.putExtra("str-username", username );
                intent.putExtra("str-password", password );
                intent.putExtra("str-hostname", hostname );
                intent.putExtra("str-sharename", sharename );
                intent.putExtra("str-remotepath", remotepath );
            }
            startActivityForResult(intent, MainActivity.CODE_GET_REMOTE_PATH);
        });

        schedule_view.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                schedule = (String) parent.getItemAtPosition(pos);
            }

            public void onNothingSelected(AdapterView<?> parent)
            {

            }
        });

        AddButtonIfReady();
    }

    private void AddButtonIfReady(){
        //Log.d(TAG, "local_path_set=" + local_path_set + " remote_path_set=" + remote_path_set);

        if (local_path_set && remote_path_set && save_button_id == 0) {
            Button save_schedule = new Button(context);
            save_button_id = View.generateViewId();
            save_schedule.setId(save_button_id);
            save_schedule.setText(R.string.save_schedule);
            save_schedule.setOnClickListener(v -> {
                Intent returnIntent = new Intent();
                returnIntent.putExtra("str-sched_name",((TextInputEditText) findViewById(R.id.textinput_schedule_name)).getText().toString());
                returnIntent.putExtra("str-localpath", localpath);
                returnIntent.putExtra("str-username", username);
                returnIntent.putExtra("str-password", password);
                returnIntent.putExtra("str-hostname", hostname);
                returnIntent.putExtra("str-sharename", sharename);
                returnIntent.putExtra("str-remotepath", remotepath);
                returnIntent.putExtra("str-schedule", schedule);

                if ( ((CheckBox) findViewById(R.id.checkbox_when_charging)).isChecked() ){
                    returnIntent.putExtra("str-onlywhencharging", "true");
                } else {
                    returnIntent.putExtra("str-onlywhencharging", "false");
                }

                setResult(MainActivity.CODE_SUCCESS, returnIntent);
                finish();
            });

            ((LinearLayout) findViewById(R.id.edit_schedule_layout)).addView(save_schedule);


            Button save_and_run_schedule = new Button(context);
            save_and_run_button_id = View.generateViewId();
            save_and_run_schedule.setId(save_and_run_button_id);
            save_and_run_schedule.setText(R.string.save_and_run_schedule);
            save_and_run_schedule.setOnClickListener(v -> {
                Intent returnIntent = new Intent();
                returnIntent.putExtra("str-sched_name",((TextInputEditText) findViewById(R.id.textinput_schedule_name)).getText().toString());
                returnIntent.putExtra("str-localpath", localpath);
                returnIntent.putExtra("str-username", username);
                returnIntent.putExtra("str-password", password);
                returnIntent.putExtra("str-hostname", hostname);
                returnIntent.putExtra("str-sharename", sharename);
                returnIntent.putExtra("str-remotepath", remotepath);
                returnIntent.putExtra("str-schedule", schedule);

                if ( ((CheckBox) findViewById(R.id.checkbox_when_charging)).isChecked() ){
                    returnIntent.putExtra("str-onlywhencharging", "true");
                } else {
                    returnIntent.putExtra("str-onlywhencharging", "false");
                }

                setResult(MainActivity.CODE_SAVE_AND_RUN, returnIntent);
                finish();
            });

            ((LinearLayout) findViewById(R.id.edit_schedule_layout)).addView(save_and_run_schedule);
        }
    }

    private String FormatRemotePath(Intent args_intent){
        return "smb://" + args_intent.getStringExtra("str-hostname") + "/" + args_intent.getStringExtra("str-sharename") + "/" + args_intent.getStringExtra("str-remotepath");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode) {
            case MainActivity.CODE_FAILURE:
                Log.d(TAG, "Error code returned");
                break;
            case MainActivity.CODE_SUCCESS:
                Log.d(TAG, "Success code returned");

                if (requestCode == MainActivity.CODE_GET_LOCAL_PATH) {
                    localpath = data.getStringExtra("str-localpath");
                    ((TextView) findViewById(R.id.textview_localpath)).setText(localpath);

                    local_path_set = true;
                    AddButtonIfReady();
                } else if (requestCode == MainActivity.CODE_GET_REMOTE_PATH) {
                    username = data.getStringExtra("str-username");
                    password = data.getStringExtra("str-password");
                    hostname = data.getStringExtra("str-hostname");
                    sharename = data.getStringExtra("str-sharename");
                    remotepath = data.getStringExtra("str-remotepath");
                    ((TextView) findViewById(R.id.textview_remotepath)).setText( FormatRemotePath(data) );

                    remote_path_set = true;
                    AddButtonIfReady();
                } else {
                    Log.e(TAG, "Received unexpected requestCode (expected " + MainActivity.CODE_GET_LOCAL_PATH + " or " + MainActivity.CODE_GET_REMOTE_PATH + " but got " + requestCode + ")");
                }
                break;
            case RESULT_CANCELED:
                break;
            default:
                Log.e(TAG, "Received unexpected result (expected " + MainActivity.CODE_FAILURE + " or " + MainActivity.CODE_SUCCESS + " but got " + resultCode + ")");
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}