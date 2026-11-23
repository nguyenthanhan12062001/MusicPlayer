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
import com.music.annnnn.data.local.entity.ArtistSummary;
import com.music.annnnn.util.CoverLoader;

import java.util.ArrayList;
import java.util.List;

public class ArtistAdapter extends RecyclerView.Adapter<ArtistAdapter.ViewHolder> {

    public interface Listener {
        void onArtistClick(ArtistSummary artist);
    }

    private final List<ArtistSummary> items = new ArrayList<>();
    private final Listener listener;

    public ArtistAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<ArtistSummary> artists) {
        List<ArtistSummary> target = artists != null ? new ArrayList<>(artists) : new ArrayList<>();
        List<ArtistSummary> oldList = new ArrayList<>(items);
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
                return TextUtils.equals(oldList.get(oldItemPosition).artist, target.get(newItemPosition).artist);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ArtistSummary o = oldList.get(oldItemPosition);
                ArtistSummary n = target.get(newItemPosition);
                return o.songCount == n.songCount && TextUtils.equals(o.coverResName, n.coverResName);
            }
        });
        items.clear();
        items.addAll(target);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_artist_circle, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ArtistSummary item = items.get(position);
        holder.tvName.setText(item.artist);
        holder.tvCount.setText(holder.itemView.getContext().getString(R.string.count_songs_format, item.songCount));
        CoverLoader.loadCover(holder.ivAvatar, null, item.coverResName);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onArtistClick(items.get(pos));
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView ivAvatar;
        final TextView tvName;
        final TextView tvCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_artist_avatar);
            tvName = itemView.findViewById(R.id.tv_artist_name);
            tvCount = itemView.findViewById(R.id.tv_artist_count);
        }
    }
}
