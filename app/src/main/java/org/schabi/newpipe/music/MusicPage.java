package org.schabi.newpipe.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.RelativeCornerSize;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.player.MusicActivity;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.RestrictedVideos;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** La base delle pagine di NewPipe Music: una colonna che scorre e i pezzi per riempirla. */
public abstract class MusicPage extends Fragment {
    protected static final int YOUTUBE = ServiceList.YouTube.getServiceId();
    protected static final String FILTER_SONGS = "music_songs";
    protected static final String FILTER_ALBUMS = "music_albums";
    protected static final String FILTER_PLAYLISTS = "music_playlists";
    protected static final String FILTER_ARTISTS = "music_artists";
    private static final String TRENDING_KIOSK = "trending_music";
    private static final String TRENDING_FALLBACK = "top hits";
    private static final int COVER_SIZE = 800;
    private static final int NEAR_END_DP = 500;
    private static final int TINT_ALPHA = 0x8C;
    private static final float HALF = 0.5f;
    private static final int MINUTE = 60;
    private static final int HOUR = 3600;

    protected final CompositeDisposable disposables = new CompositeDisposable();
    protected LinearLayout content;
    private final List<View> songRows = new ArrayList<>();
    /** true per mettere il numero davanti a ogni brano, come in una classifica. */
    protected boolean numbered = false;

    /** Riempie la pagina. */
    protected abstract void build();

    /** La lista è quasi finita: chi ha altre pagine le carica qui. */
    protected void onNearEnd() {
        // di solito non c'è altro
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final NestedScrollView scroll = new NestedScrollView(inflater.getContext());
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content = new LinearLayout(inflater.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (view, x, y, oldX, oldY) -> {
                    if (y + view.getHeight() >= content.getHeight() - dp(NEAR_END_DP)) {
                        onNearEnd();
                    }
                });
        return scroll;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyInsets();
        build();
    }

    @Override
    public void onDestroyView() {
        disposables.clear();
        songRows.clear();
        super.onDestroyView();
    }

