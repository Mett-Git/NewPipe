package org.schabi.newpipe.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;

/** Il timer di spegnimento: allo scadere mette in pausa, anche se Music non è più aperta. */
public final class MusicSleepTimer {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable pending;
    private static long endsAt = 0;

    private MusicSleepTimer() {
    }

    /**
     * @param context per raggiungere il lettore
     * @param minutes tra quanto mettere in pausa
     */
    public static void start(@NonNull final Context context, final int minutes) {
        cancel();
        final Context application = context.getApplicationContext();
        final long delay = minutes * 60000L;
        endsAt = SystemClock.elapsedRealtime() + delay;
        pending = () -> {
            endsAt = 0;
            pending = null;
            pausePlayer(application);
        };
        HANDLER.postDelayed(pending, delay);
    }

    /** Annulla il timer. */
    public static void cancel() {
        if (pending != null) {
            HANDLER.removeCallbacks(pending);
            pending = null;
        }
        endsAt = 0;
    }

    /**
     * @return i minuti che mancano, arrotondati in su; 0 se il timer è spento
     */
    public static int minutesLeft() {
        if (endsAt == 0) {
            return 0;
        }
        return (int) Math.max(1, (endsAt - SystemClock.elapsedRealtime() + 59999) / 60000);
    }

    private static void pausePlayer(final Context context) {
        final Intent intent = new Intent(context, PlayerService.class)
                .setAction(PlayerService.BIND_PLAYER_HOLDER_ACTION);
        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName name, final IBinder binder) {
                if (binder instanceof PlayerService.LocalBinder) {
                    final Player player = ((PlayerService.LocalBinder) binder)
                            .getService().getPlayer();
                    if (player != null) {
                        player.pause();
                    }
                }
                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                // niente da fare: era un collegamento di un attimo
            }
        }, Context.BIND_AUTO_CREATE);
    }
}
