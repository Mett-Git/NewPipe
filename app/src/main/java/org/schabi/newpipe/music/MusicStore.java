package org.schabi.newpipe.music;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** I brani ascoltati di recente e quelli che piacciono: restano solo sul telefono. */
public final class MusicStore {
    /** Nelle impostazioni normali: la legge anche la cronologia di NewPipe. */
    public static final String PRIVATE_SESSION_KEY = "vetro_music_private_session";
    private static final String PREFERENCES = "vetro_music";
    private static final String RECENT = "recent";
    private static final String LIKED = "liked";
    private static final String SEARCHES = "searches";
    private static final String SESSION_SONGS = "session_songs";
    private static final String SESSION_INDEX = "session_index";
    private static final String SESSION_POSITION = "session_position";
    private static final String PLAYS = "plays";
    private static final String SECONDS = "seconds_";
    private static final String FOLLOWED = "followed";
    private static final String FADE = "fade_seconds";
    private static final int MAX_RECENT = 50;
    private static final int MAX_SEARCHES = 8;

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

    /**
     * @param context per leggere l'elenco
     * @return le ultime ricerche, dalla più recente
     */
    public static List<String> searches(@NonNull final Context context) {
        final List<String> searches = new ArrayList<>();
        try {
            final JSONArray array = new JSONArray(preferences(context).getString(SEARCHES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                searches.add(array.getString(i));
            }
        } catch (final JSONException e) {
            return new ArrayList<>();
        }
        return searches;
    }

    /**
     * @param context per scrivere l'elenco
     * @param search  la ricerca appena fatta
     */
    public static void addSearch(@NonNull final Context context, @NonNull final String search) {
        final List<String> searches = searches(context);
        searches.remove(search);
        searches.add(0, search);
        writeSearches(context, searches.subList(0, Math.min(MAX_SEARCHES, searches.size())));
    }

    /**
     * @param context per scrivere l'elenco
     * @param search  la ricerca da togliere
     */
    public static void removeSearch(@NonNull final Context context,
                                    @NonNull final String search) {
        final List<String> searches = searches(context);
        searches.remove(search);
        writeSearches(context, searches);
    }

    private static void writeSearches(final Context context, final List<String> searches) {
        preferences(context).edit().putString(SEARCHES, new JSONArray(searches).toString())
                .apply();
    }

    /**
     * @param context per scrivere
     * @param songs   la coda in riproduzione
     * @param index   il brano a cui è arrivata
     */
    public static void saveSession(@NonNull final Context context,
                                   @NonNull final List<StreamInfoItem> songs, final int index) {
        write(context, SESSION_SONGS, songs);
        preferences(context).edit().putInt(SESSION_INDEX, index)
                .putLong(SESSION_POSITION, 0).apply();
    }

    /**
     * @param context  per scrivere
     * @param position a che punto è il brano, in millisecondi
     */
    public static void savePosition(@NonNull final Context context, final long position) {
        preferences(context).edit().putLong(SESSION_POSITION, position).apply();
    }

    /**
     * @param context per leggere
     * @return quello che si stava ascoltando, null se non c'è niente da riprendere
     */
    @Nullable
    public static Session session(@NonNull final Context context) {
        final List<StreamInfoItem> songs = read(context, SESSION_SONGS);
        final int index = preferences(context).getInt(SESSION_INDEX, 0);
        return index < songs.size() ? new Session(songs, index,
                preferences(context).getLong(SESSION_POSITION, 0)) : null;
    }

    /** Quello che si stava ascoltando. */
    public static final class Session {
        private final List<StreamInfoItem> songs;
        private final int index;
        private final long position;

        Session(final List<StreamInfoItem> songs, final int index, final long position) {
            this.songs = songs;
            this.index = index;
            this.position = position;
        }

        /**
         * @return la coda
         */
        public List<StreamInfoItem> getSongs() {
            return songs;
        }

        /**
         * @return il brano a cui era arrivata
         */
        public int getIndex() {
            return index;
        }

        /**
         * @return a che punto era il brano, in millisecondi
         */
        public long getPosition() {
            return position;
        }
    }

    private static JSONObject toJson(final StreamInfoItem song) throws JSONException {
        return new JSONObject()
                .put("service", song.getServiceId())
                .put("url", song.getUrl())
                .put("title", song.getName())
                .put("type", song.getStreamType().name())
                .put("artist", song.getUploaderName())
                .put("artistUrl", song.getUploaderUrl())
                .put("duration", song.getDuration())
                .put("cover", ImageStrategy.INSTANCE.imageListToDbUrl(song.getThumbnails()));
    }

    private static StreamInfoItem fromJson(final JSONObject json) throws JSONException {
        final StreamInfoItem song = new StreamInfoItem(json.getInt("service"),
                json.getString("url"), json.getString("title"),
                StreamType.valueOf(json.getString("type")));
        song.setUploaderName(json.optString("artist"));
        song.setUploaderUrl(json.optString("artistUrl"));
        song.setDuration(json.optLong("duration"));
        song.setThumbnails(ImageStrategy.INSTANCE.dbUrlToImageList(
                json.optString("cover", null)));
        return song;
    }

    //region Quanto si ascolta

    /**
     * @param context per scrivere
     * @param song    il brano appena partito
     */
    public static void addPlay(@NonNull final Context context,
                               @NonNull final StreamInfoItem song) {
        try {
            final JSONObject plays = new JSONObject(preferences(context).getString(PLAYS, "{}"));
            final JSONObject old = plays.optJSONObject(song.getUrl());
            plays.put(song.getUrl(), new JSONObject()
                    .put("count", old == null ? 1 : old.optInt("count") + 1)
                    .put("song", toJson(song)));
            preferences(context).edit().putString(PLAYS, plays.toString()).apply();
        } catch (final JSONException e) {
            // un ascolto non contato non è grave
        }
    }

    private static List<Play> plays(final Context context) {
        final List<Play> plays = new ArrayList<>();
        try {
            final JSONObject json = new JSONObject(preferences(context).getString(PLAYS, "{}"));
            final Iterator<String> urls = json.keys();
            while (urls.hasNext()) {
                final JSONObject play = json.getJSONObject(urls.next());
                plays.add(new Play(fromJson(play.getJSONObject("song")), play.optInt("count")));
            }
        } catch (final JSONException | IllegalArgumentException e) {
            return new ArrayList<>();
        }
        Collections.sort(plays, (x, y) -> Integer.compare(y.count, x.count));
        return plays;
    }

    /**
     * @param context per leggere
     * @param limit   quanti al massimo
     * @return i brani più ascoltati
     */
    public static List<StreamInfoItem> topSongs(@NonNull final Context context, final int limit) {
        final List<StreamInfoItem> songs = new ArrayList<>();
        for (final Play play : plays(context)) {
            if (songs.size() < limit) {
                songs.add(play.song);
            }
        }
        return songs;
    }

    /**
     * @param context per leggere
     * @param limit   quanti al massimo
     * @return gli artisti più ascoltati, ognuno col suo brano più ascoltato
     */
    public static List<TopArtist> topArtists(@NonNull final Context context, final int limit) {
        final Map<String, TopArtist> artists = new LinkedHashMap<>();
        for (final Play play : plays(context)) {
            final String name = artistName(play.song.getUploaderName());
            if (name.isEmpty()) {
                continue;
            }
            final TopArtist artist = artists.get(name);
            if (artist == null) {
                artists.put(name, new TopArtist(name, play.song, play.count));
            } else {
                artist.plays += play.count;
            }
        }
        final List<TopArtist> sorted = new ArrayList<>(artists.values());
        Collections.sort(sorted, (x, y) -> Integer.compare(y.plays, x.plays));
        return new ArrayList<>(sorted.subList(0, Math.min(limit, sorted.size())));
    }

    // "Queen", "Queen Official", "Queen - Topic" e "QueenVEVO" sono lo stesso artista
    private static String artistName(@Nullable final String uploader) {
        return uploader == null ? "" : uploader
                .replaceAll("(?i)\\s*-\\s*topic$", "")
                .replaceAll("(?i)\\s+official$", "")
                .replaceAll("VEVO$", "")
                .trim();
    }

    private static String monthKey() {
        final Calendar now = Calendar.getInstance();
        return SECONDS + now.get(Calendar.YEAR) + "_" + now.get(Calendar.MONTH);
    }

    /**
     * @param context per scrivere
     * @param seconds quanti secondi in più sono stati ascoltati
     */
    public static void addListenedSeconds(@NonNull final Context context, final int seconds) {
        final String key = monthKey();
        preferences(context).edit()
                .putInt(key, preferences(context).getInt(key, 0) + seconds).apply();
    }

    /**
     * @param context per leggere
     * @return i minuti ascoltati in questo mese
     */
    public static int minutesThisMonth(@NonNull final Context context) {
        return preferences(context).getInt(monthKey(), 0) / 60;
    }

    /** Un artista tra i più ascoltati. */
    public static final class TopArtist {
        private final String name;
        private final StreamInfoItem song;
        private int plays;

        TopArtist(final String name, final StreamInfoItem song, final int plays) {
            this.name = name;
            this.song = song;
            this.plays = plays;
        }

        /**
         * @return il nome
         */
        public String getName() {
            return name;
        }

        /**
         * @return il suo brano più ascoltato
         */
        public StreamInfoItem getSong() {
            return song;
        }

        /**
         * @return quante volte sono partiti i suoi brani
         */
        public int getPlays() {
            return plays;
        }
    }

    private static final class Play {
        private final StreamInfoItem song;
        private final int count;

        Play(final StreamInfoItem song, final int count) {
            this.song = song;
            this.count = count;
        }
    }
    //endregion

    //region Artisti seguiti

    /**
     * @param context per leggere
     * @return gli artisti seguiti
     */
    public static List<FollowedArtist> followed(@NonNull final Context context) {
        final List<FollowedArtist> artists = new ArrayList<>();
        try {
            final JSONArray array = new JSONArray(preferences(context).getString(FOLLOWED, "[]"));
            for (int i = 0; i < array.length(); i++) {
                final JSONObject json = array.getJSONObject(i);
                artists.add(new FollowedArtist(json.getString("name"),
                        json.optString("cover", null)));
            }
        } catch (final JSONException e) {
            return new ArrayList<>();
        }
        return artists;
    }

    /**
     * @param context per leggere
     * @param name    il nome dell'artista
     * @return true se lo si segue
     */
    public static boolean isFollowed(@NonNull final Context context, final String name) {
        for (final FollowedArtist artist : followed(context)) {
            if (artist.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param context per scrivere
     * @param name    il nome dell'artista
     * @param cover   la sua foto
     * @return true se ora lo si segue
     */
    public static boolean toggleFollow(@NonNull final Context context, @NonNull final String name,
                                       @Nullable final String cover) {
        final JSONArray array = new JSONArray();
        boolean found = false;
        try {
            for (final FollowedArtist artist : followed(context)) {
                if (artist.getName().equals(name)) {
                    found = true;
                } else {
                    array.put(new JSONObject().put("name", artist.getName())
                            .put("cover", artist.getCover()));
                }
            }
            if (!found) {
                array.put(new JSONObject().put("name", name).put("cover", cover));
            }
        } catch (final JSONException e) {
            return found;
        }
        preferences(context).edit().putString(FOLLOWED, array.toString()).apply();
        return !found;
    }

    /** Un artista seguito. */
    public static final class FollowedArtist {
        private final String name;
        @Nullable
        private final String cover;

        FollowedArtist(final String name, @Nullable final String cover) {
            this.name = name;
            this.cover = cover;
        }

        /**
         * @return il nome
         */
        public String getName() {
            return name;
        }

        /**
         * @return la foto, se si conosce
         */
        @Nullable
        public String getCover() {
            return cover;
        }
    }
    //endregion

    //region Scelte

    /**
     * @param context per leggere
     * @return true se la sessione privata è accesa
     */
    public static boolean isPrivate(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PRIVATE_SESSION_KEY, false);
    }

    /**
     * @param context per scrivere
     * @param value   true per non salvare ascolti né cronologia
     */
    public static void setPrivate(@NonNull final Context context, final boolean value) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(PRIVATE_SESSION_KEY, value).apply();
    }

    /**
     * @param context per leggere
     * @return quanti secondi dura la dissolvenza, 0 se è spenta
     */
    public static int fadeSeconds(@NonNull final Context context) {
        return preferences(context).getInt(FADE, 0);
    }

    /**
     * @param context per scrivere
     * @param seconds quanti secondi deve durare la dissolvenza, 0 per spegnerla
     */
    public static void setFadeSeconds(@NonNull final Context context, final int seconds) {
        preferences(context).edit().putInt(FADE, seconds).apply();
    }
    //endregion

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
                final StreamInfoItem song = fromJson(array.getJSONObject(i));
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
                array.put(toJson(song));
            }
        } catch (final JSONException e) {
            return;
        }
        preferences(context).edit().putString(key, array.toString()).apply();
    }
}
