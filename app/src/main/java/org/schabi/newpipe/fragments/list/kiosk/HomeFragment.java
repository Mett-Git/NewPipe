package org.schabi.newpipe.fragments.list.kiosk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.database.stream.StreamWithState;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.feed.FeedDatabaseManager;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Home con un algoritmo suo, tutto sul telefono (nessun account, niente esce dall'app oltre
 * alle normali richieste a YouTube). Mescola tre cose:
 * <ul>
 *     <li><b>dai tuoi canali</b>: i video nuovi dei canali a cui sei iscritto, non ancora
 *     guardati, dal più recente;</li>
 *     <li><b>per te</b>: video simili a quelli che guardi (i "correlati" degli ultimi video
 *     della cronologia, pesati per quanto sono recenti e quante volte li hai rivisti) e, se la
 *     cronologia delle ricerche è accesa, i risultati delle ultime cose che hai cercato. Un
 *     video consigliato da più parti sale; i canali che guardi spesso salgono un po';</li>
 *     <li><b>da scoprire</b>: video di altri canali dalle sezioni di tendenza del servizio.</li>
 * </ul>
 * Lo schema si ripete a blocchi di sei (iscrizione, per te, per te, iscrizione, da scoprire,
 * per te); quando una fonte finisce si continua con le altre. Ogni aggiornamento mescola un
 * po' i "per te" vicini, così la home cambia come quella di YouTube. I video già guardati non
 * compaiono.
 */
public class HomeFragment extends KioskFragment {
    /** Quanti video della cronologia usare come punto di partenza per i simili. */
    private static final int SEEDS = 8;
    /** Quante ricerche recenti usare. */
    private static final int SEARCHES = 4;
    /** Le fonti dello schema: S = iscrizioni, P = per te, D = da scoprire. */
    private static final String PATTERN = "SPPSDP";

    public static HomeFragment getInstance(final int serviceId) throws ExtractionException {
        final HomeFragment instance = new HomeFragment();
        final KioskList kiosks = NewPipe.getService(serviceId).getKioskList();
        final String kioskId = kiosks.getDefaultKioskId();
        instance.setInitialData(serviceId,
                kiosks.getListLinkHandlerFactoryByType(kioskId).fromId(kioskId).getUrl(), kioskId);
        instance.kioskId = kioskId;
        return instance;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kioskTranslatedName = getString(R.string.tab_home);
        name = kioskTranslatedName;
    }

    @Override
    public Single<KioskInfo> loadResult(final boolean forceReload) {
        contentCountry = Localization.getPreferredContentCountry(requireContext());
        final Context context = requireContext().getApplicationContext();
        return Single.fromCallable(() -> NewPipe.getService(serviceId))
                .flatMap(service -> loadAll(service, context));
    }

    private Single<KioskInfo> loadAll(@NonNull final StreamingService service,
                                      @NonNull final Context context)
            throws ExtractionException {
        final KioskList kiosks = service.getKioskList();
        final List<Single<List<KioskInfo>>> sections = new ArrayList<>();
        for (final String id : kiosks.getAvailableKiosks()) {
            final String kioskUrl = kiosks.getListLinkHandlerFactoryByType(id).fromId(id)
                    .getUrl();
            // una sezione che non risponde (YouTube ne ha tolte alcune) non ferma le altre
            // niente cache: qui sotto la prima sezione viene riempita con il misto di tutte, e
            // non deve restare così per chi apre quella sezione da sola (o per NewPipe Music)
            sections.add(Single.fromCallable(() -> KioskInfo.getInfo(service, kioskUrl))
                    .subscribeOn(Schedulers.io())
                    .map(Collections::singletonList)
                    .onErrorReturnItem(Collections.emptyList()));
        }

        return Single.zip(sections, results -> results)
                .zipWith(subscriptionStreams(context), (results, subscriptions) -> {
                    KioskInfo container = null;
                    final List<StreamInfoItem> discover = new ArrayList<>();
                    for (final Object result : results) {
                        for (final Object info : (List<?>) result) {
                            final KioskInfo kiosk = (KioskInfo) info;
                            if (container == null) {
                                container = kiosk;
                            }
                            discover.addAll(streamsOf(kiosk.getRelatedItems()));
                        }
                    }
                    if (container == null) {
                        throw new ExtractionException("Nessuna sezione della home risponde");
                    }
                    return new Object[]{container, subscriptions, discover};
                })
                .zipWith(forYou(service.getServiceId(), context), (parts, taste) -> {
                    final KioskInfo container = (KioskInfo) parts[0];
                    @SuppressWarnings("unchecked") final List<StreamWithState> subscriptions =
                            (List<StreamWithState>) parts[1];
                    @SuppressWarnings("unchecked") final List<StreamInfoItem> discover =
                            (List<StreamInfoItem>) parts[2];
                    // la pagina è una sola, già tutta caricata
                    container.setRelatedItems(compose(subscriptions, taste, discover));
                    container.setNextPage(null);
                    return container;
                });
    }

