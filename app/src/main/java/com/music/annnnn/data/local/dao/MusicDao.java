package com.music.annnnn.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.music.annnnn.data.local.entity.AlbumEntity;
import com.music.annnnn.data.local.entity.ArtistSummary;
import com.music.annnnn.data.local.entity.PlaylistEntity;
import com.music.annnnn.data.local.entity.PlaylistSongCrossRef;
import com.music.annnnn.data.local.entity.PlaylistWithCount;
import com.music.annnnn.data.local.entity.RadioEntity;
import com.music.annnnn.data.local.entity.SearchHistoryEntity;
import com.music.annnnn.data.local.entity.SongEntity;

import java.util.List;

@Dao
public interface MusicDao {

    @Query("SELECT * FROM songs ORDER BY createdAt DESC")
    LiveData<List<SongEntity>> observeAllSongs();

    @Query("SELECT * FROM songs ORDER BY createdAt DESC")
    List<SongEntity> getAllSongsSync();

    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    LiveData<SongEntity> observeSongById(long songId);

    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    SongEntity getSongByIdSync(long songId);

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY createdAt DESC")
    LiveData<List<SongEntity>> observeFavoriteSongs();

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY createdAt DESC")
    LiveData<List<SongEntity>> observeDownloadedSongs();

    @Query("SELECT * FROM songs WHERE filePath = :filePath LIMIT 1")
    SongEntity findSongByPathSync(String filePath);

    @Query("SELECT * FROM songs WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 50")
    LiveData<List<SongEntity>> observeRecentSongs();

    @Query("SELECT * FROM songs WHERE chartRegion = :region ORDER BY chartRank ASC")
    LiveData<List<SongEntity>> observeChartSongs(String region);

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY id ASC")
    LiveData<List<SongEntity>> observeSongsByAlbum(long albumId);

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY id ASC")
    List<SongEntity> getSongsByAlbumSync(long albumId);

    @Query("SELECT * FROM songs WHERE artist = :artistName ORDER BY playCount DESC, title ASC")
    LiveData<List<SongEntity>> observeSongsByArtist(String artistName);

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.position ASC, ps.addedAt ASC")
    LiveData<List<SongEntity>> observeSongsInPlaylist(long playlistId);

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.position ASC, ps.addedAt ASC")
    List<SongEntity> getSongsInPlaylistSync(long playlistId);

    @Query("SELECT * FROM songs WHERE genre LIKE '%' || :keyword || '%' OR mood LIKE '%' || :keyword || '%' OR artist LIKE '%' || :keyword || '%' OR title LIKE '%' || :keyword || '%' ORDER BY playCount DESC")
    LiveData<List<SongEntity>> observeSongsByKeyword(String keyword);

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR albumName LIKE '%' || :query || '%' ORDER BY playCount DESC")
    LiveData<List<SongEntity>> searchSongs(String query);

    @Query("SELECT artist, COUNT(*) as songCount, MIN(coverResName) as coverResName FROM songs GROUP BY artist ORDER BY songCount DESC, artist ASC")
    LiveData<List<ArtistSummary>> observeArtists();

    @Query("SELECT artist, COUNT(*) as songCount, MIN(coverResName) as coverResName FROM songs WHERE artist LIKE '%' || :query || '%' GROUP BY artist ORDER BY songCount DESC")
    LiveData<List<ArtistSummary>> searchArtists(String query);

    @Query("SELECT COUNT(*) FROM songs WHERE isFavorite = 1")
    LiveData<Integer> observeFavoriteCount();

    @Query("SELECT COUNT(*) FROM songs WHERE isDownloaded = 1")
    LiveData<Integer> observeDownloadedCount();

    @Query("SELECT COUNT(*) FROM songs WHERE lastPlayedAt > 0")
    LiveData<Integer> observeRecentCount();

    @Query("SELECT COALESCE(SUM(playCount), 0) FROM songs")
    LiveData<Integer> observeTotalPlayCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertSong(SongEntity song);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertSongs(List<SongEntity> songs);

    @Update
    void updateSong(SongEntity song);

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    void updateFavorite(long songId, boolean isFavorite);

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :songId")
    void recordSongPlay(long songId, long timestamp);

