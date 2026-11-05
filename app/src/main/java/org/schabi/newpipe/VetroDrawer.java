package org.schabi.newpipe;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.util.Consumer;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.navigation.NavigationView;

import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.local.feed.FeedDatabaseManager;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.image.CoilHelper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Il menu a sinistra in stile Vetro: le voci principali, "Scopri" a icone rotonde e i canali a cui
 * sei iscritto, con un pallino se hanno video nuovi. Le voci restano quelle del menu della
 * NavigationView, che qui viene solo disegnato in un altro modo. La scelta del servizio usa
 * ancora il menu normale, che ha anche la scelta del server di PeerTube.
 */
final class VetroDrawer {
    private static final int MAX_CHANNELS = 5;
    private static final int FRESH_DAYS = 3;

    private final FragmentActivity activity;
    private final NavigationView navigation;
    private final Consumer<MenuItem> onItemSelected;
    private final Runnable closeDrawer;
    private final View content;
    private final LinearLayout mainRows;
    private final LinearLayout kioskRow;
    private final LinearLayout channelRows;
    private final ImageView serviceIcon;
    private final TextView serviceName;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private List<Channel> shownChannels = Collections.emptyList();

    VetroDrawer(@NonNull final FragmentActivity activity,
                @NonNull final NavigationView navigation,
                @NonNull final Consumer<MenuItem> onItemSelected,
                @NonNull final Runnable onServiceClicked,
                @NonNull final Runnable closeDrawer) {
        this.activity = activity;
        this.navigation = navigation;
        this.onItemSelected = onItemSelected;
        this.closeDrawer = closeDrawer;
        content = navigation.findViewById(R.id.vetroDrawerContent);
        mainRows = navigation.findViewById(R.id.vetroDrawerMain);
        kioskRow = navigation.findViewById(R.id.vetroDrawerKiosks);
        channelRows = navigation.findViewById(R.id.vetroDrawerChannels);
        serviceIcon = navigation.findViewById(R.id.vetroDrawerServiceIcon);
        serviceName = navigation.findViewById(R.id.vetroDrawerServiceName);

        final int optionsGroup = R.id.menu_options_about_group;
        navigation.findViewById(R.id.vetroDrawerSettings).setOnClickListener(
                v -> select(optionsGroup, MainActivity.ITEM_ID_SETTINGS));
        navigation.findViewById(R.id.vetroDrawerDonate).setOnClickListener(
                v -> select(optionsGroup, MainActivity.ITEM_ID_DONATION));
        navigation.findViewById(R.id.vetroDrawerAbout).setOnClickListener(
                v -> select(optionsGroup, MainActivity.ITEM_ID_ABOUT));
        navigation.findViewById(R.id.vetroDrawerAllChannels).setOnClickListener(
                v -> select(R.id.menu_tabs_group, MainActivity.ITEM_ID_SUBSCRIPTIONS));
        navigation.findViewById(R.id.vetroDrawerService).setOnClickListener(
                v -> onServiceClicked.run());

        setServicesShown(false);
        refresh();
    }

    /**
     * @param shown true per mostrare il menu normale con l'elenco dei servizi
     */
    void setServicesShown(final boolean shown) {
        // il primo figlio della NavigationView è la lista del menu normale
        navigation.getChildAt(0).setVisibility(shown ? View.VISIBLE : View.GONE);
        content.setVisibility(shown ? View.GONE : View.VISIBLE);
    }

    /** Il menu sta per aprirsi: aggiorna le voci e le fa entrare una dopo l'altra. */
    void onOpening() {
        refresh();
        loadChannels();
        animateIn();
    }

    void dispose() {
        disposables.clear();
    }

