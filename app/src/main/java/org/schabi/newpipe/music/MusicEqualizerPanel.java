package org.schabi.newpipe.music;

import android.content.Context;
import android.content.res.ColorStateList;
import android.media.audiofx.Equalizer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.DeviceUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Il pannello dell'equalizzatore: interruttore, regolazioni pronte, bande e bassi. */
public final class MusicEqualizerPanel {
    private final Context context;
    private final List<SeekBar> bands = new ArrayList<>();
    private LinearLayout column;

    /**
     * @param body dove mostrarlo
     */
    public MusicEqualizerPanel(@NonNull final ViewGroup body) {
        context = body.getContext();
        body.removeAllViews();
        final Equalizer equalizer = MusicEqualizer.equalizer();
        final NestedScrollView scroll = new NestedScrollView(context);
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(18), dp(4), dp(18), dp(40));
        scroll.addView(column);
        body.addView(scroll);
        if (equalizer == null) {
            final TextView message = label(context.getString(R.string.vm_equalizer_none));
            message.setGravity(Gravity.CENTER);
            message.setPadding(dp(14), dp(60), dp(14), 0);
            column.addView(message);
            return;
        }
        addSwitch();
        addPresets(equalizer);
        addBands(equalizer);
        if (MusicEqualizer.hasBassBoost()) {
            column.addView(sectionTitle(context.getString(R.string.vm_bass)));
            final SeekBar bass = slider(1000, MusicEqualizer.bass(context));
            bass.setOnSeekBarChangeListener(new SliderListener() {
                @Override
                public void onProgressChanged(final SeekBar seekBar, final int progress,
                                              final boolean fromUser) {
                    if (fromUser) {
                        MusicEqualizer.setBass(context, progress);
                    }
                }
            });
            column.addView(bass);
        }
    }

    private int dp(final int value) {
        return DeviceUtils.dpToPx(value, context);
    }

    private void addSwitch() {
        final SwitchCompat toggle = new SwitchCompat(context);
        toggle.setText(R.string.vm_equalizer_on);
        toggle.setTextColor(ContextCompat.getColor(context, R.color.vm_text));
        toggle.setTextSize(16);
        toggle.setMinHeight(dp(52));
        toggle.setChecked(MusicEqualizer.isEnabled(context));
        toggle.setOnCheckedChangeListener((button, checked) ->
                MusicEqualizer.setEnabled(context, checked));
        column.addView(toggle);
    }

    private void addPresets(final Equalizer equalizer) {
        if (equalizer.getNumberOfPresets() == 0) {
            return;
        }
        final HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout strip = new LinearLayout(context);
        final LayoutInflater inflater = LayoutInflater.from(context);
        for (short i = 0; i < equalizer.getNumberOfPresets(); i++) {
            final short preset = i;
            final TextView chip = (TextView) inflater.inflate(R.layout.music_chip, strip, false);
            chip.setText(equalizer.getPresetName(i));
            chip.setOnClickListener(v -> {
                MusicEqualizer.usePreset(context, preset);
                for (int c = 0; c < strip.getChildCount(); c++) {
                    strip.getChildAt(c).setSelected(strip.getChildAt(c) == v);
                }
                final short minimum = equalizer.getBandLevelRange()[0];
                for (short band = 0; band < bands.size(); band++) {
                    bands.get(band).setProgress(equalizer.getBandLevel(band) - minimum);
                }
            });
            strip.addView(chip);
        }
        scroll.addView(strip);
        column.addView(scroll);
    }

    private void addBands(final Equalizer equalizer) {
        final short minimum = equalizer.getBandLevelRange()[0];
        final short maximum = equalizer.getBandLevelRange()[1];
        column.addView(sectionTitle(context.getString(R.string.vm_equalizer)));
        for (short i = 0; i < equalizer.getNumberOfBands(); i++) {
            final short band = i;
            column.addView(label(frequency(equalizer.getCenterFreq(band))));
            final SeekBar slider = slider(maximum - minimum,
                    equalizer.getBandLevel(band) - minimum);
            slider.setOnSeekBarChangeListener(new SliderListener() {
                @Override
                public void onProgressChanged(final SeekBar seekBar, final int progress,
                                              final boolean fromUser) {
                    if (fromUser) {
                        MusicEqualizer.setBand(context, band, (short) (progress + minimum));
                    }
                }
            });
            bands.add(slider);
            column.addView(slider);
        }
    }

    private static String frequency(final int milliHertz) {
        final int hertz = milliHertz / 1000;
        return hertz >= 1000
                ? String.format(Locale.ROOT, "%.1f kHz", hertz / 1000f).replace(".0", "")
                : hertz + " Hz";
    }

    private TextView sectionTitle(final String text) {
        final TextView title = (TextView) LayoutInflater.from(context)
                .inflate(R.layout.music_section, column, false);
        title.setText(text);
        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) title.getLayoutParams();
        params.setMarginStart(0);
        return title;
    }

    private TextView label(final String text) {
        final TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(context, R.color.vm_text_soft));
        view.setTextSize(13);
        return view;
    }

    private SeekBar slider(final int max, final int progress) {
        final SeekBar slider = new SeekBar(context);
        final ColorStateList accent = ContextCompat.getColorStateList(context, R.color.vm_accent);
        slider.setMax(max);
        slider.setProgress(progress);
        slider.setProgressTintList(accent);
        slider.setThumbTintList(accent);
        slider.setProgressBackgroundTintList(
                ContextCompat.getColorStateList(context, R.color.vm_glass_stroke));
        slider.setSplitTrack(false);
        slider.setPadding(dp(4), dp(10), dp(4), dp(14));
        return slider;
    }

    private abstract static class SliderListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(final SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(final SeekBar seekBar) {
        }
    }
}
