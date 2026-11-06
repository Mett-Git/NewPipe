package org.schabi.newpipe.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.exoplayer2.PlaybackParameters;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ActivityMusicBinding;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.music.MusicExplorePage;
import org.schabi.newpipe.music.MusicHomePage;
import org.schabi.newpipe.music.MusicHost;
import org.schabi.newpipe.music.MusicLibraryPage;
import org.schabi.newpipe.music.MusicPage;
import org.schabi.newpipe.music.MusicStore;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * NewPipe Music: l'intro, la barra in basso, il mini lettore e il lettore a tutto schermo.
 * Il suono è quello del lettore in sottofondo di NewPipe; qui cambia tutta l'interfaccia.
 */
public final class MusicActivity extends AppCompatActivity
        implements MusicHost, PlayerEventListener {
    private static final String EXTRA_OPEN_PLAYER = "music_open_player";
    private static final String STATE_TAB = "music_tab";
    private static final long INTRO_MILLIS = 1500;
    private static final long POLL_MILLIS = 500;
    private static final int MAX_QUEUE_ROWS = 30;
    private static final int TAB_HOME = 0;
    private static final int TAB_EXPLORE = 1;
    private static final int TAB_LIBRARY = 2;

    // la musica è partita da qui: l'avviso del lettore deve riportare qui, non alla coda classica
    private static boolean session = false;

    private ActivityMusicBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::pollPlayer;
    private PlayerService service;
    private boolean bound = false;
    @Nullable
    private Player player;
    private int topInset = 0;
    private int bottomInset = 0;
    private int tab = TAB_HOME;
    @Nullable
    private String currentUrl;
    private String queueKey = "";
    private boolean playing = false;
    private boolean seeking = false;
    private boolean playerOpen = false;
    private float playerProgress = 0f;
    @Nullable
    private ValueAnimator playerAnimator;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            if (binder instanceof PlayerService.LocalBinder) {
                service = ((PlayerService.LocalBinder) binder).getService();
                pollPlayer();
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            service = null;
        }
    };

    /**
     * @param context per preparare l'indirizzo
     * @return dove deve portare l'avviso del lettore, null se la musica non è partita da qui
     */
    @Nullable
    public static Intent notificationIntent(@NonNull final Context context) {
        return session
                ? new Intent(context, MusicActivity.class).putExtra(EXTRA_OPEN_PLAYER, true)
                : null;
    }

    //region Ciclo di vita
    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMusicBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topInset = bars.top;
            bottomInset = bars.bottom;
            applyInsets();
            return insets;
        });
        setupNavigation();
        setupMiniPlayer();
        setupPlayer();
        if (savedInstanceState == null) {
            selectTab(TAB_HOME);
            if (getIntent().getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
                binding.musicIntro.setVisibility(View.GONE);
                binding.getRoot().post(() -> settlePlayer(1f));
            } else {
                playIntro();
            }
        } else {
            binding.musicIntro.setVisibility(View.GONE);
            tab = savedInstanceState.getInt(STATE_TAB, TAB_HOME);
            markTab();
        }
        bound = bindService(new Intent(this, PlayerService.class)
                .setAction(PlayerService.BIND_PLAYER_HOLDER_ACTION), connection,
                BIND_AUTO_CREATE);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            binding.getRoot().post(() -> settlePlayer(1f));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(poll);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(poll);
        if (player != null) {
            player.removeActivityListener(this);
            player = null;
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, tab);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        final FragmentManager fragments = getSupportFragmentManager();
        if (playerOpen) {
            settlePlayer(0f);
        } else if (fragments.getBackStackEntryCount() > 0) {
            fragments.popBackStack();
        } else if (tab != TAB_HOME) {
            selectTab(TAB_HOME);
        } else {
            super.onBackPressed();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    private void applyInsets() {
        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) binding.musicBottom.getLayoutParams();
        params.bottomMargin = bottomInset + DeviceUtils.dpToPx(8, this);
        binding.musicBottom.setLayoutParams(params);
        binding.musicPlayerContent.setPadding(
                binding.musicPlayerContent.getPaddingLeft(), topInset,
                binding.musicPlayerContent.getPaddingRight(),
                bottomInset + DeviceUtils.dpToPx(16, this));
        for (final Fragment page : getSupportFragmentManager().getFragments()) {
            if (page instanceof MusicPage) {
                ((MusicPage) page).applyInsets();
            }
        }
    }
    //endregion

    //region Intro
    // il logo compare, sotto arriva MUSIC, poi si scopre la Home
    private void playIntro() {
        final float distance = DeviceUtils.dpToPx(16, this);
        binding.musicIntroLogo.setAlpha(0f);
        binding.musicIntroLogo.setScaleX(0.8f);
        binding.musicIntroLogo.setScaleY(0.8f);
        binding.musicIntroLogo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        binding.musicIntroLabel.setAlpha(0f);
        binding.musicIntroLabel.setTranslationY(distance);
        binding.musicIntroLabel.animate().alpha(1f).translationY(0f).setStartDelay(500)
                .setDuration(460).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        handler.postDelayed(() -> binding.musicIntro.animate().alpha(0f).setDuration(380)
                .withEndAction(() -> binding.musicIntro.setVisibility(View.GONE)).start(),
                INTRO_MILLIS);
    }
    //endregion

    //region Pagine
    private void setupNavigation() {
        binding.musicNavHome.setOnClickListener(v -> selectTab(TAB_HOME));
        binding.musicNavExplore.setOnClickListener(v -> selectTab(TAB_EXPLORE));
        binding.musicNavLibrary.setOnClickListener(v -> selectTab(TAB_LIBRARY));
    }

    private void selectTab(final int newTab) {
        tab = newTab;
        markTab();
        final FragmentManager fragments = getSupportFragmentManager();
        fragments.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        final Fragment page;
        if (newTab == TAB_EXPLORE) {
            page = new MusicExplorePage();
        } else if (newTab == TAB_LIBRARY) {
            page = new MusicLibraryPage();
        } else {
            page = new MusicHomePage();
        }
        fragments.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.musicPages, page)
                .commit();
    }

    private void markTab() {
        binding.musicNavHome.setSelected(tab == TAB_HOME);
        binding.musicNavExplore.setSelected(tab == TAB_EXPLORE);
        binding.musicNavLibrary.setSelected(tab == TAB_LIBRARY);
    }

    @Nullable
    private Fragment currentPage() {
        return getSupportFragmentManager().findFragmentById(R.id.musicPages);
    }

    private void refreshRows() {
        final Fragment page = currentPage();
        if (page instanceof MusicPage && page.getView() != null) {
            ((MusicPage) page).refreshNowPlaying();
        }
    }

    @Override
    public void openPage(@NonNull final Fragment page) {
        // quella di sotto resta com'è: tornando indietro si ritrova risultati e posizione
        final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out);
        final Fragment current = currentPage();
        if (current != null) {
            transaction.hide(current);
        }
        transaction.add(R.id.musicPages, page).addToBackStack(null).commit();
    }

    @Override
    public void goBack() {
        getOnBackPressedDispatcher().onBackPressed();
    }

    @Override
    public int topInset() {
        return topInset;
    }

    @Override
    public int bottomSpace() {
        // barra in basso + mini lettore: il resto della pagina ci passa dietro
        return bottomInset + DeviceUtils.dpToPx(148, this);
    }

    @Nullable
    @Override
    public String nowPlayingUrl() {
        return currentUrl;
    }

    @Override
    public boolean isPlayingNow() {
        return playing;
    }
    //endregion

    //region Riproduzione
    @Override
    public void playSongs(@NonNull final List<StreamInfoItem> songs, final int index,
                          final boolean shuffle) {
        final List<StreamInfoItem> order = new ArrayList<>(songs);
        if (shuffle) {
            Collections.shuffle(order);
        }
        final PlayQueue queue = new SinglePlayQueue(order, shuffle ? 0 : index);
        session = true;
        final Intent intent = NavigationHelper.getPlayerIntent(this, PlayerService.class, queue,
                PlayerIntentType.AllOthers)
                .putExtra(Player.PLAYER_TYPE, PlayerType.AUDIO)
                .putExtra(Player.RESUME_PLAYBACK, false);
        ContextCompat.startForegroundService(this, intent);
        pollPlayer();
    }

    @Override
    public void enqueue(@NonNull final StreamInfoItem song) {
        if (player == null) {
            playSongs(Collections.singletonList(song), 0, false);
        } else {
            NavigationHelper.enqueueOnPlayer(this, new SinglePlayQueue(song), PlayerType.AUDIO);
        }
    }

    // Il servizio ha un solo ascoltatore per sapere quando parte il lettore, ed è già di
    // PlayerHolder: qui si controlla ogni tanto, finché questa schermata è visibile.
    private void pollPlayer() {
        handler.removeCallbacks(poll);
        final Player current = service == null ? null : service.getPlayer();
        if (current != player) {
            if (player != null) {
                player.removeActivityListener(this);
            }
            player = current;
            if (player != null) {
                player.setActivityListener(this);
                showCurrentItem();
            }
        }
        if (player == null || player.getPlayQueue() == null) {
            clearNowPlaying();
        }
        handler.postDelayed(poll, POLL_MILLIS);
    }

    private void clearNowPlaying() {
        if (currentUrl != null || binding.musicMini.getVisibility() == View.VISIBLE) {
            currentUrl = null;
            playing = false;
            queueKey = "";
            binding.musicMini.setVisibility(View.GONE);
            if (playerOpen) {
                settlePlayer(0f);
            }
            refreshRows();
        }
    }

    private void showCurrentItem() {
        final PlayQueue queue = player == null ? null : player.getPlayQueue();
        final PlayQueueItem item = queue == null ? null : queue.getItem();
        if (item == null) {
            return;
        }
        binding.musicMini.setVisibility(View.VISIBLE);
        if (!item.getUrl().equals(currentUrl)) {
            currentUrl = item.getUrl();
            binding.musicMiniTitle.setText(item.getTitle());
            binding.musicMiniArtist.setText(item.getUploader());
            binding.musicPlayerTitle.setText(item.getTitle());
            binding.musicPlayerArtist.setText(item.getUploader());
            MusicPage.loadCover(binding.musicMiniCover, item.getThumbnails());
            MusicPage.loadCover(binding.musicPlayerCover, item.getThumbnails());
            final StreamInfoItem song = toSong(item);
            binding.musicPlayerLike.setImageResource(MusicStore.isLiked(this, song.getUrl())
                    ? R.drawable.ic_favorite : R.drawable.ic_vm_favorite_border);
            if (session && player.audioPlayerSelected()) {
                MusicStore.addRecent(this, song);
            }
            refreshRows();
        }
        final String key = queue.getIndex() + ":" + queue.size() + ":" + queue.isShuffled();
        if (!key.equals(queueKey)) {
            queueKey = key;
            renderQueue(queue);
        }
    }

    private static StreamInfoItem toSong(final PlayQueueItem item) {
        final StreamInfoItem song = new StreamInfoItem(item.getServiceId(), item.getUrl(),
                item.getTitle(), item.getStreamType());
        song.setUploaderName(item.getUploader());
        song.setUploaderUrl(item.getUploaderUrl());
        song.setDuration(item.getDuration());
        song.setThumbnails(item.getThumbnails());
        return song;
    }

    private void renderQueue(final PlayQueue queue) {
        final ViewGroup rows = binding.musicPlayerQueue;
        rows.removeAllViews();
        final LayoutInflater inflater = getLayoutInflater();
        final int last = Math.min(queue.size(), queue.getIndex() + 1 + MAX_QUEUE_ROWS);
        for (int i = queue.getIndex() + 1; i < last; i++) {
            final PlayQueueItem item = queue.getItem(i);
            if (item == null) {
                continue;
            }
            final View row = inflater.inflate(R.layout.music_row, rows, false);
            ((TextView) row.findViewById(R.id.musicRowTitle)).setText(item.getTitle());
            ((TextView) row.findViewById(R.id.musicRowSubtitle)).setText(item.getUploader());
            row.findViewById(R.id.musicRowMore).setVisibility(View.GONE);
            MusicPage.loadCover(row.findViewById(R.id.musicRowCover), item.getThumbnails());
            final int index = i;
            row.setOnClickListener(v -> queue.setIndex(index));
            rows.addView(row);
        }
        binding.musicPlayerQueueLabel.setVisibility(
                rows.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }
    //endregion

    //region Eventi del lettore
    @Override
    public void onQueueUpdate(final PlayQueue queue) {
        showCurrentItem();
    }

    @Override
    public void onPlaybackUpdate(final int state, final int repeatMode, final boolean shuffled,
                                 final PlaybackParameters parameters) {
        showCurrentItem();
        final boolean nowPlaying = state == Player.STATE_PLAYING;
        final boolean loading = state == Player.STATE_BLOCKED
                || state == Player.STATE_BUFFERING || state == Player.STATE_PAUSED_SEEK;
        if (nowPlaying != playing) {
            playing = nowPlaying;
            refreshRows();
        }
        final int icon = state == Player.STATE_COMPLETED ? R.drawable.ic_replay
                : playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow;
        binding.musicPlayerPlay.setImageResource(icon);
        binding.musicMiniPlay.setImageResource(icon);
        binding.musicPlayerLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        final int accent = ContextCompat.getColor(this, R.color.vm_accent);
        final int idle = ContextCompat.getColor(this, R.color.vm_text);
        final boolean repeatOn =
                repeatMode != com.google.android.exoplayer2.Player.REPEAT_MODE_OFF;
        binding.musicPlayerRepeat.setImageResource(
                repeatMode == com.google.android.exoplayer2.Player.REPEAT_MODE_ONE
                        ? R.drawable.ic_vm_repeat_one : R.drawable.ic_repeat);
        tint(binding.musicPlayerRepeat, repeatOn ? accent : idle);
        tint(binding.musicPlayerShuffle, shuffled ? accent : idle);
    }

    private static void tint(final ImageView view, final int color) {
        view.setImageTintList(ColorStateList.valueOf(color));
    }

    @Override
    public void onProgressUpdate(final int currentProgress, final int duration,
                                 final int bufferPercent) {
        binding.musicMiniProgress.setMax(duration);
        binding.musicMiniProgress.setProgress(currentProgress);
        binding.musicPlayerSeek.setMax(duration);
        binding.musicPlayerSeek.setSecondaryProgress((int) (duration * (bufferPercent / 100f)));
        binding.musicPlayerEnd.setText(Localization.getDurationString(duration / 1000));
        if (!seeking) {
            binding.musicPlayerSeek.setProgress(currentProgress);
            binding.musicPlayerNow.setText(Localization.getDurationString(currentProgress / 1000));
        }
    }

    @Override
    public void onMetadataUpdate(final StreamInfo info, final PlayQueue queue) {
        showCurrentItem();
    }

    @Override
    public void onServiceStopped() {
        session = false;
        if (player != null) {
            player.removeActivityListener(this);
            player = null;
        }
        clearNowPlaying();
    }
    //endregion

    //region Mini lettore e lettore
    @SuppressLint("ClickableViewAccessibility")
    private void setupMiniPlayer() {
        binding.musicMini.setOnClickListener(v -> settlePlayer(1f));
        binding.musicMini.setOnTouchListener(new DragListener());
        binding.musicMiniPlay.setOnClickListener(v -> togglePlay());
        binding.musicMiniNext.setOnClickListener(v -> {
            if (player != null) {
                player.playNext();
            }
        });
    }

    private void togglePlay() {
        if (player != null) {
            player.playPause();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupPlayer() {
        binding.musicPlayer.setVisibility(View.INVISIBLE);
        binding.musicPlayerClose.setOnClickListener(v -> settlePlayer(0f));
        binding.musicPlayerHeader.setOnTouchListener(new DragListener());
        binding.musicPlayerCover.setOnTouchListener(new DragListener());
        binding.musicPlayerPlay.setOnClickListener(v -> togglePlay());
        binding.musicPlayerPrevious.setOnClickListener(v -> {
            if (player != null) {
                player.playPrevious();
            }
        });
        binding.musicPlayerNext.setOnClickListener(v -> {
            if (player != null) {
                player.playNext();
            }
        });
        binding.musicPlayerShuffle.setOnClickListener(v -> {
            if (player != null) {
                player.toggleShuffleModeEnabled();
            }
        });
        binding.musicPlayerRepeat.setOnClickListener(v -> {
            if (player != null) {
                player.cycleNextRepeatMode();
            }
        });
        binding.musicPlayerLike.setOnClickListener(v -> toggleLike());
        binding.musicPlayerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress,
                                          final boolean fromUser) {
                if (fromUser) {
                    binding.musicPlayerNow.setText(
                            Localization.getDurationString(progress / 1000));
                }
            }

            @Override
            public void onStartTrackingTouch(final SeekBar seekBar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                seeking = false;
                if (player != null) {
                    player.seekTo(seekBar.getProgress());
                }
            }
        });
    }

    private void toggleLike() {
        final PlayQueue queue = player == null ? null : player.getPlayQueue();
        final PlayQueueItem item = queue == null ? null : queue.getItem();
        if (item != null) {
            final boolean liked = MusicStore.toggleLiked(this, toSong(item));
            binding.musicPlayerLike.setImageResource(
                    liked ? R.drawable.ic_favorite : R.drawable.ic_vm_favorite_border);
        }
    }

    // 0 = chiuso, 1 = tutto aperto
    private void setPlayerProgress(final float value) {
        playerProgress = value;
        final View view = binding.musicPlayer;
        if (view.getVisibility() != View.VISIBLE) {
            view.setVisibility(View.VISIBLE);
        }
        view.setTranslationY(view.getHeight() * (1f - value));
        final float rest = 1f - Math.min(1f, value * 1.6f);
        binding.musicBottom.setAlpha(rest);
    }

    private void settlePlayer(final float target) {
        if (playerAnimator != null) {
            playerAnimator.cancel();
        }
        final boolean opening = target > 0f;
        playerOpen = opening;
        playerAnimator = ValueAnimator.ofFloat(playerProgress, target);
        playerAnimator.setDuration(opening ? 340 : 260);
        playerAnimator.setInterpolator(new DecelerateInterpolator(1.6f));
        playerAnimator.addUpdateListener(a -> setPlayerProgress((float) a.getAnimatedValue()));
        playerAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(final Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                if (!cancelled && !opening) {
                    binding.musicPlayer.setVisibility(View.INVISIBLE);
                }
                binding.musicBottom.setVisibility(
                        !cancelled && opening ? View.INVISIBLE : View.VISIBLE);
            }
        });
        binding.musicBottom.setVisibility(View.VISIBLE);
        playerAnimator.start();
    }

    // Il lettore segue il dito, come la tendina: lasciandolo si apre o si chiude da solo.
    private final class DragListener implements View.OnTouchListener {
        private final int slop = DeviceUtils.dpToPx(6, MusicActivity.this);
        private float startY;
        private float startProgress;
        private boolean moved;
        @Nullable
        private VelocityTracker velocity;

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(final View v, final MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getRawY();
                    moved = false;
                    // il lettore scorre, ma qui il gesto è per trascinarlo
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    velocity = VelocityTracker.obtain();
                    velocity.addMovement(event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (velocity != null) {
                        velocity.addMovement(event);
                    }
                    drag(event.getRawY() - startY);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float speed = 0f;
                    if (velocity != null) {
                        velocity.addMovement(event);
                        velocity.computeCurrentVelocity(1000);
                        speed = velocity.getYVelocity();
                        velocity.recycle();
                        velocity = null;
                    }
                    release(v, speed, event.getActionMasked());
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
                if (playerAnimator != null) {
                    playerAnimator.cancel();
                }
                startProgress = playerProgress;
                binding.musicBottom.setVisibility(View.VISIBLE);
            }
            final int height = binding.musicPlayer.getHeight();
            if (height > 0) {
                setPlayerProgress(Math.max(0f, Math.min(1f,
                        startProgress - distance / height)));
            }
        }

        private void release(final View v, final float speed, final int action) {
            if (!moved) {
                if (action == MotionEvent.ACTION_UP) {
                    v.performClick();
                }
                return;
            }
            final boolean fast = Math.abs(speed) > DeviceUtils.dpToPx(500, MusicActivity.this);
            // basta un terzo di strada, da aperto come da chiuso
            final float threshold = startProgress > 0.5f ? 0.67f : 0.33f;
            settlePlayer((fast ? speed < 0 : playerProgress > threshold) ? 1f : 0f);
        }
    }
    //endregion
}
