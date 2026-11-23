package com.music.annnnn.ui.profile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.music.annnnn.R;
import com.music.annnnn.util.ThemeStudioManager;

import java.util.List;

public class ThemePresetAdapter extends RecyclerView.Adapter<ThemePresetAdapter.ViewHolder> {

    public interface OnPresetSelectedListener {
        void onPresetSelected(ThemeStudioManager.ThemePreset preset);
    }

    private final List<ThemeStudioManager.ThemePreset> presets;
    private final boolean gridMode;
    private final OnPresetSelectedListener listener;
    private String selectedId;

    public ThemePresetAdapter(
            List<ThemeStudioManager.ThemePreset> presets,
            String selectedId,
            boolean gridMode,
            OnPresetSelectedListener listener
    ) {
        this.presets = presets;
        this.selectedId = selectedId;
        this.gridMode = gridMode;
        this.listener = listener;
    }

    public void setSelectedId(String selectedId) {
        if (TextUtils.equals(this.selectedId, selectedId)) return;
        this.selectedId = selectedId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_theme_preset, parent, false);
        if (gridMode) {
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            int m = dp(parent.getContext(), 4);
            lp.setMargins(m, m, m, m);
            view.setLayoutParams(lp);
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ThemeStudioManager.ThemePreset preset = presets.get(position);
        boolean isSelected = TextUtils.equals(preset.id, selectedId);

        holder.tvName.setText(preset.nameRes);
        holder.viewSwatch.setBackground(ThemeStudioManager.createSwatchDrawable(preset));

        if (isSelected) {
            holder.viewRing.setVisibility(View.VISIBLE);
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(Color.TRANSPARENT);
            ring.setStroke(dp(holder.itemView.getContext(), 2.5f), preset.accentColor);
            holder.viewRing.setBackground(ring);
            holder.tvName.setTextColor(preset.accentColor);
        } else {
            holder.viewRing.setVisibility(View.GONE);
            holder.tvName.setTextColor(
                    holder.itemView.getContext().getColor(R.color.text_primary)
            );
        }

        holder.itemView.setTag("theme_preset_" + preset.id);
        holder.itemView.setContentDescription(holder.itemView.getContext().getString(preset.nameRes));
        holder.itemView.setOnClickListener(v -> {
            setSelectedId(preset.id);
            if (listener != null) {
                listener.onPresetSelected(preset);
            }
        });
    }

    @Override
    public int getItemCount() {
        return presets != null ? presets.size() : 0;
    }

    private static int dp(Context ctx, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()
        ));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View viewSwatch;
        final View viewRing;
        final TextView tvName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewSwatch = itemView.findViewById(R.id.view_theme_swatch);
            viewRing = itemView.findViewById(R.id.view_theme_ring);
            tvName = itemView.findViewById(R.id.tv_theme_preset_name);
        }
    }
}
