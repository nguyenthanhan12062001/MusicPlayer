package com.music.annnnn.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.music.annnnn.R;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.util.CoverLoader;

import java.util.ArrayList;
import java.util.List;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.ViewHolder> {

    public interface Listener {
        void onQueueItemClick(int position);

        void onQueueRemoveClick(int position);
    }

    private final List<SongEntity> items = new ArrayList<>();
    private final Listener listener;
    private int currentIndex = -1;

    public QueueAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitQueue(List<SongEntity> queue, int currentIdx) {
        List<SongEntity> target = queue != null ? new ArrayList<>(queue) : new ArrayList<>();
        int prevIndex = this.currentIndex;
        boolean sameList = target.size() == items.size();
        if (sameList) {
            for (int i = 0; i < target.size(); i++) {
                if (target.get(i).id != items.get(i).id) {
                    sameList = false;
                    break;
                }
            }
        }
        if (sameList) {
            this.currentIndex = currentIdx;
            if (prevIndex != currentIdx) {
                if (prevIndex >= 0 && prevIndex < items.size()) notifyItemChanged(prevIndex);
                if (currentIdx >= 0 && currentIdx < items.size()) notifyItemChanged(currentIdx);
            }
            return;
        }

        List<SongEntity> oldList = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return target.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldList.get(oldPos).id == target.get(newPos).id;
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                SongEntity o = oldList.get(oldPos);
                SongEntity n = target.get(newPos);
                return oldPos != prevIndex && newPos != currentIdx
                        && TextUtils.equals(o.title, n.title)
                        && TextUtils.equals(o.artist, n.artist)
                        && TextUtils.equals(o.coverUrl, n.coverUrl);
            }
        });
        items.clear();
        items.addAll(target);
        this.currentIndex = currentIdx;
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SongEntity song = items.get(position);
        holder.tvTitle.setText(song.title);
        holder.tvArtist.setText(song.artist);
        CoverLoader.loadSongArtwork(holder.ivCover, song.coverUrl);

        boolean isCurrent = position == currentIndex;
        holder.tvTitle.setTextColor(ContextCompat.getColor(
                holder.itemView.getContext(),
                isCurrent ? R.color.zing_purple : R.color.text_primary
        ));

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onQueueItemClick(pos);
            }
        });
        holder.btnRemove.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onQueueRemoveClick(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView ivCover;
        final TextView tvTitle;
        final TextView tvArtist;
        final ImageButton btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_queue_cover);
            tvTitle = itemView.findViewById(R.id.tv_queue_title);
            tvArtist = itemView.findViewById(R.id.tv_queue_artist);
            btnRemove = itemView.findViewById(R.id.btn_queue_remove);
        }
    }
}
