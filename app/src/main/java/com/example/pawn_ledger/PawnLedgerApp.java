package com.example.pawn_ledger;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class PawnLedgerApp extends Application
        implements Application.ActivityLifecycleCallbacks {

    private int startedActivities = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        startedActivities++;
    }

    @Override
    public void onActivityStopped(Activity activity) {
        startedActivities--;

        if (startedActivities == 0 &&
                !activity.isChangingConfigurations()) {
            OwnerSession.lock();
        }
    }

    @Override
    public void onActivityCreated(
            Activity activity,
            Bundle savedInstanceState
    ) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(
            Activity activity,
            Bundle outState
    ) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}