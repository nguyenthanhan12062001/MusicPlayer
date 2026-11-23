package com.music.annnnn.data.download;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.music.annnnn.data.local.PreferencesManager;
import com.music.annnnn.data.local.dao.MusicDao;
import com.music.annnnn.data.local.entity.DownloadTaskEntity;
import com.music.annnnn.data.local.entity.SongEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Registers downloaded audio/video files into a user-selected SAF folder or Android MediaStore,
 * and inserts downloaded audio tracks into Room so they appear in Library > Downloaded Music.
 */
public final class MediaStorageHelper {

    public static class StorageResult {
        public String primaryPlayablePath;
        public String primaryMediaStoreUri;
        public long totalBytes;
        public int savedFileCount;
    }

    private MediaStorageHelper() {
    }

    public static StorageResult persistDownloadedOutputs(
            Context context,
            DownloadTaskEntity task,
            File tempDir,
            MusicDao musicDao
    ) throws IOException {
        List<File> mediaFiles = collectMediaFiles(tempDir);
        if (mediaFiles.isEmpty()) {
            throw new IOException("Không tìm thấy tệp media sau khi tải xuống.");
        }

        StorageResult result = new StorageResult();
        PreferencesManager prefs = new PreferencesManager(context);
        String customTreeUriStr = prefs.getDownloadTreeUri();

        File appDownloadsDir = new File(context.getFilesDir(), "downloads");

        for (File srcFile : mediaFiles) {
            String fileName = srcFile.getName();
            String ext = getExtension(fileName);
            boolean isVideo = isVideoExtension(ext);
            long fileSize = srcFile.length();
            result.totalBytes += fileSize;

            ExtractedMetadata meta = extractMetadata(srcFile, task);

            String savedUri = null;
            if (!TextUtils.isEmpty(customTreeUriStr)) {
                savedUri = saveToCustomTreeFolder(context, customTreeUriStr, srcFile, ext, isVideo, meta);
            }
            if (TextUtils.isEmpty(savedUri)) {
                savedUri = saveToMediaStore(context, srcFile, ext, isVideo, meta);
            }

            String playablePath = savedUri;
            if (TextUtils.isEmpty(playablePath)) {
                if (!appDownloadsDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    appDownloadsDir.mkdirs();
                }
                File internalCopy = new File(appDownloadsDir, buildUniqueSafeFilename(task.id, fileName));
                copyFile(srcFile, internalCopy);
                playablePath = internalCopy.getAbsolutePath();
            }

            if (!isVideo) {
                SongEntity song = new SongEntity(
                        meta.title,
                        meta.artist,
                        0L,
                        meta.album,
                        meta.durationMs,
                        playablePath,
                        "",
                        task.thumbnailUrl,
                        "",
                        task.sourceName + " " + task.format,
                        "Downloaded",
                        false,
                        true,
                        0,
                        null,
                        0
                );
                musicDao.insertSong(song);
            }

            if (TextUtils.isEmpty(result.primaryPlayablePath)) {
                result.primaryPlayablePath = playablePath;
            }
            if (TextUtils.isEmpty(result.primaryMediaStoreUri)) {
                result.primaryMediaStoreUri = savedUri;
            }
            result.savedFileCount++;
        }

        deleteRecursively(tempDir);
        return result;
    }

    private static String saveToCustomTreeFolder(
            Context context,
            String treeUriStr,
            File srcFile,
            String ext,
            boolean isVideo,
            ExtractedMetadata meta
    ) {
        try {
            Uri treeUri = Uri.parse(treeUriStr);
            ContentResolver resolver = context.getContentResolver();
            Uri parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri));
            String mimeType = resolveMimeType(ext, isVideo);
            String sanitizedTitle = sanitizeTitle(meta.title);
            String displayName = sanitizedTitle + "." + ext;

            Uri docUri = DocumentsContract.createDocument(resolver, parentDocUri, mimeType, displayName);
            if (docUri == null) return null;

            try (InputStream is = new FileInputStream(srcFile);
                 OutputStream os = resolver.openOutputStream(docUri)) {
                if (os == null) return null;
                byte[] buf = new byte[16 * 1024];
                int read;
                while ((read = is.read(buf)) != -1) {
                    os.write(buf, 0, read);
                }
                os.flush();
            }
            return docUri.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitizeTitle(String title) {
        String sanitized = title != null ? title.replaceAll("[\\\\/:*?\"<>|]", "_").trim() : "";
        if (TextUtils.isEmpty(sanitized)) {
            sanitized = "music_media_" + System.currentTimeMillis();
        }
        return sanitized;
    }

