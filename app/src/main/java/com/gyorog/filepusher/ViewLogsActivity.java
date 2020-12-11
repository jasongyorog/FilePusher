package com.gyorog.filepusher;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ViewLogsActivity extends AppCompatActivity {
    public static final String TAG = "com.gyorog.filepusher.ViewLogsActivity";

    private String log_dir = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_view_logs);

        LinearLayout layout = findViewById(R.id.layout_logview);
        Context context = layout.getContext();

        File e_files = context.getExternalFilesDir(null); // will be deleted when the app is uninstalled
        File log_dir_root = new File(e_files, context.getString(R.string.log_dir_root));

        File[] log_dirs = log_dir_root.listFiles();
        if( log_dirs != null) {
            Log.d(TAG, "Found " + log_dirs.length + " log dirs.");

            Pattern log_date_pattern = Pattern.compile("run_([^_]*)_at_([^_]*)");
            for (int i = 0; i < log_dirs.length; i++) {
                if (log_dirs[i].isDirectory()) {
                    String dir_name = log_dirs[i].getName();
                    File log_dir = new File(log_dir_root, dir_name );
                    File user_log = new File(log_dir, context.getString(R.string.user_log));
                    if ( user_log.exists() ) {
                        Log.d(TAG, "Showing " + user_log.getAbsolutePath() );

                        TextView title_view = new TextView(context);

                        Matcher log_date_matcher = log_date_pattern.matcher(dir_name);
                        String title_text;
                        if ( log_date_matcher.find() ) {
                            Calendar cal = Calendar.getInstance(Locale.ENGLISH);
                            cal.setTimeInMillis( Long.parseLong( log_date_matcher.group(2) ) );
                            title_text = "Task " + log_date_matcher.group(1) + " at " + DateFormat.format("yyyy-MM-dd h:mm:ss a", cal).toString();
                        } else {
                            title_text = dir_name;
                        }

                        title_view.setText(title_text);
                        title_view.setTypeface(null, Typeface.BOLD );
                        layout.addView(title_view);

                        String log_contents;
                        try {
                            StringBuilder log_contents_builder = new StringBuilder();
                            BufferedReader user_log_reader = new BufferedReader(new FileReader(user_log.getAbsolutePath()));
                            String line = user_log_reader.readLine();
                            while (line != null) {
                                log_contents_builder.append(line + "\n");
                                line = user_log_reader.readLine();
                            }
                            user_log_reader.close();
                            log_contents = log_contents_builder.toString();
                        } catch (Exception e){
                            log_contents = "Exception " + e;
                        }

                        TextView contents_view = new TextView(context);
                        contents_view.setHorizontallyScrolling(true);
                        contents_view.setText(log_contents);
                        layout.addView(contents_view);
                    }

                }
            }

            ScrollView scrollView = findViewById(R.id.layout_scrollview);
            scrollView.postDelayed(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN),100);
        }
    }
}
