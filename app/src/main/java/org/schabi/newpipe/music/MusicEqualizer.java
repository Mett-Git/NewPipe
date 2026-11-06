package org.schabi.newpipe.music;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** L'equalizzatore di Android collegato al suono del lettore; le regolazioni restano salvate. */
public final class MusicEqualizer {
    private static final String PREFERENCES = "vetro_music_equalizer";
    private static final String ENABLED = "enabled";
    private static final String BAND = "band_";
    private static final String BASS = "bass";

    @Nullable
    private static Equalizer equalizer;
    @Nullable
    private static BassBoost bassBoost;
    private static int session = 0;

    private MusicEqualizer() {
    }

    private static SharedPreferences preferences(final Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /**
     * @param context      per leggere le regolazioni
     * @param audioSession il suono a cui applicarlo
     */
    public static void attach(@NonNull final Context context, final int audioSession) {
        if (audioSession == session && equalizer != null) {
            return;
        }
        release();
        if (audioSession <= 0) {
            return;
        }
        session = audioSession;
        final SharedPreferences preferences = preferences(context);
        final boolean enabled = preferences.getBoolean(ENABLED, false);
        try {
            equalizer = new Equalizer(0, audioSession);
            for (short band = 0; band < equalizer.getNumberOfBands(); band++) {
                equalizer.setBandLevel(band, (short) preferences.getInt(BAND + band, 0));
            }
            equalizer.setEnabled(enabled);
            bassBoost = new BassBoost(0, audioSession);
            if (bassBoost.getStrengthSupported()) {
                bassBoost.setStrength((short) preferences.getInt(BASS, 0));
            }
            bassBoost.setEnabled(enabled);
        } catch (final RuntimeException e) {
            // non tutti i telefoni lo permettono: il pannello lo dice, la musica va lo stesso
            release();
        }
    }

    /** Stacca l'equalizzatore: il lettore è stato chiuso. */
    public static void release() {
        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }
        if (bassBoost != null) {
            bassBoost.release();
            bassBoost = null;
        }
        session = 0;
    }

    /**
     * @return l'equalizzatore in uso; null se non c'è musica o il telefono non lo permette
     */
    @Nullable
    public static Equalizer equalizer() {
        return equalizer;
    }

    /**
     * @return true se si possono regolare i bassi
     */
    public static boolean hasBassBoost() {
        return bassBoost != null && bassBoost.getStrengthSupported();
    }

    /**
     * @param context per leggere le regolazioni
     * @return true se è acceso
     */
    public static boolean isEnabled(@NonNull final Context context) {
        return preferences(context).getBoolean(ENABLED, false);
    }

    /**
     * @param context per salvare la scelta
     * @param enabled true per accenderlo
     */
    public static void setEnabled(@NonNull final Context context, final boolean enabled) {
        preferences(context).edit().putBoolean(ENABLED, enabled).apply();
        if (equalizer != null) {
            equalizer.setEnabled(enabled);
        }
        if (bassBoost != null) {
            bassBoost.setEnabled(enabled);
        }
    }

    /**
     * @param context per salvare la scelta
     * @param band    la banda
     * @param level   il livello in millibel
     */
    public static void setBand(@NonNull final Context context, final short band,
                               final short level) {
        preferences(context).edit().putInt(BAND + band, level).apply();
        if (equalizer != null) {
            equalizer.setBandLevel(band, level);
        }
    }

    /**
     * @param context per salvare la scelta
     * @param preset  la regolazione già pronta di Android
     */
    public static void usePreset(@NonNull final Context context, final short preset) {
        if (equalizer == null) {
            return;
        }
        equalizer.usePreset(preset);
        final SharedPreferences.Editor editor = preferences(context).edit();
        for (short band = 0; band < equalizer.getNumberOfBands(); band++) {
            editor.putInt(BAND + band, equalizer.getBandLevel(band));
        }
        editor.apply();
    }

    /**
     * @param context per leggere le regolazioni
     * @return la forza dei bassi, da 0 a 1000
     */
    public static int bass(@NonNull final Context context) {
        return preferences(context).getInt(BASS, 0);
    }

    /**
     * @param context  per salvare la scelta
     * @param strength la forza dei bassi, da 0 a 1000
     */
    public static void setBass(@NonNull final Context context, final int strength) {
        preferences(context).edit().putInt(BASS, strength).apply();
        if (bassBoost != null) {
            bassBoost.setStrength((short) strength);
        }
    }
}
