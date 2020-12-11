package com.gyorog.filepusher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PreferenceManager {
    public static final String TAG = "com.gyorog.filepusher.PreferenceManager";
    Context context;

    public PreferenceManager(Context app_context) {
        context = app_context;
    }

    public static List<String> ListScheduleNames(Context context) {
        LinkedList<String> sched_list = new LinkedList<>();
        ApplicationInfo app_info = null;

        try {
            app_info = context.getPackageManager().getApplicationInfo("com.gyorog.smbsync", 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "getApplicationInfo(com.gyorog.smbsync, 0) failed\n" + e.toString());
            return sched_list;
        }

        File shared_prefs_dir = new File(app_info.dataDir, "shared_prefs");
        if (shared_prefs_dir.exists() && shared_prefs_dir.isDirectory()) {
            String[] xml_list = shared_prefs_dir.list(new XmlOnly());
            Pattern pattern = Pattern.compile("schedule_(.*?)\\.xml"); // Double-backslash to tell Java it's a real backslash in the string.

            for (int i = 0; i < xml_list.length; ++i) {
                Matcher matcher = pattern.matcher(xml_list[i]);
                if (matcher.find()) {
                    sched_list.add(matcher.group(1));
                }
            }
        }
        return sched_list;
    }

    public static void DeleteAllSchedules(Context context) {
        ApplicationInfo app_info = null;
        try {
            app_info = context.getPackageManager().getApplicationInfo("com.gyorog.smbsync", 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "getApplicationInfo(com.gyorog.smbsync, 0) failed\n" + e.toString());
            return;
        }
        File shared_prefs_dir = new File(app_info.dataDir, "shared_prefs");
        String[] shared_prefs_files = shared_prefs_dir.list();
        for (String shared_prefs_file : shared_prefs_files) {
            new File(shared_prefs_dir, shared_prefs_file).delete();
        }
    }

    public static class XmlOnly implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return name.endsWith(".xml");
        }
    }
}
