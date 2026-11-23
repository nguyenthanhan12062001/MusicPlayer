package com.music.annnnn.ui.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.music.annnnn.R;
import com.music.annnnn.data.local.entity.DownloadTaskEntity;
import com.music.annnnn.util.CoverLoader;
import com.music.annnnn.util.TimeFormatter;

import java.util.ArrayList;
import java.util.List;

public class DownloadTaskAdapter extends RecyclerView.Adapter<DownloadTaskAdapter.ViewHolder> {

    public interface Listener {
        void onActionClick(DownloadTaskEntity task);

        void onDeleteClick(DownloadTaskEntity task);

        void onCompletedItemClick(DownloadTaskEntity task);
    }

    private static final String PAYLOAD_PROGRESS = "payload_progress";

    private final List<DownloadTaskEntity> items = new ArrayList<>();
    private final Listener listener;

    public DownloadTaskAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    public void submitList(List<DownloadTaskEntity> list) {
        List<DownloadTaskEntity> newList = list != null ? new ArrayList<>(list) : new ArrayList<>();
        List<DownloadTaskEntity> oldList = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldList.get(oldItemPosition).id == newList.get(newItemPosition).id;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                DownloadTaskEntity o = oldList.get(oldItemPosition);
                DownloadTaskEntity n = newList.get(newItemPosition);
                return o.progress == n.progress
                        && o.etaSeconds == n.etaSeconds
                        && o.downloadedBytes == n.downloadedBytes
                        && o.totalBytes == n.totalBytes
                        && o.currentItemIndex == n.currentItemIndex
                        && TextUtils.equals(o.status, n.status)
                        && TextUtils.equals(o.speedText, n.speedText)
                        && TextUtils.equals(o.sizeText, n.sizeText)
                        && TextUtils.equals(o.errorMessage, n.errorMessage)
                        && TextUtils.equals(o.title, n.title)
                        && TextUtils.equals(o.thumbnailUrl, n.thumbnailUrl);
            }

