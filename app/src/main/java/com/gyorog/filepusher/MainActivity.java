package com.gyorog.filepusher;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity {
    static String TAG = "com.gyorog.filepusher.MainActivity";
    public static final int CODE_SUCCESS = 1337;
    public static final int CODE_FAILURE = 1001;

    public static final int CODE_GET_SCHEDULE = 42;
    public static final int CODE_GET_LOCAL_PATH = 43;
    public static final int CODE_GET_STORAGE_PERMISSIONS = 44;
    public static final int CODE_GET_REMOTE_PATH = 45;
    public static final int CODE_GET_PATH_CONTENTS = 46;
    public static final int CODE_SAVE_AND_RUN = 47;

    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        DrawWidgets();
    }

    private void DrawWidgets(){
        boolean found_schedules = false;
        setContentView(R.layout.activity_main);

        final Button button = findViewById(R.id.btn_add_schedule);
        button.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), EditScheduleActivity.class);
//            PendingIntent pendingResult = createPendingResult(R.integer.code_get_schedule, new Intent(), 0);
//            intent.putExtra("pendintent-result", pendingResult);
            startActivityForResult(intent, MainActivity.CODE_GET_SCHEDULE);
        });

        final LinearLayout layout = findViewById(R.id.main_layout);

        Iterator<String> sched_iter = PreferenceManager.ListScheduleNames(context).iterator();

        if(sched_iter.hasNext()) {
            found_schedules = true;
            TextView tv = new TextView(context);
            tv.setText(R.string.schedules_found);
            layout.addView(tv);
        }

        while(sched_iter.hasNext()) {
            String sched_name = sched_iter.next();
            Button btn = new Button(context);
            //btn.setTag(sched_name);
            btn.setText(getString(R.string.edit_schedule, sched_name));
            btn.setOnClickListener(v -> {
                //String sched_name = (String) v.getTag();
                try {
                    Intent intent = new SchedulePreference(context, sched_name).getIntent(EditScheduleActivity.class);
//                    PendingIntent pendingResult = createPendingResult(MainActivity.CODE_GET_SCHEDULE, new Intent(), 0);
//                    intent.putExtra("pendintent-result", pendingResult);
                    startActivityForResult(intent, MainActivity.CODE_GET_SCHEDULE);
                } catch (GeneralSecurityException | IOException e) {
                    e.printStackTrace();
                }
            });
            layout.addView(btn);
        }

        if(found_schedules){
            Button btn = new Button(context);
            btn.setText(R.string.delete_schedules);
            btn.setOnClickListener(v -> {
                PreferenceManager.DeleteAllSchedules(context);
                DrawWidgets();
            });
            layout.addView(btn);
        }

        Button btn = new Button(context);
        btn.setText(R.string.view_logs);
        btn.setOnClickListener(v -> {
            Intent intent = new Intent(context, ViewLogsActivity.class);
            startActivity(intent);
        });
        layout.addView(btn);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MainActivity.CODE_GET_SCHEDULE) {
            switch (resultCode) {
                case CODE_FAILURE:
                    Log.d(TAG, "Error code returned");
                    break;
                case CODE_SUCCESS:
                    Log.d(TAG, "Success code returned");
                    ScheduleJob(data, "later");
                    break;
                case CODE_SAVE_AND_RUN:
                    Log.d(TAG, "Save and run code returned");
                    // Must run "later" before "now", as "later" will clear out any existing jobs.
                    ScheduleJob(data, "later");
                    ScheduleJob(data, "now");
                    break;
                case RESULT_CANCELED:
                    break;
                default:
                    Log.e(TAG, "Received unexpected result (expected " + MainActivity.CODE_SUCCESS + " or " + MainActivity.CODE_FAILURE + " but got " + resultCode + ")");
            }
        } else {
            Log.e(TAG, "Received unexpected requestCode from AddScheduleActivity (expected " + MainActivity.CODE_GET_SCHEDULE + " but got " + requestCode + ")");
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void ScheduleJob(Intent data, String when) {
        String schedule_time;
        if ("now".equals(when)){
            schedule_time = "now";
        } else {
            schedule_time = data.getStringExtra("str-schedule");
        }

        try {
            String sched_name = data.getStringExtra("str-sched_name");
            if( sched_name == null ) {
                Log.e(TAG, "Couldn't find str-sched_name in Intent.");
            } else {
                Log.d(TAG, "Applying values to " + sched_name);
                SchedulePreference new_sched = new SchedulePreference(context, sched_name,
                        data.getStringExtra("str-localpath"),
                        data.getStringExtra("str-username"),
                        data.getStringExtra("str-password"),
                        data.getStringExtra("str-hostname"),
                        data.getStringExtra("str-sharename"),
                        data.getStringExtra("str-remotepath"),
                        schedule_time,
                        data.getStringExtra("str-onlywhencharging"));
                if ("now".equals(when)) {
                    new_sched.ScheduleJob(false);
                } else {
                    new_sched.ScheduleJob(true);
                }
            }
            DrawWidgets(); // Redraws page
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }
}