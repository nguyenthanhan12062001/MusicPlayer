package com.music.annnnn.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.music.annnnn.R;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.player.PlaybackController;
import com.music.annnnn.ui.player.FullPlayerActivity;

public class MusicPlaybackService extends Service {

    public static final String CHANNEL_ID = "office_music_playback_channel";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_UPDATE = "com.music.annnnn.ACTION_UPDATE";
    public static final String ACTION_PLAY_PAUSE = "com.music.annnnn.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.music.annnnn.ACTION_NEXT";
    public static final String ACTION_PREV = "com.music.annnnn.ACTION_PREV";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PlaybackController controller = PlaybackController.getInstance(this);
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_PLAY_PAUSE.equals(action)) {
                controller.togglePlayPause();
            } else if (ACTION_NEXT.equals(action)) {
                controller.playNext();
            } else if (ACTION_PREV.equals(action)) {
                controller.playPrevious();
            }
        }

        SongEntity song = controller.getCurrentSong().getValue();
        boolean playing = Boolean.TRUE.equals(controller.getIsPlaying().getValue());
        if (song != null) {
            Notification notification = buildNotification(song, playing);
            if (playing) {
                startForeground(NOTIFICATION_ID, notification);
            } else {
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.notify(NOTIFICATION_ID, notification);
                }
                stopForeground(STOP_FOREGROUND_DETACH);
            }
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification(SongEntity song, boolean isPlaying) {
        Intent openIntent = new Intent(this, FullPlayerActivity.class);
        PendingIntent contentPending = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent prevPending = PendingIntent.getService(
                this, 1,
                new Intent(this, MusicPlaybackService.class).setAction(ACTION_PREV),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent playPausePending = PendingIntent.getService(
                this, 2,
                new Intent(this, MusicPlaybackService.class).setAction(ACTION_PLAY_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent nextPending = PendingIntent.getService(
                this, 3,
                new Intent(this, MusicPlaybackService.class).setAction(ACTION_NEXT),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_zing_logo)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setContentIntent(contentPending)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_prev, getString(R.string.cd_previous), prevPending)
                .addAction(
                        isPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                        getString(R.string.cd_play_pause),
                        playPausePending
                )
                .addAction(R.drawable.ic_next, getString(R.string.cd_next), nextPending)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.banner_title));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