    private static List<File> collectMediaFiles(File dir) {
        List<File> out = new ArrayList<>();
        if (dir == null || !dir.exists()) return out;
        File[] files = dir.listFiles();
        if (files == null) return out;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            if (f.isDirectory()) {
                out.addAll(collectMediaFiles(f));
            } else if (f.length() > 0 && isSupportedMediaFile(f.getName())) {
                out.add(f);
            }
        }
        return out;
    }

    private static boolean isSupportedMediaFile(String name) {
        String ext = getExtension(name);
        return "mp3".equals(ext)
                || "m4a".equals(ext)
                || "opus".equals(ext)
                || "flac".equals(ext)
                || "ogg".equals(ext)
                || "aac".equals(ext)
                || "wav".equals(ext)
                || "mp4".equals(ext)
                || "mkv".equals(ext)
                || "webm".equals(ext);
    }

    private static boolean isVideoExtension(String ext) {
        return "mp4".equals(ext) || "mkv".equals(ext) || "webm".equals(ext);
    }

    private static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static String buildUniqueSafeFilename(long taskId, String originalName) {
        String cleaned = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return taskId + "_" + System.currentTimeMillis() + "_" + cleaned;
    }

    private static String resolveMimeType(String ext, boolean isVideo) {
        switch (ext) {
            case "mp3":
                return "audio/mpeg";
            case "m4a":
            case "aac":
                return "audio/mp4";
            case "opus":
                return "audio/opus";
            case "ogg":
                return "audio/ogg";
            case "flac":
                return "audio/flac";
            case "wav":
                return "audio/wav";
            case "mp4":
                return "video/mp4";
            case "mkv":
                return "video/x-matroska";
            case "webm":
                return isVideo ? "video/webm" : "audio/webm";
            default:
                return isVideo ? "video/*" : "audio/*";
        }
    }

    private static class ExtractedMetadata {
        String title;
        String artist;
        String album;
        long durationMs;
    }

    private static ExtractedMetadata extractMetadata(File file, DownloadTaskEntity task) {
        ExtractedMetadata meta = new ExtractedMetadata();
        String baseName = file.getName().replaceFirst("[.][^.]+$", "");
        meta.title = !TextUtils.isEmpty(task.title) && !task.isPlaylist ? task.title : baseName;
        meta.artist = !TextUtils.isEmpty(task.channelName) ? task.channelName : task.sourceName;
        meta.album = task.isPlaylist && !TextUtils.isEmpty(task.title)
                ? task.title
                : (task.sourceName + " Offline");
        meta.durationMs = task.durationMs > 0 ? task.durationMs : 180_000L;

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());
            String t = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String a = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String al = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (!TextUtils.isEmpty(t)) meta.title = t;
            if (!TextUtils.isEmpty(a)) meta.artist = a;
            if (!TextUtils.isEmpty(al)) meta.album = al;
            if (!TextUtils.isEmpty(d)) {
                long parsed = Long.parseLong(d);
                if (parsed > 0) meta.durationMs = parsed;
            }
        } catch (Exception ignored) {
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {
            }
        }
        return meta;
    }

    private static String saveToMediaStore(
            Context context,
            File srcFile,
            String ext,
            boolean isVideo,
            ExtractedMetadata meta
    ) {
        String mimeType = resolveMimeType(ext, isVideo);
        String sanitizedTitle = meta.title.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (TextUtils.isEmpty(sanitizedTitle)) {
            sanitizedTitle = "office_ai_media_" + System.currentTimeMillis();
        }
        String displayName = sanitizedTitle + "." + ext;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = isVideo
                    ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.TITLE, meta.title);
            if (!isVideo) {
                values.put(MediaStore.Audio.Media.ARTIST, meta.artist);
                values.put(MediaStore.Audio.Media.ALBUM, meta.album);
                values.put(MediaStore.Audio.Media.DURATION, meta.durationMs);
                values.put(MediaStore.Audio.Media.IS_MUSIC, 1);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Music");
            } else {
                values.put(MediaStore.Video.Media.DURATION, meta.durationMs);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Music");
            }
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri itemUri = null;
            try {
                itemUri = resolver.insert(collection, values);
                if (itemUri == null) return null;

                try (InputStream is = new FileInputStream(srcFile);
                     OutputStream os = resolver.openOutputStream(itemUri)) {
                    if (os != null) {
                        byte[] buf = new byte[16 * 1024];
                        int read;
                        while ((read = is.read(buf)) != -1) {
                            os.write(buf, 0, read);
                        }
                        os.flush();
                    }
                }

                ContentValues doneValues = new ContentValues();
                doneValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(itemUri, doneValues, null, null);
                return itemUri.toString();
            } catch (Exception e) {
                if (itemUri != null) {
                    try {
                        resolver.delete(itemUri, null, null);
                    } catch (Exception ignored) {
                    }
                }
                return null;
            }
        } else {
            try {
                File baseDir = Environment.getExternalStoragePublicDirectory(
                        isVideo ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_MUSIC
                );
                File targetDir = new File(baseDir, "Music");
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    return null;
                }
                File outFile = new File(targetDir, displayName);
                copyFile(srcFile, outFile);
                MediaScannerConnection.scanFile(
                        context,
                        new String[]{outFile.getAbsolutePath()},
                        new String[]{mimeType},
                        null
                );
                return Uri.fromFile(outFile).toString();
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[16 * 1024];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
        }
    }

    public static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
