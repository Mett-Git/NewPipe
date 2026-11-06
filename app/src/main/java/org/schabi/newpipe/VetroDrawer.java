package org.schabi.newpipe;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.fragment.app.FragmentActivity;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.local.subscription.ChannelsRow;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Il menu: una tendina che scende dall'alto, trasparente, con la pagina sfocata dietro. In alto le
 * tue cose scritte grandi, sotto "Scopri" e i canali a cui sei iscritto, con un pallino se hanno
 * video nuovi. Le voci restano quelle del menu della NavigationView (nascosta), così tutto quello
 * che fanno resta in MainActivity.
 */
final class VetroDrawer {
    private static final int MAX_CHANNELS = 12;
    private static final float BLUR_DP = 26f;

    private final FragmentActivity activity;
    private final Menu items;
    private final View blurTarget;
    private final Consumer<MenuItem> onItemSelected;
    private final View root;
    private final View scrim;
    private final View panel;
    private final LinearLayout mainRows;
    private final LinearLayout discoverRows;
    private final LinearLayout channelCells;
    private final View channelsLabel;
    private final View channelsScroll;
    private final LinearLayout serviceRows;
    private final ImageView serviceIcon;
    private final TextView serviceName;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private List<ChannelsRow.Channel> shownChannels = Collections.emptyList();
    private boolean open = false;
    private ValueAnimator blurAnimator;

    VetroDrawer(@NonNull final FragmentActivity activity,
                @NonNull final Menu items,
                @NonNull final View blurTarget,
                @NonNull final View root,
                @NonNull final Consumer<MenuItem> onItemSelected) {
        this.activity = activity;
        this.items = items;
        this.blurTarget = blurTarget;
        this.root = root;
        this.onItemSelected = onItemSelected;
        scrim = root.findViewById(R.id.vetroMenuScrim);
        panel = root.findViewById(R.id.vetroMenuPanel);
        mainRows = root.findViewById(R.id.vetroMenuMain);
        discoverRows = root.findViewById(R.id.vetroMenuDiscover);
        channelCells = root.findViewById(R.id.vetroMenuChannels);
        channelsLabel = root.findViewById(R.id.vetroMenuChannelsLabel);
        channelsScroll = root.findViewById(R.id.vetroMenuChannelsScroll);
        serviceRows = root.findViewById(R.id.vetroMenuServices);
        serviceIcon = root.findViewById(R.id.vetroMenuServiceIcon);
        serviceName = root.findViewById(R.id.vetroMenuServiceName);

        final int optionsGroup = R.id.menu_options_about_group;
        root.findViewById(R.id.vetroMenuSettings).setOnClickListener(
                v -> select(optionsGroup, MainActivity.ITEM_ID_SETTINGS));
        root.findViewById(R.id.vetroMenuDonate).setOnClickListener(
                v -> select(optionsGroup, MainActivity.ITEM_ID_DONATION));
        root.findViewById(R.id.vetroMenuAbout).setOnClickListener(
                v -> select(optionsGroup, MainActivity.ITEM_ID_ABOUT));
        root.findViewById(R.id.vetroMenuClose).setOnClickListener(v -> close());
        root.findViewById(R.id.vetroMenuService).setOnClickListener(v -> toggleServices());
        scrim.setOnClickListener(v -> close());
        final View grip = root.findViewById(R.id.vetroMenuGrip);
        grip.setOnClickListener(v -> close());
        grip.setOnTouchListener(new SwipeListener(-1, this::close));
    }

    boolean isOpen() {
        return open;
    }

    /**
     * @param handle la linietta in alto: swipe verso il basso o tocco per aprire il menu
     */
    void attachOpenHandle(@NonNull final View handle) {
        handle.setOnClickListener(v -> open());
        handle.setOnTouchListener(new SwipeListener(1, this::open));
    }

    // tocco o swipe nella direzione giusta su una linietta: 1 = in basso, -1 = in alto
    private final class SwipeListener implements View.OnTouchListener {
        private final int direction;
        private final Runnable action;
        private float startY;
        private boolean done;

