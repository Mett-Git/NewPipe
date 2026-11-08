package org.schabi.newpipe.music;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Il profilo di un artista: foto grande, brani più popolari, album, video e informazioni. */
public class MusicArtistPage extends MusicPage {
    private static final String ARG_NAME = "name";
    private static final String ARG_COVER = "cover";
    private static final int TOP_SONGS = 5;
    private static final int MAX_ALBUMS = 15;
    private static final int MAX_VIDEOS = 15;
    private static final int COLLAPSED_LINES = 4;

    private final List<StreamInfoItem> songs = new ArrayList<>();
    private View hero;
    private LinearLayout songsBox;
    private LinearLayout albumsBox;
    private LinearLayout videosBox;
    private LinearLayout infoBox;
    private String name;
    @Nullable
    private String cover;

    /**
     * @param name  il nome dell'artista
     * @param cover una foto da mostrare subito, se si conosce
     * @return la pagina
     */
    public static MusicArtistPage create(@Nullable final String name,
                                         @Nullable final String cover) {
        final MusicArtistPage page = new MusicArtistPage();
        final Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        args.putString(ARG_COVER, cover);
        page.setArguments(args);
        return page;
    }

    // la foto arriva fin sotto la barra di stato
    @Override
    public void applyInsets() {
        final View view = getView();
        if (view != null) {
            view.setPadding(0, 0, 0, host().bottomSpace());
        }
        if (hero != null) {
            final View back = hero.findViewById(R.id.musicArtistBack);
            final ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) back.getLayoutParams();
            params.topMargin = host().topInset();
            back.setLayoutParams(params);
        }
    }

    @Override
    protected void build() {
        name = requireArguments().getString(ARG_NAME, "");
        cover = requireArguments().getString(ARG_COVER);
        hero = inflater().inflate(R.layout.music_artist_header, content, false);
        content.addView(hero);
        applyInsets();
        hero.findViewById(R.id.musicArtistBack).setOnClickListener(v -> host().goBack());
        ((TextView) hero.findViewById(R.id.musicArtistName)).setText(name);
        showCover();
        setupFollow();
        hero.findViewById(R.id.musicArtistPlay).setOnClickListener(v -> {
            if (!songs.isEmpty()) {
                host().playSongs(songs, 0, false);
            }
        });
        hero.findViewById(R.id.musicArtistShuffle).setOnClickListener(v -> {
            if (!songs.isEmpty()) {
                host().playSongs(songs, 0, true);
            }
        });
        songsBox = addBox();
        albumsBox = addBox();
        videosBox = addBox();
        infoBox = addBox();
        loadSongs();
        loadAlbums();
        loadVideos();
        loadProfile();
    }

    private LinearLayout addBox() {
        final LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        content.addView(box);
        return box;
    }

    private void showCover() {
        if (cover != null) {
            loadCover(hero.findViewById(R.id.musicArtistCover),
                    ImageStrategy.INSTANCE.dbUrlToImageList(cover));
        }
    }

    private void setupFollow() {
        final TextView button = hero.findViewById(R.id.musicArtistFollow);
        button.setText(MusicStore.isFollowed(requireContext(), name)
                ? R.string.vm_following : R.string.vm_follow);
        button.setOnClickListener(v -> button.setText(
                MusicStore.toggleFollow(requireContext(), name, cover)
                        ? R.string.vm_following : R.string.vm_follow));
    }

    private boolean isTheirs(@Nullable final String uploader) {
        return uploader != null && uploader.toLowerCase(Locale.ROOT)
                .contains(name.toLowerCase(Locale.ROOT));
    }

    //region Brani
    private void loadSongs() {
        songsBox.removeAllViews();
        final View spinner = addSpinner(songsBox);
        disposables.add(search(name, FILTER_SONGS).subscribe(items -> {
            songsBox.removeView(spinner);
            final List<StreamInfoItem> all = onlySongs(items);
            for (final StreamInfoItem song : all) {
                if (isTheirs(song.getUploaderName())) {
                    songs.add(song);
                }
            }
            // lo stesso nome porta anche a brani di altri: se non ne resta nessuno, tutti
            if (songs.isEmpty()) {
                songs.addAll(all);
            }
            showSongs();
        }, error -> {
            songsBox.removeView(spinner);
            addMessage(songsBox, R.string.vm_load_error, this::loadSongs);
        }));
    }

    private void showSongs() {
        if (songs.isEmpty()) {
            addMessage(songsBox, R.string.vm_nothing_found, null);
            return;
        }
        addSection(songsBox, getString(R.string.vm_popular));
        final int rowsStart = songsBox.getChildCount();
        addSongRows(songsBox, songs);
        for (int i = rowsStart; i < songsBox.getChildCount(); i++) {
            final TextView rank = songsBox.getChildAt(i).findViewById(R.id.musicRowRank);
            rank.setText(String.valueOf(i - rowsStart + 1));
            rank.setVisibility(View.VISIBLE);
            if (i - rowsStart >= TOP_SONGS) {
                songsBox.getChildAt(i).setVisibility(View.GONE);
            }
        }
        if (songs.size() > TOP_SONGS) {
            final TextView more = (TextView) inflater()
                    .inflate(R.layout.music_show_more, songsBox, false);
            more.setOnClickListener(v -> {
                for (int i = rowsStart; i < songsBox.getChildCount(); i++) {
                    songsBox.getChildAt(i).setVisibility(View.VISIBLE);
                }
                songsBox.removeView(more);
            });
            songsBox.addView(more);
        }
    }
    //endregion

    //region Album e video
    private HorizontalScrollView addStrip(final ViewGroup parent, final LinearLayout strip) {
        final HorizontalScrollView scroll = new HorizontalScrollView(requireContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(12), 0, dp(12), 0);
        scroll.addView(strip);
        parent.addView(scroll);
        return scroll;
    }

    private void loadAlbums() {
        disposables.add(search(name, FILTER_ALBUMS).subscribe(items -> {
            final LinearLayout strip = new LinearLayout(requireContext());
            for (final InfoItem item : items) {
                if (strip.getChildCount() < MAX_ALBUMS && item instanceof PlaylistInfoItem
                        && isTheirs(((PlaylistInfoItem) item).getUploaderName())) {
                    final View card = inflater().inflate(R.layout.music_card, strip, false);
                    ((TextView) card.findViewById(R.id.musicCardTitle)).setText(item.getName());
                    card.findViewById(R.id.musicCardSubtitle).setVisibility(View.GONE);
                    loadCover(card.findViewById(R.id.musicCardCover), item.getThumbnails());
                    card.setOnClickListener(v -> host().openPage(
                            MusicListPage.playlist(item.getName(), item.getUrl())));
                    strip.addView(card);
                }
            }
            if (strip.getChildCount() > 0) {
                addSection(albumsBox, getString(R.string.vm_chip_albums));
                addStrip(albumsBox, strip);
            }
        }, error -> { /* senza album il profilo resta valido */ }));
    }

    private void loadVideos() {
        disposables.add(search(name, FILTER_VIDEOS).subscribe(items -> {
            final List<StreamInfoItem> videos = new ArrayList<>();
            for (final StreamInfoItem video : onlySongs(items)) {
                if (videos.size() < MAX_VIDEOS && isTheirs(video.getUploaderName())) {
                    videos.add(video);
                }
            }
            if (!videos.isEmpty()) {
                addSection(videosBox, getString(R.string.vm_chip_videos));
                addCarousel(videosBox, videos);
            }
        }, error -> { /* senza video il profilo resta valido */ }));
    }
    //endregion

    //region Foto e informazioni
    private void loadProfile() {
        disposables.add(search(name, FILTER_ARTISTS).subscribe(items -> {
            for (final InfoItem item : items) {
                if (item instanceof ChannelInfoItem && name.equalsIgnoreCase(item.getName())) {
                    showProfile((ChannelInfoItem) item);
                    return;
                }
            }
        }, error -> { /* restano nome e foto che già si hanno */ }));
    }

    private void showProfile(@NonNull final ChannelInfoItem artist) {
        final String photo = ImageStrategy.INSTANCE.imageListToDbUrl(artist.getThumbnails());
        if (photo != null) {
            cover = photo;
            showCover();
        }
        setupFollow();
        showFollowers(artist.getSubscriberCount());
        disposables.add(ExtractorHelper.getChannelInfo(YOUTUBE, artist.getUrl(), false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    showFollowers(info.getSubscriberCount());
                    showDescription(info.getDescription());
                }, error -> { /* le informazioni sono un di più */ }));
    }

    private void showFollowers(final long count) {
        if (count >= 0) {
            final TextView followers = hero.findViewById(R.id.musicArtistFollowers);
            followers.setText(Localization.shortSubscriberCount(requireContext(), count));
            followers.setVisibility(View.VISIBLE);
        }
    }

    private void showDescription(@Nullable final String description) {
        if (TextUtils.isEmpty(description) || infoBox.getChildCount() > 0) {
            return;
        }
        addSection(infoBox, getString(R.string.vm_about));
        final TextView text = (TextView) inflater()
                .inflate(R.layout.music_about, infoBox, false);
        text.setText(description);
        text.setMaxLines(COLLAPSED_LINES);
        text.setOnClickListener(v -> text.setMaxLines(
                text.getMaxLines() == COLLAPSED_LINES ? Integer.MAX_VALUE : COLLAPSED_LINES));
        infoBox.addView(text);
    }
    //endregion
}
