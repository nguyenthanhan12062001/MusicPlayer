package com.music.annnnn.data.local;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.music.annnnn.R;
import com.music.annnnn.data.local.dao.MusicDao;
import com.music.annnnn.data.local.entity.AlbumEntity;
import com.music.annnnn.data.local.entity.SongEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Scans device audio files from MediaStore without requiring broad MANAGE_EXTERNAL_STORAGE.
 * Uses READ_MEDIA_AUDIO on Android 13+ and READ_EXTERNAL_STORAGE on Android 8-12.
 */
public final class LocalMusicScanner {

    public static class ScanResult {
        public final int addedCount;
        public final int totalDeviceCount;

        public ScanResult(int addedCount, int totalDeviceCount) {
            this.addedCount = addedCount;
            this.totalDeviceCount = totalDeviceCount;
        }
    }

    private static final String[] COVER_POOL = {
            "img_cover_vpop",
            "img_cover_lofi",
            "img_cover_radio",
            "bg_cover_aurora",
            "bg_cover_sunset",
            "bg_cover_midnight",
            "bg_cover_ocean"
    };

    private LocalMusicScanner() {
    }

    public static String getRequiredAudioPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static boolean hasAudioScanPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, getRequiredAudioPermission())
                == PackageManager.PERMISSION_GRANTED;
    }

    public static ScanResult scanAndSync(Context context, MusicDao musicDao) {
        Context appContext = context.getApplicationContext();
        if (!hasAudioScanPermission(appContext)) {
            return new ScanResult(0, 0);
        }

        ContentResolver resolver = appContext.getContentResolver();
        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
                + MediaStore.Audio.Media.DURATION + " >= 5000";
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        List<SongEntity> existingSongs = musicDao.getAllSongsSync();
        Set<String> existingPaths = new HashSet<>(existingSongs.size() * 2);
        for (SongEntity s : existingSongs) {
            existingPaths.add(s.filePath);
        }

        List<AlbumEntity> existingAlbums = musicDao.getAllAlbumsSync();
        Map<String, Long> albumKeyToId = new HashMap<>();
        for (AlbumEntity album : existingAlbums) {
            albumKeyToId.put(buildAlbumKey(album.title, album.artist), album.id);
        }

        Set<String> foundMediaUris = new HashSet<>();
        List<SongEntity> toInsert = new ArrayList<>();
        String unknownArtist = appContext.getString(R.string.unknown_artist);
        String singleAlbum = appContext.getString(R.string.single_album);

        try (Cursor cursor = resolver.query(collection, projection, selection, null, sortOrder)) {
            if (cursor != null) {
                int colId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int colTitle = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int colArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int colAlbum = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                int colAlbumId = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
                int colDuration = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                int colDisplayName = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
                int colMime = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);

                while (cursor.moveToNext()) {
                    long audioId = cursor.getLong(colId);
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId);
                    String uriStr = contentUri.toString();
                    foundMediaUris.add(uriStr);

                    if (existingPaths.contains(uriStr)) {
                        continue;
                    }

                    String title = colTitle >= 0 ? cursor.getString(colTitle) : null;
                    if (TextUtils.isEmpty(title)) {
                        String disp = colDisplayName >= 0 ? cursor.getString(colDisplayName) : null;
                        title = !TextUtils.isEmpty(disp)
                                ? disp.replaceFirst("[.][^.]+$", "")
                                : ("Audio " + audioId);
                    }

                    String artist = colArtist >= 0 ? cursor.getString(colArtist) : null;
                    if (TextUtils.isEmpty(artist) || "<unknown>".equalsIgnoreCase(artist.trim())) {
                        artist = unknownArtist;
                    }

                    String albumTitle = colAlbum >= 0 ? cursor.getString(colAlbum) : null;
                    if (TextUtils.isEmpty(albumTitle) || "<unknown>".equalsIgnoreCase(albumTitle.trim())) {
                        albumTitle = singleAlbum;
                    }

                    long durationMs = colDuration >= 0 ? cursor.getLong(colDuration) : 0L;
                    if (durationMs <= 0L) durationMs = 180_000L;

                    long mediaAlbumId = colAlbumId >= 0 ? cursor.getLong(colAlbumId) : 0L;
                    String coverUrl = mediaAlbumId > 0L
                            ? ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), mediaAlbumId).toString()
                            : null;
                    String albumFallback = COVER_POOL[(int) (Math.abs(audioId) % COVER_POOL.length)];

                    String albumKey = buildAlbumKey(albumTitle, artist);
                    Long roomAlbumId = albumKeyToId.get(albumKey);
                    if (roomAlbumId == null && !singleAlbum.equals(albumTitle)) {
                        long insertedAlbumId = musicDao.insertAlbum(new AlbumEntity(
                                albumTitle,
                                artist,
                                albumTitle + " • " + artist,
                                2025,
                                albumFallback,
                                "Local"
                        ));
                        roomAlbumId = insertedAlbumId;
                        albumKeyToId.put(albumKey, insertedAlbumId);
                    }

                    String mime = colMime >= 0 ? cursor.getString(colMime) : "";
                    String genre = !TextUtils.isEmpty(mime) && mime.contains("/")
                            ? mime.substring(mime.indexOf('/') + 1).toUpperCase(Locale.ROOT)
                            : "Audio";

                    SongEntity entity = new SongEntity(
                            title.trim(),
                            artist.trim(),
                            roomAlbumId != null ? roomAlbumId : 0L,
                            albumTitle.trim(),
                            durationMs,
                            uriStr,
                            "",
                            coverUrl,
                            "",
                            genre,
                            "Local",
                            false,
                            false,
                            0,
                            null,
                            0
                    );
                    toInsert.add(entity);
                    existingPaths.add(uriStr);
                }
            }
        } catch (Exception ignored) {
        }

        if (!toInsert.isEmpty()) {
            musicDao.insertSongs(toInsert);
        }

        // Remove stale scanned device entries that no longer exist in MediaStore
        List<Long> staleIds = new ArrayList<>();
        String mediaPrefix = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString();
        for (SongEntity existing : existingSongs) {
            if (existing.filePath.startsWith(mediaPrefix)
                    && !existing.isDownloaded
                    && !foundMediaUris.contains(existing.filePath)) {
                staleIds.add(existing.id);
            }
        }
        if (!staleIds.isEmpty()) {
            musicDao.deleteSongsByIds(staleIds);
        }

        return new ScanResult(toInsert.size(), foundMediaUris.size());
    }

    private static String buildAlbumKey(String title, String artist) {
        return (title != null ? title.trim().toLowerCase(Locale.ROOT) : "")
                + "|"
                + (artist != null ? artist.trim().toLowerCase(Locale.ROOT) : "");
    }
}
