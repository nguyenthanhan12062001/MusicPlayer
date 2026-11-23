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
import com.music.annnnn.util.DiscSpinAnimator;
import com.music.annnnn.util.TimeFormatter;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    public interface Listener {
        void onSongClick(SongEntity song, int position);

        void onFavoriteClick(SongEntity song);

        void onMoreClick(SongEntity song);
    }

    private static final String PAYLOAD_ACTIVE = "payload_active";

    private final List<SongEntity> items = new ArrayList<>();
    private final Listener listener;
    private long activeSongId = -1L;
    private boolean isPlaybackPlaying = false;
    private int maxItems = Integer.MAX_VALUE;

    public SongAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    public void submitList(List<SongEntity> songs) {
        List<SongEntity> newList = new ArrayList<>();
        if (songs != null) {
            int limit = Math.min(songs.size(), maxItems);
            for (int i = 0; i < limit; i++) {
                newList.add(songs.get(i));
            }
        }
        List<SongEntity> oldList = new ArrayList<>(items);
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
                SongEntity o = oldList.get(oldItemPosition);
                SongEntity n = newList.get(newItemPosition);
                return o.isFavorite == n.isFavorite
                        && o.durationMs == n.durationMs
                        && TextUtils.equals(o.title, n.title)
                        && TextUtils.equals(o.artist, n.artist)
                        && TextUtils.equals(o.coverUrl, n.coverUrl)
                        && TextUtils.equals(o.coverResName, n.coverResName);
            }
        });
        items.clear();
        items.addAll(newList);
        diff.dispatchUpdatesTo(this);
    }

    public List<SongEntity> getItems() {
        return items;
    }

    public void setActiveSongId(long songId) {
        if (this.activeSongId == songId) return;
        long previousId = this.activeSongId;
        this.activeSongId = songId;
        for (int i = 0; i < items.size(); i++) {
            long id = items.get(i).id;
            if (id == previousId || id == songId) {
                notifyItemChanged(i, PAYLOAD_ACTIVE);
            }
        }
    }

    public void setPlaybackPlaying(boolean playing) {
        if (this.isPlaybackPlaying == playing) return;
        this.isPlaybackPlaying = playing;
        if (activeSongId != -1L) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id == activeSongId) {
                    notifyItemChanged(i, PAYLOAD_ACTIVE);
                }
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_ACTIVE)) {
            SongEntity song = items.get(position);
            updateActiveState(holder, song.id == activeSongId);
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SongEntity song = items.get(position);
        holder.tvTitle.setText(song.title);
        holder.tvArtist.setText(song.artist + " • " + TimeFormatter.formatDuration(song.durationMs));
        CoverLoader.loadSongArtwork(holder.ivCover, song.coverUrl);
        updateActiveState(holder, song.id == activeSongId);

        holder.btnFav.setImageResource(song.isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        holder.btnFav.setColorFilter(ContextCompat.getColor(
                holder.itemView.getContext(),
                song.isFavorite ? R.color.zing_pink : R.color.icon_inactive
        ));

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onSongClick(items.get(pos), pos);
            }
        });
        holder.btnFav.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onFavoriteClick(items.get(pos));
            }
        });
        holder.btnMore.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onMoreClick(items.get(pos));
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.spinAnimator.setPlaying(false);
        holder.ivCover.setRotation(0f);
    }

    private void updateActiveState(@NonNull ViewHolder holder, boolean isCurrent) {
        holder.tvTitle.setTextColor(ContextCompat.getColor(
                holder.itemView.getContext(),
                isCurrent ? R.color.zing_purple : R.color.text_primary
        ));
        boolean shouldSpin = isCurrent && isPlaybackPlaying;
        holder.spinAnimator.setPlaying(shouldSpin);
        if (!isCurrent) {
            holder.ivCover.setRotation(0f);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView ivCover;
        final TextView tvTitle;
        final TextView tvArtist;
        final ImageButton btnFav;
        final ImageButton btnMore;
        final DiscSpinAnimator spinAnimator;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_song_cover);
            tvTitle = itemView.findViewById(R.id.tv_song_title);
            tvArtist = itemView.findViewById(R.id.tv_song_artist);
            btnFav = itemView.findViewById(R.id.btn_song_favorite);
            btnMore = itemView.findViewById(R.id.btn_song_more);
            spinAnimator = new DiscSpinAnimator(ivCover, 11000L);
        }
    }
}