    @Query("UPDATE songs SET title = :title, artist = :artist, albumName = :albumName, genre = :genre WHERE id = :songId")
    void updateSongMetadata(long songId, String title, String artist, String albumName, String genre);

    @Query("UPDATE songs SET coverUrl = :coverUrl WHERE id = :songId")
    void updateSongCover(long songId, String coverUrl);

    @Query("DELETE FROM songs WHERE id = :songId")
    void deleteSongById(long songId);

    @Query("DELETE FROM songs WHERE id IN (:songIds)")
    void deleteSongsByIds(List<Long> songIds);

    @Query("DELETE FROM songs WHERE filePath LIKE '%/offline_tracks/%'")
    void deleteSeedOfflineSongs();

    @Query("DELETE FROM playlist_songs WHERE songId = :songId")
    void deleteSongFromAllPlaylists(long songId);

    @Query("DELETE FROM playlist_songs WHERE songId IN (SELECT id FROM songs WHERE filePath LIKE '%/offline_tracks/%')")
    void deleteSeedPlaylistLinks();

    // Playlists
    @Query("SELECT p.*, (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS songCount FROM playlists p ORDER BY p.isUserCreated DESC, p.createdAt DESC")
    LiveData<List<PlaylistWithCount>> observeAllPlaylistsWithCount();

    @Query("SELECT p.*, (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS songCount FROM playlists p WHERE p.isUserCreated = 1 ORDER BY p.createdAt DESC")
    LiveData<List<PlaylistWithCount>> observeUserPlaylistsWithCount();

    @Query("SELECT * FROM playlists WHERE isUserCreated = 0 ORDER BY id ASC")
    LiveData<List<PlaylistEntity>> observeFeaturedPlaylists();

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    LiveData<PlaylistEntity> observePlaylistById(long playlistId);

    @Query("SELECT * FROM playlists WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    LiveData<List<PlaylistEntity>> searchPlaylists(String query);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertPlaylist(PlaylistEntity playlist);

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    void deletePlaylistById(long playlistId);

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    void clearPlaylistSongs(long playlistId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertPlaylistSong(PlaylistSongCrossRef crossRef);

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    void removeSongFromPlaylist(long playlistId, long songId);

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    int getNextPlaylistPosition(long playlistId);

    @Query("DELETE FROM playlists WHERE isUserCreated = 0")
    void deleteNonUserPlaylists();

    @Query("DELETE FROM playlists WHERE isUserCreated = 1 AND name = 'Nhạc Nghe Mỗi Ngày' AND creatorName = 'Bạn' AND id NOT IN (SELECT DISTINCT playlistId FROM playlist_songs)")
    void deleteEmptySeedUserPlaylist();

    // Albums
    @Query("SELECT * FROM albums ORDER BY releaseYear DESC, id ASC")
    LiveData<List<AlbumEntity>> observeAllAlbums();

    @Query("SELECT * FROM albums ORDER BY id ASC")
    List<AlbumEntity> getAllAlbumsSync();

    @Query("DELETE FROM albums WHERE title IN ('Đêm Thành Phố Mưa', 'Chiều Hoàng Hôn Lo-Fi', 'Midnight Neon Echoes', 'Seoul Starlight') AND id NOT IN (SELECT DISTINCT albumId FROM songs)")
    void deleteEmptySeedAlbums();

    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    LiveData<AlbumEntity> observeAlbumById(long albumId);

    @Query("SELECT * FROM albums WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    LiveData<List<AlbumEntity>> searchAlbums(String query);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertAlbum(AlbumEntity album);

    // Radios
    @Query("SELECT * FROM radios ORDER BY id ASC")
    LiveData<List<RadioEntity>> observeAllRadios();

    @Query("SELECT * FROM radios WHERE id = :radioId LIMIT 1")
    LiveData<RadioEntity> observeRadioById(long radioId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRadios(List<RadioEntity> radios);

    @Query("DELETE FROM radios")
    void deleteAllRadios();

    // Search history
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 15")
    LiveData<List<SearchHistoryEntity>> observeSearchHistory();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSearchHistory(SearchHistoryEntity history);

    @Query("DELETE FROM search_history")
    void clearSearchHistory();
}
