package com.mytube.offline.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mytube.offline.R;
import com.mytube.offline.data.DbHelper;
import com.mytube.offline.util.PasswordUtil;

/**
 * SignupActivity — handles the action=signup POST handler from the PHP.
 *
 * Mirrors the PHP:
 *   - Validate that username, email and password are all non-empty
 *   - Reject duplicate username or email (SQLite UNIQUE constraints + our pre-check)
 *   - Hash the password with PasswordUtil (PBKDF2 in Java, bcrypt-equivalent in PHP)
 *   - On success: take the user to the login screen
 */
public class SignupActivity extends AppCompatActivity {

    private EditText etUser, etEmail, etPass;
    private DbHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        db = new DbHelper(this);

        etUser  = findViewById(R.id.et_username);
        etEmail = findViewById(R.id.et_email);
        etPass  = findViewById(R.id.et_password);

        Button btnSignup = findViewById(R.id.btn_signup);
        TextView tvLogin = findViewById(R.id.tv_goto_login);

        btnSignup.setOnClickListener(v -> doSignup());
        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void doSignup() {
        String username = etUser.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPass.getText().toString();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, R.string.err_fields_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.err_invalid_email, Toast.LENGTH_SHORT).show();
            return;
        }
        if (db.userExists(username, email)) {
            Toast.makeText(this, R.string.err_user_exists, Toast.LENGTH_SHORT).show();
            return;
        }

        String hash = PasswordUtil.hash(password);
        long id = db.insertUser(username, email, hash);
        if (id > 0) {
            Toast.makeText(this, R.string.signup_success, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        } else {
            Toast.makeText(this, R.string.err_signup_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
