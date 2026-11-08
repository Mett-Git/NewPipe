package org.schabi.newpipe.music;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final int KIND_MOOD = 2;
    private static final int KIND_LIKED = 3;
    private static final int KIND_RECENT = 4;
    private static final int KIND_LOCAL = 5;
    private static final int KIND_MIX = 6;
    private static final String ARG_ARTIST = "artist";

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
     * @param title  il titolo del mix
     * @param artist l'artista da cui parte
     * @param seed   il suo brano più ascoltato: i brani simili partono da qui
     * @return la pagina
     */
    public static MusicListPage mix(@NonNull final String title, @NonNull final String artist,
                                    @NonNull final StreamInfoItem seed) {
        final MusicListPage page = page(KIND_MIX, title);
        page.requireArguments().putString(ARG_ARTIST, artist);
        page.requireArguments().putString(ARG_URL, seed.getUrl());
        page.requireArguments().putString(ARG_COVER,
                ImageStrategy.INSTANCE.imageListToDbUrl(seed.getThumbnails()));
        return page;
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
                            setupSaveButton(info);
                            shownSongs.clear();
                            shownSongs.addAll(onlySongs(info.getRelatedItems()));
                            showSongs(shownSongs);
                        }, error -> showError(spinner)));
                break;
            case KIND_MIX:
                disposables.add(Single.zip(
                                ExtractorHelper.getStreamInfo(YOUTUBE,
                                                args.getString(ARG_URL), false)
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .map(info -> onlySongs(info.getRelatedItems()))
                                        .onErrorReturnItem(new ArrayList<>()),
                                search(args.getString(ARG_ARTIST, ""), FILTER_SONGS)
                                        .map(MusicPage::onlySongs)
                                        .onErrorReturnItem(new ArrayList<>()),
                                MusicListPage::mixTogether)
                        .subscribe(songs -> {
                            box.removeView(spinner);
                            showSongs(songs);
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

    // uno a testa, senza doppioni
    private static List<StreamInfoItem> mixTogether(final List<StreamInfoItem> first,
                                                    final List<StreamInfoItem> second) {
        final Map<String, StreamInfoItem> mixed = new LinkedHashMap<>();
        for (int i = 0; i < Math.max(first.size(), second.size()); i++) {
            if (i < first.size()) {
                mixed.putIfAbsent(first.get(i).getUrl(), first.get(i));
            }
            if (i < second.size()) {
                mixed.putIfAbsent(second.get(i).getUrl(), second.get(i));
            }
        }
        return new ArrayList<>(mixed.values());
    }

    // l'album o la playlist finisce nella Raccolta, con i segnalibri di NewPipe
    private void setupSaveButton(final PlaylistInfo info) {
        final TextView button = header.findViewById(R.id.musicListExtra);
        final RemotePlaylistManager manager =
                new RemotePlaylistManager(NewPipeDatabase.getInstance(requireContext()));
        button.setVisibility(View.VISIBLE);
        disposables.add(manager.getPlaylist(info)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(saved -> {
                    button.setText(saved.isEmpty() ? R.string.vm_save : R.string.vm_saved);
                    button.setOnClickListener(v -> disposables.add((saved.isEmpty()
                            ? manager.onBookmark(info).map(id -> 0)
                            : manager.deletePlaylist(saved.get(0).getUid()))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(done -> { }, error -> { })));
                }, error -> button.setVisibility(View.GONE)));
    }

    // Prima carica anche le pagine non ancora scorse: devono entrare tutti i brani.
    private void addAllToPlaylist(final List<StreamInfoItem> songs) {
        final List<StreamInfoItem> all = new ArrayList<>(songs);
        if (Page.isValid(nextPage)) {
            Toast.makeText(requireContext(), R.string.vm_loading_all, Toast.LENGTH_SHORT).show();
        }
        disposables.add(loadRemaining(all, nextPage)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(complete -> {
                    final List<StreamEntity> entities = new ArrayList<>();
                    for (final StreamInfoItem song : complete) {
                        entities.add(new StreamEntity(song));
                    }
                    final AppCompatActivity activity = (AppCompatActivity) requireActivity();
                    disposables.add(PlaylistDialog.createCorrespondingDialog(activity, entities,
                            dialog -> dialog.show(activity.getSupportFragmentManager(),
                                    "musicAddAll")));
                }, error -> Toast.makeText(requireContext(), R.string.vm_load_error,
                        Toast.LENGTH_SHORT).show()));
    }

    private Single<List<StreamInfoItem>> loadRemaining(final List<StreamInfoItem> songs,
                                                       @Nullable final Page page) {
        if (!Page.isValid(page)) {
            return Single.just(songs);
        }
        return ExtractorHelper.getMorePlaylistItems(YOUTUBE,
                        requireArguments().getString(ARG_URL), page)
                .subscribeOn(Schedulers.io())
                .flatMap(more -> {
                    songs.addAll(more.getItems());
                    return loadRemaining(songs, more.getNextPage());
                });
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
        final View addAll = header.findViewById(R.id.musicListAddAll);
        addAll.setVisibility(View.VISIBLE);
        addAll.setOnClickListener(v -> addAllToPlaylist(songs));
        addSongRows(box, songs);
    }
}
