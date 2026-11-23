package com.music.annnnn.data.repository;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import androidx.lifecycle.LiveData;

import com.music.annnnn.data.download.MultiSourceAnalyzer;
import com.music.annnnn.data.local.AppDatabase;
import com.music.annnnn.data.local.LocalMusicScanner;
import com.music.annnnn.data.local.PreferencesManager;
import com.music.annnnn.data.local.dao.DownloadDao;
import com.music.annnnn.data.local.dao.MusicDao;
import com.music.annnnn.data.local.entity.AlbumEntity;
import com.music.annnnn.data.local.entity.ArtistSummary;
import com.music.annnnn.data.local.entity.DownloadTaskEntity;
import com.music.annnnn.data.local.entity.PlaylistEntity;
import com.music.annnnn.data.local.entity.PlaylistSongCrossRef;
import com.music.annnnn.data.local.entity.PlaylistWithCount;
import com.music.annnnn.data.local.entity.RadioEntity;
import com.music.annnnn.data.local.entity.SearchHistoryEntity;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.service.MediaDownloadService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

public class MusicRepository {

    private static volatile MusicRepository INSTANCE;

    private final Context appContext;
    private final MusicDao musicDao;
    private final DownloadDao downloadDao;
    private final PreferencesManager preferencesManager;
    private final MultiSourceAnalyzer multiSourceAnalyzer;

