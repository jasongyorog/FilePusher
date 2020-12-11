package com.gyorog.filepusher;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msdtyp.FileTime;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import com.hierynomus.msfscc.fileinformation.FileBasicInformation;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.msfscc.fileinformation.FileStandardInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.protocol.transport.TransportException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskEntry;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.Share;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_DIRECTORY;

public class SmbWorker extends Worker {
    private static final String TAG = "com.gyorog.filepusher.SmbWorker";
    private final Context context;
    private final WorkerParameters workerParams;

    public SmbWorker(Context context, WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        this.workerParams = workerParams;
    }

    @NonNull
    @Override
    public Result doWork() {
        Data.Builder outputData = new Data.Builder();
        ListenableWorker.Result result = null;

        Data in = getInputData();

        String action = in.getString("action");
        if (action == null) {
            Log.e(TAG, "Received null 'action' string in intent.");
            return Result.failure();
        }

        String remotepath = in.getString("str-remotepath");

        if (remotepath != null ) {
            Log.d(TAG, "Received " + action + " on smb://" + in.getString("str-sharename") + "/" + remotepath);
        } else {
            Log.d(TAG, "Received " + action + " on smb://" + in.getString("str-sharename") );
        }

        SmbWorkerShare share = null;
        String username = in.getString("str-username");
        String password = in.getString("str-password");
        String hostname = in.getString("str-hostname");
        String sharename = in.getString("str-sharename");

        String sched_name = in.getString("str-schedname");
        String log_lock_serial = in.getString("str-log_lock"); // Might be null
        String log_dir = in.getString("str-log_dir");

        String only_when_charging = in.getString("str-onlywhencharging");
        boolean requires_charging = "true".equals(only_when_charging);
        if ( requires_charging ){
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);

            int plug_status = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            if ( plug_status == BatteryManager.BATTERY_PLUGGED_USB || plug_status == BatteryManager.BATTERY_PLUGGED_AC || plug_status == BatteryManager.BATTERY_PLUGGED_WIRELESS ){
                if ( plug_status == BatteryManager.BATTERY_PLUGGED_USB ) {
                    Log.d(TAG, "USB is connected.");
                } else if ( plug_status == BatteryManager.BATTERY_PLUGGED_AC ) {
                    Log.d(TAG, "AC is connected.");
                } else {
                    Log.d(TAG, "Wireless Charging is connected.");
                }
            } else {
                Log.d(TAG, "Running on battery. Canceling work.");

                if (sched_name != null && action.equals("PushRecursive")) {
                    Log.d(TAG, "Scheduling next instance.");
                    try {
                        SchedulePreference sp = new SchedulePreference(context, sched_name);
                        sp.ScheduleJob(false);
                    } catch (GeneralSecurityException | IOException e) {
                        e.printStackTrace();
                    }
                }

                result = Result.failure();
                return result;
            }
        } else {
            Log.d(TAG, "Not checking power source before running.");
        }

