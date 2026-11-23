package com.music.annnnn.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.music.annnnn.R;
import com.music.annnnn.data.local.entity.PlaylistWithCount;
import com.music.annnnn.util.CoverLoader;

import java.util.ArrayList;
import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {

    public interface Listener {
        void onPlaylistClick(PlaylistWithCount item);
    }

    private final List<PlaylistWithCount> items = new ArrayList<>();
    private final Listener listener;

    public PlaylistAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).playlist.id;
    }

    public void submitList(List<PlaylistWithCount> list) {
        List<PlaylistWithCount> target = list != null ? new ArrayList<>(list) : new ArrayList<>();
        List<PlaylistWithCount> oldList = new ArrayList<>(items);
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
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldList.get(oldItemPosition).playlist.id == target.get(newItemPosition).playlist.id;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                PlaylistWithCount o = oldList.get(oldItemPosition);
                PlaylistWithCount n = target.get(newItemPosition);
                return o.songCount == n.songCount
                        && TextUtils.equals(o.playlist.name, n.playlist.name)
                        && TextUtils.equals(o.playlist.creatorName, n.playlist.creatorName)
                        && TextUtils.equals(o.playlist.coverResName, n.playlist.coverResName);
            }
        });
        items.clear();
        items.addAll(target);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlaylistWithCount item = items.get(position);
        holder.tvName.setText(item.playlist.name);
        holder.tvCount.setText(holder.itemView.getContext().getString(R.string.count_songs_format, item.songCount) + " • " + item.playlist.creatorName);
        CoverLoader.loadCover(holder.ivCover, null, item.playlist.coverResName);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onPlaylistClick(items.get(pos));
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView ivCover;
        final TextView tvName;
        final TextView tvCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_playlist_cover);
            tvName = itemView.findViewById(R.id.tv_playlist_name);
            tvCount = itemView.findViewById(R.id.tv_playlist_count);
        }
    }
}
