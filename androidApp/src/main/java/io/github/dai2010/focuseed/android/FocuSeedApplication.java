package io.github.dai2010.focuseed.android;

import android.app.Application;

public final class FocuSeedApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AndroidDiagnostics.install(this);
        AndroidDiagnostics.logStartup(this);
    }
}
