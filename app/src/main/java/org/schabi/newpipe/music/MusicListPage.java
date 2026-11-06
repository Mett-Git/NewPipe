package org.schabi.newpipe.music;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Una pagina con copertina, Riproduci e Casuale: album, playlist, artista, preferiti... */
public class MusicListPage extends MusicPage {
    private static final String ARG_KIND = "kind";
    private static final String ARG_TITLE = "title";
    private static final String ARG_URL = "url";
    private static final String ARG_COVER = "cover";
    private static final String ARG_ID = "id";
    private static final int KIND_PLAYLIST = 0;
    private static final int KIND_ARTIST = 1;
    private static final int KIND_MOOD = 2;
    private static final int KIND_LIKED = 3;
    private static final int KIND_RECENT = 4;
    private static final int KIND_LOCAL = 5;
    private static final int MAX_ALBUMS = 12;

    private LinearLayout box;
    private View header;
    private final List<StreamInfoItem> shownSongs = new ArrayList<>();
    private Page nextPage;
    private boolean loadingMore = false;

    private static MusicListPage page(final int kind, @Nullable final String title) {
        final MusicListPage page = new MusicListPage();
        final Bundle args = new Bundle();
        args.putInt(ARG_KIND, kind);
        args.putString(ARG_TITLE, title);
        page.setArguments(args);
        return page;
    }

    /**
     * @param title il nome
     * @param url   l'indirizzo dell'album o della playlist
     * @return la pagina
     */
    public static MusicListPage playlist(@Nullable final String title,
                                         @Nullable final String url) {
        final MusicListPage page = page(KIND_PLAYLIST, title);
        page.requireArguments().putString(ARG_URL, url);
        return page;
    }

    /**
     * @param name  il nome dell'artista
     * @param cover la sua foto, se si conosce
     * @return la pagina
     */
    public static MusicListPage artist(@Nullable final String name, @Nullable final String cover) {
        final MusicListPage page = page(KIND_ARTIST, name);
        page.requireArguments().putString(ARG_COVER, cover);
        return page;
    }

    /**
     * @param mood il genere o lo stato d'animo da cercare tra le playlist
     * @return la pagina
     */
    public static MusicListPage mood(@NonNull final String mood) {
        return page(KIND_MOOD, mood);
    }

    /**
     * @param title il titolo
     * @return la pagina dei brani che piacciono
     */
    public static MusicListPage liked(@NonNull final String title) {
        return page(KIND_LIKED, title);
    }

    /**
     * @param title il titolo
     * @return la pagina dei brani ascoltati
     */
    public static MusicListPage recent(@NonNull final String title) {
        return page(KIND_RECENT, title);
    }

    /**
     * @param title il nome
     * @param id    il numero della playlist salvata in NewPipe
     * @return la pagina
     */
    public static MusicListPage localPlaylist(@Nullable final String title, final long id) {
        final MusicListPage page = page(KIND_LOCAL, title);
        page.requireArguments().putLong(ARG_ID, id);
        return page;
    }

    @Override
    protected void build() {
        final Bundle args = requireArguments();
        header = inflater().inflate(R.layout.music_list_header, content, false);
        content.addView(header);
        header.findViewById(R.id.musicListBack).setOnClickListener(v -> host().goBack());
        ((TextView) header.findViewById(R.id.musicListTitle))
                .setText(args.getString(ARG_TITLE));
        final String cover = args.getString(ARG_COVER);
        if (cover != null) {
            loadCover(header.findViewById(R.id.musicListCover),
                    ImageStrategy.INSTANCE.dbUrlToImageList(cover));
        }
        box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        content.addView(box);
        loadContent();
    }

