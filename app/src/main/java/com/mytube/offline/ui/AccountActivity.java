package com.mytube.offline.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mytube.offline.R;
import com.mytube.offline.adapter.VideoAdapter;
import com.mytube.offline.data.DbHelper;
import com.mytube.offline.data.SessionManager;
import com.mytube.offline.util.FileUtil;

import java.io.File;
import java.util.List;

/**
 * AccountActivity — page=account in the PHP.
 *
 * Shows the logged-in user's profile header + the list of videos they
 * uploaded. Each video row has a "Delete" button which mirrors the
 * action=delete_video handler:
 *   - Delete the video file and thumbnail file from uploads/
 *   - Delete the DB row
 *   - Refresh the list
 *
 * If the user is not logged in, they're bounced to LoginActivity.
 */
public class AccountActivity extends AppCompatActivity
        implements VideoAdapter.OnItemClickListener {

    private DbHelper        db;
    private SessionManager  session;
    private VideoAdapter    adapter;
    private TextView        tvUsername, tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        db      = new DbHelper(this);
        session = new SessionManager(this);

        if (!session.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        tvUsername = findViewById(R.id.tv_username);
        tvEmpty    = findViewById(R.id.tv_empty);
        tvUsername.setText(session.getUsername());

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VideoAdapter(this, this);
        adapter.setShowDelete(true, this::deleteVideo);
        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<DbHelper.VideoRow> mine = db.videosByUser(session.getUserId());
        adapter.submit(mine);
        tvEmpty.setVisibility(mine.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void deleteVideo(DbHelper.VideoRow row) {
        // 1. Delete video file
        File vf = FileUtil.videoFile(this, row.filename);
        //noinspection ResultOfMethodCallIgnored
        vf.delete();
        // 2. Delete thumbnail file
        if (row.thumbnail != null) {
            File tf = FileUtil.thumbFile(this, row.thumbnail);
            //noinspection ResultOfMethodCallIgnored
            tf.delete();
        }
        // 3. Delete DB row
        db.deleteVideo(row.id, session.getUserId());
        // 4. Refresh list
        adapter.submit(db.videosByUser(session.getUserId()));
        tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClick(DbHelper.VideoRow row) {
        Intent i = new Intent(this, WatchActivity.class);
        i.putExtra(WatchActivity.EXTRA_VIDEO_ID, row.videoId);
        startActivity(i);
    }
}
