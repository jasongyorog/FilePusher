package com.gyorog.filepusher;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

public class BrowseRemotePathActivity extends AppCompatActivity {
    static String TAG = "com.gyorog.filepusher.BrowseRemotePathActivity";
    String username;
    String password;
    String hostname;
    String sharename;
    String remotepath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.please_wait);

        Intent args_intent = getIntent();
        username = args_intent.getStringExtra("str-username");
        password = args_intent.getStringExtra("str-password");
        hostname = args_intent.getStringExtra("str-hostname");
        sharename = args_intent.getStringExtra("str-sharename");

        ShowPath(args_intent.getStringExtra("str-remotepath"));
    }

    private void ShowPath(String path) {
        Log.d(TAG, "Requesting Show Path " + path);
        remotepath = path;

        Intent work_intent = new Intent(getApplicationContext(), SmbIntentService.class);
        work_intent.putExtra("action", "RequestPathContents-Dirs");
        work_intent.putExtra("str-username", username);
        work_intent.putExtra("str-password", password);
        work_intent.putExtra("str-hostname", hostname);
        work_intent.putExtra("str-sharename", sharename);
        work_intent.putExtra("str-remotepath", path);

        PendingIntent pendingResult = createPendingResult(MainActivity.CODE_GET_PATH_CONTENTS, new Intent(), 0);
        work_intent.putExtra("pendintent-result", pendingResult);

        SmbIntentService.enqueueWork(getApplicationContext(), work_intent);


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MainActivity.CODE_GET_PATH_CONTENTS) {
            switch (resultCode) {
                case MainActivity.CODE_FAILURE:
                    Log.d(TAG, "Error code returned");
                    break;
                case MainActivity.CODE_SUCCESS:
                    Log.d(TAG, "Success code returned");
                    DrawWidgets(data);
                    break;
                default:
                    Log.e(TAG, "Received unexpected result (expected " + MainActivity.CODE_FAILURE + " or " + MainActivity.CODE_SUCCESS + " but got " + resultCode + ")");
            }
        } else {
            Log.e(TAG, "Received unexpected requestCode (expected " + MainActivity.CODE_GET_PATH_CONTENTS + " but got " + requestCode + ")");
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void DrawWidgets(Intent data) {
        setContentView(R.layout.activity_browse_path);

        LinearLayout layout = findViewById(R.id.layout_browse);
        Context context = layout.getContext();

        ArrayList<String> pathList = data.getStringArrayListExtra("liststr-pathcontents");

        TextView choose_text = new TextView(context);
        choose_text.setText(getString(R.string.smb_path, hostname, sharename, remotepath));
        layout.addView(choose_text);

        Button choose_this = new Button(context);
        choose_this.setText(R.string.choose_path);
        choose_this.setOnClickListener(v -> {
            Log.d(TAG, "chosen_path = " + remotepath);

            Intent returnIntent = new Intent();
            returnIntent.putExtra("str-remotepath", remotepath);
            setResult(MainActivity.CODE_SUCCESS, returnIntent);
            finish();
        });
        layout.addView(choose_this);


        if (!remotepath.equals("")) {
            Button go_up = new Button(context);
            go_up.setText(R.string.go_up);
            go_up.setOnClickListener(v -> {
                // Paths are assumed to always be directories and have no leading slash and no trailing slash.
                if (remotepath.contains("/")) {
                    int index = remotepath.lastIndexOf('/');
                    ShowPath(remotepath.substring(0, index));
                } else {
                    // If there is no slash, there is only one path component.
                    ShowPath("");
                }
            });
            layout.addView(go_up);
        }

        for (int i = 0; i < pathList.size(); i++) {
            TextView textview = new TextView(context);
            //textview.setText( getString(R.string.foldericon) + " " + pathList.get(i) );
            textview.setText(pathList.get(i));
            textview.setBackground(ContextCompat.getDrawable(context, R.drawable.textview_border));
            textview.setPadding(10, 10, 10, 10);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(10, 10, 10, 10);
            textview.setLayoutParams(params);
            textview.setOnClickListener(v -> {
                TextView tv = (TextView) v;
                String folder_name = (String) tv.getText();
                String new_path;
                if (remotepath.equals("")) {
                    new_path = folder_name;
                } else {
                    new_path = remotepath + "/" + folder_name;
                }
                ShowPath(new_path);
            });
            layout.addView(textview);
        }

    }
}
