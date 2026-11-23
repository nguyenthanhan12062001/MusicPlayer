package com.music.annnnn.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.music.annnnn.data.download.MediaStorageHelper;
import com.music.annnnn.data.local.dao.DownloadDao;
import com.music.annnnn.data.local.dao.MusicDao;
import com.music.annnnn.data.local.entity.AlbumEntity;
import com.music.annnnn.data.local.entity.DownloadTaskEntity;
import com.music.annnnn.data.local.entity.PlaylistEntity;
import com.music.annnnn.data.local.entity.PlaylistSongCrossRef;
import com.music.annnnn.data.local.entity.RadioEntity;
import com.music.annnnn.data.local.entity.SearchHistoryEntity;
import com.music.annnnn.data.local.entity.SongEntity;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                SongEntity.class,
                PlaylistEntity.class,
                PlaylistSongCrossRef.class,
                AlbumEntity.class,
                RadioEntity.class,
                SearchHistoryEntity.class,
                DownloadTaskEntity.class
        },
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    public static final ExecutorService DATABASE_EXECUTOR = Executors.newFixedThreadPool(4);

    private static final androidx.room.migration.Migration MIGRATION_1_2 =
            new androidx.room.migration.Migration(1, 2) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase db) {
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN sourceName TEXT NOT NULL DEFAULT 'YouTube'");
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'AUDIO'");
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN isPlaylist INTEGER NOT NULL DEFAULT 0");
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN playlistCount INTEGER NOT NULL DEFAULT 0");
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN currentItemIndex INTEGER NOT NULL DEFAULT 0");
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN formatId TEXT");
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN etaSeconds INTEGER NOT NULL DEFAULT 0");
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN speedText TEXT");
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN sizeText TEXT");
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN processId TEXT");
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN tempDirPath TEXT");
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN mediaStoreUri TEXT");
                }
            };

    private static final androidx.room.migration.Migration MIGRATION_2_3 =
            new androidx.room.migration.Migration(2, 3) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase db) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_filePath ON songs (filePath)");
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_artist ON songs (artist)");
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_albumId ON songs (albumId)");
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_chartRegion ON songs (chartRegion)");
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_isFavorite ON songs (isFavorite)");
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_isDownloaded ON songs (isDownloaded)");
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_lastPlayedAt ON songs (lastPlayedAt)");
                }
            };

    public abstract MusicDao musicDao();

    public abstract DownloadDao downloadDao();

    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    final Context appContext = context.getApplicationContext();
                    INSTANCE = Room.databaseBuilder(appContext, AppDatabase.class, "office_music_db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static void purgeSampleDataOnce(Context context) {
        final Context appContext = context.getApplicationContext();
        final PreferencesManager prefs = new PreferencesManager(appContext);
        if (prefs.isSampleDataPurged()) {
            return;
        }
        AppDatabase db = getInstance(appContext);
        DATABASE_EXECUTOR.execute(() -> {
            try {
                db.runInTransaction(() -> {
                    MusicDao dao = db.musicDao();
                    dao.deleteSeedPlaylistLinks();
                    dao.deleteSeedOfflineSongs();
                    dao.deleteNonUserPlaylists();
                    dao.deleteEmptySeedUserPlaylist();
                    dao.deleteEmptySeedAlbums();
                    dao.deleteAllRadios();
                });
                MediaStorageHelper.deleteRecursively(new File(appContext.getFilesDir(), "offline_tracks"));
                prefs.markSampleDataPurged();
            } catch (Exception ignored) {
            }
        });
    }
}