        SwipeListener(final int direction, final Runnable action) {
            this.direction = direction;
            this.action = action;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(final View v, final MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getRawY();
                    done = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!done && (event.getRawY() - startY) * direction
                            > DeviceUtils.dpToPx(16, activity)) {
                        done = true;
                        action.run();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (!done) {
                        v.performClick();
                    }
                    break;
                default:
                    break;
            }
            return true;
        }
    }

    void open() {
        if (open) {
            return;
        }
        open = true;
        refresh();
        loadChannels();
        root.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(260).setListener(null).start();
        animateBlur(BLUR_DP);
        hideItems();
        // la tendina si conosce la sua altezza solo dopo essere stata misurata
        panel.setVisibility(View.INVISIBLE);
        panel.post(() -> {
            panel.setVisibility(View.VISIBLE);
            panel.setTranslationY(-panel.getHeight());
            panel.animate().translationY(0f).setDuration(430)
                    .setInterpolator(new OvershootInterpolator(0.7f)).setListener(null).start();
            showItems();
        });
    }

    void close() {
        if (!open) {
            return;
        }
        open = false;
        disposables.clear();
        scrim.animate().alpha(0f).setDuration(220).start();
        animateBlur(0f);
        panel.animate().translationY(-panel.getHeight()).setDuration(230)
                .setInterpolator(new AccelerateInterpolator(1.4f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(final Animator animation) {
                        if (!open) {
                            root.setVisibility(View.GONE);
                            serviceRows.setVisibility(View.GONE);
                        }
                    }
                }).start();
    }

    /** Chiude subito, senza animazione: quando si torna all'app da un'altra schermata. */
    void closeNow() {
        open = false;
        disposables.clear();
        panel.animate().cancel();
        scrim.animate().cancel();
        if (blurAnimator != null) {
            blurAnimator.cancel();
        }
        setBlur(0f);
        root.setVisibility(View.GONE);
        serviceRows.setVisibility(View.GONE);
    }

    void dispose() {
        disposables.clear();
    }

    // la pagina dietro si sfoca da Android 12; prima la copre solo il velo scuro
    private void animateBlur(final float toDp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        if (blurAnimator != null) {
            blurAnimator.cancel();
        }
        blurAnimator = ValueAnimator.ofFloat(toDp == 0f ? BLUR_DP : 0f, toDp);
        blurAnimator.setDuration(toDp == 0f ? 220 : 320);
        blurAnimator.addUpdateListener(a -> setBlur((float) a.getAnimatedValue()));
        blurAnimator.start();
    }