    private void loadContent() {
        final Bundle args = requireArguments();
        final String title = args.getString(ARG_TITLE, "");
        box.removeAllViews();
        final View spinner = addSpinner(box);
        switch (args.getInt(ARG_KIND)) {
            case KIND_PLAYLIST:
                disposables.add(ExtractorHelper.getPlaylistInfo(YOUTUBE,
                                args.getString(ARG_URL), false)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(info -> {
                            box.removeView(spinner);
                            setSubtitle(info.getUploaderName());
                            nextPage = info.getNextPage();
                            shownSongs.clear();
                            shownSongs.addAll(onlySongs(info.getRelatedItems()));
                            showSongs(shownSongs);
                        }, error -> showError(spinner)));
                break;
            case KIND_ARTIST:
                disposables.add(Single.zip(
                                search(title, FILTER_SONGS),
                                search(title, FILTER_ALBUMS)
                                        .onErrorReturnItem(new ArrayList<>()),
                                ArtistResults::new)
                        .subscribe(results -> {
                            box.removeView(spinner);
                            showArtist(title, results);
                        }, error -> showError(spinner)));
                break;
            case KIND_MOOD:
                disposables.add(search(title, FILTER_PLAYLISTS).subscribe(items -> {
                    box.removeView(spinner);
                    hidePlayButtons();
                    addItemRows(box, items);
                }, error -> showError(spinner)));
                break;
            case KIND_LOCAL:
                disposables.add(new LocalPlaylistManager(
                        NewPipeDatabase.getInstance(requireContext()))
                        .getPlaylistStreams(args.getLong(ARG_ID)).firstOrError()
                        .map(entries -> {
                            final List<StreamInfoItem> songs = new ArrayList<>();
                            for (final PlaylistStreamEntry entry : entries) {
                                songs.add(entry.toStreamInfoItem());
                            }
                            return songs;
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(songs -> {
                            box.removeView(spinner);
                            showSongs(songs);
                        }, error -> showError(spinner)));
                break;
            default:
                box.removeView(spinner);
                showSongs(args.getInt(ARG_KIND) == KIND_LIKED
                        ? MusicStore.liked(requireContext())
                        : MusicStore.recent(requireContext()));
                break;
        }
    }

    @Override
    protected void onNearEnd() {
        if (loadingMore || !Page.isValid(nextPage)) {
            return;
        }
        loadingMore = true;
        final View spinner = addSpinner(box);
        disposables.add(ExtractorHelper.getMorePlaylistItems(YOUTUBE,
                        requireArguments().getString(ARG_URL), nextPage)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(page -> {
                    box.removeView(spinner);
                    loadingMore = false;
                    nextPage = page.getNextPage();
                    final int from = shownSongs.size();
                    shownSongs.addAll(page.getItems());
                    addSongRows(box, shownSongs, from);
                }, error -> {
                    box.removeView(spinner);
                    loadingMore = false;
                    nextPage = null;
                }));
    }

    private void showError(final View spinner) {
        box.removeView(spinner);
        addMessage(box, R.string.vm_load_error, this::loadContent);
    }

    private void setSubtitle(@Nullable final String text) {
        final TextView subtitle = header.findViewById(R.id.musicListSubtitle);
        subtitle.setText(text);
        subtitle.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    private void hidePlayButtons() {
        header.findViewById(R.id.musicListButtons).setVisibility(View.GONE);
        header.findViewById(R.id.musicListCover).setVisibility(View.GONE);
    }

    private void showSongs(@NonNull final List<StreamInfoItem> songs) {
        if (songs.isEmpty()) {
            hidePlayButtons();
            addMessage(box, R.string.vm_nothing_found, null);
            return;
        }
        if (requireArguments().getString(ARG_COVER) == null) {
            loadCover(header.findViewById(R.id.musicListCover), songs.get(0).getThumbnails());
        }
        header.findViewById(R.id.musicListPlay)
                .setOnClickListener(v -> host().playSongs(songs, 0, false));
        header.findViewById(R.id.musicListShuffle)
                .setOnClickListener(v -> host().playSongs(songs, 0, true));
        addSongRows(box, songs);
    }

    // lo stesso nome porta anche a brani di altri: tiene quelli dell'artista, se ce ne sono
    private void showArtist(final String name, final ArtistResults results) {
        final List<StreamInfoItem> all = onlySongs(results.songs);
        final List<StreamInfoItem> songs = new ArrayList<>();
        for (final StreamInfoItem song : all) {
            if (matches(song.getUploaderName(), name)) {
                songs.add(song);
            }
        }
        showSongs(songs.isEmpty() ? all : songs);
        final List<InfoItem> albums = new ArrayList<>();
        for (final InfoItem album : results.albums) {
            if (albums.size() < MAX_ALBUMS && album instanceof PlaylistInfoItem
                    && matches(((PlaylistInfoItem) album).getUploaderName(), name)) {
                albums.add(album);
            }
        }
        if (!albums.isEmpty()) {
            addSection(box, getString(R.string.vm_chip_albums));
            addItemRows(box, albums);
        }
    }

    private static boolean matches(@Nullable final String uploader, @NonNull final String name) {
        return uploader != null && uploader.toLowerCase(Locale.ROOT)
                .contains(name.toLowerCase(Locale.ROOT));
    }

    private static final class ArtistResults {
        private final List<InfoItem> songs;
        private final List<InfoItem> albums;

        ArtistResults(final List<InfoItem> songs, final List<InfoItem> albums) {
            this.songs = songs;
            this.albums = albums;
        }
    }
}
