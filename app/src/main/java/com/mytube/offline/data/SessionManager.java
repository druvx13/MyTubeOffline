package com.mytube.offline.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SessionManager — the Android equivalent of PHP's $_SESSION['user_id'].
 *
 * The PHP code uses session_start() + a cookie to persist login across
 * requests. Android has no native equivalent — we use SharedPreferences
 * to hold the active user id and username. Bcrypt hash of the password
 * is NOT stored here; only the user id of the authenticated account.
 */
public class SessionManager {

    private static final String PREFS = "mytube_session";
    private static final String K_UID = "user_id";
    private static final String K_UNAME = "username";

    private final SharedPreferences prefs;

    public SessionManager(Context ctx) {
        // getApplicationContext() so we don't leak an Activity.
        this.prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveLogin(int userId, String username) {
        prefs.edit()
                .putInt   (K_UID,   userId)
                .putString(K_UNAME, username)
                .apply();
    }

    public void logout() {
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return prefs.contains(K_UID);
    }

    public int getUserId() {
        return prefs.getInt(K_UID, -1);
    }

    public String getUsername() {
        return prefs.getString(K_UNAME, null);
    }
}
