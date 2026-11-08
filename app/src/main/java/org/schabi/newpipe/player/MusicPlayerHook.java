package org.schabi.newpipe.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.music.MusicStore;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Quello che NewPipe Music deve fare anche con la schermata chiusa o il telefono bloccato:
 * ricordare gli ascolti, contare i minuti e sfumare tra un brano e l'altro.
 */
final class MusicPlayerHook {
    private static final int MAX_SAVED_SONGS = 100;
    private static final int SAVE_EVERY_SECONDS = 10;
    private static final float MIN_VOLUME = 0.02f;
    private static final long FADE_TICK_MILLIS = 50;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static boolean session = false;
    private static int secondsToSave = 0;
    private static boolean fading = false;
    private static boolean fadedOut = false;
    @Nullable
    private static Player fadingPlayer;
    private static final Runnable FADE_TICK = MusicPlayerHook::fadeTick;

    private MusicPlayerHook() {
    }

    static boolean isSession() {
        return session;
    }

    static void setSession(final boolean value) {
        session = value;
    }

    private static boolean isActive(final Player player) {
        return session && player.audioPlayerSelected() && player.getPlayQueue() != null;
    }

    static StreamInfoItem toSong(@NonNull final PlayQueueItem item) {
        final StreamInfoItem song = new StreamInfoItem(item.getServiceId(), item.getUrl(),
                item.getTitle(), item.getStreamType());
        song.setUploaderName(item.getUploader());
        song.setUploaderUrl(item.getUploaderUrl());
        song.setDuration(item.getDuration());
        song.setThumbnails(item.getThumbnails());
        return song;
    }

    /**
     * @param player il lettore: è partito un brano nuovo
     */
    static void onSongStarted(@NonNull final Player player) {
        if (!isActive(player)) {
            return;
        }
        final Context context = player.getContext();
        final PlayQueue queue = player.getPlayQueue();
        final PlayQueueItem item = queue.getItem();
        if (item == null || MusicStore.isPrivate(context)) {
            return;
        }
        final StreamInfoItem song = toSong(item);
        MusicStore.addRecent(context, song);
        MusicStore.addPlay(context, song);
        // per poter riprendere da qui la prossima volta
        final List<PlayQueueItem> items = queue.getStreams();
        final int first = queue.getIndex();
        final int last = Math.min(items.size(), first + MAX_SAVED_SONGS);
        final List<StreamInfoItem> songs = new ArrayList<>();
        for (int i = first; i < last; i++) {
            songs.add(toSong(items.get(i)));
        }
        MusicStore.saveSession(context, songs, 0);
    }

    /**
     * Viene chiamato una volta al secondo.
     *
     * @param player   il lettore
     * @param progress a che punto è il brano, in millisecondi
     * @param duration quanto dura, in millisecondi
     */
    static void onProgress(@NonNull final Player player, final int progress, final int duration) {
        if (!isActive(player)) {
            return;
        }
        final Context context = player.getContext();
        if (player.isPlaying() && !MusicStore.isPrivate(context)) {
            secondsToSave++;
            if (secondsToSave >= SAVE_EVERY_SECONDS) {
                MusicStore.addListenedSeconds(context, secondsToSave);
                MusicStore.savePosition(context, progress);
                secondsToSave = 0;
            }
        }
        if (!fading && isInFadeWindow(player, progress, duration)) {
            fading = true;
            fadingPlayer = player;
            HANDLER.post(FADE_TICK);
        }
    }

    /**
     * @param player il lettore che si sta chiudendo
     */
    static void onPlayerDestroyed(@NonNull final Player player) {
        session = false;
        if (fadingPlayer == player) {
            HANDLER.removeCallbacks(FADE_TICK);
            fading = false;
            fadingPlayer = null;
        }
    }

    private static boolean hasNext(final Player player) {
        final PlayQueue queue = player.getPlayQueue();
        return queue != null && (queue.getIndex() < queue.size() - 1
                || player.getRepeatMode()
                == com.google.android.exoplayer2.Player.REPEAT_MODE_ALL);
    }

    private static boolean isInFadeWindow(final Player player, final long progress,
                                          final long duration) {
        final long fade = MusicStore.fadeSeconds(player.getContext()) * 1000L;
        if (fade == 0 || duration < fade * 3 || player.isMuted()) {
            return false;
        }
        return (fadedOut && progress < fade)
                || (duration - progress < fade && hasNext(player));
    }

    // Il volume dipende da dove si è nel brano: così funziona anche saltando avanti e indietro.
    private static void fadeTick() {
        final Player player = fadingPlayer;
        if (player == null || player.exoPlayerIsNull()) {
            fading = false;
            fadingPlayer = null;
            return;
        }
        final long progress = Math.max(0, player.getExoPlayer().getCurrentPosition());
        final long duration = player.getExoPlayer().getDuration();
        if (!isInFadeWindow(player, progress, duration)) {
            // finita la dissolvenza, o toccato il volume: si torna al normale
            if (!player.isMuted()) {
                player.getExoPlayer().setVolume(1f);
            }
            final long fade = MusicStore.fadeSeconds(player.getContext()) * 1000L;
            if (progress >= fade) {
                fadedOut = false;
            }
            fading = false;
            fadingPlayer = null;
            return;
        }
        final float fade = MusicStore.fadeSeconds(player.getContext()) * 1000f;
        final float volume;
        if (duration - progress < fade && hasNext(player)) {
            fadedOut = true;
            volume = (duration - progress) / fade;
        } else {
            volume = progress / fade;
        }
        player.getExoPlayer().setVolume(Math.max(MIN_VOLUME, Math.min(1f, volume)));
        HANDLER.postDelayed(FADE_TICK, FADE_TICK_MILLIS);
    }
}
