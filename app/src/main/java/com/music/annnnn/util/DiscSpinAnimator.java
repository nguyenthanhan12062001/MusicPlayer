package com.music.annnnn.util;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Smooth continuous vinyl/disc rotation controller synced with play/pause state.
 */
public class DiscSpinAnimator {

    private final View targetView;
    private final ObjectAnimator animator;

    public DiscSpinAnimator(View targetView, long revolutionDurationMs) {
        this.targetView = targetView;
        this.animator = ObjectAnimator.ofFloat(targetView, View.ROTATION, 0f, 360f);
        this.animator.setDuration(revolutionDurationMs);
        this.animator.setInterpolator(new LinearInterpolator());
        this.animator.setRepeatCount(ValueAnimator.INFINITE);
        this.animator.setRepeatMode(ValueAnimator.RESTART);
    }

    public void setPlaying(boolean playing) {
        if (targetView == null) return;
        if (playing) {
            if (!animator.isStarted()) {
                animator.start();
            } else if (animator.isPaused()) {
                animator.resume();
            }
        } else {
            if (animator.isRunning() && !animator.isPaused()) {
                animator.pause();
            }
        }
    }

    public void release() {
        animator.cancel();
    }
}
