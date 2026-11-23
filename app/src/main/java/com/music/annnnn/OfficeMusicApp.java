package com.music.annnnn;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import com.music.annnnn.data.download.MediaEngineInitializer;
import com.music.annnnn.data.local.AppDatabase;
import com.music.annnnn.data.local.PreferencesManager;

public class OfficeMusicApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PreferencesManager prefs = new PreferencesManager(this);
        prefs.applySavedTheme();
        prefs.applySavedLanguage();
        AppDatabase.purgeSampleDataOnce(this);
        AppDatabase.DATABASE_EXECUTOR.execute(() ->
                AppDatabase.getInstance(this).downloadDao().markInterruptedAsPausedSync()
        );
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> MediaEngineInitializer.initAsync(this),
                900L
        );
    }
}
