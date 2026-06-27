package com.mytube.offline.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mytube.offline.R;
import com.mytube.offline.data.DbHelper;
import com.mytube.offline.data.SessionManager;
import com.mytube.offline.util.PasswordUtil;

import android.database.Cursor;

/**
 * LoginActivity — handles the action=login POST handler from the PHP.
 *
 * Mirrors the PHP logic exactly:
 *   - Look up the user row by username
 *   - If found AND PasswordUtil.verify(password, stored_hash) -> save session, go home
 *   - Else show "Invalid username or password."
 *
 * Banned/suspended accounts are not implemented (the users table in this v1
 * has no is_banned column) — that's a deliberate scope cut for Core-5.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etUser, etPass;
    private DbHelper db;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        db      = new DbHelper(this);
        session = new SessionManager(this);

        etUser = findViewById(R.id.et_username);
        etPass = findViewById(R.id.et_password);

        Button btnLogin = findViewById(R.id.btn_login);
        TextView tvSignup = findViewById(R.id.tv_goto_signup);

        btnLogin.setOnClickListener(v -> doLogin());
        tvSignup.setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));
    }

    private void doLogin() {
        String username = etUser.getText().toString().trim();
        String password = etPass.getText().toString();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, R.string.err_fields_required, Toast.LENGTH_SHORT).show();
            return;
        }

        try (Cursor c = db.findUserByUsername(username)) {
            if (c.moveToFirst()) {
                int    id     = c.getInt   (c.getColumnIndexOrThrow(DbHelper.Users.ID));
                String stored = c.getString(c.getColumnIndexOrThrow(DbHelper.Users.PASSWORD));
                if (PasswordUtil.verify(password, stored)) {
                    session.saveLogin(id, username);
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                    finish();
                    return;
                }
            }
        }
        Toast.makeText(this, R.string.err_invalid_credentials, Toast.LENGTH_SHORT).show();
    }
}