        try {
            share = new SmbWorkerShare(sched_name, username, password, hostname, sharename, only_when_charging, log_lock_serial, log_dir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert share != null;

        if (action.equals("RequestPathContents")) {
            String list_type = in.getString("list_type");
            String[] pathList;

            if ("files".equals(list_type) ) {
                pathList = share.listDirContents(in.getString("str-remotepath"), true, false);
            } else {
                pathList = share.listDirContents(in.getString("str-remotepath"), false, true);
            }

            outputData.putStringArray("str[]-pathlist", pathList);
            result = Result.success(outputData.build());

        } else if (action.equals("PushRecursive")) {
            String localpath = in.getString("str-localpath");

            if ( sched_name != null ) {
                Log.d(TAG, "Found schedule name when starting PushRecursive. Scheduling next run...");
                try {
                    SchedulePreference sp = new SchedulePreference(context, sched_name);
                    sp.ScheduleJob(false);
                } catch (GeneralSecurityException | IOException e) {
                    e.printStackTrace();
                }
            }


            try {
                java.io.File dir_perms_file = new java.io.File(share.log_dir, context.getString(R.string.dir_perms));
                String dir_perms_file_name = dir_perms_file.getAbsolutePath();
                Log.d(TAG, "Opening dir_perms file " + dir_perms_file_name);
                BufferedWriter dir_perms = new BufferedWriter(new FileWriter(dir_perms_file));

                // output list
                ArrayList<OneTimeWorkRequest> push_file_list = new ArrayList<OneTimeWorkRequest>();

                long updated_files = share.pushItemRecursive(sched_name, localpath, remotepath, push_file_list, dir_perms);

                if (push_file_list.size() > 0) {
                    Data.Builder dir_perms_data_builder = new Data.Builder();
                    dir_perms_data_builder.putString("action", "ApplyDirPerms");
                    dir_perms_data_builder.putString("str-dirpermsfile", dir_perms_file_name);
                    dir_perms_data_builder.putString("str-username", username);
                    dir_perms_data_builder.putString("str-password", password);
                    dir_perms_data_builder.putString("str-hostname", hostname);
                    dir_perms_data_builder.putString("str-sharename", sharename);

                    Constraints constraints = new Constraints.Builder()
                            .setRequiresCharging(requires_charging)
                            .build();

                    OneTimeWorkRequest set_dir_perms = new OneTimeWorkRequest.Builder(SmbWorker.class)
                            .setConstraints(constraints)
                            .setInputData(dir_perms_data_builder.build()).build();

                    WorkManager.getInstance(context).beginWith(push_file_list).then(set_dir_perms).enqueue();
                    // WorkManager.getInstance(context).beginWith(push_file_list).enqueue();

                    Log.d(TAG, "Scheduled " + updated_files + " files for upload");
                } else {
                    Log.d(TAG, "No files needed transferring");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            result = Result.success(outputData.build());

        } else if (action.equals("PushFile")) {
            String localpath = in.getString("str-localpath");
            long mTime = in.getLong("long-mTime", -1);
            String remote_exists_behavior = in.getString("str-remote_exists");
            long resume = in.getLong("long-resume", 0);

            share.pushFile(sched_name, localpath, remotepath, mTime, remote_exists_behavior, resume);

            result = Result.success(outputData.build());

        } else if (action.equals("ApplyDirPerms")) {
            String dir_perms_file_name = in.getString("str-dirpermsfile");
            Log.d(TAG, "Would interpret " + dir_perms_file_name);

            share.SyncLog("(not implemented) should interpret " + dir_perms_file_name, "should interpret " + dir_perms_file_name);

            result = Result.success(outputData.build());

        } else if (action.equals("PushUrl")) {
            String sourceurl = in.getString("str-sourceurl");
            String filename = in.getString("str-filename");
            String remote_exists_behavior = in.getString("str-remote_exists");

            long now_millis = System.currentTimeMillis();
            share.pushFile(null, sourceurl, remotepath + "/" + filename, now_millis / 1000, remote_exists_behavior, 0);

            result = Result.success(outputData.build());

        } else {
            Log.e(TAG, "Unrecognized action " + action);
            result = Result.failure();
        }

        share.LogOut();
        return result;
    }

    private class SmbWorkerShare {
        final Context context;

        final String username;
        final String password;
        final String hostname;
        final String sharename;

        final String only_when_charging;

        private SMBClient client = null;
        private Connection conn = null;
        private Session ses = null;
        private Share share = null;
        private DiskShare ds = null;

        private String log_lock_serial = null;
        private Semaphore log_lock = null;
        public String log_dir = null;
        private String debug_log_filename = null;
        private String user_log_filename = null;

        public SmbWorkerShare(String job_name, String username, String password, String hostname, String sharename, String only_when_charging, String log_lock_serial, String log_dir) throws IOException {
            context = getApplicationContext();
            this.username = username;
            this.password = password;
            this.hostname = hostname;
            this.sharename = sharename;
            this.only_when_charging = only_when_charging;
            if (log_lock_serial != null) {
                this.log_lock_serial = log_lock_serial;
                try {
                    byte[] byte_array = Base64.decode(this.log_lock_serial, Base64.DEFAULT);
                    ByteArrayInputStream bi = new ByteArrayInputStream(byte_array);
                    ObjectInputStream si = new ObjectInputStream(bi);
                    this.log_lock = (Semaphore) si.readObject();
                } catch (ClassNotFoundException e){
                    e.printStackTrace();
                }

                // log_dir will also be defined
                this.log_dir = log_dir;
                this.debug_log_filename = new java.io.File(log_dir, context.getString(R.string.debug_log)).getAbsolutePath();
                this.user_log_filename = new java.io.File(log_dir, context.getString(R.string.user_log)).getAbsolutePath();
            } else {
                log_lock = new Semaphore(1, false);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                ObjectOutputStream so = new ObjectOutputStream(bo);
                so.writeObject(log_lock);
                so.close();
                this.log_lock_serial =  Base64.encodeToString( bo.toByteArray(), Base64.DEFAULT );
                bo.close();

                java.io.File e_files = context.getExternalFilesDir(null); // will be deleted when the app is uninstalled
                java.io.File log_dir_root = new java.io.File(e_files, context.getString(R.string.log_dir_root));
                java.io.File new_log_dir = new java.io.File(log_dir_root, context.getString(R.string.log_dir, job_name, System.currentTimeMillis()));
                this.log_dir = new_log_dir.getAbsolutePath();
                Log.d(TAG, "Creating " + this.log_dir );
                new_log_dir.mkdirs();
                SimpleDateFormat now = new SimpleDateFormat("YYYYMMdd-hhmmss", Locale.US);
                this.debug_log_filename = new java.io.File(new_log_dir, context.getString(R.string.debug_log)).getAbsolutePath();
                this.user_log_filename = new java.io.File(new_log_dir, context.getString(R.string.user_log)).getAbsolutePath();
            }


            long perf_start = System.currentTimeMillis();
            try {
                client = new SMBClient();
                conn = client.connect(hostname);

                AuthenticationContext auth = new AuthenticationContext(username, password.toCharArray(), null);
                ses = conn.authenticate(auth);

                share = ses.connectShare(sharename);
                if (share instanceof DiskShare) {
                    ds = (DiskShare) share;

                    long perf_share_opened = System.currentTimeMillis();
                    SyncLog(null, "Connected to " + getShareString() + " in " + (perf_share_opened - perf_start) + "ms");
                } else {
                    Log.e(TAG, "Share was not a disk share: " + getShareString() );
                    ds = null;
                }
            } catch (IOException e) {
                Log.d(TAG, "Exception during SMB connection: " + e);
            }

        }

        private void SyncLog(String user_message, String debug_message){
            try {
                long log_stamp = System.currentTimeMillis();
                if (log_lock != null) { log_lock.acquire(); }
                if (user_message != null) {
                    BufferedWriter user_log = new BufferedWriter(new FileWriter(user_log_filename, true));
                    user_log.write(user_message + "\n");
                    user_log.close();
                }
                if (debug_message != null) {
                    BufferedWriter debug_log = new BufferedWriter(new FileWriter(debug_log_filename, true));
                    debug_log.write("" + log_stamp + ": " + debug_message + "\n");
                    debug_log.close();
                }
                if (log_lock != null) { log_lock.release(); }
            } catch (InterruptedException|IOException e){
                e.printStackTrace();
            }

        }

        public String getShareString(String path){
            return this.context.getString(R.string.smb_path, hostname, sharename, path);
        }
        public String getShareString(){ return getShareString("");}

        public String[] listDirContents(String path, boolean list_files, boolean list_dirs){
            ArrayList<String> pathList = new ArrayList<String>();

            for (FileIdBothDirectoryInformation item : ds.list(path)) {
                String file_name = item.getFileName();
                long file_attributes = item.getFileAttributes();
                boolean is_dir = EnumWithValue.EnumUtils.isSet(file_attributes, FILE_ATTRIBUTE_DIRECTORY);

                if ( ! (file_name.equals(".") || file_name.equals("..")) ) {
                    if (list_dirs && is_dir){
                        pathList.add(file_name);
                    } else if (list_files & !is_dir){
                        pathList.add(file_name);
                    }
                }
            }
            return (String[]) pathList.toArray();
        }

        public void pushFile(String sched_name, String localpath, String remotepath, long mTime, String remote_exists_behavior, long resume){
            if ( resume == 0){
                if ( ! "overwrite".equals(remote_exists_behavior) ) {
                    if ( ds.fileExists(remotepath) ){
                        Log.d(TAG, "Skipping " + remotepath );
                        return;
                    } else {
                        Log.d(TAG, "No existing file at " + remotepath);
                    }
                } else {
                    Log.d(TAG, "Overwrite enabled. Don't care if there's a file at " + remotepath);
                    SyncLog(null, "Overwrite enabled. Don't care if there's a file at " + remotepath);
                }
            } else {
                Log.d(TAG, "Resuming " + remotepath + " at " + resume + " bytes.");
                SyncLog(null, "Resuming " + remotepath + " at " + resume + " bytes.");
            }

            if ("Temp/SMBsync_test/Camera/VID_20200819_151059_1.mp4".equals(remotepath)){
                Log.d(TAG, "Finishing this stupid file.");
                resume = 3874488320L;
            }

            RandomAccessFile instream = null;
            boolean instream_seek_successful = false;
            try {
                //instream = new FileInputStream(localpath);
                instream = new RandomAccessFile(localpath, "r");
                if ( resume != 0 ){
                    instream.seek(resume);
                }
                instream_seek_successful = true;
            } catch (IOException e) {
                e.printStackTrace();
            }

            long perf_start = System.currentTimeMillis();
            com.hierynomus.smbj.share.File outfile = ds.openFile(remotepath,
                    EnumSet.of(AccessMask.FILE_READ_DATA, AccessMask.FILE_READ_ATTRIBUTES, AccessMask.FILE_WRITE_DATA, AccessMask.FILE_WRITE_ATTRIBUTES),
                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE, SMB2CreateOptions.FILE_SEQUENTIAL_ONLY)
            );
//            OutputStream outstream = outfile.getOutputStream();

            int buffer_size = 65536;
            long written_by_this_worker = 0;
            byte[] buf = new byte[buffer_size];
            int bytes_read, bytes_written;
            boolean schedule_resume = false;

            try {
                while ((bytes_read = instream.read(buf)) > 0) {
                    bytes_written = outfile.write(buf, resume + written_by_this_worker, 0, bytes_read);
                    written_by_this_worker += bytes_written;
//                    outstream.write(buf, 0, length);
                    if ( bytes_read != bytes_written ){
                        Log.e(TAG, "Unable to write into " + remotepath);
                        break;
                    }

                    if( System.currentTimeMillis() - perf_start > 540000 ) { // If we've been running 9 minutes
                        schedule_resume = true;
                        Log.d(TAG, "Detected 9 minute work time on " + remotepath + ". Scheduling a new worker...");
                        break;
                    }

                    if( isStopped() ) {
                        Log.e(TAG, "Task interrupted early");
                        break;
                    }
                }
                instream.close();
//                outstream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            outfile.close();
            long perf_transferred = System.currentTimeMillis();

            if( schedule_resume ) {
                String work_tag;
                String sched_tag;
                if ( sched_name != null) {
                    work_tag = "PushFile-" + sched_name + "-" + remotepath;
                    WorkManager.getInstance(context).cancelAllWorkByTag(work_tag);
                    sched_tag = "PushFile-" + sched_name;
                } else {
                    work_tag = "PushFile-" + remotepath;
                    sched_tag = "PushFile";
                }

                Log.e(TAG, "Scheduling resume at " + (resume + written_by_this_worker) + " for " + work_tag );
                SyncLog(null, "Scheduling resume at " + (resume + written_by_this_worker) + " for " + work_tag );

                Data.Builder push_data_builder = new Data.Builder();
                push_data_builder.putString("action", "PushFile");
                push_data_builder.putLong("long-resume", resume + written_by_this_worker);
                push_data_builder.putString("str-localpath", localpath);
                push_data_builder.putLong("long-mTime", mTime);
                push_data_builder.putString("str-username", username);
                push_data_builder.putString("str-password", password);
                push_data_builder.putString("str-hostname", hostname);
                push_data_builder.putString("str-sharename", sharename);
                push_data_builder.putString("str-remotepath", remotepath);
                push_data_builder.putString("str-onlywhencharging", only_when_charging);
                push_data_builder.putString("str-log_lock", log_lock_serial);
                push_data_builder.putString("str-log_dir", log_dir);

                Log.d(TAG, "Setting str-onlywhencharging=" + only_when_charging + " in " + work_tag);

                Constraints constraints = new Constraints.Builder()
                        .setRequiresCharging( "true".equals(only_when_charging) )
                        .build();

                OneTimeWorkRequest.Builder push_builder = new OneTimeWorkRequest.Builder(SmbWorker.class)
                        .addTag(work_tag)
                        .addTag(sched_tag)
                        .setConstraints(constraints)
                        .setInputData( push_data_builder.build() );

                WorkManager.getInstance(context).enqueue( push_builder.build() );
            } else if (!isStopped()) {
                Log.d(TAG, "Copy reached EOF successfully: " + remotepath);
                SyncLog("Copied " + remotepath, "Copy reached EOF successfully: " + remotepath);

                PathEntry foo = new PathEntry(this, remotepath);
                foo.setInfo(mTime);
            }


            Log.d(TAG, "Transferred " + written_by_this_worker + " bytes in " + (perf_transferred - perf_start) + " ms -> " + (written_by_this_worker / 1000 / (perf_transferred - perf_start)) + "MB/s");
            SyncLog(null, "Transferred " + written_by_this_worker + " bytes in " + (perf_transferred - perf_start) + " ms -> " + (written_by_this_worker / 1000 / (perf_transferred - perf_start)) + "MB/s");
        }

        private long pushItemRecursive(String sched_name, String localpath, String remotepath, ArrayList<OneTimeWorkRequest> push_file_list, BufferedWriter dir_perms) {
            long updated_files = 0;
            long perf_start = System.currentTimeMillis();

            java.io.File local_item = new java.io.File(localpath);
            //BasicFileAttributes local_basic_info = Files.readAttributes(localpath,  BasicFileAttributes.class);
            long local_mTime = local_item.lastModified() / 1000;
            boolean local_isDir = local_item.isDirectory();
            long local_file_size = -1;
            if (!local_isDir) {
                local_file_size = local_item.length();
                //Log.d(TAG, "local_file_size=" + local_file_size);
            }

            long perf_read_local = System.currentTimeMillis();

            PathEntry remote_item = new PathEntry(this, remotepath);

            long perf_read_remote = System.currentTimeMillis();

            // Log.d(TAG, "local_mTime=" + local_mTime + " remote_mTime=" + remote_item.mTime);

            if (local_isDir) {
                boolean dir_created = false;
                if ( ! remote_item.exists ){
                    Log.d(TAG, "mkdir('" + remotepath + "')");
                    ds.mkdir(remotepath);
                    dir_created = true;

                    try {
                        // Add dir permissions to file for final worker task
                        dir_perms.write(local_mTime + " " + remotepath + "\n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Ready to iterate over children.
                } else {
                    if ( ! remote_item.isDir ) {
                        Log.e(TAG, "Mismatch: (dir) " + localpath + " != (file) " + remotepath);
                        // No need to iterate over children.
                        return 0;
                    }
                }
                Log.d(TAG, "process children('" + localpath + "')");

                // Also log whether dir_created when logging this.
                long perf_decision_made = System.currentTimeMillis();
                long perf_children_processed;
                java.io.File[] local_children;

                try {
                    local_children = local_item.listFiles();
                    assert local_children != null;

                    long[] child_timer = new long[local_children.length];
                    long child_timer_start = System.currentTimeMillis();

                    for(int i=0; i<local_children.length; ++i){
                        String file_name = local_children[i].getName();
                        //Log.d(TAG, "Sending PushRecursive " + localpath + "/" + file_name);
                        long child_updated_files = pushItemRecursive(sched_name, localpath + "/" + file_name, remotepath + "/" + file_name, push_file_list, dir_perms);
                        updated_files += child_updated_files;
                        //Log.d(TAG, "Scheduled upload for " + child_updated_files + " files from " + localpath + "/" + file_name);
                        child_timer[i] = System.currentTimeMillis();
                    }
                    perf_children_processed = System.currentTimeMillis();

                    long fastest_child=0, slowest_child=0, child_total=0;
                    for(int i=0; i<local_children.length; ++i){
                        long child_time;
                        if (i == 0) {
                            child_time = child_timer[i] - child_timer_start;
                        } else {
                            child_time = child_timer[i] - child_timer[i-1];
                        }
                        child_total += child_time;
                        if ( fastest_child == 0 || child_time < fastest_child ) {
                            fastest_child = child_time;
                        }
                        if ( slowest_child == 0 || child_time > slowest_child ) {
                            slowest_child = child_time;
                        }
                    }
                    if (local_children.length > 0) {
                        // Log.d(TAG, "Processed " + local_children.length + " children of " + localpath + ". fastest=" + fastest_child + " slowest=" + slowest_child + " mean=" + (child_total / local_children.length));
                        SyncLog(null, "Processed " + local_children.length + " children of " + localpath + ". fastest=" + fastest_child + " slowest=" + slowest_child + " mean=" + (child_total / local_children.length));
                    }

                } catch(Exception e){
                    Log.e(TAG, "Caught exception: " + e);
                    e.printStackTrace();
                    return updated_files;
                }

                SyncLog(null, "@" + perf_start +
                        " read_local=" + (perf_read_local - perf_start) +
                        " readremote=" + (perf_read_remote - perf_read_local) +
                        " decision_made=" + (perf_decision_made - perf_read_remote) + " (dir_created=" + dir_created + ")" +
                        " " + local_children.length + " children_processed=" + (perf_children_processed - perf_decision_made)
                );
            } else { // local item is a file
                if ( remote_item.exists ) {
                    if ((local_mTime == remote_item.mTime) && (local_file_size == remote_item.file_size)) {
                        //Log.d(TAG, "skip('" + remotepath + "')");
                        return 0;
                    } else {
                        Log.d(TAG, "" + local_mTime + "!=" + remote_item.mTime + " or " + local_file_size + "!=" + remote_item.file_size);
                    }
                } else {
                    Log.d(TAG, "new_file('" + remotepath + "')");
                }

                long perf_decision_made = System.currentTimeMillis();

                String work_tag;
                String sched_tag;
                if ( sched_name != null) {
                    work_tag = "PushFile-" + sched_name + "-" + remotepath;
                    WorkManager.getInstance(context).cancelAllWorkByTag(work_tag);
                    sched_tag = "PushFile-" + sched_name;
                } else {
                    work_tag = "PushFile-" + remotepath;
                    sched_tag = "PushFile";
                }

                Data.Builder push_data_builder = new Data.Builder();
                push_data_builder.putString("action", "PushFile");
                push_data_builder.putString("str-schedname", sched_name);
                push_data_builder.putString("str-remote_exists", "overwrite");
                push_data_builder.putString("str-localpath", localpath);
                push_data_builder.putLong("long-mTime", local_mTime);
                push_data_builder.putString("str-username", username);
                push_data_builder.putString("str-password", password);
                push_data_builder.putString("str-hostname", hostname);
                push_data_builder.putString("str-sharename", sharename);
                push_data_builder.putString("str-remotepath", remotepath);
                push_data_builder.putString("str-onlywhencharging", only_when_charging);
                push_data_builder.putString("str-log_lock", log_lock_serial);
                push_data_builder.putString("str-log_dir", log_dir);

                Constraints constraints = new Constraints.Builder()
                        .setRequiresCharging( "true".equals(only_when_charging) )
                        .build();

                OneTimeWorkRequest.Builder push_builder = new OneTimeWorkRequest.Builder(SmbWorker.class)
                        .addTag(work_tag)
                        .addTag(sched_tag)
                        .setConstraints(constraints)
                        .setInputData( push_data_builder.build() );

                push_file_list.add( push_builder.build() );

                long job_scheduled = System.currentTimeMillis();

                SyncLog(null, "@" + perf_start +
                        " read_local=" + (perf_read_local - perf_start) +
                        " readremote=" + (perf_read_remote - perf_read_local) +
                        " decision_made=" + (perf_decision_made - perf_read_remote) +
                        " job_scheduled=" + (job_scheduled - perf_decision_made)
                );

            }

            try {
                dir_perms.close();
            } catch (IOException e){
                e.printStackTrace();
            }

            return updated_files;
        }

        public void LogOut() {
            long perf_start = System.currentTimeMillis();
            if (share != null){
                try {
                    share.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }

            if (ses != null) {
                try {
                    ses.logoff();
                } catch (TransportException e) {
                    e.printStackTrace();
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (client != null) {
                client.close();
            }

            long perf_logged_out = System.currentTimeMillis();

            SyncLog(null, "Logged out in " + (perf_logged_out - perf_start) + " ms");


        }

    }

    public class PathEntry {
        private SmbWorkerShare share;
        private DiskEntry de;
        String path;

        boolean exists;

        FileAllInformation all_info = null;
        FileBasicInformation basic_info = null;
        long mTime;
        FileStandardInformation standard_info = null;
        boolean isDir;
        long file_size;


        public PathEntry(SmbWorkerShare share, String path){
            this.share = share;
            this.path = path;

            if (share.ds.fileExists(path) || share.ds.folderExists(path)) {
                exists = true;

                this.de = share.ds.open(path,
                        EnumSet.of(AccessMask.FILE_READ_DATA, AccessMask.FILE_WRITE_DATA, AccessMask.FILE_READ_ATTRIBUTES, AccessMask.FILE_WRITE_ATTRIBUTES),
                        EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                        EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                        SMB2CreateDisposition.FILE_OPEN,
                        new HashSet<SMB2CreateOptions>()
                );

                FileAllInformation all_info = de.getFileInformation();
                basic_info = all_info.getBasicInformation();
                FileStandardInformation standard_info = all_info.getStandardInformation();
                mTime = basic_info.getLastWriteTime().toEpoch(TimeUnit.SECONDS);
                isDir = standard_info.isDirectory();
                if (!isDir) {
                    file_size = standard_info.getEndOfFile();
                    //Log.d(TAG, "remote_file_size=" + file_size);
                }

                this.de.close();
            } else {
                exists = false;
            }
        }

        public void setInfo(long new_mTime){
            this.de = share.ds.open(path,
                    EnumSet.of(AccessMask.FILE_READ_DATA, AccessMask.FILE_WRITE_DATA, AccessMask.FILE_READ_ATTRIBUTES, AccessMask.FILE_WRITE_ATTRIBUTES),
                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                    SMB2CreateDisposition.FILE_OPEN,
                    new HashSet<SMB2CreateOptions>()
            );

            FileBasicInformation new_basic_info = new FileBasicInformation(
                    basic_info.getCreationTime(),
                    basic_info.getLastAccessTime(),
                    FileTime.ofEpoch(new_mTime, TimeUnit.SECONDS),
                    basic_info.getChangeTime(),
                    basic_info.getFileAttributes()
            );
            this.de.setFileInformation(new_basic_info);
            this.de.close();
        }

    }
}
