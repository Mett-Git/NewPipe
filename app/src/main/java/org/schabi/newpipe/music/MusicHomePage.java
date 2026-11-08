package org.schabi.newpipe.music;

import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;

/** La Home: ascoltati di recente, tendenze e altro dell'ultimo artista ascoltato. */
public class MusicHomePage extends MusicPage {
    private static final int RECENT_ROWS = 3;
    private static final int FREQUENT_TILES = 6;
    private static final int MIN_FREQUENT_TILES = 4;
    private static final int MAX_MIXES = 5;
    private static final int MAX_FOLLOWED_SEARCHES = 3;
    private static final int SONGS_PER_ARTIST = 5;
    private static final String DEFAULT_SEED = "musica italiana";
    private static final int[] CHIPS = {
            R.string.vm_chip_all, R.string.vm_chip_songs,
            R.string.vm_chip_albums, R.string.vm_chip_artists};

    private LinearLayout strip;
    private LinearLayout body;
    private LinearLayout resumeBox;
    private int selectedChip = 0;

    @Override
    protected void build() {
        final View title = inflater().inflate(R.layout.music_home_title, content, false);
        content.addView(title);
        title.findViewById(R.id.musicPageSearch)
                .setOnClickListener(v -> host().openPage(new MusicSearchPage()));
        final HorizontalScrollView chips = new HorizontalScrollView(requireContext());
        chips.setHorizontalScrollBarEnabled(false);
        chips.setClipToPadding(false);
        chips.setPadding(dp(12), 0, dp(12), 0);
        strip = new LinearLayout(requireContext());
        for (int i = 0; i < CHIPS.length; i++) {
            final int index = i;
            final TextView chip = (TextView) inflater()
                    .inflate(R.layout.music_chip, strip, false);
            chip.setText(CHIPS[i]);
            chip.setOnClickListener(v -> select(index));
            strip.addView(chip);
        }
        chips.addView(strip);
        content.addView(chips);
        body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        content.addView(body);
        select(selectedChip);
    }

    private void select(final int index) {
        selectedChip = index;
        for (int i = 0; i < strip.getChildCount(); i++) {
            strip.getChildAt(i).setSelected(i == index);
        }
        disposables.clear();
        body.removeAllViews();
        resumeBox = null;
        switch (index) {
            case 1:
                showSearch(FILTER_SONGS, R.string.vm_for_you_songs);
                break;
            case 2:
                showSearch(FILTER_ALBUMS, R.string.vm_for_you_albums);
                break;
            case 3:
                showSearch(FILTER_ARTISTS, R.string.vm_for_you_artists);
                break;
            default:
                showAll();
                break;
        }
    }

    // l'ultimo artista ascoltato; all'inizio, quando non c'è, la musica italiana
    private String seed() {
        final List<StreamInfoItem> recent = MusicStore.recent(requireContext());
        if (!recent.isEmpty() && recent.get(0).getUploaderName() != null
                && !recent.get(0).getUploaderName().isEmpty()) {
            return recent.get(0).getUploaderName();
        }
        return DEFAULT_SEED;
    }

