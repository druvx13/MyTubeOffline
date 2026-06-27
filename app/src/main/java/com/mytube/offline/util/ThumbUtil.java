package com.mytube.offline.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;

/**
 * ThumbUtil — extracts a thumbnail frame from a video file and saves it as JPG.
 *
 * Equivalent of the PHP script's "thumbnail_data" base64 capture from the
 * <canvas> element on the upload page. We don't need a canvas here — Android's
 * MediaMetadataRetriever does the same job natively.
 *
 * The thumbnail is written into the same uploads/ directory and the filename
 * is returned to the caller, who persists it in the videos.thumbnail column.
 */
public final class ThumbUtil {

    private ThumbUtil() {}

    /**
     * Extracts a frame at ~1 second into the video and writes it as thumb_<suffix>.jpg
     * inside uploads/. Returns the filename (NOT the full path) so the DB stores the
     * same form as PHP ("thumb_abc.jpg"). Returns null on failure — caller treats
     * null as "no thumbnail available".
     */
    public static String extractThumbnail(Context ctx, Uri videoUri, String suffix) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(ctx, videoUri);

            // Try grabbing a frame at 1 second in — works for most clips.
            // Fall back to frameAtTime(0) if the video is shorter than that.
            Bitmap bmp = retriever.getFrameAtTime(1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bmp == null) {
                bmp = retriever.getFrameAtTime(0);
            }
            if (bmp == null) return null;

            String filename = "thumb_" + suffix + ".jpg";
            File out = FileUtil.thumbFile(ctx, filename);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                fos.flush();
            }
            //noinspection UnusedAssignment
            bmp.recycle();
            bmp = null;
            return filename;
        } catch (Exception e) {
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }
}