    /** Quello che serve dei tuoi gusti: i video "per te" e cosa hai già visto. */
    private static final class Taste {
        final List<StreamInfoItem> forYou;
        final Set<String> watched;
        final Set<String> favouriteChannels;

        Taste(final List<StreamInfoItem> forYou, final Set<String> watched,
              final Set<String> favouriteChannels) {
            this.forYou = forYou;
            this.watched = watched;
            this.favouriteChannels = favouriteChannels;
        }
    }

    /**
     * @param context per il database delle iscrizioni
     * @return tutti i video dei canali seguiti (anche quelli già guardati: servono per sapere
     *         quali canali segui), dal più recente
     */
    private static Single<List<StreamWithState>> subscriptionStreams(final Context context) {
        return new FeedDatabaseManager(context)
                .getStreams(FeedGroupEntity.GROUP_ALL_ID, true, true, false)
                .defaultIfEmpty(Collections.emptyList())
                .onErrorReturnItem(Collections.emptyList())
                .subscribeOn(Schedulers.io());
    }

    /**
     * I video "per te": simili agli ultimi guardati e, se la cronologia delle ricerche è
     * accesa, quelli delle ultime ricerche.
     *
     * @param serviceId il servizio della home
     * @param context   per la cronologia e le impostazioni
     * @return i video consigliati in ordine di punteggio, con cosa hai già visto
     */
    private static Single<Taste> forYou(final int serviceId, final Context context) {
        final HistoryRecordManager history = new HistoryRecordManager(context);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean searchHistory = prefs.getBoolean(
                context.getString(R.string.enable_search_history_key), true);

        final Single<List<StreamStatisticsEntry>> stats = history.getStreamStatistics()
                .firstOrError()
                .map(list -> (List<StreamStatisticsEntry>) list)
                .onErrorReturnItem(Collections.emptyList());
        final Single<List<String>> searches = searchHistory
                ? history.getRelatedSearches("", 0, SEARCHES).firstOrError()
                        .map(list -> (List<String>) list)
                        .onErrorReturnItem(Collections.emptyList())
                : Single.just(Collections.emptyList());

        return stats.zipWith(searches, (statistics, queries) -> new Object[]{statistics, queries})
                .flatMap(both -> {
                    @SuppressWarnings("unchecked") final List<StreamStatisticsEntry> statistics =
                            (List<StreamStatisticsEntry>) both[0];
                    @SuppressWarnings("unchecked") final List<String> queries =
                            (List<String>) both[1];
                    return score(serviceId, statistics, queries);
                })
                .subscribeOn(Schedulers.io());
    }

    private static Single<Taste> score(final int serviceId,
                                       final List<StreamStatisticsEntry> statistics,
                                       final List<String> queries) {
        final Set<String> watched = new HashSet<>();
        final Map<String, Integer> channelViews = new HashMap<>();
        for (final StreamStatisticsEntry s : statistics) {
            watched.add(s.getStreamEntity().getUrl());
            final String channel = s.getStreamEntity().getUploaderUrl();
            if (channel != null) {
                channelViews.merge(channel, (int) Math.max(1, s.getWatchCount()), Integer::sum);
            }
        }
        // i canali che guardi spesso: quelli con almeno 3 visioni
        final Set<String> favourite = new HashSet<>();
        for (final Map.Entry<String, Integer> e : channelViews.entrySet()) {
            if (e.getValue() >= 3) {
                favourite.add(e.getKey());
            }
        }

        // punti di partenza: gli ultimi video guardati (più recenti e più rivisti = più peso)
        final List<StreamStatisticsEntry> recent = new ArrayList<>(statistics);
        recent.sort((a, b) -> b.getLatestAccessDate().compareTo(a.getLatestAccessDate()));
        final List<Single<WeightedList>> sources = new ArrayList<>();
        for (int i = 0; i < Math.min(SEEDS, recent.size()); i++) {
            final StreamEntity seed = recent.get(i).getStreamEntity();
            if (seed.getServiceId() != serviceId) {
                continue;
            }
            final double weight = (1 + Math.log(Math.max(1, recent.get(i).getWatchCount())))
                    / (1 + i * 0.35);
            sources.add(ExtractorHelper.getStreamInfo(serviceId, seed.getUrl(), false)
                    .map(info -> new WeightedList(streamsOf(relatedOf(info)), weight))
                    .onErrorReturnItem(WeightedList.EMPTY));
        }
        for (int i = 0; i < queries.size(); i++) {
            final double weight = 0.8 / (1 + i * 0.5);
            sources.add(ExtractorHelper.searchFor(serviceId, queries.get(i),
                            Collections.emptyList(), "")
                    .map(info -> new WeightedList(streamsOf(itemsOf(info)), weight))
                    .onErrorReturnItem(WeightedList.EMPTY));
        }
        if (sources.isEmpty()) {
            return Single.just(new Taste(Collections.emptyList(), watched, favourite));
        }

        final List<Single<WeightedList>> parallel = new ArrayList<>();
        for (final Single<WeightedList> source : sources) {
            parallel.add(source.subscribeOn(Schedulers.io()));
        }
        return Single.zip(parallel, lists -> {
            final Map<String, Double> points = new HashMap<>();
            final Map<String, StreamInfoItem> items = new HashMap<>();
            for (final Object o : lists) {
                final WeightedList list = (WeightedList) o;
                for (int pos = 0; pos < list.items.size(); pos++) {
                    final StreamInfoItem item = list.items.get(pos);
                    if (watched.contains(item.getUrl())) {
                        continue;
                    }
                    // più in alto nella lista = più simile
                    points.merge(item.getUrl(), list.weight / (1 + pos * 0.15), Double::sum);
                    items.putIfAbsent(item.getUrl(), item);
                }
            }
            final List<StreamInfoItem> ranked = new ArrayList<>(items.values());
            for (final StreamInfoItem item : ranked) {
                if (favourite.contains(item.getUploaderUrl())) {
                    points.computeIfPresent(item.getUrl(), (k, v) -> v * 1.3);
                }
            }
            ranked.sort((a, b) -> Double.compare(points.get(b.getUrl()), points.get(a.getUrl())));
            shuffleNearby(ranked);
            return new Taste(ranked, watched, favourite);
        });
    }

