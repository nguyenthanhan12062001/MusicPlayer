package com.music.annnnn.util;

import android.content.Context;
import android.text.TextUtils;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.music.annnnn.R;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CoverLoader {

    private static final Map<String, Integer> DRAWABLE_MAP = new HashMap<>();

    static {
        DRAWABLE_MAP.put("img_cover_vpop", R.drawable.img_cover_vpop);
        DRAWABLE_MAP.put("img_cover_lofi", R.drawable.img_cover_lofi);
        DRAWABLE_MAP.put("img_cover_radio", R.drawable.img_cover_radio);
        DRAWABLE_MAP.put("img_banner_featured", R.drawable.img_banner_featured);
        DRAWABLE_MAP.put("bg_cover_aurora", R.drawable.bg_cover_aurora);
        DRAWABLE_MAP.put("bg_cover_sunset", R.drawable.bg_cover_sunset);
        DRAWABLE_MAP.put("bg_cover_midnight", R.drawable.bg_cover_midnight);
        DRAWABLE_MAP.put("bg_cover_ocean", R.drawable.bg_cover_ocean);
        DRAWABLE_MAP.put("bg_vinyl_disc", R.drawable.bg_vinyl_disc);
    }

    public static int resolveDrawableRes(Context context, String coverResName) {
        if (TextUtils.isEmpty(coverResName)) {
            return R.drawable.bg_vinyl_disc;
        }
        Integer mapped = DRAWABLE_MAP.get(coverResName);
        if (mapped != null) {
            return mapped;
        }
        int resId = context.getResources().getIdentifier(coverResName, "drawable", context.getPackageName());
        return resId != 0 ? resId : R.drawable.bg_vinyl_disc;
    }

    private static boolean isRasterCover(int resId) {
        return resId == R.drawable.img_cover_vpop
                || resId == R.drawable.img_cover_lofi
                || resId == R.drawable.img_cover_radio
                || resId == R.drawable.img_banner_featured;
    }

    /**
     * Loads a song's artwork if available; otherwise renders the ZingMP3-style spinning vinyl disc.
     */
    public static void loadSongArtwork(ImageView imageView, String coverUrl) {
        if (imageView == null) return;
        if (!TextUtils.isEmpty(coverUrl)) {
            Object model = coverUrl.startsWith("/") ? new File(coverUrl) : coverUrl;
            Glide.with(imageView)
                    .load(model)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.bg_vinyl_disc)
                    .error(R.drawable.bg_vinyl_disc)
                    .fallback(R.drawable.bg_vinyl_disc)
                    .centerCrop()
                    .dontAnimate()
                    .into(imageView);
        } else {
            Glide.with(imageView).clear(imageView);
            imageView.setImageResource(R.drawable.bg_vinyl_disc);
        }
    }

    public static void loadCover(ImageView imageView, String coverUrl, String coverResName) {
        if (imageView == null) return;
        int fallbackRes = resolveDrawableRes(imageView.getContext(), coverResName);
        if (!TextUtils.isEmpty(coverUrl)) {
            Object model = coverUrl.startsWith("/") ? new File(coverUrl) : coverUrl;
            Glide.with(imageView)
                    .load(model)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(fallbackRes)
                    .error(fallbackRes)
                    .fallback(fallbackRes)
                    .centerCrop()
                    .dontAnimate()
                    .into(imageView);
        } else if (isRasterCover(fallbackRes)) {
            Glide.with(imageView)
                    .load(fallbackRes)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .centerCrop()
                    .dontAnimate()
                    .into(imageView);
        } else {
            Glide.with(imageView).clear(imageView);
            imageView.setImageResource(fallbackRes);
        }
    }
}
