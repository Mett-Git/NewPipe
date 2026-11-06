package org.schabi.newpipe.music;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.ArrayList;
import java.util.List;

/** I brani ascoltati di recente e quelli che piacciono: restano solo sul telefono. */
public final class MusicStore {
    private static final String PREFERENCES = "vetro_music";
    private static final String RECENT = "recent";
    private static final String LIKED = "liked";
    private static final int MAX_RECENT = 50;

    private MusicStore() {
    }

    /**
     * @param context per leggere l'elenco
     * @return i brani ascoltati, dal più recente
     */
    public static List<StreamInfoItem> recent(@NonNull final Context context) {
        return read(context, RECENT);
    }

    /**
     * @param context per leggere l'elenco
     * @return i brani che piacciono, dall'ultimo aggiunto
     */
    public static List<StreamInfoItem> liked(@NonNull final Context context) {
        return read(context, LIKED);
    }

    /**
     * @param context per scrivere l'elenco
     * @param song    il brano appena partito
     */
    public static void addRecent(@NonNull final Context context,
                                 @NonNull final StreamInfoItem song) {
        final List<StreamInfoItem> songs = read(context, RECENT);
        remove(songs, song.getUrl());
        songs.add(0, song);
        while (songs.size() > MAX_RECENT) {
            songs.remove(songs.size() - 1);
        }
        write(context, RECENT, songs);
    }

    /**
     * @param context per leggere l'elenco
     * @param url     l'indirizzo del brano
     * @return true se il brano è tra quelli che piacciono
     */
    public static boolean isLiked(@NonNull final Context context, final String url) {
        for (final StreamInfoItem song : read(context, LIKED)) {
            if (song.getUrl().equals(url)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param context per scrivere l'elenco
     * @param song    il brano da aggiungere o togliere
     * @return true se ora piace
     */
    public static boolean toggleLiked(@NonNull final Context context,
                                      @NonNull final StreamInfoItem song) {
        final List<StreamInfoItem> songs = read(context, LIKED);
        final boolean added = !remove(songs, song.getUrl());
        if (added) {
            songs.add(0, song);
        }
        write(context, LIKED, songs);
        return added;
    }

    private static boolean remove(final List<StreamInfoItem> songs, final String url) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).getUrl().equals(url)) {
                songs.remove(i);
                return true;
            }
        }
        return false;
    }

    private static SharedPreferences preferences(final Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static List<StreamInfoItem> read(final Context context, final String key) {
        final List<StreamInfoItem> songs = new ArrayList<>();
        try {
            final JSONArray array = new JSONArray(preferences(context).getString(key, "[]"));
            for (int i = 0; i < array.length(); i++) {
                final JSONObject json = array.getJSONObject(i);
                final StreamInfoItem song = new StreamInfoItem(json.getInt("service"),
                        json.getString("url"), json.getString("title"),
                        StreamType.valueOf(json.getString("type")));
                song.setUploaderName(json.optString("artist"));
                song.setUploaderUrl(json.optString("artistUrl"));
                song.setDuration(json.optLong("duration"));
                song.setThumbnails(ImageStrategy.INSTANCE.dbUrlToImageList(
                        json.optString("cover", null)));
                songs.add(song);
            }
        } catch (final JSONException | IllegalArgumentException e) {
            return new ArrayList<>();
        }
        return songs;
    }

    private static void write(final Context context, final String key,
                              final List<StreamInfoItem> songs) {
        final JSONArray array = new JSONArray();
        try {
            for (final StreamInfoItem song : songs) {
                array.put(new JSONObject()
                        .put("service", song.getServiceId())
                        .put("url", song.getUrl())
                        .put("title", song.getName())
                        .put("type", song.getStreamType().name())
                        .put("artist", song.getUploaderName())
                        .put("artistUrl", song.getUploaderUrl())
                        .put("duration", song.getDuration())
                        .put("cover", ImageStrategy.INSTANCE.imageListToDbUrl(
                                song.getThumbnails())));
            }
        } catch (final JSONException e) {
            return;
        }
        preferences(context).edit().putString(key, array.toString()).apply();
    }
}
