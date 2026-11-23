package com.music.annnnn.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.music.annnnn.R;
import com.music.annnnn.util.CoverLoader;

import java.util.ArrayList;
import java.util.List;

public class HorizontalCardAdapter extends RecyclerView.Adapter<HorizontalCardAdapter.ViewHolder> {

    public static class CardItem {
        public long id;
        public String title;
        public String subtitle;
        public String coverResName;
        public String coverUrl;
        public String tag;

        public CardItem(long id, String title, String subtitle, String coverResName, String coverUrl, String tag) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.coverResName = coverResName;
            this.coverUrl = coverUrl;
            this.tag = tag;
        }
    }

    public interface Listener {
        void onCardClick(CardItem item, int position);
    }

    private final List<CardItem> items = new ArrayList<>();
    private final Listener listener;

    public HorizontalCardAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<CardItem> newItems) {
        List<CardItem> target = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        List<CardItem> oldList = new ArrayList<>(items);
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
                return oldList.get(oldItemPosition).id == target.get(newItemPosition).id
                        && TextUtils.equals(oldList.get(oldItemPosition).tag, target.get(newItemPosition).tag);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                CardItem o = oldList.get(oldItemPosition);
                CardItem n = target.get(newItemPosition);
                return TextUtils.equals(o.title, n.title)
                        && TextUtils.equals(o.subtitle, n.subtitle)
                        && TextUtils.equals(o.coverResName, n.coverResName)
                        && TextUtils.equals(o.coverUrl, n.coverUrl);
            }
        });
        items.clear();
        items.addAll(target);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_horizontal_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CardItem item = items.get(position);
        holder.tvTitle.setText(item.title);
        holder.tvSubtitle.setText(item.subtitle);
        CoverLoader.loadCover(holder.ivCover, item.coverUrl, item.coverResName);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onCardClick(items.get(pos), pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivCover;
        final TextView tvTitle;
        final TextView tvSubtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_card_cover);
            tvTitle = itemView.findViewById(R.id.tv_card_title);
            tvSubtitle = itemView.findViewById(R.id.tv_card_subtitle);
            com.music.annnnn.util.ViewAnimationHelper.applyPressScale(itemView);
        }
    }
}
