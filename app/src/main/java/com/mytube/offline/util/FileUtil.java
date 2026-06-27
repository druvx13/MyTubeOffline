package com.mytube.offline.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * FileUtil — copies a content:// URI picked via ACTION_GET_CONTENT
 * into the app's private "uploads" directory. This mirrors PHP's
 * move_uploaded_file() to uploads/.
 *
 * Storage location: getExternalFilesDir(null)/uploads/
 *   - No storage permission needed (app-scoped on API 19+)
 *   - Wiped when the app is uninstalled — matches PHP "uploads/"
 *     being a sub-folder of the app, not a shared media location.
 */
public final class FileUtil {

    public static final String UPLOADS_DIR = "uploads";

    private FileUtil() {}

    /** Returns (creating if needed) the app-private uploads directory. */
    public static File uploadsDir(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(null), UPLOADS_DIR);
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return dir;
    }

    public static File videoFile(Context ctx, String filename) {
        return new File(uploadsDir(ctx), filename);
    }

    public static File thumbFile(Context ctx, String filename) {
        return new File(uploadsDir(ctx), filename);
    }

    /**
     * Copies a content URI into the uploads dir with the given target filename.
     * Returns the destination File on success, null on failure.
     */
    public static File copyUriToUploads(Context ctx, Uri source, String destFilename) {
        File out = new File(uploadsDir(ctx), destFilename);
        try (InputStream in  = ctx.getContentResolver().openInputStream(source);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) return null;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            fos.flush();
            return out;
        } catch (Exception e) {
            // Best-effort cleanup of a half-written file.
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return null;
        }
    }

    /** Extracts the file extension (without dot, lowercase) from a display name URI. */
    public static String extensionOf(Context ctx, Uri uri) {
        String name = displayName(ctx, uri);
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Tries to read the display name from the ContentResolver; falls back to the URI's last path segment. */
    public static String displayName(Context ctx, Uri uri) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(uri, new String[]{ OpenableColumns.DISPLAY_NAME },
                    null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        String last = uri.getLastPathSegment();
        return last == null ? "file" : last;
    }
}