    private void refresh() {
        final LayoutInflater inflater = LayoutInflater.from(navigation.getContext());
        mainRows.removeAllViews();
        kioskRow.removeAllViews();
        final Menu menu = navigation.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            if (!item.isVisible()) {
                continue;
            }
            if (item.getGroupId() == R.id.menu_tabs_group
                    && item.getItemId() != MainActivity.ITEM_ID_SUBSCRIPTIONS) {
                addMainRow(inflater, item);
            } else if (item.getGroupId() == R.id.menu_kiosks_group) {
                addKiosk(inflater, item);
            }
        }
        final int serviceId = ServiceHelper.getSelectedServiceId(activity);
        serviceIcon.setImageResource(ServiceHelper.getIcon(serviceId));
        serviceName.setText(ServiceHelper.getNameOfServiceById(serviceId));
    }

    private void addMainRow(final LayoutInflater inflater, final MenuItem item) {
        final View row = inflater.inflate(R.layout.vetro_drawer_row, mainRows, false);
        ((ImageView) row.findViewById(R.id.vetroRowIcon)).setImageDrawable(item.getIcon());
        ((TextView) row.findViewById(R.id.vetroRowTitle)).setText(item.getTitle());
        row.setOnClickListener(v -> onItemSelected.accept(item));
        mainRows.addView(row);
    }

    private void addKiosk(final LayoutInflater inflater, final MenuItem item) {
        if (kioskRow.getChildCount() > 0) {
            kioskRow.addView(new Space(activity),
                    new LinearLayout.LayoutParams(0, 0, 1f));
        }
        final ImageButton button = (ImageButton) inflater.inflate(
                R.layout.vetro_drawer_kiosk, kioskRow, false);
        button.setImageDrawable(item.getIcon());
        button.setContentDescription(item.getTitle());
        TooltipCompat.setTooltipText(button, item.getTitle());
        button.setOnClickListener(v -> onItemSelected.accept(item));
        kioskRow.addView(button);
    }

    private void select(final int group, final int id) {
        final Menu menu = navigation.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            // i numeri delle voci si ripetono tra un gruppo e l'altro
            if (item.getGroupId() == group && item.getItemId() == id) {
                onItemSelected.accept(item);
                return;
            }
        }
    }

    // canali con il video più recente in cima; il pallino se ne ha di nuovi non ancora visti
    private void loadChannels() {
        final Context context = activity.getApplicationContext();
        final int serviceId = ServiceHelper.getSelectedServiceId(context);
        disposables.clear();
        disposables.add(Single.zip(
                new SubscriptionManager(context).subscriptions().firstOrError()
                        .subscribeOn(Schedulers.io()),
                new FeedDatabaseManager(context)
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
                    return channels.subList(0, Math.min(channels.size(), MAX_CHANNELS));
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showChannels, error -> { }));
    }

    private void showChannels(@NonNull final List<Channel> channels) {
        if (channels.equals(shownChannels)) {
            return;
        }
        shownChannels = new ArrayList<>(channels);
        channelRows.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(navigation.getContext());
        for (final Channel channel : channels) {
            final View row = inflater.inflate(R.layout.vetro_drawer_channel, channelRows, false);
            final String name = channel.subscription.getName();
            final String url = channel.subscription.getUrl();
            ((TextView) row.findViewById(R.id.vetroRowTitle)).setText(name);
            row.findViewById(R.id.vetroRowDot)
                    .setVisibility(channel.fresh ? View.VISIBLE : View.GONE);
            CoilHelper.INSTANCE.loadAvatar(row.findViewById(R.id.vetroRowIcon),
                    channel.subscription.getAvatarUrl());
            row.setOnClickListener(v -> {
                NavigationHelper.openChannelFragment(activity.getSupportFragmentManager(),
                        channel.subscription.getServiceId(), url, name == null ? "" : name);
                closeDrawer.run();
            });
            channelRows.addView(row);
        }
        animateIn(channelRows, 0);
    }

    private void animateIn() {
        int delay = animateIn(mainRows, 0);
        delay = animateIn(kioskRow, delay);
        animateIn(channelRows, delay);
    }

    private int animateIn(final ViewGroup parent, final int firstDelay) {
        final float shift = -DeviceUtils.dpToPx(24, activity);
        int delay = firstDelay;
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View view = parent.getChildAt(i);
            if (view instanceof Space) {
                continue;
            }
            view.setAlpha(0f);
            view.setTranslationX(shift);
            view.animate().alpha(1f).translationX(0f)
                    .setStartDelay(delay)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
            delay += 22;
        }
        return delay;
    }

    private static final class Channel {
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
