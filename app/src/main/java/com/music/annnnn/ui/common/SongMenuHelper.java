package com.music.annnnn.ui.common;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.music.annnnn.R;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.data.repository.MusicRepository;
import com.music.annnnn.player.PlaybackController;
import com.music.annnnn.ui.adapter.PlaylistAdapter;
import com.music.annnnn.ui.adapter.QueueAdapter;
import com.music.annnnn.util.CoverLoader;
import com.music.annnnn.util.TimeFormatter;

import java.io.File;

/**
 * Centralized helper for all secondary Bottom Sheets, Dialogs, and Popups:
 * Menu bài hát, Đổi ảnh bài hát, Thêm vào Playlist, Tạo Playlist, Hàng đợi phát nhạc,
 * Chỉnh sửa thông tin bài hát, Xác nhận xóa, Thông tin chi tiết.
 */
public class SongMenuHelper {

    public static void showSongMenu(AppCompatActivity activity, SongEntity song) {
        if (activity == null || song == null) return;
        MusicRepository repo = MusicRepository.getInstance(activity);
        PlaybackController player = PlaybackController.getInstance(activity);

        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.Theme_OfficeMusic_BottomSheet);
        View view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_song_menu, null);
        dialog.setContentView(view);

        ShapeableImageView ivCover = view.findViewById(R.id.iv_sheet_cover);
        TextView tvTitle = view.findViewById(R.id.tv_sheet_title);
        TextView tvArtist = view.findViewById(R.id.tv_sheet_artist);
        ImageView ivFav = view.findViewById(R.id.iv_sheet_fav);
        TextView tvFav = view.findViewById(R.id.tv_sheet_fav);

        CoverLoader.loadSongArtwork(ivCover, song.coverUrl);
        tvTitle.setText(song.title);
        tvArtist.setText(song.artist);
        ivFav.setImageResource(song.isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        tvFav.setText(song.isFavorite ? R.string.sheet_remove_favorite : R.string.sheet_add_favorite);

        View.OnClickListener pickCoverAction = v -> {
            dialog.dismiss();
            if (activity instanceof BaseMusicActivity) {
                ((BaseMusicActivity) activity).launchSongCoverPicker(song);
            }
        };
        ivCover.setOnClickListener(pickCoverAction);
        view.findViewById(R.id.action_sheet_change_cover).setOnClickListener(pickCoverAction);

        View btnResetCover = view.findViewById(R.id.action_sheet_reset_cover);
        btnResetCover.setVisibility(!TextUtils.isEmpty(song.coverUrl) ? View.VISIBLE : View.GONE);
        btnResetCover.setOnClickListener(v -> {
            dialog.dismiss();
            repo.resetSongCover(song.id, () -> activity.runOnUiThread(() -> {
                player.syncCurrentSongCover(song.id, null);
                Toast.makeText(activity, R.string.toast_cover_reset, Toast.LENGTH_SHORT).show();
            }));
        });

        view.findViewById(R.id.action_sheet_favorite).setOnClickListener(v -> {
            boolean next = !song.isFavorite;
            repo.toggleFavorite(song.id, next);
            player.syncCurrentSongFavorite(song.id, next);
            Toast.makeText(activity, next ? R.string.toast_added_favorite : R.string.toast_removed_favorite, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        view.findViewById(R.id.action_sheet_add_queue).setOnClickListener(v -> {
            player.addToQueue(song);
            Toast.makeText(activity, R.string.toast_added_queue, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        view.findViewById(R.id.action_sheet_add_playlist).setOnClickListener(v -> {
            dialog.dismiss();
            showAddToPlaylistSheet(activity, activity, song);
        });

        view.findViewById(R.id.action_sheet_edit).setOnClickListener(v -> {
            dialog.dismiss();
            showEditSongDialog(activity, song, repo);
        });

        view.findViewById(R.id.action_sheet_info).setOnClickListener(v -> {
            dialog.dismiss();
            showSongInfoDialog(activity, song);
        });

        view.findViewById(R.id.action_sheet_delete).setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteConfirmDialog(activity, song, repo);
        });

        dialog.show();
    }

    public static void showAddToPlaylistSheet(Context context, LifecycleOwner owner, SongEntity song) {
        MusicRepository repo = MusicRepository.getInstance(context);
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.Theme_OfficeMusic_BottomSheet);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_add_to_playlist, null);
        dialog.setContentView(view);

        RecyclerView rv = view.findViewById(R.id.rv_sheet_playlists);
        rv.setLayoutManager(new LinearLayoutManager(context));

        PlaylistAdapter adapter = new PlaylistAdapter(item -> {
            repo.addSongToPlaylist(item.playlist.id, song.id);
            Toast.makeText(context, context.getString(R.string.toast_added_to_named_playlist, item.playlist.name), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        rv.setAdapter(adapter);
        repo.observeAllPlaylistsWithCount().observe(owner, adapter::submitList);

        view.findViewById(R.id.btn_sheet_new_playlist).setOnClickListener(v ->
                showCreatePlaylistDialog(context, repo)
        );

        dialog.show();
    }

    public static void showCreatePlaylistDialog(Context context, MusicRepository repo) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_create_playlist, null);
        TextInputEditText etName = view.findViewById(R.id.et_playlist_name);
        TextInputEditText etDesc = view.findViewById(R.id.et_playlist_desc);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_create_playlist_title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_create, (d, w) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    String desc = etDesc.getText() != null ? etDesc.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(context, R.string.toast_enter_playlist_name, Toast.LENGTH_SHORT).show();
                    } else {
                        repo.createPlaylist(name, desc);
                        Toast.makeText(context, context.getString(R.string.toast_created_playlist, name), Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    public static void showQueueSheet(Context context, LifecycleOwner owner) {
        PlaybackController player = PlaybackController.getInstance(context);
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.Theme_OfficeMusic_BottomSheet);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_queue, null);
        dialog.setContentView(view);

        RecyclerView rv = view.findViewById(R.id.rv_sheet_queue);
        rv.setLayoutManager(new LinearLayoutManager(context));
        QueueAdapter adapter = new QueueAdapter(new QueueAdapter.Listener() {
            @Override
            public void onQueueItemClick(int position) {
                player.playAtIndex(position);
            }

            @Override
            public void onQueueRemoveClick(int position) {
                player.removeFromQueue(position);
            }
        });
        rv.setAdapter(adapter);
        player.getQueue().observe(owner, list -> {
            Integer idx = player.getCurrentIndex().getValue();
            adapter.submitQueue(list, idx != null ? idx : -1);
        });
        player.getCurrentIndex().observe(owner, idx ->
                adapter.submitQueue(player.getQueue().getValue(), idx != null ? idx : -1)
        );
        dialog.show();
    }

    public static void showEditSongDialog(Context context, SongEntity song, MusicRepository repo) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_song, null);
        TextInputEditText etTitle = view.findViewById(R.id.et_edit_title);
        TextInputEditText etArtist = view.findViewById(R.id.et_edit_artist);
        TextInputEditText etAlbum = view.findViewById(R.id.et_edit_album);
        TextInputEditText etGenre = view.findViewById(R.id.et_edit_genre);

        etTitle.setText(song.title);
        etArtist.setText(song.artist);
        etAlbum.setText(song.albumName);
        etGenre.setText(song.genre);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.sheet_edit_song)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : song.title;
                    String artist = etArtist.getText() != null ? etArtist.getText().toString().trim() : song.artist;
                    String album = etAlbum.getText() != null ? etAlbum.getText().toString().trim() : song.albumName;
                    String genre = etGenre.getText() != null ? etGenre.getText().toString().trim() : song.genre;
                    if (!TextUtils.isEmpty(title)) {
                        String safeArtist = TextUtils.isEmpty(artist) ? context.getString(R.string.unknown_artist) : artist;
                        repo.updateSongMetadata(song.id, title, safeArtist, album, genre);
                        PlaybackController.getInstance(context).syncCurrentSongMetadata(song.id, title, safeArtist, album, genre);
                        Toast.makeText(context, R.string.toast_song_updated, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    public static void showSongInfoDialog(Context context, SongEntity song) {
        long size = 0;
        if (!TextUtils.isEmpty(song.filePath)) {
            File f = new File(song.filePath);
            if (f.exists()) size = f.length();
        }
        String albumDisplay = TextUtils.isEmpty(song.albumName)
                ? context.getString(R.string.single_album)
                : song.albumName;
        String statusDisplay = context.getString(song.isDownloaded
                ? R.string.status_downloaded_offline
                : R.string.status_local_library);
        String message = context.getString(
                R.string.song_info_format,
                song.title,
                song.artist,
                albumDisplay,
                song.genre,
                song.mood,
                TimeFormatter.formatDuration(song.durationMs),
                song.playCount,
                TimeFormatter.formatBytes(size),
                statusDisplay
        );

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.sheet_song_info)
                .setMessage(message)
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    public static void showDeleteConfirmDialog(Context context, SongEntity song, MusicRepository repo) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.sheet_delete_confirm)
                .setMessage(context.getString(R.string.delete_song_confirm_format, song.title))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    repo.deleteSong(song);
                    Toast.makeText(context, R.string.toast_song_deleted, Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