    private void setBlur(final float dp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        final float px = dp * activity.getResources().getDisplayMetrics().density;
        blurTarget.setRenderEffect(px < 1f ? null
                : RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP));
    }

    private void refresh() {
        final LayoutInflater inflater = LayoutInflater.from(root.getContext());
        mainRows.removeAllViews();
        discoverRows.removeAllViews();
        LinearLayout pair = null;
        for (int i = 0; i < items.size(); i++) {
            final MenuItem item = items.getItem(i);
            if (!item.isVisible()) {
                continue;
            }
            if (item.getGroupId() == R.id.menu_tabs_group
                    && item.getItemId() != MainActivity.ITEM_ID_FEED) {
                addBigRow(inflater, item);
            } else if (item.getGroupId() == R.id.menu_kiosks_group) {
                if (pair == null) {
                    pair = new LinearLayout(activity);
                    discoverRows.addView(pair);
                    addSmallCell(inflater, pair, item);
                } else {
                    addSmallCell(inflater, pair, item);
                    pair = null;
                }
            }
        }
        final int serviceId = ServiceHelper.getSelectedServiceId(activity);
        serviceIcon.setImageResource(ServiceHelper.getIcon(serviceId));
        serviceName.setText(ServiceHelper.getNameOfServiceById(serviceId));
    }

    private void addBigRow(final LayoutInflater inflater, final MenuItem item) {
        final View row = inflater.inflate(R.layout.vetro_menu_row, mainRows, false);
        ((ImageView) row.findViewById(R.id.vetroRowIcon)).setImageDrawable(item.getIcon());
        ((TextView) row.findViewById(R.id.vetroRowTitle)).setText(item.getTitle());
        row.setOnClickListener(v -> choose(item));
        mainRows.addView(row);
    }

    private void addSmallCell(final LayoutInflater inflater, final ViewGroup parent,
                              final MenuItem item) {
        final View cell = inflater.inflate(R.layout.vetro_menu_small, parent, false);
        ((ImageView) cell.findViewById(R.id.vetroRowIcon)).setImageDrawable(item.getIcon());
        // nella sezione "Scopri" basta la parola: "Musica", non "Musica in tendenza"
        ((TextView) cell.findViewById(R.id.vetroRowTitle))
                .setText(String.valueOf(item.getTitle()).replace(" in tendenza", ""));
        cell.setContentDescription(item.getTitle());
        cell.setOnClickListener(v -> choose(item));
        parent.addView(cell, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void choose(final MenuItem item) {
        close();
        onItemSelected.accept(item);
    }

    private void select(final int group, final int id) {
        for (int i = 0; i < items.size(); i++) {
            final MenuItem item = items.getItem(i);
            // i numeri delle voci si ripetono tra un gruppo e l'altro
            if (item.getGroupId() == group && item.getItemId() == id) {
                choose(item);
                return;
            }
        }
    }

    private void toggleServices() {
        if (serviceRows.getVisibility() == View.VISIBLE) {
            serviceRows.setVisibility(View.GONE);
            return;
        }
        serviceRows.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(root.getContext());
        final int selected = ServiceHelper.getSelectedServiceId(activity);
        for (final StreamingService service : NewPipe.getServices()) {
            final int id = service.getServiceId();
            final View row = inflater.inflate(R.layout.vetro_menu_small, serviceRows, false);
            ((ImageView) row.findViewById(R.id.vetroRowIcon))
                    .setImageResource(ServiceHelper.getIcon(id));
            final TextView title = row.findViewById(R.id.vetroRowTitle);
            title.setText(service.getServiceInfo().getName());
            if (id == selected) {
                title.setTextColor(ContextCompat.getColor(activity, R.color.vetro_pill_hot_text));
            }
            row.setOnClickListener(v -> {
                if (id != selected) {
                    ServiceHelper.setSelectedServiceId(activity, id);
                    ActivityCompat.recreate(activity);
                } else {
                    serviceRows.setVisibility(View.GONE);
                }
            });
            serviceRows.addView(row);
        }
        serviceRows.setVisibility(View.VISIBLE);
    }

    private void loadChannels() {
        disposables.clear();
        disposables.add(ChannelsRow.load(activity,
                        ServiceHelper.getSelectedServiceId(activity), MAX_CHANNELS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showChannels, error -> { }));
    }

    private void showChannels(@NonNull final List<ChannelsRow.Channel> channels) {
        channelsLabel.setVisibility(channels.isEmpty() ? View.GONE : View.VISIBLE);
        channelsScroll.setVisibility(channels.isEmpty() ? View.GONE : View.VISIBLE);
        if (!channels.equals(shownChannels)) {
            shownChannels = new ArrayList<>(channels);
            ChannelsRow.show(channelCells, channels, activity.getSupportFragmentManager(),
                    this::close);
        }
    }

    private List<View> animatedItems() {
        final List<View> views = new ArrayList<>();
        for (final ViewGroup group : new ViewGroup[]{mainRows, discoverRows, channelCells}) {
            for (int i = 0; i < group.getChildCount(); i++) {
                views.add(group.getChildAt(i));
            }
        }
        return views;
    }

    private void hideItems() {
        final float shift = -DeviceUtils.dpToPx(16, activity);
        for (final View view : animatedItems()) {
            view.animate().cancel();
            view.setAlpha(0f);
            view.setTranslationY(shift);
        }
    }

    private void showItems() {
        int delay = 110;
        for (final View view : animatedItems()) {
            view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(280)
                    .setInterpolator(new OvershootInterpolator(1.2f)).start();
            delay += 24;
        }
    }
}
