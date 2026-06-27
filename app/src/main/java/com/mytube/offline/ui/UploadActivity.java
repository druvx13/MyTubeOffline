package com.mytube.offline.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.mytube.offline.R;
import com.mytube.offline.data.DbHelper;
import com.mytube.offline.data.IdGenerator;
import com.mytube.offline.data.SessionManager;
import com.mytube.offline.util.FileUtil;
import com.mytube.offline.util.ThumbUtil;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UploadActivity — handles the action=upload_video POST handler from the PHP.
 *
 * The PHP version receives an uploaded tmp file via $_FILES. On Android we
 * use ACTION_GET_CONTENT to let the user pick an existing video from their
 * gallery/storage. Then we:
 *   1. Copy the picked file into app-private uploads/ with a unique name
 *   2. Extract a thumbnail frame via MediaMetadataRetriever
 *   3. Insert a row into videos(user_id, video_id, title, description, filename, thumbnail)
 *   4. Open WatchActivity with the new video_id
 *
 * Steps 1+2 can take a few seconds for large videos, so we run them on a
 * background thread and show a ProgressBar.
 *
 * Allowed extensions match the PHP: mp4, webm, ogg. Android's VideoView can
 * only decode mp4 reliably on most devices — webm/ogg support is hardware-
 * dependent. The constraint is preserved for parity, not enforced strictly.
 */
public class UploadActivity extends AppCompatActivity {

    private static final int RC_PICK = 7001;

    private EditText    etTitle, etDesc;
    private Button      btnPick, btnUpload;
    private TextView    tvPickedName;
    private ProgressBar progress;

    private DbHelper        db;
    private SessionManager  session;

    private Uri pickedUri;
    private String pickedExt;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        db      = new DbHelper(this);
        session = new SessionManager(this);

        etTitle     = findViewById(R.id.et_title);
        etDesc      = findViewById(R.id.et_description);
        btnPick     = findViewById(R.id.btn_pick);
        btnUpload   = findViewById(R.id.btn_upload);
        tvPickedName= findViewById(R.id.tv_picked_name);
        progress    = findViewById(R.id.progress);

        btnPick.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("video/*");
            // ALLOW MULTIPLE not enabled — parity with PHP single-file upload.
            String[] mimes = { "video/mp4", "video/webm", "video/ogg", "video/x-matroska" };
            i.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i,
                    getString(R.string.pick_video)), RC_PICK);
        });

        btnUpload.setOnClickListener(v -> doUpload());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_PICK && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            pickedUri = data.getData();
            pickedExt = FileUtil.extensionOf(this, pickedUri);
            String name = FileUtil.displayName(this, pickedUri);
            tvPickedName.setText(getString(R.string.picked_file, name));
            btnUpload.setEnabled(true);
        }
    }

    private void doUpload() {
        if (pickedUri == null) {
            Toast.makeText(this, R.string.err_pick_first, Toast.LENGTH_SHORT).show();
            return;
        }
        String title = etTitle.getText().toString().trim();
        String desc  = etDesc.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, R.string.err_title_required, Toast.LENGTH_SHORT).show();
            return;
        }

        // Lock UI while we do file IO + thumbnail extraction
        setBusy(true);

        final int userId = session.getUserId();
        final String finalTitle = title;
        final String finalDesc  = desc;
        final Uri uri = pickedUri;
        final String ext = (pickedExt == null || pickedExt.isEmpty()) ? "mp4" : pickedExt;

        io.execute(() -> {
            // 1. Copy file into uploads/
            String unique = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            if (FileUtil.copyUriToUploads(this, uri, unique) == null) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, R.string.err_upload_failed, Toast.LENGTH_LONG).show();
                });
                return;
            }

            // 2. Extract thumbnail — best-effort; null is acceptable.
            String thumbName = ThumbUtil.extractThumbnail(this, uri,
                    unique.substring(0, 8));

            // 3. Insert DB row.
            String videoId = IdGenerator.nextVideoId();
            long rowId = db.insertVideo(userId, videoId, finalTitle, finalDesc, unique, thumbName);

            runOnUiThread(() -> {
                setBusy(false);
                if (rowId > 0) {
                    Toast.makeText(this, R.string.upload_success, Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(this, WatchActivity.class)
                            .putExtra(WatchActivity.EXTRA_VIDEO_ID, videoId)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                    finish();
                } else {
                    Toast.makeText(this, R.string.err_db_insert, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnUpload.setEnabled(!busy);
        btnPick.setEnabled(!busy);
        etTitle.setEnabled(!busy);
        etDesc.setEnabled(!busy);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't shut down the shared executor mid-task — let the OS clean it up on exit.
        // The activity is short-lived; this is fine.
    }
}
