package org.schabi.newpipe.music;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;

/** La ricerca: su YouTube Music, per brani, video, album, playlist o artisti. */
public class MusicSearchPage extends MusicPage {
    private static final int[] CHIPS = {
            R.string.vm_chip_songs, R.string.vm_chip_videos, R.string.vm_chip_albums,
            R.string.vm_chip_playlists, R.string.vm_chip_artists};
    private static final String[] FILTERS = {
            FILTER_SONGS, FILTER_VIDEOS, FILTER_ALBUMS, FILTER_PLAYLISTS, FILTER_ARTISTS};

    private EditText field;
    private LinearLayout strip;
    private LinearLayout results;
    private int selectedChip = 0;

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
        field.requestFocus();
        field.post(() -> keyboard().showSoftInput(field, 0));
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

    private void runSearch() {
        final String query = field.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }
        keyboard().hideSoftInputFromWindow(field.getWindowToken(), 0);
        disposables.clear();
        results.removeAllViews();
        final View spinner = addSpinner(results);
        disposables.add(search(query, FILTERS[selectedChip]).subscribe(items -> {
            results.removeView(spinner);
            show(items);
        }, error -> {
            results.removeView(spinner);
            addMessage(results, R.string.vm_load_error, this::runSearch);
        }));
    }

    private void show(final List<InfoItem> items) {
        final List<StreamInfoItem> songs = onlySongs(items);
        if (!songs.isEmpty()) {
            addSongRows(results, songs);
        } else if (!items.isEmpty()) {
            addItemRows(results, items);
        } else {
            addMessage(results, R.string.vm_nothing_found, null);
        }
    }
}
