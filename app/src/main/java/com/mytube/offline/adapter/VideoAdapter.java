package com.mytube.offline.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mytube.offline.R;
import com.mytube.offline.data.DbHelper;
import com.mytube.offline.util.FileUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * VideoAdapter — drives the home feed and the Account "my videos" list.
 *
 * It also renders an optional per-row "Delete" button when the caller
 * is the AccountActivity. The same adapter is reused to avoid duplicating
 * card layout code between Home and Account.
 */
public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VH> {

    public interface OnItemClickListener { void onClick(DbHelper.VideoRow row); }
    public interface OnDeleteListener   { void onDelete(DbHelper.VideoRow row); }

    private final Context ctx;
    private final OnItemClickListener click;
    private OnDeleteListener delete;
    private boolean showDelete = false;

    private final List<DbHelper.VideoRow> items = new ArrayList<>();

    public VideoAdapter(Context ctx, OnItemClickListener click) {
        this.ctx = ctx;
        this.click = click;
    }

    public void setShowDelete(boolean show, OnDeleteListener dl) {
        this.showDelete = show;
        this.delete = dl;
    }

    public void submit(List<DbHelper.VideoRow> rows) {
        items.clear();
        if (rows != null) items.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_video, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        DbHelper.VideoRow row = items.get(position);
        h.title.setText(row.title);
        h.uploader.setText(row.uploader == null ? "" : row.uploader);
        h.views.setText(ctx.getString(R.string.views_count, row.views));
        if (row.uploadDate != null) h.date.setText(row.uploadDate);
        else h.date.setText("");

        // Thumbnail: try the file on disk; fall back to placeholder.
        Bitmap bmp = loadThumbnail(row.thumbnail);
        if (bmp != null) {
            h.thumb.setImageBitmap(bmp);
            h.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            h.thumb.setImageResource(android.R.drawable.ic_media_play);
            h.thumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        }

        // Whole card click -> Watch
        h.itemView.setOnClickListener(v -> { if (click != null) click.onClick(row); });

        // Delete button (Account page only)
        if (showDelete) {
            h.btnDelete.setVisibility(View.VISIBLE);
            h.btnDelete.setOnClickListener(v -> {
                if (delete != null) delete.onDelete(row);
            });
        } else {
            h.btnDelete.setVisibility(View.GONE);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    private Bitmap loadThumbnail(String filename) {
        if (filename == null) return null;
        java.io.File f = FileUtil.thumbFile(ctx, filename);
        if (!f.exists()) return null;
        // Sample down to avoid OOM on long lists.
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 2;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView  title, uploader, views, date;
        final Button    btnDelete;
        VH(@NonNull View v) {
            super(v);
            thumb     = v.findViewById(R.id.thumb);
            title     = v.findViewById(R.id.title);
            uploader  = v.findViewById(R.id.uploader);
            views     = v.findViewById(R.id.views);
            date      = v.findViewById(R.id.date);
            btnDelete = v.findViewById(R.id.btn_delete);
        }
    }
}
