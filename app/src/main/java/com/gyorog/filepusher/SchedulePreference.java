package com.gyorog.filepusher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class SchedulePreference {
    public static final String TAG = "com.gyorog.filepusher.SchedulePreference";
    private final String MASTER_KEY_ALIAS = "VinzClortho"; // https://ghostbusters.fandom.com/wiki/Vinz_Clortho
    private final int KEY_SIZE=256;

    private final Context context;
    private final String sched_name;
    private final SharedPreferences sharedPreferences;
    private boolean run_now = false;

    public SchedulePreference(Context context, String sched_name) throws GeneralSecurityException, IOException {
        this.context = context;
        this.sched_name = sched_name;
        this.sharedPreferences = OpenPreferencesFile();
    }

    public SchedulePreference(Context context, String sched_name, String localpath, String username, String password, String hostname, String sharename, String remotepath, String schedule, String only_when_charging) throws GeneralSecurityException, IOException {
        this.context = context;
        this.sched_name = sched_name;
        this.sharedPreferences = OpenPreferencesFile();
        if ("now".equals(schedule)) {
            run_now = true;
            // Don't save schedule parameters if this is the "now" instance.
        } else {
            SharedPreferences.Editor editor = this.sharedPreferences.edit();
            editor.putString("localpath", localpath);
            editor.putString("username", username);
            editor.putString("password", password);
            editor.putString("hostname", hostname);
            editor.putString("sharename", sharename);
            editor.putString("remotepath", remotepath);
            editor.putString("schedule", schedule);
            editor.putString("only_when_charging", only_when_charging);
            if (!editor.commit()) {
                Log.e(TAG, "Could not commit changes for " + sched_name);
            }
        }
    }

    private SharedPreferences OpenPreferencesFile() throws GeneralSecurityException, IOException {
        String file_name = "com.gyorog.SendToSmb.schedule_" + this.sched_name;

        KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .build();

        MasterKey master_key =  new MasterKey.Builder(context, MASTER_KEY_ALIAS).setKeyGenParameterSpec(keyGenParameterSpec).build();

        SharedPreferences sp = EncryptedSharedPreferences.create(context, file_name, master_key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );

        Log.d(TAG, "opened sharedPreferences for '" + this.sched_name + "': " + sp);
        return sp;
    }

    public String getString(String key) {
        // Log.d(TAG, "Getting " + key + " from " + sched_name);
        return sharedPreferences.getString(key, null);
    }

    public Intent getIntent(Class activity){
        Intent intent = new Intent(context, activity);
        intent.putExtra("str-sched_name", sched_name);
        intent.putExtra("str-localpath", getString("localpath"));
        intent.putExtra("str-username", getString("username") );
        intent.putExtra("str-password", getString("password") );
        intent.putExtra("str-hostname", getString("hostname") );
        intent.putExtra("str-sharename", getString("sharename") );
        intent.putExtra("str-remotepath", getString("remotepath")  );
        intent.putExtra("str-schedule", getString("schedule")  );
        intent.putExtra("str-onlywhencharging", getString("only_when_charging"));
//        intent.putExtra("str-job_id", getString("job_id"));
        return intent;
    }

    public void ScheduleJob(boolean cancel_existing) {
        String work_tag = "PushRecursive-" + sched_name;
        WorkManager wm = WorkManager.getInstance(context);

        if (cancel_existing) {
            List<WorkInfo> workinfos_list = null;
            long work_count = 0;
            try {
                workinfos_list = wm.getWorkInfosByTag(work_tag).get();
                for (WorkInfo work_info : workinfos_list) {
                    if (work_info.getState() == WorkInfo.State.RUNNING || work_info.getState() == WorkInfo.State.ENQUEUED) {
                        work_count++;
                    }
                }
                if (work_count > 0) {
                    Log.i(TAG, "Found " + work_count + " instances of " + work_tag + " running/enqueued. Cancelling all of them.");
                    wm.cancelAllWorkByTag(work_tag);
                }

                work_count = 0;
                workinfos_list = wm.getWorkInfosByTag("PushFile-" + sched_name).get();
                for (WorkInfo work_info : workinfos_list) {
                    if (work_info.getState() == WorkInfo.State.RUNNING || work_info.getState() == WorkInfo.State.ENQUEUED) {
                        work_count++;
                    }
                }
                if (work_count > 0) {
                    Log.i(TAG, "Found " + work_count + " instances of PushFile-" + sched_name + " running/enqueued. Cancelling all of them.");
                    wm.cancelAllWorkByTag(work_tag);
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        long wait_millis = 0;
        if (! run_now){
            long schedule_millis = 0;
            long now_millis = System.currentTimeMillis();

            String schedule[] = getString("schedule").split(" ");
            SimpleDateFormat date_only_formatter = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat date_time_formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            SimpleDateFormat day_of_week_formatter = new SimpleDateFormat("EEEE");
            Date now = new Date(now_millis);

            String day_of_week = null;
            if ("weekly".equals(schedule[0])) {
                day_of_week = schedule[2];
            }

            long millis_per_day = 86400000; // 86400 sec = 1 day
            try {
                schedule_millis = date_time_formatter.parse(date_only_formatter.format(now) + " " + schedule[1]).getTime();
            } catch (ParseException e) {
                e.printStackTrace();
                return;
            }
            while (schedule_millis < now_millis) {
                Log.d(TAG, schedule[1] + " has already passed today.");
                schedule_millis += millis_per_day;

            }
            if (day_of_week != null) {
                String schedule_dow = day_of_week_formatter.format(new Date(schedule_millis));
                while (!day_of_week.equals(schedule_dow)) {
                    Log.d(TAG, "Selected: " + day_of_week + " doesn't equal " + schedule_dow + ", so adding a day.");
                    schedule_millis += millis_per_day;
                    schedule_dow = day_of_week_formatter.format(new Date(schedule_millis));
                }
            }

            wait_millis = schedule_millis - now_millis;
            Log.d(TAG, "Calculated a wait time of " + wait_millis + " millis (" + (wait_millis / 60000) + " minutes) (" + (wait_millis / 3600000) + " hours)");
        }

        Data.Builder push_data_builder = new Data.Builder();
        push_data_builder.putString("action", "PushRecursive");
        push_data_builder.putString("str-remote_exists", "overwrite"); // someday implement "rename_remote"
        push_data_builder.putString("str-schedname", sched_name);
        push_data_builder.putString("str-localpath", getString("localpath"));
        push_data_builder.putString("str-username", getString("username"));
        push_data_builder.putString("str-password", getString("password"));
        push_data_builder.putString("str-hostname", getString("hostname"));
        push_data_builder.putString("str-sharename", getString("sharename"));
        push_data_builder.putString("str-remotepath", getString("remotepath"));
        push_data_builder.putString("str-onlywhencharging", getString("only_when_charging"));

        Log.d(TAG, "Setting str-onlywhencharging=" + getString("only_when_charging") + " in PushRecursive" );

        OneTimeWorkRequest.Builder push_builder = new OneTimeWorkRequest.Builder(SmbWorker.class)
                .addTag(work_tag)
                .setInitialDelay( wait_millis, TimeUnit.MILLISECONDS )
                .setInputData( push_data_builder.build());

        wm.enqueue( push_builder.build() );
    }
}
