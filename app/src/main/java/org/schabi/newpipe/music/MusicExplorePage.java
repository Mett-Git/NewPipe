package org.schabi.newpipe.music;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.schabi.newpipe.R;

/** Esplora: i generi e gli stati d'animo da toccare, poi le tendenze. */
public class MusicExplorePage extends MusicPage {
    private static final int[] MOODS = {
            R.string.vm_mood_relax, R.string.vm_mood_workout, R.string.vm_mood_party,
            R.string.vm_mood_focus, R.string.vm_mood_happy, R.string.vm_mood_love,
            R.string.vm_mood_80s, R.string.vm_mood_italian};
    private static final int[][] COLORS = {
            {0xff2b6c8f, 0xff173a55}, {0xffb6442f, 0xff5e1f24}, {0xff8d3ec2, 0xff401d66},
            {0xff2c7f6d, 0xff143f3c}, {0xffc78a2c, 0xff6a3f18}, {0xffb73a6a, 0xff5a1b3a},
            {0xff4a53b8, 0xff23285f}, {0xff3d8a4d, 0xff8f3535}};

    @Override
    protected void build() {
        content.addView(pageTitle(getString(R.string.vm_explore)));
        addSection(content, getString(R.string.vm_moods));
        LinearLayout pair = null;
        for (int i = 0; i < MOODS.length; i++) {
            if (i % 2 == 0) {
                pair = new LinearLayout(requireContext());
                pair.setPadding(dp(8), 0, dp(8), 0);
                content.addView(pair);
            }
            pair.addView(tile(pair, i));
        }
        addSection(content, getString(R.string.vm_trending));
        loadTrending();
    }

    private View tile(final LinearLayout parent, final int index) {
        final TextView tile = (TextView) inflater()
                .inflate(R.layout.music_tile, parent, false);
        final String name = getString(MOODS[index]);
        tile.setText(name);
        final GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, COLORS[index]);
        background.setCornerRadius(dp(14));
        tile.setBackground(background);
        tile.setOnClickListener(v -> host().openPage(MusicListPage.mood(name)));
        return tile;
    }

    private void loadTrending() {
        final LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        content.addView(box);
        final View spinner = addSpinner(box);
        disposables.add(trending().subscribe(songs -> {
            box.removeView(spinner);
            addSongRows(box, songs);
        }, error -> {
            box.removeView(spinner);
            addMessage(box, R.string.vm_load_error, null);
        }));
    }
}