    private MusicRepository(Context context) {
        this.appContext = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appContext);
        this.musicDao = db.musicDao();
        this.downloadDao = db.downloadDao();
        this.preferencesManager = new PreferencesManager(appContext);
        this.multiSourceAnalyzer = new MultiSourceAnalyzer();
    }

    public static MusicRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (MusicRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MusicRepository(context);
                }
            }
        }
        return INSTANCE;
    }

    public PreferencesManager getPreferencesManager() {
        return preferencesManager;
    }

    public MultiSourceAnalyzer getMultiSourceAnalyzer() {
        return multiSourceAnalyzer;
    }

    // Songs
    public LiveData<List<SongEntity>> observeAllSongs() {
        return musicDao.observeAllSongs();
    }

    public LiveData<SongEntity> observeSongById(long id) {
        return musicDao.observeSongById(id);
    }

    public LiveData<List<SongEntity>> observeFavoriteSongs() {
        return musicDao.observeFavoriteSongs();
    }

    public LiveData<List<SongEntity>> observeDownloadedSongs() {
        return musicDao.observeDownloadedSongs();
    }

    public LiveData<List<SongEntity>> observeRecentSongs() {
        return musicDao.observeRecentSongs();
    }

    public LiveData<List<SongEntity>> observeChartSongs(String region) {
        return musicDao.observeChartSongs(region);
    }

    public LiveData<List<SongEntity>> observeSongsByAlbum(long albumId) {
        return musicDao.observeSongsByAlbum(albumId);
    }

    public LiveData<List<SongEntity>> observeSongsByArtist(String artist) {
        return musicDao.observeSongsByArtist(artist);
    }

    public LiveData<List<SongEntity>> observeSongsInPlaylist(long playlistId) {
        return musicDao.observeSongsInPlaylist(playlistId);
    }

    public LiveData<List<SongEntity>> observeSongsByKeyword(String keyword) {
        return musicDao.observeSongsByKeyword(keyword);
    }

    public LiveData<List<SongEntity>> searchSongs(String query) {
        return musicDao.searchSongs(query);
    }

    public LiveData<List<ArtistSummary>> observeArtists() {
        return musicDao.observeArtists();
    }

    public LiveData<List<ArtistSummary>> searchArtists(String query) {
        return musicDao.searchArtists(query);
    }

    // Counts & Stats
    public LiveData<Integer> observeFavoriteCount() {
        return musicDao.observeFavoriteCount();
    }

    public LiveData<Integer> observeDownloadedCount() {
        return musicDao.observeDownloadedCount();
    }

    public LiveData<Integer> observeRecentCount() {
        return musicDao.observeRecentCount();
    }

    public LiveData<Integer> observeTotalPlayCount() {
        return musicDao.observeTotalPlayCount();
    }

    // Mutations
    public void toggleFavorite(long songId, boolean newFavoriteState) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> musicDao.updateFavorite(songId, newFavoriteState));
    }

    public void recordSongPlay(long songId) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> musicDao.recordSongPlay(songId, System.currentTimeMillis()));
    }

    public void updateSongMetadata(long songId, String title, String artist, String album, String genre) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> musicDao.updateSongMetadata(songId, title, artist, album, genre));
    }

    public void updateSongCoverFromUri(long songId, Uri imageUri, Consumer<String> onSaved) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            String savedPath = null;
            try {
                File coversDir = new File(appContext.getFilesDir(), "custom_covers");
                if (!coversDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    coversDir.mkdirs();
                }
                deleteCustomCoverFilesForSong(coversDir, songId);

                File dest = new File(coversDir, "cover_" + songId + "_" + System.currentTimeMillis() + ".jpg");
                Bitmap decoded = decodeScaledBitmap(imageUri, 840);
                if (decoded != null) {
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        decoded.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                        fos.flush();
                    } finally {
                        decoded.recycle();
                    }
                    savedPath = Uri.fromFile(dest).toString();
                    musicDao.updateSongCover(songId, savedPath);
                }
            } catch (Exception ignored) {
            }
            if (onSaved != null) {
                onSaved.accept(savedPath);
            }
        });
    }

    public void resetSongCover(long songId, Runnable onDone) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            File coversDir = new File(appContext.getFilesDir(), "custom_covers");
            if (coversDir.exists()) {
                deleteCustomCoverFilesForSong(coversDir, songId);
            }
            musicDao.updateSongCover(songId, null);
            if (onDone != null) {
                onDone.run();
            }
        });
    }

    private void deleteCustomCoverFilesForSong(File coversDir, long songId) {
        String prefix = "cover_" + songId + "_";
        File[] files = coversDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().startsWith(prefix)) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
    }

    private Bitmap decodeScaledBitmap(Uri uri, int maxDim) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream is = appContext.getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                BitmapFactory.decodeStream(is, null, bounds);
            }
            int sample = 1;
            int w = bounds.outWidth;
            int h = bounds.outHeight;
            while (w / sample > maxDim * 2 || h / sample > maxDim * 2) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream is = appContext.getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
                if (bmp == null) return null;
                int bw = bmp.getWidth();
                int bh = bmp.getHeight();
                if (bw > maxDim || bh > maxDim) {
                    float scale = Math.min((float) maxDim / bw, (float) maxDim / bh);
                    Bitmap scaled = Bitmap.createScaledBitmap(bmp, Math.round(bw * scale), Math.round(bh * scale), true);
                    if (scaled != bmp) {
                        bmp.recycle();
                    }
                    return scaled;
                }
                return bmp;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public void deleteSong(SongEntity song) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            musicDao.deleteSongFromAllPlaylists(song.id);
            musicDao.deleteSongById(song.id);
            if (!TextUtils.isEmpty(song.filePath) && song.filePath.contains("/downloads/")) {
                File f = new File(song.filePath);
                if (f.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        });
    }

    // Playlists
    public LiveData<List<PlaylistWithCount>> observeAllPlaylistsWithCount() {
        return musicDao.observeAllPlaylistsWithCount();
    }

    public LiveData<List<PlaylistWithCount>> observeUserPlaylistsWithCount() {
        return musicDao.observeUserPlaylistsWithCount();
    }

    public LiveData<List<PlaylistEntity>> observeFeaturedPlaylists() {
        return musicDao.observeFeaturedPlaylists();
    }

    public LiveData<PlaylistEntity> observePlaylistById(long playlistId) {
        return musicDao.observePlaylistById(playlistId);
    }

    public LiveData<List<PlaylistEntity>> searchPlaylists(String query) {
        return musicDao.searchPlaylists(query);
    }

    public void createPlaylist(String name, String description) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            PlaylistEntity p = new PlaylistEntity(
                    name,
                    TextUtils.isEmpty(description) ? "Playlist cá nhân" : description,
                    preferencesManager.getUserName(),
                    "bg_cover_aurora",
                    true
            );
            musicDao.insertPlaylist(p);
        });
    }

    public void addSongToPlaylist(long playlistId, long songId) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            int pos = musicDao.getNextPlaylistPosition(playlistId);
            musicDao.insertPlaylistSong(new PlaylistSongCrossRef(playlistId, songId, pos));
        });
    }

    public void removeSongFromPlaylist(long playlistId, long songId) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> musicDao.removeSongFromPlaylist(playlistId, songId));
    }

    public void deletePlaylist(long playlistId) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            musicDao.clearPlaylistSongs(playlistId);
            musicDao.deletePlaylistById(playlistId);
        });
    }

    // Albums
    public LiveData<List<AlbumEntity>> observeAllAlbums() {
        return musicDao.observeAllAlbums();
    }

    public LiveData<AlbumEntity> observeAlbumById(long albumId) {
        return musicDao.observeAlbumById(albumId);
    }

    public LiveData<List<AlbumEntity>> searchAlbums(String query) {
        return musicDao.searchAlbums(query);
    }

    // Radios
    public LiveData<List<RadioEntity>> observeAllRadios() {
        return musicDao.observeAllRadios();
    }

    public LiveData<RadioEntity> observeRadioById(long radioId) {
        return musicDao.observeRadioById(radioId);
    }

    // Search history
    public LiveData<List<SearchHistoryEntity>> observeSearchHistory() {
        return musicDao.observeSearchHistory();
    }

    public void saveSearchQuery(String query) {
        if (TextUtils.isEmpty(query) || query.trim().isEmpty()) return;
        AppDatabase.DATABASE_EXECUTOR.execute(() ->
                musicDao.insertSearchHistory(new SearchHistoryEntity(query.trim(), System.currentTimeMillis()))
        );
    }

    public void clearSearchHistory() {
        AppDatabase.DATABASE_EXECUTOR.execute(musicDao::clearSearchHistory);
    }

    // Downloads
    public LiveData<List<DownloadTaskEntity>> observeActiveDownloads() {
        return downloadDao.observeActiveDownloads();
    }

    public LiveData<List<DownloadTaskEntity>> observeCompletedDownloads() {
        return downloadDao.observeCompletedDownloads();
    }

    public LiveData<List<DownloadTaskEntity>> observeFailedDownloads() {
        return downloadDao.observeFailedDownloads();
    }

    public void enqueueMediaDownload(
            MultiSourceAnalyzer.MediaAnalysisResult info,
            String format,
            String quality,
            Consumer<Boolean> onResultEnqueued
    ) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            DownloadTaskEntity existing = downloadDao.findDuplicateTaskSync(info.originalUrl, format, quality);
            if (existing != null) {
                if (onResultEnqueued != null) {
                    onResultEnqueued.accept(false);
                }
                return;
            }

            DownloadTaskEntity task = new DownloadTaskEntity();
            task.youtubeUrl = info.originalUrl;
            task.videoId = info.mediaId != null ? info.mediaId : "";
            task.sourceName = info.sourceName != null ? info.sourceName : "Web Media";
            task.mediaType = "MP4".equalsIgnoreCase(format)
                    ? DownloadTaskEntity.MEDIA_VIDEO
                    : DownloadTaskEntity.MEDIA_AUDIO;
            task.isPlaylist = info.isPlaylist;
            task.playlistCount = info.playlistCount;
            task.currentItemIndex = info.isPlaylist ? 1 : 0;
            task.title = info.title != null ? info.title : "Media " + task.videoId;
            task.channelName = info.channelName != null ? info.channelName : task.sourceName;
            task.thumbnailUrl = info.thumbnailUrl;
            task.durationText = info.durationText != null ? info.durationText : "00:00";
            task.durationMs = info.durationMs;
            task.format = format;
            task.quality = quality;
            task.streamUrl = "MP4".equalsIgnoreCase(format) ? info.bestVideoUrl : info.bestAudioUrl;
            task.status = DownloadTaskEntity.STATUS_QUEUED;
            task.totalBytes = info.approxSizeBytes;
            task.speedText = "Đang chờ...";

            long taskId = downloadDao.insertTask(task);
            MediaDownloadService.startTask(appContext, taskId);
            if (onResultEnqueued != null) {
                onResultEnqueued.accept(true);
            }
        });
    }

    public void pauseDownload(long taskId) {
        MediaDownloadService.pauseTask(appContext, taskId);
    }

    public void resumeOrRetryDownload(long taskId) {
        MediaDownloadService.resumeOrRetryTask(appContext, taskId);
    }

    public void cancelDownload(long taskId) {
        MediaDownloadService.cancelTask(appContext, taskId);
    }

    public void loadSongByFilePath(String filePath, Consumer<SongEntity> callback) {
        if (TextUtils.isEmpty(filePath) || callback == null) return;
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            SongEntity song = musicDao.findSongByPathSync(filePath);
            callback.accept(song);
        });
    }

    // Import local audio via SAF (Zero-permission OpenDocument)
    public void importLocalAudioUri(Uri uri, Runnable onDone) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            try {
                File importDir = new File(appContext.getFilesDir(), "imported_music");
                if (!importDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    importDir.mkdirs();
                }

                String displayName = resolveFileName(uri);
                String safeName = System.currentTimeMillis() + "_" + displayName.replaceAll("[^a-zA-Z0-9._-]", "_");
                File outFile = new File(importDir, safeName);

                try (InputStream is = appContext.getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(outFile)) {
                    if (is != null) {
                        byte[] buf = new byte[16 * 1024];
                        int r;
                        while ((r = is.read(buf)) != -1) {
                            fos.write(buf, 0, r);
                        }
                    }
                }

                String title = displayName.replaceFirst("[.][^.]+$", "");
                String artist = "Nghệ sĩ thiết bị";
                String album = "Nhạc từ thiết bị";
                long durationMs = 180_000L;

                String coverUrl = null;
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                try {
                    mmr.setDataSource(outFile.getAbsolutePath());
                    String metaTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                    String metaArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    String metaAlbum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                    String metaDur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (!TextUtils.isEmpty(metaTitle)) title = metaTitle;
                    if (!TextUtils.isEmpty(metaArtist)) artist = metaArtist;
                    if (!TextUtils.isEmpty(metaAlbum)) album = metaAlbum;
                    if (!TextUtils.isEmpty(metaDur)) durationMs = Long.parseLong(metaDur);

                    byte[] embeddedArt = mmr.getEmbeddedPicture();
                    if (embeddedArt != null && embeddedArt.length > 0) {
                        File coversDir = new File(appContext.getFilesDir(), "custom_covers");
                        if (!coversDir.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            coversDir.mkdirs();
                        }
                        File artFile = new File(coversDir, "embedded_" + System.currentTimeMillis() + ".jpg");
                        try (FileOutputStream artFos = new FileOutputStream(artFile)) {
                            artFos.write(embeddedArt);
                            artFos.flush();
                        }
                        coverUrl = Uri.fromFile(artFile).toString();
                    }
                } catch (Exception ignored) {
                } finally {
                    mmr.release();
                }

                SongEntity imported = new SongEntity(
                        title, artist, 0L, album, durationMs,
                        outFile.getAbsolutePath(), "", coverUrl,
                        "",
                        "Offline", "Thư giãn", false, true, 0, null, 0
                );
                musicDao.insertSong(imported);
            } catch (Exception ignored) {
            } finally {
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
    }

    private String resolveFileName(Uri uri) {
        String result = "audio_" + System.currentTimeMillis() + ".mp3";
        try (Cursor cursor = appContext.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    result = cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public void scanDeviceMusic(Consumer<LocalMusicScanner.ScanResult> onResult) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            LocalMusicScanner.ScanResult result = LocalMusicScanner.scanAndSync(appContext, musicDao);
            if (onResult != null) {
                onResult.accept(result);
            }
        });
    }

    public long calculateUsedStorageBytes() {
        long total = 0;
        total += dirSize(new File(appContext.getFilesDir(), "downloads"));
        total += dirSize(new File(appContext.getFilesDir(), "imported_music"));
        return total;
    }

    public void clearCacheFiles() {
        deleteDirContents(appContext.getCacheDir());
    }

    private long dirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long sum = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) sum += dirSize(f);
                else sum += f.length();
            }
        }
        return sum;
    }

    private void deleteDirContents(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirContents(f);
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }
}
