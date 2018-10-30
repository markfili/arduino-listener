package hr.mfilipovic.arduinolistener;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

class Logger {

    private static final String LOGS_DIR = "/logs";
    private static final String TAG = "Logger";
    private Context mContext;
    private File mLogsDir;
    private File mStorageDirectory;
    private String mStorageDirectoryPath;

    Logger(Context context) throws IOException {
        mContext = context;
        checkPermissions();
        checkLogsDirectory();
    }

    private void checkPermissions() throws IOException {
        boolean granted = ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            throw new IOException("Write not permitted. Check app permissions.");
        }
    }

    private void checkLogsDirectory() throws IOException {
        mStorageDirectory = Environment.getExternalStorageDirectory();
        mStorageDirectoryPath = mStorageDirectory.getPath();
        mLogsDir = checkOrCreateFile(LOGS_DIR);
    }

    private File checkOrCreateFile(String fileName) throws IOException {
        if (!fileName.startsWith(mStorageDirectoryPath)) {
            fileName = mStorageDirectoryPath + fileName;
        }
        Log.i(TAG, "Checking for '" + fileName + "' in '" + mStorageDirectoryPath + "'.");
        File file = new File(fileName);
        if (file.exists()) {
            Log.i(TAG, "'" + fileName + "' exists.");
        } else if (file.isDirectory()) {
            create(fileName, file.mkdirs());
        } else {
            Log.i(TAG, "delete parent file " + file.getParentFile().delete());
            Log.i(TAG, "create parent file " + file.getParentFile().mkdirs());
            create(fileName, file.createNewFile());
        }
        Log.i(TAG, "Returning '" + fileName + "'.");
        return file;
    }

    private void create(String fileName, boolean created) {
        if (created) {
            Log.i(TAG, "'" + fileName + "' created.");
        } else {
            Log.i(TAG, "Unable to create '" + fileName + "'.");
        }
    }

    File createNewLogFile() throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String fileName = mStorageDirectory.getPath() + "/"
                + mLogsDir.getName()
                + "/log_" + sdf.format(Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.getDefault()).getTime())
                + ".txt";
        return checkOrCreateFile(fileName);
    }
}
