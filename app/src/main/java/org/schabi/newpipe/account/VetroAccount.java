package org.schabi.newpipe.account;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.subscription.SubscriptionManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Il tuo account YouTube dentro NewPipe, come in Metrolist: entri con la pagina di Google
 * (VetroLoginActivity) e l'app tiene solo i cookie della sessione, sul telefono.
 *
 * <p>Per non rischiare l'account l'app lo usa SOLO PER LEGGERE e solo quando lo chiedi tu
 * (all'accesso e col tasto Sincronizza): iscrizioni e playlist, una richiesta per volta con
 * una pausa fra l'una e l'altra. I video, le ricerche e tutto il resto continuano a passare
 * senza account, come prima. Niente like, commenti, iscrizioni o cronologia mandati a YouTube.
 *
 * <p>Uscendo si torna com'eri prima di entrare: si tolgono le iscrizioni e le playlist che
 * erano arrivate dall'account (quelle che avevi già restano) e si cancellano i cookie.
 */
public final class VetroAccount {
    private static final String PREFS = "vetro_account";
    private static final String KEY_COOKIES = "cookies";
    private static final String KEY_NAME = "name";
    private static final String KEY_PHOTO = "photo";
    private static final String KEY_HANDLE = "handle";
    private static final String KEY_ADDED_SUBSCRIPTIONS = "added_subscriptions";
    private static final String KEY_PLAYLISTS = "playlists";
    private static final String ORIGIN = "https://www.youtube.com";
    private static final String API = ORIGIN + "/youtubei/v1/";
    /** Pausa fra una richiesta all'account e la successiva, per non sembrare un robot. */
    private static final long PAUSE_MILLIS = 700;
    /** Al massimo quante pagine per elenco e quanti video per playlist. */
    private static final int MAX_PAGES = 40;
    private static final int MAX_PLAYLIST_VIDEOS = 2000;

    /** Le richieste fatte dentro {@link #withAuth} portano i cookie dell'account. */
    private static final ThreadLocal<Boolean> AUTH = new ThreadLocal<>();
    @Nullable
    private static volatile String cookies;

    private VetroAccount() {
    }

    /** Come procede la sincronizzazione (per la scritta sotto il tasto). */
    public interface Progress {
        /**
         * @param subscriptions iscrizioni trovate finora
         * @param playlists     playlist copiate finora
         */
        void update(int subscriptions, int playlists);
    }

    private static SharedPreferences prefs(@NonNull final Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * @param context da chiamare all'apertura dell'app: ricarica i cookie salvati
     */
    public static void init(@NonNull final Context context) {
        cookies = prefs(context).getString(KEY_COOKIES, null);
    }

    /**
     * @param context per leggere i dati salvati
     * @return true se sei entrato con un account
     */
    public static boolean isLoggedIn(@NonNull final Context context) {
        return prefs(context).getString(KEY_COOKIES, null) != null;
    }

    /**
     * @param context per leggere i dati salvati
     * @return il nome dell'account, o "" se non si conosce
     */
    @NonNull
    public static String name(@NonNull final Context context) {
        return prefs(context).getString(KEY_NAME, "");
    }

    /**
     * @param context per leggere i dati salvati
     * @return l'indirizzo della foto dell'account, o null
     */
    @Nullable
    public static String photo(@NonNull final Context context) {
        return prefs(context).getString(KEY_PHOTO, null);
    }

    /**
     * @param context per leggere i dati salvati
     * @return il nome col @ del canale dell'account, o ""
     */
    @NonNull
    public static String handle(@NonNull final Context context) {
        return prefs(context).getString(KEY_HANDLE, "");
    }

    /**
     * @param cookieHeader i cookie della pagina di YouTube dopo l'accesso
     * @return true se sono i cookie di un accesso vero (c'è SAPISID)
     */
    static boolean isSignedIn(@Nullable final String cookieHeader) {
        return cookieValue(cookieHeader, "SAPISID") != null
                || cookieValue(cookieHeader, "__Secure-3PAPISID") != null;
    }

    /**
     * @param context      per salvare
     * @param cookieHeader i cookie della sessione
     */
    static void saveLogin(@NonNull final Context context, @NonNull final String cookieHeader) {
        prefs(context).edit().putString(KEY_COOKIES, cookieHeader).apply();
        cookies = cookieHeader;
    }

    // ----------------------------------------------------------------------------------------
    // Richieste con l'account
    // ----------------------------------------------------------------------------------------

    /**
     * Fa le richieste di {@code task} con i cookie dell'account (solo verso youtube.com).
     *
     * @param task cosa fare
     * @param <T>  il risultato
     * @return il risultato di {@code task}
     * @throws Exception se {@code task} non riesce
     */
    public static <T> T withAuth(@NonNull final Callable<T> task) throws Exception {
        AUTH.set(Boolean.TRUE);
        try {
            return task.call();
        } finally {
            AUTH.remove();
        }
    }

    /**
     * Chiamata da DownloaderImpl per ogni richiesta: aggiunge i cookie e la firma
     * dell'account, ma solo dentro {@link #withAuth} e solo verso youtube.com.
     *
     * @param url            dove va la richiesta
     * @param existingCookie i cookie che la richiesta ha già (o null)
     * @return le intestazioni da mettere (vuoto = niente account)
     */
    @NonNull
    public static Map<String, String> authHeaders(@NonNull final String url,
                                                  @Nullable final String existingCookie) {
        final String sessionCookies = cookies;
        if (!Boolean.TRUE.equals(AUTH.get()) || sessionCookies == null
                || !(url.startsWith("https://www.youtube.com/")
                || url.startsWith("https://youtube.com/")
                || url.startsWith("https://m.youtube.com/"))) {
            return Collections.emptyMap();
        }
        final Map<String, String> headers = new HashMap<>();
        headers.put("Cookie", existingCookie == null || existingCookie.isEmpty()
                ? sessionCookies : existingCookie + "; " + sessionCookies);
        String sapisid = cookieValue(sessionCookies, "SAPISID");
        if (sapisid == null) {
            sapisid = cookieValue(sessionCookies, "__Secure-3PAPISID");
        }
        if (sapisid != null) {
            // la firma che usa il sito di YouTube: SHA-1 di "ora SAPISID origine"
            final long now = System.currentTimeMillis() / 1000;
            headers.put("Authorization", "SAPISIDHASH " + now + "_"
                    + sha1(now + " " + sapisid + " " + ORIGIN));
            headers.put("X-Origin", ORIGIN);
            headers.put("X-Goog-AuthUser", "0");
        }
        return headers;
    }

    @Nullable
    private static String cookieValue(@Nullable final String header, @NonNull final String key) {
        if (header == null) {
            return null;
        }
        for (final String part : header.split(";")) {
            final String trimmed = part.trim();
            if (trimmed.startsWith(key + "=")) {
                return trimmed.substring(key.length() + 1);
            }
        }
        return null;
    }

    private static String sha1(@NonNull final String text) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder();
            for (final byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // una richiesta all'API del sito di YouTube, con l'account
    private static JSONObject innertube(@NonNull final String endpoint,
                                        @NonNull final JSONObject request) throws Exception {
        final JSONObject client = new JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", YoutubeParsingHelper.getClientVersion())
                .put("hl", Locale.getDefault().getLanguage())
                .put("gl", Locale.getDefault().getCountry().isEmpty()
                        ? "US" : Locale.getDefault().getCountry());
        request.put("context", new JSONObject().put("client", client));
        final Map<String, List<String>> headers =
                new HashMap<>(YoutubeParsingHelper.getYouTubeHeaders());
        headers.put("Content-Type", Collections.singletonList("application/json"));
        final Response response = withAuth(() -> DownloaderImpl.getInstance().post(
                API + endpoint + "?prettyPrint=false", headers,
                request.toString().getBytes(StandardCharsets.UTF_8)));
        Thread.sleep(PAUSE_MILLIS);
        if (response.responseCode() >= 400) {
            throw new IllegalStateException("YouTube ha risposto " + response.responseCode());
        }
        return new JSONObject(response.responseBody());
    }

    // ----------------------------------------------------------------------------------------
    // Cosa si legge dall'account
    // ----------------------------------------------------------------------------------------

    /**
     * Legge nome, foto e @ dell'account e li salva.
     *
     * @param context per salvare
     * @throws Exception se YouTube non risponde
     */
    public static void loadAccountInfo(@NonNull final Context context) throws Exception {
        final JSONObject menu = innertube("account/account_menu", new JSONObject());
        final JSONObject header = findFirst(menu, "activeAccountHeaderRenderer");
        if (header == null) {
            throw new IllegalStateException("Account non riconosciuto");
        }
        final String name = text(header.optJSONObject("accountName"));
        final String handle = text(header.optJSONObject("channelHandle"));
        final String photo = lastThumbnail(header.optJSONObject("accountPhoto"));
        prefs(context).edit().putString(KEY_NAME, name).putString(KEY_HANDLE, handle)
                .putString(KEY_PHOTO, photo).apply();
    }

    /** Un canale a cui sei iscritto sull'account. */
    static final class Channel {
        final String id;
        final String name;
        @Nullable
        final String avatar;

        Channel(final String id, final String name, @Nullable final String avatar) {
            this.id = id;
            this.name = name;
            this.avatar = avatar;
        }
    }

    // tutti i canali di "Iscrizioni" dell'account (pagina "Tutte le iscrizioni")
    private static List<Channel> fetchSubscriptions(@Nullable final Progress progress)
            throws Exception {
        final Map<String, Channel> channels = new LinkedHashMap<>();
        JSONObject page = innertube("browse", new JSONObject().put("browseId", "FEchannels"));
        for (int i = 0; i < MAX_PAGES; i++) {
            final List<JSONObject> found = new ArrayList<>();
            collect(page, "channelRenderer", found);
            collect(page, "gridChannelRenderer", found);
            for (final JSONObject c : found) {
                final String id = c.optString("channelId");
                if (!id.isEmpty() && !channels.containsKey(id)) {
                    channels.put(id, new Channel(id, text(c.optJSONObject("title")),
                            lastThumbnail(c.optJSONObject("thumbnail"))));
                }
            }
            if (progress != null) {
                progress.update(channels.size(), 0);
            }
            final String token = continuation(page);
            if (token == null) {
                break;
            }
            page = innertube("browse", new JSONObject().put("continuation", token));
        }
        return new ArrayList<>(channels.values());
    }

    // le playlist dell'account: le tue, quelle salvate, più Video piaciuti e Guarda più tardi
    private static Map<String, String> fetchPlaylistIds() throws Exception {
        final Map<String, String> playlists = new LinkedHashMap<>();
        playlists.put("LL", "");
        playlists.put("WL", "");
        JSONObject page = innertube("browse",
                new JSONObject().put("browseId", "FEplaylist_aggregation"));
        for (int i = 0; i < MAX_PAGES; i++) {
            final List<JSONObject> grid = new ArrayList<>();
            collect(page, "gridPlaylistRenderer", grid);
            collect(page, "playlistRenderer", grid);
            for (final JSONObject p : grid) {
                final String id = p.optString("playlistId");
                if (!id.isEmpty()) {
                    playlists.putIfAbsent(id, text(p.optJSONObject("title")));
                }
            }
            final List<JSONObject> lockups = new ArrayList<>();
            collect(page, "lockupViewModel", lockups);
            for (final JSONObject l : lockups) {
                final String id = l.optString("contentId");
                if (!id.isEmpty() && l.optString("contentType").contains("PLAYLIST")) {
                    final JSONObject meta = findFirst(l, "lockupMetadataViewModel");
                    final JSONObject title = meta == null ? null : meta.optJSONObject("title");
                    playlists.putIfAbsent(id, title == null ? "" : title.optString("content"));
                }
            }
            final String token = continuation(page);
            if (token == null) {
                break;
            }
            page = innertube("browse", new JSONObject().put("continuation", token));
        }
        return playlists;
    }

    // ----------------------------------------------------------------------------------------
    // Sincronizza ed esci
    // ----------------------------------------------------------------------------------------

    /**
     * Copia nell'app iscrizioni e playlist dell'account. Si può ripetere: le iscrizioni nuove
     * si aggiungono, le playlist dell'account si rifanno da capo.
     *
     * @param context  per il database
     * @param progress come procede (chiamato fuori dal thread principale)
     * @return quante iscrizioni e playlist sono arrivate: {iscrizioni, playlist}
     * @throws Exception se l'account non risponde
     */
    public static int[] sync(@NonNull final Context context, @Nullable final Progress progress)
            throws Exception {
        loadAccountInfo(context);
        final SharedPreferences prefs = prefs(context);

        // iscrizioni: si aggiungono quelle che non avevi, e ci si ricorda quali sono
        final List<Channel> channels = fetchSubscriptions(progress);
        final SubscriptionManager subscriptions = new SubscriptionManager(context);
        final Set<String> existing = new HashSet<>();
        for (final SubscriptionEntity s : subscriptions.subscriptions().blockingFirst()) {
            existing.add(s.getUrl());
        }
        final Set<String> added =
                new HashSet<>(prefs.getStringSet(KEY_ADDED_SUBSCRIPTIONS, new HashSet<>()));
        final int service = ServiceList.YouTube.getServiceId();
        for (final Channel c : channels) {
            final String url = "https://www.youtube.com/channel/" + c.id;
            if (existing.contains(url)) {
                continue;
            }
            final SubscriptionEntity entity = new SubscriptionEntity();
            entity.setServiceId(service);
            entity.setUrl(url);
            entity.setName(c.name);
            entity.setAvatarUrl(c.avatar);
            subscriptions.insertSubscription(entity);
            added.add(url);
        }
        prefs.edit().putStringSet(KEY_ADDED_SUBSCRIPTIONS, added).apply();

        // playlist: quelle copiate l'altra volta si tolgono e si rifanno aggiornate
        deleteImportedPlaylists(context);
        final LocalPlaylistManager playlists =
                new LocalPlaylistManager(NewPipeDatabase.getInstance(context));
        final Set<String> created = new HashSet<>();
        int count = 0;
        for (final Map.Entry<String, String> entry : fetchPlaylistIds().entrySet()) {
            try {
                final PlaylistInfo info = withAuth(() -> PlaylistInfo.getInfo(
                        NewPipe.getService(service),
                        "https://www.youtube.com/playlist?list=" + entry.getKey()));
                final List<StreamEntity> streams = new ArrayList<>();
                for (final StreamInfoItem item : info.getRelatedItems()) {
                    streams.add(new StreamEntity(item));
                }
                Page next = info.getNextPage();
                while (Page.isValid(next) && streams.size() < MAX_PLAYLIST_VIDEOS) {
                    Thread.sleep(PAUSE_MILLIS);
                    final Page current = next;
                    final var more = withAuth(() -> PlaylistInfo.getMoreItems(
                            NewPipe.getService(service), info.getUrl(), current));
                    for (final StreamInfoItem item : more.getItems()) {
                        streams.add(new StreamEntity(item));
                    }
                    next = more.getNextPage();
                }
                Thread.sleep(PAUSE_MILLIS);
                if (streams.isEmpty()) {
                    continue;
                }
                final String name = entry.getValue().isEmpty() ? info.getName() : entry.getValue();
                playlists.createPlaylist(name, streams).blockingGet();
                final Long id = newestPlaylistId(playlists, name);
                if (id != null) {
                    created.add(String.valueOf(id));
                }
                count++;
                if (progress != null) {
                    progress.update(channels.size(), count);
                }
            } catch (final Exception e) {
                // una playlist che non si legge (privata di un altro, tolta...) non ferma le altre
            }
        }
        prefs.edit().putStringSet(KEY_PLAYLISTS, created).apply();
        return new int[]{channels.size(), count};
    }

    @Nullable
    private static Long newestPlaylistId(@NonNull final LocalPlaylistManager playlists,
                                         @NonNull final String name) {
        Long best = null;
        for (final PlaylistMetadataEntry p : playlists.getPlaylists().blockingFirst()) {
            if (name.equals(p.getOrderingName()) && (best == null || p.getUid() > best)) {
                best = p.getUid();
            }
        }
        return best;
    }

    private static void deleteImportedPlaylists(@NonNull final Context context) {
        final Set<String> ids = prefs(context).getStringSet(KEY_PLAYLISTS, new HashSet<>());
        final var dao = NewPipeDatabase.getInstance(context).playlistDAO();
        for (final String id : ids) {
            try {
                dao.deletePlaylist(Long.parseLong(id));
            } catch (final Exception ignored) {
                // già tolta a mano
            }
        }
        prefs(context).edit().remove(KEY_PLAYLISTS).apply();
    }

    /**
     * Esci dall'account e torna com'eri prima di entrare: via le iscrizioni e le playlist
     * arrivate dall'account, via i cookie. Le cose che avevi già prima restano.
     *
     * @param context per il database
     */
    public static void logout(@NonNull final Context context) {
        final SharedPreferences prefs = prefs(context);
        final SubscriptionManager subscriptions = new SubscriptionManager(context);
        final int service = ServiceList.YouTube.getServiceId();
        for (final String url : prefs.getStringSet(KEY_ADDED_SUBSCRIPTIONS, new HashSet<>())) {
            try {
                subscriptions.subscriptionTable().deleteSubscription(service, url);
            } catch (final Exception ignored) {
                // già tolta a mano
            }
        }
        deleteImportedPlaylists(context);
        prefs.edit().clear().apply();
        cookies = null;
        android.webkit.CookieManager.getInstance().removeAllCookies(null);
    }

    // ----------------------------------------------------------------------------------------
    // Lettura delle risposte: si cercano i pezzi per nome ovunque siano, così se YouTube
    // sposta le cose nella pagina si continua a trovarli
    // ----------------------------------------------------------------------------------------

    private static void collect(@Nullable final Object node, @NonNull final String key,
                                @NonNull final List<JSONObject> out) {
        if (node instanceof JSONObject) {
            final JSONObject object = (JSONObject) node;
            final JSONObject hit = object.optJSONObject(key);
            if (hit != null) {
                out.add(hit);
            }
            final JSONArray names = object.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    final String name = names.optString(i);
                    if (!name.equals(key)) {
                        collect(object.opt(name), key, out);
                    }
                }
            }
        } else if (node instanceof JSONArray) {
            final JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                collect(array.opt(i), key, out);
            }
        }
    }

    @Nullable
    private static JSONObject findFirst(@Nullable final Object node, @NonNull final String key) {
        final List<JSONObject> out = new ArrayList<>();
        collect(node, key, out);
        return out.isEmpty() ? null : out.get(0);
    }

    @Nullable
    private static String continuation(@NonNull final JSONObject page) {
        final List<JSONObject> commands = new ArrayList<>();
        collect(page, "continuationCommand", commands);
        for (final JSONObject c : commands) {
            final String token = c.optString("token");
            if (!token.isEmpty()) {
                return token;
            }
        }
        return null;
    }

    // testo di YouTube: {"simpleText": "..."} oppure {"runs": [{"text": "..."}]}
    @NonNull
    private static String text(@Nullable final JSONObject object) {
        if (object == null) {
            return "";
        }
        if (object.has("simpleText")) {
            return object.optString("simpleText");
        }
        final JSONArray runs = object.optJSONArray("runs");
        if (runs == null) {
            return object.optString("content");
        }
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < runs.length(); i++) {
            final JSONObject run = runs.optJSONObject(i);
            if (run != null) {
                text.append(run.optString("text"));
            }
        }
        return text.toString();
    }

    @Nullable
    private static String lastThumbnail(@Nullable final JSONObject object) {
        if (object == null) {
            return null;
        }
        final JSONArray thumbs = object.optJSONArray("thumbnails");
        if (thumbs == null || thumbs.length() == 0) {
            return null;
        }
        String url = thumbs.optJSONObject(thumbs.length() - 1).optString("url");
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        return url.isEmpty() ? null : url;
    }
}