    // le mattonelle in alto, come su Spotify: si tocca e riparte
    private void addFrequentGrid() {
        final List<StreamInfoItem> songs =
                MusicStore.topSongs(requireContext(), FREQUENT_TILES);
        if (songs.size() < MIN_FREQUENT_TILES) {
            return;
        }
        addSection(body, getString(R.string.vm_frequent));
        final int count = songs.size() - songs.size() % 2;
        LinearLayout pair = null;
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                pair = new LinearLayout(requireContext());
                pair.setPadding(dp(8), 0, dp(8), 0);
                body.addView(pair);
            }
            final StreamInfoItem song = songs.get(i);
            final int index = i;
            final View tile = inflater().inflate(R.layout.music_song_tile, pair, false);
            ((TextView) tile.findViewById(R.id.musicTileTitle)).setText(song.getName());
            loadCover(tile.findViewById(R.id.musicTileCover), song.getThumbnails());
            tile.setOnClickListener(v -> host().playSongs(songs, index, false));
            pair.addView(tile);
        }
    }

    // un mix per ogni artista più ascoltato: i suoi brani più quelli simili
    private void addMixes() {
        final List<MusicStore.TopArtist> artists =
                MusicStore.topArtists(requireContext(), MAX_MIXES);
        if (artists.isEmpty()) {
            return;
        }
        addSection(body, getString(R.string.vm_your_mixes));
        final HorizontalScrollView scroll = new HorizontalScrollView(requireContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(12), 0, dp(12), 0);
        final LinearLayout mixes = new LinearLayout(requireContext());
        for (final MusicStore.TopArtist artist : artists) {
            final String title = getString(R.string.vm_mix_of, artist.getName());
            final View card = inflater().inflate(R.layout.music_card, mixes, false);
            ((TextView) card.findViewById(R.id.musicCardTitle)).setText(title);
            ((TextView) card.findViewById(R.id.musicCardSubtitle)).setText(R.string.vm_mix_hint);
            loadCover(card.findViewById(R.id.musicCardCover),
                    artist.getSong().getThumbnails());
            card.setOnClickListener(v -> host().openPage(
                    MusicListPage.mix(title, artist.getName(), artist.getSong())));
            mixes.addView(card);
        }
        scroll.addView(mixes);
        body.addView(scroll);
    }

    private void addFollowedArtists() {
        final List<MusicStore.FollowedArtist> artists = MusicStore.followed(requireContext());
        if (artists.isEmpty()) {
            return;
        }
        addSection(body, getString(R.string.vm_from_your_artists));
        final List<Single<List<StreamInfoItem>>> searches = new ArrayList<>();
        for (int i = artists.size() - 1; i >= 0 && searches.size() < MAX_FOLLOWED_SEARCHES; i--) {
            searches.add(search(artists.get(i).getName(), FILTER_SONGS)
                    .map(MusicPage::onlySongs)
                    .onErrorReturnItem(new ArrayList<>()));
        }
        loadCarousel(box(), Single.zip(searches, results -> {
            final List<StreamInfoItem> mixed = new ArrayList<>();
            for (int position = 0; position < SONGS_PER_ARTIST; position++) {
                for (final Object result : results) {
                    final List<?> songs = (List<?>) result;
                    if (position < songs.size()) {
                        mixed.add((StreamInfoItem) songs.get(position));
                    }
                }
            }
            return mixed;
        }));
    }

    @Override
    public void refreshNowPlaying() {
        super.refreshNowPlaying();
        // se la musica sta già andando non c'è niente da riprendere
        if (resumeBox != null && host().nowPlayingUrl() != null) {
            resumeBox.setVisibility(View.GONE);
        }
    }

    private void addResumeRow() {
        final MusicStore.Session session = MusicStore.session(requireContext());
        if (session == null || host().nowPlayingUrl() != null) {
            return;
        }
        final StreamInfoItem song = session.getSongs().get(session.getIndex());
        resumeBox = box();
        addSection(resumeBox, getString(R.string.vm_continue));
        resumeBox.addView(linkRow(resumeBox, song.getName(), song.getUploaderName(),
                song.getThumbnails(), v -> {
                    resumeBox.setVisibility(View.GONE);
                    host().resumeSession(session);
                }));
    }

    private void showAll() {
        addResumeRow();
        addFrequentGrid();
        final List<StreamInfoItem> recent = MusicStore.recent(requireContext());
        if (!recent.isEmpty()) {
            addSection(body, getString(R.string.vm_recent));
            addSongRows(body, recent.subList(0, Math.min(RECENT_ROWS, recent.size())));
        }
        addMixes();
        addSection(body, getString(R.string.vm_trending));
        loadCarousel(box(), trending());
        addFollowedArtists();
        final String artist = seed();
        if (!recent.isEmpty() && !DEFAULT_SEED.equals(artist)) {
            addSection(body, getString(R.string.vm_more_of, artist));
            loadCarousel(box(), search(artist, FILTER_SONGS).map(MusicPage::onlySongs));
        }
    }

    private void showSearch(final String filter, @StringRes final int title) {
        addSection(body, getString(title));
        final LinearLayout resultBox = box();
        final View spinner = addSpinner(resultBox);
        disposables.add(search(seed(), filter).subscribe(items -> {
            resultBox.removeView(spinner);
            final List<StreamInfoItem> songs = onlySongs(items);
            if (!songs.isEmpty()) {
                addSongRows(resultBox, songs);
            } else {
                addItemRows(resultBox, items);
            }
        }, error -> {
            resultBox.removeView(spinner);
            addMessage(resultBox, R.string.vm_load_error, () -> select(selectedChip));
        }));
    }

    private LinearLayout box() {
        final LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        body.addView(box);
        return box;
    }

    private void loadCarousel(final LinearLayout box,
                              final Single<List<StreamInfoItem>> source) {
        final View spinner = addSpinner(box);
        disposables.add(source.subscribe(songs -> {
            box.removeView(spinner);
            if (songs.isEmpty()) {
                addMessage(box, R.string.vm_nothing_found, null);
            } else {
                addCarousel(box, songs);
            }
        }, error -> {
            box.removeView(spinner);
            addMessage(box, R.string.vm_load_error, () -> {
                box.removeAllViews();
                loadCarousel(box, source);
            });
        }));
    }
}
