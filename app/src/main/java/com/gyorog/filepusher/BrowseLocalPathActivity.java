package com.gyorog.filepusher;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrowseLocalPathActivity extends AppCompatActivity {
    public static final String TAG = "com.gyorog.filepusher.BrowseLocalPathActivity";
    String localpath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent args_intent = getIntent();

        String intent_path = args_intent.getStringExtra("str-localpath");
        if (intent_path == null) {
            ShowPath("/");
        } else {
            ShowPath(intent_path);
        }
    }

    private void ShowPath(String path) {
        localpath = path;
        setContentView(R.layout.activity_browse_path);

        LinearLayout layout = findViewById(R.id.layout_browse);
        Context context = layout.getContext();

        if ("/".equals(localpath) ) {

            // Unfortunately, listing directories is a pain with Android. From ADB shell, I can see these, but not from an app:
            // SD cards at /storage/8va9-ava09
            // CARD_NAMES=$(ls /storage | grep -v "emulated\|self")

            TextView textview;
            File[] media_dirs = context.getExternalMediaDirs();
            if( media_dirs != null) {
                Log.d(TAG, "Found " + media_dirs.length + " dirs from context.getExternalMediaDirs()");
                Pattern pattern = Pattern.compile("/storage/(.*?)/Android.*");
                for (int i = 0; i < media_dirs.length; i++) {
                    if ( media_dirs[i] != null) {
                        Matcher matcher = pattern.matcher( media_dirs[i].getAbsolutePath() );
                        if (matcher.find()) {
                            if ("emulated/0".equals( matcher.group(1) ) ){
                                textview = MakePathItem(context, "Internal Storage", "/storage/emulated/0");
                            } else {
                                textview = MakePathItem(context, "Device " + matcher.group(1), "/storage/" + matcher.group(1));
                            }
                            layout.addView(textview);
                        }
                    }
                }
            }

/*
            // This is also returned by getExternalMediaDirs() above.
            TextView textview = MakePathItem(context, "Internal Storage", "/storage/emulated/0");
            layout.addView(textview);
*/

/*
            // This one captured "ES: /sdcard"
            try {
                final String ExternalStorage = System.getenv("EXTERNAL_STORAGE");
                textview = MakePathItem(context, "ES: " + ExternalStorage, ExternalStorage);
                layout.addView(textview);

                final String SecondaryStorage = System.getenv("SECONDARY_STORAGE");
                if (SecondaryStorage != null ) {
                    final String[] SecondaryStorageList = SecondaryStorage.split(File.pathSeparator);
                    for (int i = 0; i < SecondaryStorageList.length; i++) {
                        textview = MakePathItem(context, "SS: " + SecondaryStorageList[i], SecondaryStorageList[i]);
                        layout.addView(textview);
                    }
                }
            } catch(Exception e){
                Log.e(TAG, "Exception: " + e);
            }

*/

/*
            // This seems to simply not be allowed by Android
            File directory = new File("/storage/");
            File[] files = directory.listFiles();
            if( files != null) {
                Log.d(TAG, "Found " + files.length + " files in /storage/");
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        String file_name_lcase = files[i].getName().toLowerCase();
                        if ((!"emulated".equals(file_name_lcase)) && (!"self".equals(file_name_lcase))) {
                            textview = MakePathItem(context, "External Storage (SD?) " + files[i].getName(), "/storage/" + files[i].getName());
                            layout.addView(textview);
                        }
                    }
                }
            }
 */

        } else {
            TextView choose_text = new TextView(context);
            choose_text.setText(localpath);
            layout.addView(choose_text);

            Button choose_this = new Button(context);
            choose_this.setText(R.string.choose_path);
            choose_this.setTag(localpath);
            choose_this.setOnClickListener(v -> {
                Log.d(TAG, "Choose path " + localpath);

                Intent returnIntent = new Intent();
                returnIntent.putExtra("str-localpath", localpath);
                setResult(MainActivity.CODE_SUCCESS, returnIntent);
                finish();
            });
            layout.addView(choose_this);


            Button go_up = new Button(context);
            go_up.setText(R.string.go_up);
            go_up.setOnClickListener(v -> {
                // Paths are assumed to always be directories and have no leading slash and no trailing slash.
                int index = localpath.lastIndexOf('/');
                String newpath =  localpath.substring(0, index);
                if ( "/storage".equals(newpath) || "/storage/emulated".equals(newpath) ) {
                    ShowPath("/");
                } else {
                    ShowPath(localpath.substring(0, index));
                }
            });
            layout.addView(go_up);

            File directory = new File(localpath);

            File[] files = directory.listFiles();
            if (files != null) {
                Log.d(TAG, "Found " + files.length + " files in " + localpath);
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        TextView textview = MakePathItem(context, files[i].getName(), localpath + "/" + files[i].getName());
                        layout.addView(textview);
                        //Log.d(TAG, "added " + files[i].getName());
                    }
                }
            } else {
                Log.e(TAG, "Found no files in " + localpath);
            }
        }
    }

    TextView MakePathItem(Context context, String item_name, String browse_path) {
        TextView textview = new TextView(context);
        textview.setText( item_name );
        textview.setBackground(ContextCompat.getDrawable(context, R.drawable.textview_border));
        textview.setPadding(10, 10, 10, 10);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(10, 10, 10, 10);
        textview.setLayoutParams(params);
        textview.setTag(browse_path);
        textview.setOnClickListener(v -> {
            TextView tv = (TextView) v;
            ShowPath( (String) tv.getTag() );
        });

        return textview;
    }
}
