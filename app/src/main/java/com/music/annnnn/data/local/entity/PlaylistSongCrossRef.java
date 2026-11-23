package com.music.annnnn.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;

@Entity(
        tableName = "playlist_songs",
        primaryKeys = {"playlistId", "songId"},
        indices = {@Index("songId")}
)
public class PlaylistSongCrossRef {
    public long playlistId;
    public long songId;
    public int position;
    public long addedAt;

    public PlaylistSongCrossRef() {}

    public PlaylistSongCrossRef(long playlistId, long songId, int position) {
        this.playlistId = playlistId;
        this.songId = songId;
        this.position = position;
        this.addedAt = System.currentTimeMillis();
    }
}