            @Nullable
            @Override
            public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                DownloadTaskEntity o = oldList.get(oldItemPosition);
                DownloadTaskEntity n = newList.get(newItemPosition);
                if (TextUtils.equals(o.title, n.title)
                        && TextUtils.equals(o.thumbnailUrl, n.thumbnailUrl)
                        && TextUtils.equals(o.sourceName, n.sourceName)) {
                    return PAYLOAD_PROGRESS;
                }
                return null;
            }
        });
        items.clear();
        items.addAll(newList);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download_task, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_PROGRESS)) {
            bindDynamicState(holder, items.get(position));
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadTaskEntity task = items.get(position);
        String prefix = task.isPlaylist && task.playlistCount > 1
                ? "[" + task.sourceName + " • Playlist] "
                : "[" + task.sourceName + "] ";
        holder.tvTitle.setText(prefix + task.title);
        CoverLoader.loadCover(holder.ivThumb, task.thumbnailUrl, "bg_vinyl_disc");
        bindDynamicState(holder, task);
    }

    private void bindDynamicState(@NonNull ViewHolder holder, DownloadTaskEntity task) {
        String sizeDisplay = !TextUtils.isEmpty(task.sizeText)
                ? task.sizeText
                : (TimeFormatter.formatBytes(task.downloadedBytes) + " / " + TimeFormatter.formatBytes(task.totalBytes));
        String speedDisplay = !TextUtils.isEmpty(task.speedText)
                ? task.speedText
                : (task.speedBytesPerSec > 0 ? TimeFormatter.formatBytes(task.speedBytesPerSec) + "/s" : "");

        Context ctx = holder.itemView.getContext();
        if (DownloadTaskEntity.STATUS_QUEUED.equals(task.status)) {
            holder.tvMeta.setText(ctx.getString(R.string.dl_meta_queued_format, task.format, task.quality));
            holder.tvStatus.setText(R.string.dl_status_preparing);
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.progressBar.setIndeterminate(true);
            holder.btnAction.setVisibility(View.VISIBLE);
            holder.btnAction.setImageResource(R.drawable.ic_pause);
        } else if (DownloadTaskEntity.STATUS_CONVERTING.equals(task.status)) {
            holder.tvMeta.setText(sizeDisplay + " • " + task.format + " " + task.quality);
            holder.tvStatus.setText(!TextUtils.isEmpty(task.speedText) ? task.speedText : ctx.getString(R.string.dl_status_converting));
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.zing_pink));
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.progressBar.setIndeterminate(true);
            holder.btnAction.setVisibility(View.GONE);
        } else if (DownloadTaskEntity.STATUS_DOWNLOADING.equals(task.status)) {
            String playlistHint = (task.isPlaylist && task.playlistCount > 1 && task.currentItemIndex > 0)
                    ? ctx.getString(R.string.dl_playlist_item_prefix_format, task.currentItemIndex, task.playlistCount)
                    : "";
            String etaHint = task.etaSeconds > 0 ? ctx.getString(R.string.dl_eta_format, task.etaSeconds) : "";
            holder.tvMeta.setText(playlistHint + sizeDisplay + (TextUtils.isEmpty(speedDisplay) ? "" : " • " + speedDisplay));
            holder.tvStatus.setText(ctx.getString(R.string.dl_status_downloading_format, task.progress, task.format, task.quality, etaHint));
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.zing_purple));
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.progressBar.setIndeterminate(false);
            holder.progressBar.setProgress(task.progress);
            holder.btnAction.setVisibility(View.VISIBLE);
            holder.btnAction.setImageResource(R.drawable.ic_pause);
        } else if (DownloadTaskEntity.STATUS_PAUSED.equals(task.status)) {
            holder.tvMeta.setText(sizeDisplay + " • " + task.format + " " + task.quality);
            holder.tvStatus.setText(ctx.getString(R.string.dl_status_paused_format, task.progress));
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_warning));
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.progressBar.setIndeterminate(false);
            holder.progressBar.setProgress(task.progress);
            holder.btnAction.setVisibility(View.VISIBLE);
            holder.btnAction.setImageResource(R.drawable.ic_play);
        } else if (DownloadTaskEntity.STATUS_COMPLETED.equals(task.status)) {
            String savedSize = task.totalBytes > 0 ? TimeFormatter.formatBytes(task.totalBytes) : sizeDisplay;
            holder.tvMeta.setText(savedSize + " • " + task.format + " " + task.quality);
            boolean isVideo = DownloadTaskEntity.MEDIA_VIDEO.equals(task.mediaType);
            holder.tvStatus.setText(isVideo
                    ? R.string.dl_status_completed_video
                    : R.string.dl_status_completed_audio);
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_success));
            holder.progressBar.setVisibility(View.GONE);
            holder.btnAction.setVisibility(isVideo ? View.GONE : View.VISIBLE);
            if (!isVideo) {
                holder.btnAction.setImageResource(R.drawable.ic_play);
            }
        } else {
            holder.tvMeta.setText(task.channelName + " • " + task.format + " " + task.quality);
            holder.tvStatus.setText(!TextUtils.isEmpty(task.errorMessage) ? task.errorMessage : ctx.getString(R.string.dl_status_failed));
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_error));
            holder.progressBar.setVisibility(View.GONE);
            holder.btnAction.setVisibility(View.VISIBLE);
            holder.btnAction.setImageResource(R.drawable.ic_retry);
        }

        holder.btnAction.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener == null || pos == RecyclerView.NO_POSITION) return;
            DownloadTaskEntity current = items.get(pos);
            if (DownloadTaskEntity.STATUS_COMPLETED.equals(current.status)) {
                listener.onCompletedItemClick(current);
            } else {
                listener.onActionClick(current);
            }
        });
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                DownloadTaskEntity current = items.get(pos);
                if (DownloadTaskEntity.STATUS_COMPLETED.equals(current.status)) {
                    listener.onCompletedItemClick(current);
                }
            }
        });
        holder.btnDelete.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onDeleteClick(items.get(pos));
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView ivThumb;
        final TextView tvTitle;
        final TextView tvMeta;
        final TextView tvStatus;
        final ImageButton btnAction;
        final ImageButton btnDelete;
        final LinearProgressIndicator progressBar;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_dl_thumb);
            tvTitle = itemView.findViewById(R.id.tv_dl_title);
            tvMeta = itemView.findViewById(R.id.tv_dl_meta);
            tvStatus = itemView.findViewById(R.id.tv_dl_status);
            btnAction = itemView.findViewById(R.id.btn_dl_action);
            btnDelete = itemView.findViewById(R.id.btn_dl_delete);
            progressBar = itemView.findViewById(R.id.progress_dl_bar);
        }
    }
}
