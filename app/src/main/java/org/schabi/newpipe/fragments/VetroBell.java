package org.schabi.newpipe.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.database.stream.StreamWithState;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.feed.FeedDatabaseManager;
import org.schabi.newpipe.local.feed.service.FeedEventManager;
import org.schabi.newpipe.local.feed.service.FeedLoadService;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.image.CoilHelper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * La campanella in alto accanto alla ricerca: il numero dei video nuovi dei canali a cui sei
 * iscritto (quelli usciti dopo l'ultima volta che l'hai aperta e non ancora guardati) e, al
 * tocco, l'elenco dei video usciti, dal più recente. I video arrivano dalla stessa lista di
 * "Novità", che si aggiorna da sola a ogni apertura dell'app.
 */
public final class VetroBell {
    private static final String PREFS_LAST_OPENED = "vetro_bell_last_opened";
    /** Quanti video al massimo nell'elenco. */
    private static final int MAX_ROWS = 40;

    private final FragmentActivity activity;
    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable
    private TextView badge;
    @NonNull
    private List<StreamWithState> streams = Collections.emptyList();

    VetroBell(@NonNull final FragmentActivity activity) {
        this.activity = activity;
        // a ogni aggiornamento della lista (anche quello fatto da "Novità") si ricontano
        disposables.add(FeedEventManager.INSTANCE.events()
                .filter(e -> e instanceof FeedEventManager.Event.SuccessResultEvent)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(e -> reload(), e -> { }));
        refreshFeed(); // a ogni apertura dell'app
    }

    /**
     * @param item la voce della campanella nel menu in alto
     */
    void attach(@NonNull final MenuItem item) {
        final View view = item.getActionView();
        if (view == null) {
            return;
        }
        badge = view.findViewById(R.id.vetro_bell_badge);
        view.setOnClickListener(this::open);
        reload();
    }

    /** Da chiamare quando la pagina va via. */
    void dispose() {
        disposables.clear();
        badge = null;
    }

    // ricarica i video dei canali seguiti dal database e aggiorna il numerino
    void reload() {
        disposables.add(new FeedDatabaseManager(activity)
                .getStreams(FeedGroupEntity.GROUP_ALL_ID, true, true, false)
                .defaultIfEmpty(Collections.emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    streams = list;
                    updateBadge();
                }, e -> { }));
    }

    private void updateBadge() {
        if (badge == null) {
            return;
        }
        final OffsetDateTime lastOpened = lastOpened();
        int count = 0;
        for (final StreamWithState s : streams) {
            final OffsetDateTime date = s.getStream().getUploadDate();
            if (s.getStateProgressMillis() == null && date != null && date.isAfter(lastOpened)) {
                count++;
            }
        }
        badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        badge.setText(count > 99 ? "99+" : String.valueOf(count));
    }

    // l'ultima volta che hai aperto la campanella; la prima volta conta solo l'ultimo giorno
    private OffsetDateTime lastOpened() {
        final long millis = prefs().getLong(PREFS_LAST_OPENED, 0);
        if (millis == 0) {
            return OffsetDateTime.now().minusDays(1);
        }
        return OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(activity);
    }

    private void open(@NonNull final View anchor) {
        final LayoutInflater inflater = LayoutInflater.from(activity);
        final LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        final int pad = DeviceUtils.dpToPx(6, activity);
        list.setPadding(0, pad, 0, pad);

        final TextView title = new TextView(activity);
        title.setText(R.string.vt_bell_title);
        title.setTextSize(16);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(DeviceUtils.dpToPx(14, activity), pad, 0, pad);
        list.addView(title);

        final PopupWindow popup = new PopupWindow(activity);
        final List<StreamWithState> rows = new ArrayList<>();
        for (final StreamWithState s : streams) {
            if (s.getStream().getUploadDate() != null) {
                rows.add(s);
            }
            if (rows.size() == MAX_ROWS) {
                break;
            }
        }
        if (rows.isEmpty()) {
            final TextView empty = new TextView(activity);
            empty.setText(R.string.vt_bell_empty);
            empty.setPadding(DeviceUtils.dpToPx(14, activity), pad,
                    DeviceUtils.dpToPx(14, activity), pad * 2);
            list.addView(empty);
        }
        for (final StreamWithState s : rows) {
            list.addView(row(inflater, list, s, popup));
        }

        final ScrollView scroll = new ScrollView(activity);
        scroll.addView(list);
        popup.setContentView(scroll);
        popup.setWidth(Math.min(DeviceUtils.dpToPx(360, activity),
                activity.getResources().getDisplayMetrics().widthPixels
                        - DeviceUtils.dpToPx(16, activity)));
        popup.setHeight(rows.size() > 6
                ? (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.6f)
                : ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setBackgroundDrawable(activity.getDrawable(R.drawable.vetro_popup_background));
        popup.setElevation(DeviceUtils.dpToPx(8, activity));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        // staccata dal bordo destro dello schermo
        popup.showAsDropDown(anchor, -DeviceUtils.dpToPx(8, activity), 0, Gravity.END);

        // aperta: i video di adesso non sono più "nuovi"
        prefs().edit().putLong(PREFS_LAST_OPENED, System.currentTimeMillis()).apply();
        updateBadge();
    }

    private View row(@NonNull final LayoutInflater inflater, @NonNull final ViewGroup parent,
                     @NonNull final StreamWithState state, @NonNull final PopupWindow popup) {
        final StreamEntity stream = state.getStream();
        final View row = inflater.inflate(R.layout.vetro_bell_row, parent, false);
        row.findViewById(R.id.vetro_bell_row_dot).setVisibility(
                state.getStateProgressMillis() == null ? View.VISIBLE : View.INVISIBLE);
        ((TextView) row.findViewById(R.id.vetro_bell_row_title)).setText(activity.getString(
                R.string.vt_bell_published, stream.getUploader(), stream.getTitle()));
        ((TextView) row.findViewById(R.id.vetro_bell_row_time)).setText(
                Localization.relativeTime(stream.getUploadDate()));
        final ImageView thumb = row.findViewById(R.id.vetro_bell_row_thumb);
        thumb.setBackground(new ColorDrawable(0x22FFFFFF));
        CoilHelper.INSTANCE.loadThumbnail(thumb, stream.getThumbnailUrl());
        row.setOnClickListener(v -> {
            popup.dismiss();
            NavigationHelper.openVideoDetailFragment(activity,
                    activity.getSupportFragmentManager(), stream.getServiceId(),
                    stream.getUrl(), stream.getTitle(), null, false);
        });
        return row;
    }

    /**
     * Fa aggiornare i video dei canali seguiti (come il tasto Aggiorna di "Novità"): il
     * servizio di NewPipe salta da solo i canali aggiornati da pochi minuti.
     */
    void refreshFeed() {
        disposables.add(new FeedDatabaseManager(activity)
                .oldestSubscriptionUpdate(FeedGroupEntity.GROUP_ALL_ID)
                .firstElement()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(dates -> {
                    if (dates.isEmpty()) {
                        return; // nessun canale seguito
                    }
                    try {
                        activity.startService(new Intent(activity, FeedLoadService.class));
                    } catch (final IllegalStateException e) {
                        // app non in primo piano: si aggiorna la prossima volta
                    }
                }, e -> { }));
    }
}
