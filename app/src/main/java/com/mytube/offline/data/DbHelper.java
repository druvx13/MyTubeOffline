package com.mytube.offline.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * DbHelper — the SQLite equivalent of the PHP file's Section 1 (DB migration).
 *
 * The PHP creates 5 tables: users, videos, comments, likes, contact_messages.
 * For Core-5 scope (signup/login + upload + watch + account) we only need
 * users and videos. The schema intentionally mirrors the PHP columns so
 * a future drop-in of comments / likes is trivial.
 */
public class DbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "mytube.db";
    private static final int    DB_VERSION = 1;

    public static final String T_USERS  = "users";
    public static final String T_VIDEOS = "videos";

    public static final class Users {
        public static final String ID              = "id";
        public static final String USERNAME        = "username";
        public static final String EMAIL           = "email";
        public static final String PASSWORD        = "password";   // bcrypt-hash text
        public static final String PROFILE_PICTURE = "profile_picture";
        public static final String CREATED_AT      = "created_at";
    }

    public static final class Videos {
        public static final String ID          = "id";
        public static final String USER_ID     = "user_id";
        public static final String VIDEO_ID    = "video_id";    // 11-char public id (PHP generate_video_id)
        public static final String TITLE       = "title";
        public static final String DESCRIPTION = "description";
        public static final String FILENAME    = "filename";    // file inside uploads/
        public static final String THUMBNAIL   = "thumbnail";   // file inside uploads/
        public static final String VIEWS       = "views";
        public static final String UPLOAD_DATE = "upload_date";
    }

    public DbHelper(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // ---- users ----
        db.execSQL("CREATE TABLE " + T_USERS + " (" +
                Users.ID              + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                Users.USERNAME        + " TEXT NOT NULL UNIQUE, " +
                Users.EMAIL           + " TEXT NOT NULL UNIQUE, " +
                Users.PASSWORD        + " TEXT NOT NULL, " +
                Users.PROFILE_PICTURE + " TEXT, " +
                Users.CREATED_AT      + " TEXT DEFAULT CURRENT_TIMESTAMP)");

        // ---- videos ----
        db.execSQL("CREATE TABLE " + T_VIDEOS + " (" +
                Videos.ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                Videos.USER_ID     + " INTEGER NOT NULL, " +
                Videos.VIDEO_ID    + " TEXT NOT NULL UNIQUE, " +
                Videos.TITLE       + " TEXT NOT NULL, " +
                Videos.DESCRIPTION + " TEXT, " +
                Videos.FILENAME    + " TEXT NOT NULL, " +
                Videos.THUMBNAIL   + " TEXT, " +
                Videos.VIEWS       + " INTEGER NOT NULL DEFAULT 0, " +
                Videos.UPLOAD_DATE + " TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY(" + Videos.USER_ID + ") REFERENCES " + T_USERS + "(" + Users.ID + ") ON DELETE CASCADE)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // v1 is the first schema — drop & recreate for future revisions.
        db.execSQL("DROP TABLE IF EXISTS " + T_VIDEOS);
        db.execSQL("DROP TABLE IF EXISTS " + T_USERS);
        onCreate(db);
    }

    // =========================================================================
    //  USERS
    // =========================================================================

    /** Returns the user row if username exists (caller verifies bcrypt hash). */
    public Cursor findUserByUsername(String username) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(T_USERS, null,
                Users.USERNAME + " = ?",
                new String[]{ username },
                null, null, null);
    }

    /** Returns true if a user with this username OR email already exists. */
    public boolean userExists(String username, String email) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT 1 FROM " + T_USERS +
                " WHERE " + Users.USERNAME + " = ? OR " + Users.EMAIL + " = ? LIMIT 1",
                new String[]{ username, email });
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    /** Inserts a new user; returns the new row id, or -1 on conflict. */
    public long insertUser(String username, String email, String bcryptHash) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(Users.USERNAME, username);
        cv.put(Users.EMAIL,    email);
        cv.put(Users.PASSWORD, bcryptHash);
        return db.insert(T_USERS, null, cv);
    }

    public String getUsername(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_USERS,
                new String[]{ Users.USERNAME },
                Users.ID + " = ?",
                new String[]{ String.valueOf(userId) },
                null, null, null);
        String name = null;
        if (c.moveToFirst()) name = c.getString(0);
        c.close();
        return name;
    }

    // =========================================================================
    //  VIDEOS
    // =========================================================================

    public long insertVideo(int userId, String videoId, String title,
                            String description, String filename, String thumbnail) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(Videos.USER_ID,     userId);
        cv.put(Videos.VIDEO_ID,    videoId);
        cv.put(Videos.TITLE,       title);
        cv.put(Videos.DESCRIPTION, description);
        cv.put(Videos.FILENAME,    filename);
        cv.put(Videos.THUMBNAIL,   thumbnail);
        cv.put(Videos.VIEWS,       0);
        return db.insert(T_VIDEOS, null, cv);
    }

    /** Returns ALL videos, newest first — used by the home feed. */
    public List<VideoRow> allVideos() {
        return queryVideos(null, null);
    }

    /** Returns videos uploaded by the given user — used by the Account page. */
    public List<VideoRow> videosByUser(int userId) {
        return queryVideos(Videos.USER_ID + " = ?",
                new String[]{ String.valueOf(userId) });
    }

    /** Looks up a single video by its public 11-char video_id — used by Watch. */
    public VideoRow findVideoByPublicId(String publicId) {
        List<VideoRow> rows = queryVideos(Videos.VIDEO_ID + " = ?",
                new String[]{ publicId });
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<VideoRow> queryVideos(String where, String[] args) {
        SQLiteDatabase db = getReadableDatabase();
        // JOIN with users to fetch uploader's username in one shot — same as PHP does on the homepage.
        String sql = "SELECT v." + Videos.ID + ", v." + Videos.VIDEO_ID + ", v." + Videos.TITLE +
                ", v." + Videos.DESCRIPTION + ", v." + Videos.FILENAME + ", v." + Videos.THUMBNAIL +
                ", v." + Videos.VIEWS + ", v." + Videos.UPLOAD_DATE +
                ", u." + Users.USERNAME +
                " FROM " + T_VIDEOS + " v" +
                " INNER JOIN " + T_USERS + " u ON u." + Users.ID + " = v." + Videos.USER_ID;
        if (where != null) sql += " WHERE " + where;
        sql += " ORDER BY v." + Videos.UPLOAD_DATE + " DESC";

        Cursor c = db.rawQuery(sql, args);
        List<VideoRow> out = new ArrayList<>(c.getCount());
        while (c.moveToNext()) {
            VideoRow v = new VideoRow();
            v.id          = c.getInt   (0);
            v.videoId     = c.getString(1);
            v.title       = c.getString(2);
            v.description = c.getString(3);
            v.filename    = c.getString(4);
            v.thumbnail   = c.getString(5);
            v.views       = c.getInt   (6);
            v.uploadDate  = c.getString(7);
            v.uploader    = c.getString(8);
            out.add(v);
        }
        c.close();
        return out;
    }

    public void incrementViews(int videoRowId) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + T_VIDEOS + " SET " + Videos.VIEWS + " = " + Videos.VIEWS + " + 1" +
                " WHERE " + Videos.ID + " = " + videoRowId);
    }

    /** Deletes the video row (caller is responsible for deleting the files). */
    public int deleteVideo(int videoRowId, int ownerId) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(T_VIDEOS,
                Videos.ID + " = ? AND " + Videos.USER_ID + " = ?",
                new String[]{ String.valueOf(videoRowId), String.valueOf(ownerId) });
    }

    /** Plain row container — kept as a static inner class so the adapter/UI can iterate it cheaply. */
    public static class VideoRow {
        public int    id;
        public String videoId;
        public String title;
        public String description;
        public String filename;
        public String thumbnail;
        public int    views;
        public String uploadDate;
        public String uploader;
    }
}