    /** Una lista di video consigliati e quanto conta chi la consiglia. */
    private static final class WeightedList {
        static final WeightedList EMPTY = new WeightedList(Collections.emptyList(), 0);
        final List<StreamInfoItem> items;
        final double weight;

        WeightedList(final List<StreamInfoItem> items, final double weight) {
            this.items = items;
            this.weight = weight;
        }
    }

    // a ogni aggiornamento i video con punteggio vicino si scambiano un po' di posto
    private static void shuffleNearby(final List<StreamInfoItem> list) {
        final Random random = new Random();
        for (int i = 0; i + 1 < list.size(); i++) {
            if (random.nextInt(3) == 0) {
                Collections.swap(list, i, Math.min(list.size() - 1, i + 1 + random.nextInt(3)));
            }
        }
    }

    /**
     * @param subscriptions video dei canali seguiti
     * @param taste         i "per te" e cosa hai già visto
     * @param discover      i video delle sezioni di tendenza
     * @return il misto della home, senza doppioni né video già guardati
     */
    private static List<StreamInfoItem> compose(final List<StreamWithState> subscriptions,
                                          final Taste taste,
                                          final List<StreamInfoItem> discover) {
        final Set<String> subscribedChannels = new HashSet<>();
        final List<StreamInfoItem> fromSubscriptions = new ArrayList<>();
        final OffsetDateTime monthAgo = OffsetDateTime.now().minusDays(30);
        for (final StreamWithState s : subscriptions) {
            final StreamEntity stream = s.getStream();
            if (stream.getUploaderUrl() != null) {
                subscribedChannels.add(stream.getUploaderUrl());
            }
            // solo quelli non ancora aperti e dell'ultimo mese
            if (s.getStateProgressMillis() == null && !taste.watched.contains(stream.getUrl())
                    && (stream.getUploadDate() == null
                            || stream.getUploadDate().isAfter(monthAgo))) {
                fromSubscriptions.add(stream.toStreamInfoItem());
            }
        }
        // "da scoprire" = canali che non segui
        final List<StreamInfoItem> others = new ArrayList<>();
        for (final StreamInfoItem item : discover) {
            if (!subscribedChannels.contains(item.getUploaderUrl())) {
                others.add(item);
            }
        }

        final List<List<StreamInfoItem>> sources = List.of(fromSubscriptions, taste.forYou,
                others);
        final int[] next = new int[sources.size()];
        final Set<String> seen = new HashSet<>(taste.watched);
        final List<StreamInfoItem> mixed = new ArrayList<>();
        for (int turn = 0;; turn++) {
            final int wanted = "SPD".indexOf(PATTERN.charAt(turn % PATTERN.length()));
            StreamInfoItem pick = null;
            // la fonte del turno, e se è finita la prossima che ha ancora video
            for (int k = 0; k < sources.size() && pick == null; k++) {
                final int source = (wanted + k) % sources.size();
                final List<StreamInfoItem> list = sources.get(source);
                while (next[source] < list.size() && pick == null) {
                    final StreamInfoItem candidate = list.get(next[source]++);
                    if (seen.add(candidate.getUrl())) {
                        pick = candidate;
                    }
                }
            }
            if (pick == null) {
                return mixed;
            }
            mixed.add(pick);
        }
    }

    private static List<InfoItem> relatedOf(final StreamInfo info) {
        return info.getRelatedItems() == null ? Collections.emptyList()
                : new ArrayList<>(info.getRelatedItems());
    }

    private static List<InfoItem> itemsOf(final SearchInfo info) {
        return info.getRelatedItems() == null ? Collections.emptyList()
                : new ArrayList<>(info.getRelatedItems());
    }

    // solo i video (nelle liste ci sono anche canali e playlist)
    private static List<StreamInfoItem> streamsOf(final List<? extends InfoItem> items) {
        final List<StreamInfoItem> streams = new ArrayList<>();
        for (final InfoItem item : items) {
            if (item instanceof StreamInfoItem) {
                streams.add((StreamInfoItem) item);
            }
        }
        return streams;
    }
}
