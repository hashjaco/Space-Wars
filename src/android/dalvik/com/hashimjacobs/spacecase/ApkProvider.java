package com.hashimjacobs.spacecase;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lets Android's package installer read a downloaded update, which lives in this app's private
 * files where nothing else can see it.
 *
 * Serves exactly one file, files/update/SpaceCase.apk, read-only, whatever the path asked for.
 * Not exported: the installer reaches it only through the one-off read grant TvActivity attaches
 * to its install request. androidx's FileProvider does this job in most apps; it is not here
 * because this class is compiled against the bare Android API (see build.sh).
 */
public class ApkProvider extends ContentProvider {

    /** Matches the android:authorities in AndroidManifest.xml. */
    static final String AUTHORITY = "com.hashimjacobs.spacecase.apk";

    static final String APK_TYPE = "application/vnd.android.package-archive";

    static Uri uri() {
        return Uri.parse("content://" + AUTHORITY + "/SpaceCase.apk");
    }

    static File apk(android.content.Context context) {
        return new File(new File(context.getFilesDir(), "update"), "SpaceCase.apk");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("read only");
        }
        return ParcelFileDescriptor.open(apk(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return APK_TYPE;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
