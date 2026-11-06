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
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    private ValueAnimator settleAnimator;
    private float progress = 0f;
    @Nullable
    private View handle;

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
        grip.setOnTouchListener(new DragListener(false));
    }

    boolean isOpen() {
        return open;
    }

    /**
     * @param view la linietta in alto: la tendina scende seguendo il dito, come quella di Android
     */
    void attachOpenHandle(@NonNull final View view) {
        handle = view;
        view.setOnClickListener(v -> open());
        view.setOnTouchListener(new DragListener(true));
    }

    /** Apre con un'animazione: tasto del menu o tocco sulla linietta. */
    void open() {
        if (!open) {
            prepare();
            hideItems();
        }
        // l'altezza della tendina si conosce solo dopo che è stata misurata
        panel.post(() -> {
            settle(1f);
            showItems();
        });
    }

    void close() {
        if (open) {
            settle(0f);
        }
    }

    /** Chiude subito, senza animazione: quando si torna all'app da un'altra schermata. */
    void closeNow() {
        if (settleAnimator != null) {
            settleAnimator.cancel();
        }
        finishClosing();
    }

    void dispose() {
        disposables.clear();
    }

    // la tendina pronta sopra lo schermo; la linietta in alto sparisce, ora c'è quella che
    // scende con la tendina
    private void prepare() {
        open = true;
        refresh();
        loadChannels();
        if (settleAnimator != null) {
            settleAnimator.cancel();
        }
        root.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        panel.setTranslationY(-activity.getResources().getDisplayMetrics().heightPixels);
        progress = 0f;
        if (handle != null) {
            handle.setAlpha(0f);
        }
    }

    // 0 = tendina chiusa, 1 = tutta aperta (oltre 1 durante il piccolo rimbalzo finale)
    private void setProgress(final float value) {
        progress = value;
        final float visible = Math.max(0f, Math.min(1f, value));
        panel.setTranslationY(-panel.getHeight() * (1f - value));
        scrim.setAlpha(visible);
        setBlur(BLUR_DP * visible);
    }

    private void settle(final float target) {
        if (settleAnimator != null) {
            settleAnimator.cancel();
        }
        final boolean opening = target > 0f;
        settleAnimator = ValueAnimator.ofFloat(progress, target);
        settleAnimator.setDuration(opening ? 340 : 230);
        settleAnimator.setInterpolator(opening ? new OvershootInterpolator(0.8f)
                : new DecelerateInterpolator(1.4f));
        settleAnimator.addUpdateListener(a -> setProgress((float) a.getAnimatedValue()));
        if (!opening) {
            settleAnimator.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled = false;

                @Override
                public void onAnimationCancel(final Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(final Animator animation) {
                    if (!cancelled) {
                        finishClosing();
                    }
                }
            });
        }
        settleAnimator.start();
    }

    private void finishClosing() {
        open = false;
        progress = 0f;
        disposables.clear();
        setBlur(0f);
        root.setVisibility(View.GONE);
        serviceRows.setVisibility(View.GONE);
        if (handle != null) {
            handle.setAlpha(1f);
        }
    }

    // la pagina dietro si sfoca da Android 12; prima la copre solo il velo scuro
    private void setBlur(final float dp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        final float px = dp * activity.getResources().getDisplayMetrics().density;
        blurTarget.setRenderEffect(px < 1f ? null
                : RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP));
    }

    // La tendina segue il dito sulla linietta, come quella di Android: lasciandola si apre o si
    // richiude da sola, a seconda di dove è arrivata e della velocità del gesto.
    private final class DragListener implements View.OnTouchListener {
        private final boolean opening;
        private final int slop = DeviceUtils.dpToPx(6, activity);
        private float startY;
        private float startProgress;
        private boolean moved;
        private VelocityTracker velocity;

        DragListener(final boolean opening) {
            this.opening = opening;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(final View v, final MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getRawY();
                    moved = false;
                    velocity = VelocityTracker.obtain();
                    velocity.addMovement(event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    velocity.addMovement(event);
                    drag(event.getRawY() - startY);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    velocity.addMovement(event);
                    velocity.computeCurrentVelocity(1000);
                    release(v, velocity.getYVelocity(), event.getActionMasked());
                    velocity.recycle();
                    break;
                default:
                    break;
            }
            return true;
        }

        private void drag(final float distance) {
            if (!moved) {
                if (Math.abs(distance) < slop) {
                    return;
                }
                moved = true;
                if (settleAnimator != null) {
                    settleAnimator.cancel();
                }
                if (opening && !open) {
                    prepare();
                }
                startProgress = progress;
            }
            if (panel.getHeight() > 0) {
                setProgress(Math.max(0f, Math.min(1f,
                        startProgress + distance / panel.getHeight())));
            }
        }

        private void release(final View v, final float speed, final int action) {
            if (!moved) {
                if (action == MotionEvent.ACTION_UP) {
                    v.performClick();
                }
                return;
            }
            final boolean fast = Math.abs(speed) > DeviceUtils.dpToPx(500, activity);
            // per aprirla basta tirarla un po', per chiuderla pure: non serve arrivare in fondo
            final boolean keepOpen = fast ? speed > 0 : progress > (opening ? 0.35f : 0.75f);
            settle(keepOpen ? 1f : 0f);
        }
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