    @Override
    public void onHiddenChanged(final boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) {
            refreshNowPlaying();
        }
    }

    /** Lascia libero lo spazio sotto la barra di stato e sopra la barra in basso. */
    public void applyInsets() {
        final View view = getView();
        if (view != null) {
            view.setPadding(0, host().topInset(), 0, host().bottomSpace());
        }
    }

    /** Il brano in riproduzione è cambiato, o è andato in pausa: aggiorna le righe. */
    public void refreshNowPlaying() {
        for (final View row : songRows) {
            markRow(row);
        }
    }

    protected MusicHost host() {
        return (MusicHost) requireActivity();
    }

    protected LayoutInflater inflater() {
        return LayoutInflater.from(requireContext());
    }

    protected int dp(final int value) {
        return DeviceUtils.dpToPx(value, requireContext());
    }

    //region Pezzi della pagina

    /**
     * @param title il titolo da mettere in alto
     * @return il titolo della pagina con la lente per cercare
     */
    protected View pageTitle(@NonNull final CharSequence title) {
        final View view = inflater().inflate(R.layout.music_page_title, content, false);
        ((TextView) view.findViewById(R.id.musicPageTitle)).setText(title);
        view.findViewById(R.id.musicPageSearch)
                .setOnClickListener(v -> host().openPage(new MusicSearchPage()));
        return view;
    }

    /**
     * @param parent dove va messo
     * @param title  il titolo della sezione
     */
    protected void addSection(@NonNull final ViewGroup parent, @NonNull final CharSequence title) {
        final TextView view = (TextView) inflater()
                .inflate(R.layout.music_section, parent, false);
        view.setText(title);
        parent.addView(view);
    }

    /**
     * @param parent dove va messo
     * @return la rotella di attesa, già aggiunta
     */
    protected View addSpinner(@NonNull final ViewGroup parent) {
        final ProgressBar spinner = new ProgressBar(requireContext());
        spinner.setIndeterminateTintList(ContextCompat.getColorStateList(
                requireContext(), R.color.vm_accent));
        final LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(dp(36), dp(36));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(24);
        params.bottomMargin = dp(24);
        parent.addView(spinner, params);
        return spinner;
    }

    /**
     * @param parent  dove va messo
     * @param message il testo
     * @param retry   cosa fare toccandolo, se si può riprovare
     */
    protected void addMessage(@NonNull final ViewGroup parent, @StringRes final int message,
                              @Nullable final Runnable retry) {
        final TextView view = (TextView) inflater()
                .inflate(R.layout.music_message, parent, false);
        view.setText(message);
        if (retry != null) {
            view.setOnClickListener(v -> retry.run());
        }
        parent.addView(view);
    }

    /**
     * @param parent dove vanno messe
     * @param songs  i brani: ognuno fa partire la coda da sé
     */
    protected void addSongRows(@NonNull final ViewGroup parent,
                               @NonNull final List<StreamInfoItem> songs) {
        addSongRows(parent, songs, 0);
    }

    /**
     * @param parent dove vanno messe
     * @param songs  tutti i brani: ognuno fa partire la coda da sé
     * @param from   il primo da aggiungere (i precedenti ci sono già)
     */
    protected void addSongRows(@NonNull final ViewGroup parent,
                               @NonNull final List<StreamInfoItem> songs, final int from) {
        for (int i = from; i < songs.size(); i++) {
            parent.addView(songRow(parent, songs, i));
        }
    }

    private View songRow(final ViewGroup parent, final List<StreamInfoItem> songs,
                         final int index) {
        final StreamInfoItem song = songs.get(index);
        final View row = inflater().inflate(R.layout.music_row, parent, false);
        row.setTag(song.getUrl());
        ((TextView) row.findViewById(R.id.musicRowTitle)).setText(song.getName());
        ((TextView) row.findViewById(R.id.musicRowSubtitle)).setText(songInfo(song));
        if (numbered) {
            final TextView rank = row.findViewById(R.id.musicRowRank);
            rank.setText(String.valueOf(index + 1));
            rank.setVisibility(View.VISIBLE);
        }
        loadCover(row.findViewById(R.id.musicRowCover), song.getThumbnails());
        RestrictedVideos.bind(row.findViewById(R.id.musicRowBadge), song.getUrl(),
                song.getContentAvailability());
        row.setOnClickListener(v -> host().playSongs(songs, index, false));
        final View more = row.findViewById(R.id.musicRowMore);
        more.setOnClickListener(v -> showSongMenu(v, song));
        row.setOnLongClickListener(v -> {
            showSongMenu(more, song);
            return true;
        });
        songRows.add(row);
        markRow(row);
        return row;
    }

    // l'artista e, se si sa, quanto dura
    private static String songInfo(final StreamInfoItem song) {
        final String artist = song.getUploaderName() == null ? "" : song.getUploaderName();
        if (song.getDuration() <= 0) {
            return artist;
        }
        final String duration = time(song.getDuration());
        return artist.isEmpty() ? duration : artist + " · " + duration;
    }

    /**
     * @param seconds una durata
     * @return scritta come "3:20", senza lo zero davanti ai minuti
     */
    public static String time(final long seconds) {
        return seconds >= HOUR ? Localization.getDurationString(seconds)
                : String.format(Locale.ROOT, "%d:%02d", seconds / MINUTE, seconds % MINUTE);
    }

    private void markRow(final View row) {
        final boolean current = TextUtils.equals((String) row.getTag(), host().nowPlayingUrl());
        final MusicBars bars = row.findViewById(R.id.musicRowBars);
        bars.setVisibility(current ? View.VISIBLE : View.GONE);
        bars.setMoving(host().isPlayingNow());
        row.findViewById(R.id.musicRowMore).setVisibility(current ? View.GONE : View.VISIBLE);
        ((TextView) row.findViewById(R.id.musicRowTitle)).setTextColor(ContextCompat.getColor(
                requireContext(), current ? R.color.vm_accent : R.color.vm_text));
    }

    private void showSongMenu(final View anchor, final StreamInfoItem song) {
        final Context context = requireContext();
        final PopupMenu menu = new PopupMenu(context, anchor);
        final boolean liked = MusicStore.isLiked(context, song.getUrl());
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();
        menu.getMenu().add(Menu.NONE, 1, 1, R.string.vm_play_next);
        menu.getMenu().add(Menu.NONE, 2, 2, R.string.vm_add_to_queue);
        menu.getMenu().add(Menu.NONE, 3, 3, liked ? R.string.vm_unlike : R.string.vm_like);
        menu.getMenu().add(Menu.NONE, 4, 4, R.string.vm_radio);
        menu.getMenu().add(Menu.NONE, 5, 5, R.string.vm_add_to_playlist);
        menu.getMenu().add(Menu.NONE, 6, 6, R.string.vm_download);
        menu.getMenu().add(Menu.NONE, 7, 7, R.string.vm_share);
        if (!TextUtils.isEmpty(song.getUploaderName())) {
            menu.getMenu().add(Menu.NONE, 8, 8, R.string.vm_go_to_artist);
        }
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    host().playNext(song);
                    break;
                case 2:
                    host().enqueue(song);
                    break;
                case 3:
                    MusicStore.toggleLiked(context, song);
                    break;
                case 4:
                    MusicActions.radio(activity, host(), song, disposables);
                    break;
                case 5:
                    MusicActions.addToPlaylist(activity, song, disposables);
                    break;
                case 6:
                    MusicActions.download(activity, song, disposables);
                    break;
                case 7:
                    MusicActions.share(activity, song);
                    break;
                default:
                    host().openPage(MusicArtistPage.create(song.getUploaderName(), null));
                    break;
            }
            return true;
        });
        menu.show();
    }

    /**
     * @param parent dove vanno messe
     * @param items  album, playlist o artisti (i brani passano da {@link #addSongRows})
     */
    protected void addItemRows(@NonNull final ViewGroup parent,
                               @NonNull final List<InfoItem> items) {
        for (final InfoItem item : items) {
            if (item instanceof ChannelInfoItem) {
                parent.addView(linkRow(parent, item.getName(),
                        getString(R.string.vm_artist), item.getThumbnails(),
                        v -> openItem(item)));
            } else if (item instanceof PlaylistInfoItem) {
                parent.addView(linkRow(parent, item.getName(),
                        ((PlaylistInfoItem) item).getUploaderName(), item.getThumbnails(),
                        v -> openItem(item)));
            }
        }
    }

    /**
     * @param parent   dove va messa
     * @param title    il titolo
     * @param subtitle la riga sotto
     * @param images   la copertina
     * @param click    cosa fare toccandola
     * @return una riga senza menu, per aprire una pagina
     */
    protected View linkRow(@NonNull final ViewGroup parent, @Nullable final CharSequence title,
                           @Nullable final CharSequence subtitle,
                           @NonNull final List<Image> images,
                           @NonNull final View.OnClickListener click) {
        final View row = inflater().inflate(R.layout.music_row, parent, false);
        ((TextView) row.findViewById(R.id.musicRowTitle)).setText(title);
        ((TextView) row.findViewById(R.id.musicRowSubtitle)).setText(subtitle);
        loadCover(row.findViewById(R.id.musicRowCover), images);
        row.findViewById(R.id.musicRowMore).setVisibility(View.GONE);
        row.setOnClickListener(click);
        return row;
    }

    /**
     * @param parent dove va messa
     * @param songs  i brani da mostrare come copertine in fila
     */
    protected void addCarousel(@NonNull final ViewGroup parent,
                               @NonNull final List<StreamInfoItem> songs) {
        final HorizontalScrollView scroll = new HorizontalScrollView(requireContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(12), 0, dp(12), 0);
        final LinearLayout strip = new LinearLayout(requireContext());
        for (int i = 0; i < songs.size(); i++) {
            final StreamInfoItem song = songs.get(i);
            final int index = i;
            final View card = inflater().inflate(R.layout.music_card, strip, false);
            ((TextView) card.findViewById(R.id.musicCardTitle)).setText(song.getName());
            ((TextView) card.findViewById(R.id.musicCardSubtitle))
                    .setText(song.getUploaderName());
            loadCover(card.findViewById(R.id.musicCardCover), song.getThumbnails());
            card.setOnClickListener(v -> host().playSongs(songs, index, false));
            card.setOnLongClickListener(v -> {
                showSongMenu(v, song);
                return true;
            });
            strip.addView(card);
        }
        scroll.addView(strip);
        parent.addView(scroll);
    }

    /**
     * @param parent dove va messa
     * @param items  album, playlist o artisti da mostrare come copertine in fila
     * @param max    quanti al massimo
     * @return quanti ne ha messi
     */
    protected int addItemStrip(@NonNull final ViewGroup parent,
                               @NonNull final List<? extends InfoItem> items, final int max) {
        final LinearLayout strip = new LinearLayout(requireContext());
        for (final InfoItem item : items) {
            if (strip.getChildCount() >= max) {
                break;
            }
            final boolean artist = item instanceof ChannelInfoItem;
            if (!artist && !(item instanceof PlaylistInfoItem)) {
                continue;
            }
            final View card = inflater().inflate(
                    artist ? R.layout.music_artist_card : R.layout.music_card, strip, false);
            ((TextView) card.findViewById(R.id.musicCardTitle)).setText(item.getName());
            final TextView subtitle = card.findViewById(R.id.musicCardSubtitle);
            if (subtitle != null) {
                final String uploader = ((PlaylistInfoItem) item).getUploaderName();
                subtitle.setText(uploader);
                subtitle.setVisibility(TextUtils.isEmpty(uploader) ? View.GONE : View.VISIBLE);
            }
            loadCover(card.findViewById(R.id.musicCardCover), item.getThumbnails());
            card.setOnClickListener(v -> openItem(item));
            strip.addView(card);
        }
        if (strip.getChildCount() > 0) {
            final HorizontalScrollView scroll = new HorizontalScrollView(requireContext());
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setClipToPadding(false);
            scroll.setPadding(dp(12), 0, dp(12), 0);
            scroll.addView(strip);
            parent.addView(scroll);
        }
        return strip.getChildCount();
    }

    /**
     * @param row una riga fatta con {@link #linkRow}
     */
    protected static void makeRound(@NonNull final View row) {
        final ShapeableImageView cover = row.findViewById(R.id.musicRowCover);
        cover.setShapeAppearanceModel(cover.getShapeAppearanceModel().toBuilder()
                .setAllCornerSizes(new RelativeCornerSize(HALF))
                .build());
    }

    /**
     * Sfuma dietro a una vista il colore principale della copertina.
     *
     * @param target dove mettere il colore
     * @param images la copertina
     */
    protected void tintFromCover(@NonNull final View target, @NonNull final List<Image> images) {
        final String url = coverUrl(images);
        if (url == null) {
            return;
        }
        final Context application = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
                    final Bitmap bitmap =
                            CoilHelper.INSTANCE.loadBitmapBlocking(application, url, 0);
                    return bitmap == null ? 0 : MusicActivity.mainColor(bitmap);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(color -> {
                    if (color != 0) {
                        target.setBackground(new GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                                (color & 0x00ffffff) | (TINT_ALPHA << 24),
                                color & 0x00ffffff}));
                    }
                }, error -> { /* senza colore va bene lo stesso */ }));
    }

    private void openItem(final InfoItem item) {
        if (item instanceof ChannelInfoItem) {
            host().openPage(MusicArtistPage.create(item.getName(),
                    ImageStrategy.INSTANCE.imageListToDbUrl(item.getThumbnails())));
        } else {
            host().openPage(MusicListPage.playlist(item.getName(), item.getUrl()));
        }
    }

    /**
     * @param view   dove mostrarla
     * @param images le misure disponibili
     */
    public static void loadCover(@NonNull final ImageView view,
                                 @NonNull final List<Image> images) {
        CoilHelper.INSTANCE.loadThumbnail(view, coverUrl(images));
    }

    /**
     * @param images le misure disponibili
     * @return l'indirizzo da usare per la copertina
     */
    @Nullable
    public static String coverUrl(@NonNull final List<Image> images) {
        final String url = ImageStrategy.INSTANCE.choosePreferredImage(images);
        // YouTube Music dà copertine minuscole, ma nell'indirizzo si può chiedere la misura
        return url == null ? null
                : url.replaceAll("=w\\d+-h\\d+", "=w" + COVER_SIZE + "-h" + COVER_SIZE)
                .replaceAll("=s\\d+(?=-|$)", "=s" + COVER_SIZE);
    }
    //endregion

    //region Dati

    /**
     * @param query  cosa cercare
     * @param filter uno dei FILTER_*
     * @return i risultati di YouTube Music
     */
    protected Single<List<InfoItem>> search(@NonNull final String query,
                                            @NonNull final String filter) {
        return ExtractorHelper.searchFor(YOUTUBE, query,
                        Collections.singletonList(filter), "")
                .map(info -> (List<InfoItem>) new ArrayList<>(info.getRelatedItems()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * @param items risultati di ogni tipo
     * @return solo i brani
     */
    protected static List<StreamInfoItem> onlySongs(
            @NonNull final List<? extends InfoItem> items) {
        final List<StreamInfoItem> songs = new ArrayList<>();
        for (final InfoItem item : items) {
            if (item instanceof StreamInfoItem) {
                songs.add((StreamInfoItem) item);
            }
        }
        return songs;
    }

    /**
     * @return le tendenze musicali; se YouTube non le dà, una ricerca dei brani del momento
     */
    protected Single<List<StreamInfoItem>> trending() {
        final Single<List<StreamInfoItem>> fallback =
                search(TRENDING_FALLBACK, FILTER_SONGS).map(MusicPage::onlySongs);
        return Single.fromCallable(() -> NewPipe.getService(YOUTUBE).getKioskList()
                        .getListLinkHandlerFactoryByType(TRENDING_KIOSK)
                        .fromId(TRENDING_KIOSK).getUrl())
                .flatMap(url -> ExtractorHelper.getKioskInfo(YOUTUBE, url, false))
                .map(info -> onlySongs(info.getRelatedItems()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(songs -> songs.isEmpty() ? fallback : Single.just(songs))
                .onErrorResumeWith(fallback);
    }
    //endregion
}
