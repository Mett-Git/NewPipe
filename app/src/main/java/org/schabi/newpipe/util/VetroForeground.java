package org.schabi.newpipe.util;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Sa quando NewPipe torna in primo piano dopo essere stata chiusa o lasciata (tasto home,
 * un'altra app): serve per aggiornare da sole le schede ogni volta che riapri l'app.
 */
public final class VetroForeground implements Application.ActivityLifecycleCallbacks {
    @Nullable
    private static VetroForeground instance;
    private int started = 0;
    private long leftAt = 0;
    private long awayMillis = 0;

    private VetroForeground() {
    }

    /**
     * @param application da chiamare una volta, all'apertura della pagina principale
     */
    public static void install(@NonNull final Application application) {
        if (instance == null) {
            instance = new VetroForeground();
            application.registerActivityLifecycleCallbacks(instance);
        }
    }

    /**
     * @param minAwayMillis quanto deve essere stata fuori l'app perché conti
     * @return true una volta sola dopo ogni ritorno nell'app
     */
    public static boolean consumeReturn(final long minAwayMillis) {
        if (instance == null || instance.awayMillis == 0) {
            return false;
        }
        final boolean result = instance.awayMillis >= minAwayMillis;
        instance.awayMillis = 0;
        return result;
    }

    @Override
    public void onActivityStarted(@NonNull final Activity activity) {
        if (started++ == 0 && leftAt > 0) {
            awayMillis = SystemClock.elapsedRealtime() - leftAt;
        }
    }

    @Override
    public void onActivityStopped(@NonNull final Activity activity) {
        if (--started == 0 && !activity.isChangingConfigurations()) {
            leftAt = SystemClock.elapsedRealtime();
        }
    }

    @Override
    public void onActivityCreated(@NonNull final Activity activity,
                                  @Nullable final Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(@NonNull final Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull final Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull final Activity activity,
                                            @NonNull final Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull final Activity activity) {
    }
}
