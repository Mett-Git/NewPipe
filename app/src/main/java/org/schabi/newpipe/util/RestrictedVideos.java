package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.ContentAvailability;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;

/**
 * Il segnino sulle anteprime dei video che non si possono guardare gratis: solo per gli
 * abbonati al canale o a pagamento. Il servizio lo dice negli elenchi, ma i video salvati per la
 * scheda Novità non lo conservano: per quelli ci si segna qui l'indirizzo quando si aggiorna.
 */
public final class RestrictedVideos {
    private static final String PREFERENCES = "vetro_restricted_videos";

    private RestrictedVideos() {
    }

    /**
     * Si segna i video riservati tra quelli appena scaricati per la scheda Novità.
     *
     * @param context per leggere e scrivere l'elenco
     * @param streams i video del canale appena aggiornato
     */
    public static void remember(@NonNull final Context context,
                                @NonNull final List<StreamInfoItem> streams) {
        SharedPreferences.Editor editor = null;
        final SharedPreferences preferences = preferences(context);
        for (final StreamInfoItem stream : streams) {
            if (isRestricted(stream.getContentAvailability())
                    && !preferences.contains(stream.getUrl())) {
                if (editor == null) {
                    editor = preferences.edit();
                }
                editor.putString(stream.getUrl(), stream.getContentAvailability().name());
            }
        }
        if (editor != null) {
            editor.apply();
        }
    }

    /**
     * Mostra o nasconde il segnino di un video.
     *
     * @param badge        il segnino nell'anteprima
     * @param url          l'indirizzo del video, per cercarlo tra quelli segnati
     * @param availability quello che dice il servizio, se lo sa
     */
    public static void bind(@Nullable final TextView badge, @Nullable final String url,
                            @Nullable final ContentAvailability availability) {
        if (badge == null) {
            return;
        }
        final SharedPreferences preferences = preferences(badge.getContext());
        final String known;
        if (isRestricted(availability)) {
            known = availability.name();
            // visto in una lista qualsiasi: così si vede anche nelle altre (Novità, cronologia...)
            if (url != null && !known.equals(preferences.getString(url, null))) {
                preferences.edit().putString(url, known).apply();
            }
        } else {
            known = preferences.getString(url, null);
        }
        if (ContentAvailability.MEMBERSHIP.name().equals(known)) {
            badge.setText(R.string.video_members_only);
            badge.setVisibility(View.VISIBLE);
        } else if (ContentAvailability.PAID.name().equals(known)) {
            badge.setText(R.string.video_paid);
            badge.setVisibility(View.VISIBLE);
        } else {
            badge.setVisibility(View.GONE);
        }
    }

    /**
     * @param context per leggere l'elenco
     * @param url     l'indirizzo del video
     * @return cosa si sa di un video: serve alla scheda Novità per ridisegnarlo quando cambia
     */
    @Nullable
    public static String known(@NonNull final Context context, @Nullable final String url) {
        return preferences(context).getString(url, null);
    }

    private static boolean isRestricted(@Nullable final ContentAvailability availability) {
        return availability == ContentAvailability.MEMBERSHIP
                || availability == ContentAvailability.PAID;
    }

    private static SharedPreferences preferences(@NonNull final Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
