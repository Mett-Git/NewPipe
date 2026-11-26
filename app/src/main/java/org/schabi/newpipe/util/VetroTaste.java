package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * I tuoi gusti, solo sul telefono: i video che ti piacciono, quelli che non ti piacciono e i
 * Preferiti (stellina). Li usa l'algoritmo della Home (Preferiti contano più dei Mi piace, i
 * Non mi piace tolgono quel video e abbassano il suo canale) e il Profilo li mostra.
 * Niente viene mandato a YouTube.
 */
public final class VetroTaste {
    /** Le tre liste. */
    public enum Kind { LIKED, DISLIKED, FAVOURITE }

    private static final String PREFS = "vetro_taste_";

    private VetroTaste() {
    }

    private static SharedPreferences prefs(@NonNull final Context context,
                                           @NonNull final Kind kind) {
        return context.getApplicationContext().getSharedPreferences(
                PREFS + kind.name().toLowerCase(java.util.Locale.ROOT), Context.MODE_PRIVATE);
    }

    /**
     * @param context per leggere
     * @param kind    quale lista
     * @param url     il video
     * @return true se il video è in quella lista
     */
    public static boolean has(@NonNull final Context context, @NonNull final Kind kind,
                              @NonNull final String url) {
        return prefs(context, kind).contains(url);
    }

    /**
     * Mette o toglie il video da una lista. Mi piace e Non mi piace si escludono a vicenda.
     *
     * @param context per salvare
     * @param kind    quale lista
     * @param info    il video
     * @return true se adesso il video è nella lista
     */
    public static boolean toggle(@NonNull final Context context, @NonNull final Kind kind,
                                 @NonNull final StreamInfo info) {
        final String url = info.getUrl();
        if (has(context, kind, url)) {
            prefs(context, kind).edit().remove(url).apply();
            return false;
        }
        if (kind == Kind.LIKED) {
            prefs(context, Kind.DISLIKED).edit().remove(url).apply();
        } else if (kind == Kind.DISLIKED) {
            prefs(context, Kind.LIKED).edit().remove(url).apply();
        }
        try {
            final JSONObject json = new JSONObject()
                    .put("service", info.getServiceId())
                    .put("title", info.getName())
                    .put("uploader", info.getUploaderName())
                    .put("uploaderUrl", info.getUploaderUrl())
                    .put("thumb", ImageStrategy.imageListToDbUrl(info.getThumbnails()))
                    .put("duration", info.getDuration())
                    .put("time", System.currentTimeMillis());
            prefs(context, kind).edit().putString(url, json.toString()).apply();
        } catch (final Exception e) {
            prefs(context, kind).edit().putString(url, "{}").apply();
        }
        return true;
    }

    /**
     * @param context per leggere
     * @param kind    quale lista
     * @return i video della lista, dal più recente
     */
    @NonNull
    public static List<StreamInfoItem> list(@NonNull final Context context,
                                            @NonNull final Kind kind) {
        final List<JSONObject> entries = new ArrayList<>();
        final List<String> urls = new ArrayList<>();
        for (final Map.Entry<String, ?> e : prefs(context, kind).getAll().entrySet()) {
            try {
                entries.add(new JSONObject(String.valueOf(e.getValue())));
                urls.add(e.getKey());
            } catch (final Exception ignored) {
                // voce rovinata: si salta
            }
        }
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> Long.compare(entries.get(b).optLong("time"),
                entries.get(a).optLong("time")));
        final List<StreamInfoItem> out = new ArrayList<>();
        for (final int i : order) {
            final JSONObject j = entries.get(i);
            final StreamInfoItem item = new StreamInfoItem(j.optInt("service", 0), urls.get(i),
                    j.optString("title"), StreamType.VIDEO_STREAM);
            item.setUploaderName(j.optString("uploader"));
            item.setUploaderUrl(emptyToNull(j.optString("uploaderUrl")));
            item.setThumbnails(ImageStrategy.dbUrlToImageList(emptyToNull(j.optString("thumb"))));
            item.setDuration(j.optLong("duration", -1));
            out.add(item);
        }
        return out;
    }

    /**
     * @param context per leggere
     * @param kind    quale lista
     * @return i canali dei video di quella lista
     */
    @NonNull
    public static Set<String> channels(@NonNull final Context context, @NonNull final Kind kind) {
        final Set<String> out = new HashSet<>();
        for (final StreamInfoItem item : list(context, kind)) {
            if (item.getUploaderUrl() != null) {
                out.add(item.getUploaderUrl());
            }
        }
        return out;
    }

    @Nullable
    private static String emptyToNull(@Nullable final String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
