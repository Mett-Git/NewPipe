package org.schabi.newpipe.local.subscription;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.local.feed.FeedDatabaseManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.image.CoilHelper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * La fila di canali a cui sei iscritto, come in cima alle iscrizioni di YouTube: prima quelli col
 * video più recente, con un pallino se ne hanno di nuovi non ancora visti.
 */
public final class ChannelsRow {
    private static final int FRESH_DAYS = 3;

    private ChannelsRow() {
    }

    /**
     * @param context   per leggere le iscrizioni
     * @param serviceId il servizio scelto
     * @param max       quanti canali al massimo
     * @return i canali in ordine
     */
    public static Single<List<Channel>> load(@NonNull final Context context, final int serviceId,
                                             final int max) {
        final Context app = context.getApplicationContext();
        return Single.zip(
                new SubscriptionManager(app).subscriptions().firstOrError()
                        .subscribeOn(Schedulers.io()),
                new FeedDatabaseManager(app)
                        .getStreams(FeedGroupEntity.GROUP_ALL_ID, false, false, false)
                        .defaultIfEmpty(Collections.emptyList())
                        .onErrorReturnItem(Collections.emptyList())
                        .subscribeOn(Schedulers.io()),
                (subscriptions, streams) -> {
                    final OffsetDateTime limit = OffsetDateTime.now().minusDays(FRESH_DAYS);
                    final Set<String> recent = new LinkedHashSet<>();
                    final Set<String> fresh = new HashSet<>();
                    for (final var stream : streams) {
                        final String url = stream.getStream().getUploaderUrl();
                        final OffsetDateTime date = stream.getStream().getUploadDate();
                        recent.add(url);
                        if (date != null && date.isAfter(limit)) {
                            fresh.add(url);
                        }
                    }
                    final List<Channel> channels = new ArrayList<>();
                    for (final String url : recent) {
                        for (final SubscriptionEntity subscription : subscriptions) {
                            if (subscription.getServiceId() == serviceId
                                    && url != null && url.equals(subscription.getUrl())) {
                                channels.add(new Channel(subscription, fresh.contains(url)));
                                break;
                            }
                        }
                    }
                    for (final SubscriptionEntity subscription : subscriptions) {
                        if (subscription.getServiceId() == serviceId
                                && !recent.contains(subscription.getUrl())) {
                            channels.add(new Channel(subscription, false));
                        }
                    }
                    return new ArrayList<>(channels.subList(0, Math.min(channels.size(), max)));
                });
    }

    /**
     * @param parent   dove mettere i canali
     * @param channels i canali
     * @param manager  per aprire la pagina del canale
     * @param onOpen   chiamato quando si tocca un canale
     */
    public static void show(@NonNull final LinearLayout parent,
                            @NonNull final List<Channel> channels,
                            @NonNull final FragmentManager manager,
                            @NonNull final Runnable onOpen) {
        parent.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        for (final Channel channel : channels) {
            final View cell = inflater.inflate(R.layout.vetro_menu_channel, parent, false);
            final String name = channel.subscription.getName();
            final String url = channel.subscription.getUrl();
            ((TextView) cell.findViewById(R.id.vetroRowTitle)).setText(name);
            cell.findViewById(R.id.vetroRowDot)
                    .setVisibility(channel.fresh ? View.VISIBLE : View.GONE);
            final ImageView avatar = cell.findViewById(R.id.vetroRowIcon);
            CoilHelper.INSTANCE.loadAvatar(avatar, channel.subscription.getAvatarUrl());
            cell.setOnClickListener(v -> {
                onOpen.run();
                NavigationHelper.openChannelFragment(manager,
                        channel.subscription.getServiceId(), url, name == null ? "" : name);
            });
            parent.addView(cell);
        }
    }

    public static final class Channel {
        private final SubscriptionEntity subscription;
        private final boolean fresh;

        Channel(final SubscriptionEntity subscription, final boolean fresh) {
            this.subscription = subscription;
            this.fresh = fresh;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof Channel
                    && fresh == ((Channel) other).fresh
                    && String.valueOf(subscription.getUrl())
                            .equals(String.valueOf(((Channel) other).subscription.getUrl()));
        }

        @Override
        public int hashCode() {
            return String.valueOf(subscription.getUrl()).hashCode() + (fresh ? 1 : 0);
        }
    }
}
