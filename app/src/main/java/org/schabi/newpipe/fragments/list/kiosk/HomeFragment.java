package org.schabi.newpipe.fragments.list.kiosk;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.feed.FeedDatabaseManager;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Home: in un'unica pagina le dirette, le sezioni di tendenza del servizio (musica,
 * giochi, film, podcast...) e i video nuovi dei canali a cui sei iscritto, mescolati
 * un po' per uno, come la home di YouTube.
 */
public class HomeFragment extends KioskFragment {

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
                .flatMap(service -> loadAll(service, context, forceReload));
    }

    private Single<KioskInfo> loadAll(@NonNull final StreamingService service,
                                      @NonNull final Context context,
                                      final boolean forceReload) throws ExtractionException {
        final KioskList kiosks = service.getKioskList();
        final List<Single<List<KioskInfo>>> sections = new ArrayList<>();
        for (final String id : kiosks.getAvailableKiosks()) {
            final String kioskUrl = kiosks.getListLinkHandlerFactoryByType(id).fromId(id)
                    .getUrl();
            // una sezione che non risponde (YouTube ne ha tolte alcune) non ferma le altre
            sections.add(ExtractorHelper.getKioskInfo(serviceId, kioskUrl, forceReload)
                    .subscribeOn(Schedulers.io())
                    .map(Collections::singletonList)
                    .onErrorReturnItem(Collections.emptyList()));
        }
        final Single<List<StreamInfoItem>> subscriptions = subscriptionVideos(context);

        return Single.zip(sections, results -> results)
                .zipWith(subscriptions, (results, fromSubscriptions) -> {
                    final List<List<StreamInfoItem>> lists = new ArrayList<>();
                    KioskInfo container = null;
                    for (final Object result : results) {
                        for (final Object info : (List<?>) result) {
                            final KioskInfo kiosk = (KioskInfo) info;
                            if (container == null) {
                                container = kiosk;
                            }
                            lists.add(kiosk.getRelatedItems());
                        }
                    }
                    if (container == null) {
                        throw new ExtractionException("Nessuna sezione della home risponde");
                    }
                    lists.add(fromSubscriptions);
                    // la pagina è una sola, già tutta caricata
                    container.setRelatedItems(mix(lists));
                    container.setNextPage(null);
                    return container;
                });
    }

    /**
     * @param context per leggere il database delle iscrizioni
     * @return i video nuovi dei canali seguiti (quelli di "Novità"), senza quelli già visti
     */
    private static Single<List<StreamInfoItem>> subscriptionVideos(final Context context) {
        return new FeedDatabaseManager(context)
                .getStreams(FeedGroupEntity.GROUP_ALL_ID, false, true, false)
                .map(streams -> {
                    final List<StreamInfoItem> items = new ArrayList<>();
                    for (final var stream : streams) {
                        items.add(stream.getStream().toStreamInfoItem());
                    }
                    return items;
                })
                .defaultIfEmpty(Collections.emptyList())
                .onErrorReturnItem(Collections.emptyList())
                .subscribeOn(Schedulers.io());
    }

    /**
     * @param lists i video di ogni sezione, nel loro ordine
     * @return uno per sezione a turno, senza doppioni
     */
    private static List<StreamInfoItem> mix(final List<List<StreamInfoItem>> lists) {
        final Set<String> seen = new HashSet<>();
        final List<StreamInfoItem> mixed = new ArrayList<>();
        for (int i = 0;; i++) {
            boolean any = false;
            for (final List<StreamInfoItem> list : lists) {
                if (i < list.size()) {
                    any = true;
                    if (seen.add(list.get(i).getUrl())) {
                        mixed.add(list.get(i));
                    }
                }
            }
            if (!any) {
                return mixed;
            }
        }
    }
}
