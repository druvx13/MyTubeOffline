package com.mytube.offline.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mytube.offline.R;
import com.mytube.offline.adapter.VideoAdapter;
import com.mytube.offline.data.DbHelper;
import com.mytube.offline.data.SessionManager;

import java.util.List;

/**
 * MainActivity — the home feed (page=home in the PHP).
 *
 * Shows every uploaded video in a vertical RecyclerView, newest first.
 * Top bar carries the classic MyTube logo + Login/Account/Upload menu.
 * If the user is not logged in and taps Upload, they're pushed to Login.
 */
public class MainActivity extends AppCompatActivity {

    private DbHelper        db;
    private SessionManager  session;
    private VideoAdapter    adapter;
    private TextView        emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db      = new DbHelper(this);
        session = new SessionManager(this);

        // ---- Top bar logo: "My" red bold + "Tube" dark bold ----
        TextView logo = findViewById(R.id.logo);
        SpannableStringBuilder sb = new SpannableStringBuilder("MyTube");
        sb.setSpan(new ForegroundColorSpan(0xFFCC0000), 0, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(0xFF333333), 2, 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 2, 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        logo.setText(sb);

        emptyView = findViewById(R.id.empty_view);

        // Header 3-dot button opens the same overflow menu that the hardware menu key would.
        findViewById(R.id.btn_menu).setOnClickListener(v -> openOptionsMenu());

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VideoAdapter(this, this::openWatch);
        rv.setAdapter(adapter);

        findViewById(R.id.fab_upload).setOnClickListener(v -> {
            if (!session.isLoggedIn()) {
                startActivity(new Intent(this, LoginActivity.class));
            } else {
                startActivity(new Intent(this, UploadActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-query on every resume so newly uploaded videos appear immediately.
        List<DbHelper.VideoRow> videos = db.allVideos();
        adapter.submit(videos);
        emptyView.setVisibility(videos.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openWatch(DbHelper.VideoRow row) {
        Intent i = new Intent(this, WatchActivity.class);
        i.putExtra(WatchActivity.EXTRA_VIDEO_ID, row.videoId);
        startActivity(i);
    }

    // ----- options menu (Login / Account / Logout) ---------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean in = session.isLoggedIn();
        menu.findItem(R.id.menu_login).setVisible(!in);
        menu.findItem(R.id.menu_account).setVisible(in);
        menu.findItem(R.id.menu_logout).setVisible(in);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_login) {
            startActivity(new Intent(this, LoginActivity.class));
            return true;
        }
        if (id == R.id.menu_account) {
            startActivity(new Intent(this, AccountActivity.class));
            return true;
        }
        if (id == R.id.menu_logout) {
            session.logout();
            invalidateOptionsMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
