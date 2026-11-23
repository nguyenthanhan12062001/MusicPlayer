package com.music.annnnn.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.music.annnnn.data.local.entity.DownloadTaskEntity;

import java.util.List;

@Dao
public interface DownloadDao {

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING', 'CONVERTING', 'PAUSED') ORDER BY createdAt DESC")
    LiveData<List<DownloadTaskEntity>> observeActiveDownloads();

    @Query("SELECT * FROM download_tasks WHERE status = 'COMPLETED' ORDER BY createdAt DESC")
    LiveData<List<DownloadTaskEntity>> observeCompletedDownloads();

    @Query("SELECT * FROM download_tasks WHERE status = 'FAILED' ORDER BY createdAt DESC")
    LiveData<List<DownloadTaskEntity>> observeFailedDownloads();

    @Query("SELECT * FROM download_tasks WHERE id = :taskId LIMIT 1")
    DownloadTaskEntity getTaskByIdSync(long taskId);

    @Query("SELECT * FROM download_tasks WHERE youtubeUrl = :url AND format = :format AND quality = :quality AND status != 'FAILED' LIMIT 1")
    DownloadTaskEntity findDuplicateTaskSync(String url, String format, String quality);

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING', 'CONVERTING')")
    List<DownloadTaskEntity> getInterruptedTasksSync();

    @Query("UPDATE download_tasks SET status = 'PAUSED', speedBytesPerSec = 0, speedText = '' WHERE status IN ('QUEUED', 'DOWNLOADING', 'CONVERTING')")
    void markInterruptedAsPausedSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertTask(DownloadTaskEntity task);

    @Update
    void updateTask(DownloadTaskEntity task);

    @Query("DELETE FROM download_tasks WHERE id = :taskId")
    void deleteTaskById(long taskId);
}
