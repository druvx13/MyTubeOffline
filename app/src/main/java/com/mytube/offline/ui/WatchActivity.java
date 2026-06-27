package com.mytube.offline.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.mytube.offline.R;
import com.mytube.offline.data.DbHelper;

import java.io.File;
import java.util.Locale;

import android.net.Uri;

/**
 * WatchActivity — page=watch in the PHP. Plays a video and shows its
 * metadata (title, views, uploader, description, upload date).
 *
 * Player choice: VideoView. It is built into Android, has zero gradle
 * dependencies, and AIDE Pro can compile it without needing ExoPlayer
 * or internet access. The downside: VideoView's stock MediaController
 * is ugly and uses Material styling; we replace it with our own custom
 * seek bar + play/pause button that fits the 2006 retro look.
 *
 * The view counter is incremented once per WatchActivity creation —
 * same as PHP incrementing views when the watch page is loaded.
 */
public class WatchActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "video_id";

    private DbHelper db;
    private DbHelper.VideoRow video;

    private VideoView  videoView;
    private ImageButton btnPlay;
    private SeekBar     seekBar;
    private TextView    tvCurrent, tvDuration;
    private TextView    tvTitle, tvUploader, tvViews, tvDesc, tvDate;
    private ProgressBar loading;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean userIsSeeking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch);

        db = new DbHelper(this);

        String publicId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        if (TextUtils.isEmpty(publicId)) { finish(); return; }
        video = db.findVideoByPublicId(publicId);
        if (video == null) {
            Toast.makeText(this, R.string.err_video_not_found, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Bind views
        videoView   = findViewById(R.id.video_view);
        btnPlay     = findViewById(R.id.btn_play);
        seekBar     = findViewById(R.id.seekbar);
        tvCurrent   = findViewById(R.id.tv_current);
        tvDuration  = findViewById(R.id.tv_duration);
        tvTitle     = findViewById(R.id.tv_title);
        tvUploader  = findViewById(R.id.tv_uploader);
        tvViews     = findViewById(R.id.tv_views);
        tvDesc      = findViewById(R.id.tv_desc);
        tvDate      = findViewById(R.id.tv_date);
        loading     = findViewById(R.id.loading);

        // Populate metadata
        tvTitle.setText(video.title);
        tvUploader.setText(getString(R.string.by_uploader, video.uploader == null ? "?" : video.uploader));
        tvViews.setText(getString(R.string.views_count, video.views));
        if (!TextUtils.isEmpty(video.uploadDate)) tvDate.setText(video.uploadDate);
        if (!TextUtils.isEmpty(video.description)) tvDesc.setText(video.description);
        else tvDesc.setText(R.string.no_description);

        // Configure player
        File file = FileUtil.videoFile(this, video.filename);
        if (!file.exists()) {
            Toast.makeText(this, R.string.err_video_file_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        videoView.setVideoURI(Uri.fromFile(file));
        videoView.setOnPreparedListener(mp -> {
            loading.setVisibility(View.GONE);
            int dur = videoView.getDuration();
            seekBar.setMax(dur);
            tvDuration.setText(formatTime(dur));
            // Auto-start playback once prepared.
            videoView.start();
            btnPlay.setImageResource(R.drawable.ic_pause);
            handler.post(updateTicker);
        });
        videoView.setOnCompletionListener(mp -> {
            btnPlay.setImageResource(R.drawable.ic_play);
            seekBar.setProgress(0);
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, R.string.err_playback, Toast.LENGTH_LONG).show();
            finish();
            return true;
        });

        btnPlay.setOnClickListener(v -> {
            if (videoView.isPlaying()) {
                videoView.pause();
                btnPlay.setImageResource(R.drawable.ic_play);
            } else {
                videoView.start();
                btnPlay.setImageResource(R.drawable.ic_pause);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) tvCurrent.setText(formatTime(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { userIsSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                videoView.seekTo(sb.getProgress());
                userIsSeeking = false;
            }
        });

        // Increment views exactly once per Watch open
        db.incrementViews(video.id);
        tvViews.setText(getString(R.string.views_count, video.views + 1));
    }

    private final Runnable updateTicker = new Runnable() {
        @Override public void run() {
            if (!userIsSeeking && videoView.isPlaying()) {
                int pos = videoView.getCurrentPosition();
                seekBar.setProgress(pos);
                tvCurrent.setText(formatTime(pos));
            }
            handler.postDelayed(this, 250);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView.isPlaying()) {
            videoView.pause();
            btnPlay.setImageResource(R.drawable.ic_play);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (videoView != null) videoView.stopPlayback();
    }

    private static String formatTime(int ms) {
        if (ms <= 0) return "00:00";
        int totalSec = ms / 1000;
        int h = totalSec / 3600;
        int m = (totalSec % 3600) / 60;
        int s = totalSec % 60;
        if (h > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.ROOT, "%02d:%02d", m, s);
    }
}
