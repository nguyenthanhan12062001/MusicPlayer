package com.music.annnnn.util;

import android.view.MotionEvent;
import android.view.View;

public final class ViewAnimationHelper {

    private ViewAnimationHelper() {}

    public static void applyPressScale(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.92f).setDuration(120).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(160).start();
                    break;
            }
            return false;
        });
    }
}
