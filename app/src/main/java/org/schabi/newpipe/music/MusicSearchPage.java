package org.schabi.newpipe.music;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * La ricerca su YouTube Music: ricerche recenti, suggerimenti e poi tutto in una schermata,
 * prima gli artisti, poi brani, album e playlist.
 */
public class MusicSearchPage extends MusicPage {
    private static final long SUGGESTION_DELAY_MILLIS = 300;
    private static final int SONGS_SHOWN = 6;
    private static final int MAX_ARTISTS = 12;
    private static final int MAX_ALBUMS = 15;
    private static final int MAX_PLAYLISTS = 15;
    // segna una ricerca andata male, per distinguerla da una che non ha trovato niente
    private static final List<InfoItem> FAILED = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loadSuggestions = this::showSuggestions;
    private EditText field;
    private View clear;
    private LinearLayout results;
    private String lastQuery = "";
    private boolean showingResults = false;
    private Disposable suggestions;

    @Override
    protected void build() {
        final View bar = inflater().inflate(R.layout.music_search_bar, content, false);
        content.addView(bar);
        bar.findViewById(R.id.musicSearchBack).setOnClickListener(v -> host().goBack());
        field = bar.findViewById(R.id.musicSearchField);
        clear = bar.findViewById(R.id.musicSearchClear);
        clear.setOnClickListener(v -> {
            field.setText("");
            field.requestFocus();
            keyboard().showSoftInput(field, 0);
        });
        field.setOnEditorActionListener((final TextView v, final int action,
                                         final KeyEvent event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || event != null) {
                runSearch();
                return true;
            }
            return false;
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count,
                                          final int after) {
                // non serve
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before,
                                      final int count) {
                // non serve
            }

            @Override
            public void afterTextChanged(final Editable s) {
                clear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                onQueryChanged();
            }
        });
        results = new LinearLayout(requireContext());
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, dp(4), 0, 0);
        content.addView(results);
        showRecentSearches();
        field.requestFocus();
        field.post(() -> keyboard().showSoftInput(field, 0));
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(loadSuggestions);
        disposeSuggestions();
        super.onDestroyView();
    }

    private InputMethodManager keyboard() {
        return (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    //region Recenti e suggerimenti
    private void disposeSuggestions() {
        if (suggestions != null) {
            suggestions.dispose();
            suggestions = null;
        }
    }

    private void onQueryChanged() {
        handler.removeCallbacks(loadSuggestions);
        final String text = field.getText().toString().trim();
        if (text.equals(lastQuery) && showingResults) {
            return;
        }
        showingResults = false;
        if (text.isEmpty()) {
            disposeSuggestions();
            showRecentSearches();
        } else {
            handler.postDelayed(loadSuggestions, SUGGESTION_DELAY_MILLIS);
        }
    }

    private void showRecentSearches() {
        disposables.clear();
        results.removeAllViews();
        final List<String> searches = MusicStore.searches(requireContext());
        if (searches.isEmpty()) {
            return;
        }
        addSection(results, getString(R.string.vm_recent_searches));
        for (final String search : searches) {
            results.addView(textRow(search, R.drawable.ic_history, v -> searchFor(search), v -> {
                MusicStore.removeSearch(requireContext(), search);
                showRecentSearches();
            }));
        }
    }

    private void showSuggestions() {
        final String text = field.getText().toString().trim();
        disposeSuggestions();
        if (text.isEmpty()) {
            return;
        }
        suggestions = ExtractorHelper.suggestionsFor(YOUTUBE, text)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    if (showingResults) {
                        return;
                    }
                    disposables.clear();
                    results.removeAllViews();
                    for (final String suggestion : list) {
                        results.addView(textRow(suggestion, R.drawable.ic_search,
                                v -> searchFor(suggestion), null));
                    }
                }, error -> { /* senza suggerimenti si cerca lo stesso */ });
    }

    private View textRow(final String text, final int icon, final View.OnClickListener click,
                         final View.OnClickListener remove) {
        final View row = inflater().inflate(R.layout.music_text_row, results, false);
        ((TextView) row.findViewById(R.id.musicTextRowTitle)).setText(text);
        ((ImageView) row.findViewById(R.id.musicTextRowIcon)).setImageResource(icon);
        row.setOnClickListener(click);
        final View removeButton = row.findViewById(R.id.musicTextRowRemove);
        removeButton.setVisibility(remove == null ? View.GONE : View.VISIBLE);
        removeButton.setOnClickListener(remove);
        return row;
    }

    private void searchFor(final String text) {
        lastQuery = text;
        showingResults = true;
        field.setText(text);
        field.setSelection(text.length());
        runSearch();
    }
    //endregion

    //region Risultati
    private Single<List<InfoItem>> searchOrFail(final String query, final String filter) {
        return search(query, filter).onErrorReturnItem(FAILED);
    }

    private void runSearch() {
        final String query = field.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }
        handler.removeCallbacks(loadSuggestions);
        disposeSuggestions();
        lastQuery = query;
        showingResults = true;
        MusicStore.addSearch(requireContext(), query);
        keyboard().hideSoftInputFromWindow(field.getWindowToken(), 0);
        disposables.clear();
        results.removeAllViews();
        final View spinner = addSpinner(results);
        // le quattro ricerche partono insieme, la pagina si mostra quando ci sono tutte
        disposables.add(Single.zip(
                        searchOrFail(query, FILTER_ARTISTS),
                        searchOrFail(query, FILTER_SONGS),
                        searchOrFail(query, FILTER_ALBUMS),
                        searchOrFail(query, FILTER_PLAYLISTS),
                        (artists, songs, albums, playlists) -> {
                            final List<List<InfoItem>> all = new ArrayList<>();
                            all.add(artists);
                            all.add(songs);
                            all.add(albums);
                            all.add(playlists);
                            return all;
                        })
                .subscribe(all -> {
                    results.removeView(spinner);
                    show(all.get(0), onlySongs(all.get(1)), all.get(2), all.get(3));
                    if (results.getChildCount() == 0) {
                        final boolean failed = all.get(0) == FAILED && all.get(1) == FAILED
                                && all.get(2) == FAILED && all.get(3) == FAILED;
                        addMessage(results, failed ? R.string.vm_load_error
                                : R.string.vm_nothing_found, failed ? this::runSearch : null);
                    }
                }, error -> {
                    results.removeView(spinner);
                    addMessage(results, R.string.vm_load_error, this::runSearch);
                }));
    }

    private void show(final List<InfoItem> artistItems, final List<StreamInfoItem> songs,
                      final List<InfoItem> albums, final List<InfoItem> playlists) {
        final List<ChannelInfoItem> artists = new ArrayList<>();
        for (final InfoItem item : artistItems) {
            if (item instanceof ChannelInfoItem) {
                artists.add((ChannelInfoItem) item);
            }
        }
        if (!artists.isEmpty()) {
            addTopResult(artists.get(0), songs);
            if (artists.size() > 1) {
                addSection(results, getString(R.string.vm_chip_artists));
                addItemStrip(results, artists.subList(1, artists.size()), MAX_ARTISTS);
            }
        }
        if (!songs.isEmpty()) {
            addSection(results, getString(R.string.vm_chip_songs));
            addSongs(songs);
        }
        final LinearLayout albumsBox = new LinearLayout(requireContext());
        albumsBox.setOrientation(LinearLayout.VERTICAL);
        addSection(albumsBox, getString(R.string.vm_chip_albums));
        if (addItemStrip(albumsBox, albums, MAX_ALBUMS) > 0) {
            results.addView(albumsBox);
        }
        final LinearLayout playlistsBox = new LinearLayout(requireContext());
        playlistsBox.setOrientation(LinearLayout.VERTICAL);
        addSection(playlistsBox, getString(R.string.vm_chip_playlists));
        if (addItemStrip(playlistsBox, playlists, MAX_PLAYLISTS) > 0) {
            results.addView(playlistsBox);
        }
    }

    // L'artista in primo piano: toccandolo si apre il profilo, il tasto fa partire i suoi brani.
    private void addTopResult(@NonNull final ChannelInfoItem artist,
                              @NonNull final List<StreamInfoItem> songs) {
        final View card = inflater().inflate(R.layout.music_top_result, results, false);
        ((TextView) card.findViewById(R.id.musicTopName)).setText(artist.getName());
        String info = getString(R.string.vm_artist);
        if (artist.getSubscriberCount() >= 0) {
            info += " · " + Localization.shortSubscriberCount(requireContext(),
                    artist.getSubscriberCount());
        }
        ((TextView) card.findViewById(R.id.musicTopInfo)).setText(info);
        loadCover(card.findViewById(R.id.musicTopCover), artist.getThumbnails());
        card.setOnClickListener(v -> host().openPage(MusicArtistPage.create(artist.getName(),
                ImageStrategy.INSTANCE.imageListToDbUrl(artist.getThumbnails()))));
        final List<StreamInfoItem> theirs = new ArrayList<>();
        final String name = artist.getName() == null ? ""
                : artist.getName().toLowerCase(Locale.ROOT);
        for (final StreamInfoItem song : songs) {
            final String uploader = song.getUploaderName();
            if (!name.isEmpty() && uploader != null
                    && uploader.toLowerCase(Locale.ROOT).contains(name)) {
                theirs.add(song);
            }
        }
        final View play = card.findViewById(R.id.musicTopPlay);
        play.setVisibility(theirs.isEmpty() ? View.GONE : View.VISIBLE);
        play.setOnClickListener(v -> host().playSongs(theirs, 0, false));
        results.addView(card);
    }

    // i primi brani subito, gli altri con "Mostra tutti"
    private void addSongs(final List<StreamInfoItem> songs) {
        final int rowsStart = results.getChildCount();
        addSongRows(results, songs);
        if (songs.size() <= SONGS_SHOWN) {
            return;
        }
        for (int i = rowsStart + SONGS_SHOWN; i < results.getChildCount(); i++) {
            results.getChildAt(i).setVisibility(View.GONE);
        }
        final TextView more = (TextView) inflater()
                .inflate(R.layout.music_show_more, results, false);
        more.setOnClickListener(v -> {
            for (int i = rowsStart; i < results.indexOfChild(more); i++) {
                results.getChildAt(i).setVisibility(View.VISIBLE);
            }
            results.removeView(more);
        });
        results.addView(more);
    }
    //endregion
}
