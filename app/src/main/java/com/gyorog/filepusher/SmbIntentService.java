package com.gyorog.filepusher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msdtyp.FileTime;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.protocol.transport.TransportException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.hierynomus.smbj.share.Share;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_DIRECTORY;

public class SmbIntentService extends JobIntentService {
    private static final String TAG = "com.gyorog.filepusher.SmbIntentService";
    private static final int JOB_ID = 29326;
    String action;
    String username;
    String password;
    String hostname;
    String sharename;
    String remotepath;
    DiskShare ds = null;

    public SmbIntentService() {
        super();
    }

    static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, SmbIntentService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) { // JobIntentService entry
        action = intent.getStringExtra("action");
        assert action != null;
        username = intent.getStringExtra("str-username");
        password = intent.getStringExtra("str-password");
        hostname = intent.getStringExtra("str-hostname");
        sharename = intent.getStringExtra("str-sharename");
        remotepath = intent.getStringExtra("str-remotepath");

        PendingIntent reply = intent.getParcelableExtra("pendintent-result");

        SMBClient client = null;
        Connection conn = null;
        Session ses = null;
        Share share = null;

        try {
            assert password != null;
            assert sharename != null;

            client = new SMBClient();
            conn = client.connect(hostname);
            Log.d(TAG, "Negotiated protocol " + conn.getNegotiatedProtocol() + " with " + getString(R.string.smb_path, hostname, sharename, remotepath));

            AuthenticationContext auth = new AuthenticationContext(username, password.toCharArray(), null);
            ses = conn.authenticate(auth);
            Log.d(TAG, "Authenticated as " + username + " for Session ID " + ses.getSessionId());

            share = ses.connectShare(sharename);
            if (share instanceof DiskShare) {
                ds = (DiskShare) share;
            } else {
                Log.e(TAG, "Share was not a disk share: " + getString(R.string.smb_path, hostname, sharename, ""));
            }
        } catch (IOException e) {
            Log.d(TAG, "Exception: " + e);
        }

        if ( action.equals("RequestPathContents") || action.equals("RequestPathContents-Dirs") || action.equals("RequestPathContents-Files") ) {

            ArrayList<String> pathList = new ArrayList<String>();

            Log.d(TAG, "Received intent to list " + getString(R.string.smb_path, hostname, sharename, remotepath) );

            List<FileIdBothDirectoryInformation> contents = ds.list(remotepath);

            for (FileIdBothDirectoryInformation item : contents) {
                String file_name = item.getFileName();
                long file_attributes = item.getFileAttributes();

                if (!(file_name.equals(".") || file_name.equals(".."))) {
                    if (action.equals("RequestPathContents-Dirs") && EnumWithValue.EnumUtils.isSet(file_attributes, FILE_ATTRIBUTE_DIRECTORY)) {
                        //Log.d(TAG, "accepted-Dirs: " + file_name);
                        pathList.add(file_name);
                    } else if (action.equals("RequestPathContents-Files") && !EnumWithValue.EnumUtils.isSet(file_attributes, FILE_ATTRIBUTE_DIRECTORY)) {
                        //Log.d(TAG, "accepted-Files: " + file_name);
                        pathList.add(file_name);
                    } else if (action.equals("RequestPathContents")) {
                        //Log.d(TAG, "accepted: " + file_name);
                        pathList.add(file_name);
                    }
                }
            }
            //Log.e(TAG, "Found list: " + pathlist.toString());




            // Assuming that your List is a list of strings make data an ArrayList<String> and use intent.putStringArrayListExtra("data", data)
            // private List<String> test;
            // test = new ArrayList<String>();

            // intent.putStringArrayListExtra("test", (ArrayList<String>) test);

            // ArrayList<String> test = getIntent().getStringArrayListExtra("test");

            // For more complicated list types: If you implement the Parcelable interface in your object then you can use the putParcelableArrayListExtra() method to add it to the Intent.

            try {
                if (reply != null) {
                    Log.d(TAG, "Returning path contents");
                    Intent result = new Intent();
                    result.putStringArrayListExtra("liststr-pathcontents", pathList);
                    result.putExtra("str-path", remotepath);
                    reply.send(this, MainActivity.CODE_SUCCESS, result);
                } else {
                    Log.e(TAG, "'reply' not received in intent.");
                }
            } catch (PendingIntent.CanceledException exc) {
                Log.i(TAG, "reply cancelled", exc);
            }


        } else if ( action.equals("RequestRecursiveReport") ) {
            String report_name = getString(R.string.recursive_report, System.currentTimeMillis());
            //Uri report_uri = null;
            FileOutputStream report_stream = null;
            try {
                //report_uri = Uri.fromFile( new java.io.File(report_name) );
                report_stream =  openFileOutput(report_name, Context.MODE_PRIVATE);

                RunRecursiveReport(report_stream, remotepath);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (report_stream != null) {
                    try {
                        report_stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                if (reply != null) {
                    Log.d(TAG, "Returning report filename");
                    Intent result = new Intent();
                    result.putExtra("str-report_name", report_name);
                    reply.send(this, MainActivity.CODE_SUCCESS, result);
                } else {
                    Log.e(TAG, "'reply' not received in intent.");
                }
            } catch (PendingIntent.CanceledException exc) {
                Log.i(TAG, "reply cancelled", exc);
            }

        } else if ( action.equals("WriteFile") ) {
            if (share instanceof DiskShare) {
                Uri source_uri = intent.getParcelableExtra("str-sourceurl");
                String filename = intent.getStringExtra("str-filename");
                boolean overwrite = intent.getBooleanExtra("opt-overwrite", false);

                String path_filename = remotepath + "/" + filename;

                DiskShare ds = (DiskShare) share;
                if( !overwrite && ds.fileExists(path_filename) ){
                    Log.e(TAG, "Already Exists");
                    //FIXME: return error
                } else {
                    InputStream instream = null;
                    try {
                        instream = getContentResolver().openInputStream(source_uri);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    File outfile = ds.openFile(path_filename,
                            EnumSet.of(AccessMask.FILE_READ_DATA, AccessMask.FILE_WRITE_DATA),
                            new HashSet<FileAttributes>(),
                            new HashSet<SMB2ShareAccess>(),
                            SMB2CreateDisposition.FILE_OVERWRITE_IF,
                            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE, SMB2CreateOptions.FILE_SEQUENTIAL_ONLY)
                    );
                    OutputStream outstream = outfile.getOutputStream();
                    byte[] buf = new byte[8192];
                    int length;

                    try {
                        while ((length = instream.read(buf)) > 0) {
                            outstream.write(buf, 0, length);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    reply.send(this, MainActivity.CODE_SUCCESS, new Intent());
                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }

            } else {
                Log.e(TAG, "Got WriteFile but share was not a DiskShare");
            }
        } else {
            Log.e(TAG, "Unrecognized action " + action);
        }

        // Log out from SMB session
        if (ses != null) {
            Log.d(TAG, "Logging off session...");
            try {
                ses.logoff();
            } catch (TransportException e) {
                e.printStackTrace();
            }
            //Log.d(TAG, "Closing session...");
            //ses.close(); // Execution would hang here, so I commented it out.
        }
        if (conn != null) {
            Log.d(TAG, "Closing connection...");
            try {
                conn.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (client != null) {
            Log.d(TAG, "Closing client...");
            client.close();
        }

    }

    private void RunRecursiveReport(FileOutputStream report_stream, String dir_path) throws IOException {
        // We do not start by listing dir_path itself.
        List<FileIdBothDirectoryInformation> contents = ds.list(dir_path);

        for (FileIdBothDirectoryInformation item : contents) {
            String file_name = item.getFileName();
            String file_fullpath = dir_path + "/" + file_name;
            long file_attributes = item.getFileAttributes();

            if (file_name.equals(".") || file_name.equals("..")) { continue; }

            FileTime mTime = item.getLastWriteTime();
            FileTime cTime = item.getChangeTime();

            if (EnumWithValue.EnumUtils.isSet(file_attributes, FILE_ATTRIBUTE_DIRECTORY)) {
                String line = "d " + mTime.toEpoch(TimeUnit.SECONDS) + " " + cTime.toEpoch(TimeUnit.SECONDS) + " " + file_fullpath + "\n";
                report_stream.write(line.getBytes());

                RunRecursiveReport(report_stream, file_fullpath);
            } else {
                long sizeInBytes = item.getEndOfFile();
                Log.d(TAG, "EOF: " + sizeInBytes + " and Allocation: " + item.getAllocationSize() + " for " + file_fullpath);
                String line = "f " + mTime.toEpoch(TimeUnit.SECONDS) + " " + cTime.toEpoch(TimeUnit.SECONDS) + " " + sizeInBytes + " " + file_fullpath + "\n";
                report_stream.write(line.getBytes());
            }

        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "All work complete");
    }

}


