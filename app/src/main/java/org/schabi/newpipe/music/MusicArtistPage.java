package org.schabi.newpipe.music;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

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

/** Il profilo di un artista: foto grande, i 10 brani più popolari, album, playlist e info. */
public class MusicArtistPage extends MusicPage {
    private static final String ARG_NAME = "name";
    private static final String ARG_COVER = "cover";
    private static final int TOP_SONGS = 10;
    private static final int MAX_ALBUMS = 15;
    private static final int MAX_PLAYLISTS = 15;
    private static final int COLLAPSED_LINES = 4;

    private final List<StreamInfoItem> songs = new ArrayList<>();
    private View hero;
    private LinearLayout songsBox;
    private LinearLayout albumsBox;
    private LinearLayout playlistsBox;
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
        // la radio parte dal brano più popolare
        hero.findViewById(R.id.musicArtistRadio).setOnClickListener(v -> {
            if (!songs.isEmpty()) {
                MusicActions.radio((AppCompatActivity) requireActivity(), host(),
                        songs.get(0), disposables);
            }
        });
        numbered = true;
        songsBox = addBox();
        albumsBox = addBox();
        playlistsBox = addBox();
        infoBox = addBox();
        loadSongs();
        loadAlbums();
        loadPlaylists();
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
        showFollow(button, MusicStore.isFollowed(requireContext(), name));
        button.setOnClickListener(v -> showFollow(button,
                MusicStore.toggleFollow(requireContext(), name, cover)));
    }

    // seguito: la spunta davanti
    private void showFollow(final TextView button, final boolean followed) {
        button.setText(followed ? R.string.vm_following : R.string.vm_follow);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(
                followed ? R.drawable.ic_done : 0, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(button,
                ContextCompat.getColorStateList(requireContext(), R.color.vm_text));
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
        addSongRows(songsBox, new ArrayList<>(songs.subList(0,
                Math.min(TOP_SONGS, songs.size()))));
    }
    //endregion

    //region Album e playlist
    private void loadAlbums() {
        disposables.add(search(name, FILTER_ALBUMS).subscribe(items -> {
            final List<InfoItem> albums = new ArrayList<>();
            for (final InfoItem item : items) {
                if (item instanceof PlaylistInfoItem
                        && isTheirs(((PlaylistInfoItem) item).getUploaderName())) {
                    albums.add(item);
                }
            }
            addStripSection(albumsBox, R.string.vm_chip_albums, albums, MAX_ALBUMS);
        }, error -> { /* senza album il profilo resta valido */ }));
    }

    // le playlist che YouTube Music trova col suo nome: sono quelle con i suoi brani
    private void loadPlaylists() {
        disposables.add(search(name, FILTER_PLAYLISTS).subscribe(items ->
                        addStripSection(playlistsBox, R.string.vm_chip_playlists, items,
                                MAX_PLAYLISTS),
                error -> { /* senza playlist il profilo resta valido */ }));
    }

    private void addStripSection(final LinearLayout box, final int title,
                                 final List<InfoItem> items, final int max) {
        addSection(box, getString(title));
        if (addItemStrip(box, items, max) == 0) {
            box.removeAllViews();
        }
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
