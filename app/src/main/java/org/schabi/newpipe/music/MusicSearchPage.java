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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ExtractorHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** La ricerca su YouTube Music: ricerche recenti, suggerimenti e risultati senza fine. */
public class MusicSearchPage extends MusicPage {
    private static final long SUGGESTION_DELAY_MILLIS = 300;
    private static final int[] CHIPS = {
            R.string.vm_chip_songs, R.string.vm_chip_videos, R.string.vm_chip_albums,
            R.string.vm_chip_playlists, R.string.vm_chip_artists};
    private static final String[] FILTERS = {
            FILTER_SONGS, FILTER_VIDEOS, FILTER_ALBUMS, FILTER_PLAYLISTS, FILTER_ARTISTS};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loadSuggestions = this::showSuggestions;
    private final List<StreamInfoItem> shownSongs = new ArrayList<>();
    private EditText field;
    private LinearLayout strip;
    private LinearLayout results;
    private int selectedChip = 0;
    private String lastQuery = "";
    private boolean showingResults = false;
    private boolean loadingMore = false;
    private Page nextPage;
    private Disposable suggestions;

    @Override
    protected void build() {
        final View bar = inflater().inflate(R.layout.music_search_bar, content, false);
        content.addView(bar);
        bar.findViewById(R.id.musicSearchBack).setOnClickListener(v -> host().goBack());
        field = bar.findViewById(R.id.musicSearchField);
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
                onQueryChanged();
            }
        });
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
            chip.setSelected(i == selectedChip);
            chip.setOnClickListener(v -> select(index));
            strip.addView(chip);
        }
        chips.addView(strip);
        content.addView(chips);
        results = new LinearLayout(requireContext());
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, dp(8), 0, 0);
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

    private void select(final int index) {
        selectedChip = index;
        for (int i = 0; i < strip.getChildCount(); i++) {
            strip.getChildAt(i).setSelected(i == index);
        }
        runSearch();
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
        shownSongs.clear();
        nextPage = null;
        loadingMore = false;
        final View spinner = addSpinner(results);
        disposables.add(ExtractorHelper.searchFor(YOUTUBE, query,
                        Collections.singletonList(FILTERS[selectedChip]), "")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    results.removeView(spinner);
                    nextPage = info.getNextPage();
                    show(new ArrayList<>(info.getRelatedItems()), true);
                }, error -> {
                    results.removeView(spinner);
                    addMessage(results, R.string.vm_load_error, this::runSearch);
                }));
    }

    private void show(final List<InfoItem> items, final boolean first) {
        final List<StreamInfoItem> songs = onlySongs(items);
        if (!songs.isEmpty()) {
            final int from = shownSongs.size();
            shownSongs.addAll(songs);
            addSongRows(results, shownSongs, from);
        } else if (!items.isEmpty()) {
            addItemRows(results, items);
        } else if (first) {
            addMessage(results, R.string.vm_nothing_found, null);
        }
    }

    @Override
    protected void onNearEnd() {
        if (!showingResults || loadingMore || !Page.isValid(nextPage)) {
            return;
        }
        loadingMore = true;
        final View spinner = addSpinner(results);
        disposables.add(ExtractorHelper.getMoreSearchItems(YOUTUBE, lastQuery,
                        Collections.singletonList(FILTERS[selectedChip]), "", nextPage)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(page -> {
                    results.removeView(spinner);
                    loadingMore = false;
                    nextPage = page.getNextPage();
                    show(new ArrayList<>(page.getItems()), false);
                }, error -> {
                    results.removeView(spinner);
                    loadingMore = false;
                    nextPage = null;
                }));
    }
    //endregion
}
