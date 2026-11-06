package org.schabi.newpipe.music;

import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.DeviceUtils;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Il testo del brano, preso da LRCLIB: titolo e artista partono verso quel servizio solo quando
 * si apre questo pannello. Se il testo è sincronizzato, la riga corrente si illumina e toccando
 * una riga si salta lì.
 */
public final class MusicLyricsPanel {
    private static final String SEARCH_URL = "https://lrclib.net/api/search?q=";
    private static final Pattern LRC_LINE =
            Pattern.compile("^\\[(\\d+):(\\d+)(?:[.:](\\d+))?](.*)$");
    // con più differenza di così i tempi del testo non valgono per questo video
    private static final double SYNC_TOLERANCE_SECONDS = 5;
    private static final long MANUAL_SCROLL_PAUSE_MILLIS = 4000;

    /** Cosa fare toccando una riga. */
    public interface SeekListener {
        /**
         * @param millis dove saltare
         */
        void seekTo(long millis);
    }

    private final Context context;
    private final ViewGroup body;
    private final SeekListener seekListener;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final List<Long> times = new ArrayList<>();
    private final List<TextView> rows = new ArrayList<>();
    private NestedScrollView scroll;
    private int currentRow = -1;
    private long lastTouch = 0;

    /**
     * @param body         dove mostrarlo
     * @param seekListener cosa fare toccando una riga
     */
    public MusicLyricsPanel(@NonNull final ViewGroup body,
                            @NonNull final SeekListener seekListener) {
        this.context = body.getContext();
        this.body = body;
        this.seekListener = seekListener;
    }

    /**
     * @param title    il titolo del brano
     * @param artist   chi lo canta
     * @param duration quanto dura, in secondi (0 se non si sa)
     */
    public void load(@Nullable final String title, @Nullable final String artist,
                     final long duration) {
        disposables.clear();
        times.clear();
        rows.clear();
        currentRow = -1;
        body.removeAllViews();
        final ProgressBar spinner = new ProgressBar(context);
        spinner.setIndeterminateTintList(
                ContextCompat.getColorStateList(context, R.color.vm_accent));
        final android.widget.FrameLayout.LayoutParams params =
                new android.widget.FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER);
        body.addView(spinner, params);
        disposables.add(Single.fromCallable(() -> find(title, artist, duration))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::show, error -> showMessage(R.string.vm_lyrics_error)));
    }

    /**
     * @param millis a che punto è il brano
     */
    public void onProgress(final long millis) {
        if (times.isEmpty()) {
            return;
        }
        int line = -1;
        for (int i = 0; i < times.size() && times.get(i) <= millis; i++) {
            line = i;
        }
        if (line == currentRow) {
            return;
        }
        if (currentRow >= 0) {
            rows.get(currentRow).setTextColor(
                    ContextCompat.getColor(context, R.color.vm_text_faint));
        }
        currentRow = line;
        if (line >= 0) {
            final TextView row = rows.get(line);
            row.setTextColor(ContextCompat.getColor(context, R.color.vm_text));
            if (SystemClock.uptimeMillis() - lastTouch > MANUAL_SCROLL_PAUSE_MILLIS) {
                scroll.smoothScrollTo(0, row.getTop() - scroll.getHeight() / 3);
            }
        }
    }

    /** Ferma quello che è in corso. */
    public void dispose() {
        disposables.clear();
    }

    private int dp(final int value) {
        return DeviceUtils.dpToPx(value, context);
    }

    private void showMessage(final int message) {
        body.removeAllViews();
        final TextView text = new TextView(context);
        text.setText(message);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(32), 0, dp(32), 0);
        text.setTextColor(ContextCompat.getColor(context, R.color.vm_text_soft));
        text.setTextSize(15);
        body.addView(text, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void show(@NonNull final Lyrics lyrics) {
        if (lyrics.synced.isEmpty() && lyrics.plain.isEmpty()) {
            showMessage(R.string.vm_lyrics_none);
            return;
        }
        body.removeAllViews();
        scroll = new NestedScrollView(context);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                lastTouch = SystemClock.uptimeMillis();
            }
            return false;
        });
        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(22), dp(12), dp(22), dp(48));
        scroll.addView(column);
        if (lyrics.synced.isEmpty()) {
            final TextView text = line(lyrics.plain, R.color.vm_text);
            text.setTextSize(19);
            text.setLineSpacing(dp(4), 1f);
            text.setTextIsSelectable(true);
            column.addView(text);
        } else {
            for (final LyricLine lyric : lyrics.synced) {
                final TextView row = line(lyric.text, R.color.vm_text_faint);
                row.setPadding(0, dp(9), 0, dp(9));
                row.setOnClickListener(v -> seekListener.seekTo(lyric.millis));
                times.add(lyric.millis);
                rows.add(row);
                column.addView(row);
            }
        }
        final TextView credit = line(context.getString(R.string.vm_lyrics_credit),
                R.color.vm_text_faint);
        credit.setTextSize(12);
        credit.setTypeface(null, android.graphics.Typeface.NORMAL);
        credit.setPadding(0, dp(28), 0, 0);
        column.addView(credit);
        body.addView(scroll);
    }

    private TextView line(final String content, final int color) {
        final TextView text = new TextView(context);
        text.setText(content);
        text.setTextColor(ContextCompat.getColor(context, color));
        text.setTextSize(23);
        text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }

    //region Ricerca
    private static Lyrics find(@Nullable final String rawTitle, @Nullable final String rawArtist,
                               final long duration) throws Exception {
        String title = clean(rawTitle);
        String artist = clean(rawArtist).replaceAll("(?i)\\s*-\\s*topic$", "")
                .replaceAll("VEVO$", "").trim();
        // i video musicali hanno spesso "Artista - Titolo" come titolo
        final int dash = title.indexOf(" - ");
        if (dash > 0) {
            artist = title.substring(0, dash).trim();
            title = title.substring(dash + 3).trim();
        }
        final String query = URLEncoder.encode((title + " " + artist).trim(), "UTF-8");
        final JSONArray results = new JSONArray(DownloaderImpl.getInstance()
                .get(SEARCH_URL + query).responseBody());
        final String wanted = normalize(title);
        JSONObject best = null;
        double bestDifference = Double.MAX_VALUE;
        for (int i = 0; i < results.length(); i++) {
            final JSONObject result = results.getJSONObject(i);
            final String name = normalize(result.optString("trackName"));
            // un testo sbagliato è peggio di nessun testo: il titolo deve corrispondere
            if (name.isEmpty() || wanted.isEmpty()
                    || (!name.contains(wanted) && !wanted.contains(name))
                    || result.optBoolean("instrumental")
                    || result.optString("plainLyrics").isEmpty()) {
                continue;
            }
            final double difference = duration > 0
                    ? Math.abs(result.optDouble("duration", 0) - duration) : 0;
            if (difference < bestDifference) {
                best = result;
                bestDifference = difference;
            }
        }
        if (best == null) {
            return new Lyrics("", new ArrayList<>());
        }
        final List<LyricLine> synced = bestDifference <= SYNC_TOLERANCE_SECONDS
                ? parse(best.optString("syncedLyrics")) : new ArrayList<>();
        return new Lyrics(best.optString("plainLyrics"), synced);
    }

    private static String clean(@Nullable final String text) {
        return text == null ? "" : text.replaceAll("\\(.*?\\)|\\[.*?]|\\|.*$", "").trim();
    }

    private static String normalize(@NonNull final String text) {
        return clean(text).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static List<LyricLine> parse(@NonNull final String lrc) {
        final List<LyricLine> lines = new ArrayList<>();
        for (final String line : lrc.split("\n")) {
            final Matcher matcher = LRC_LINE.matcher(line.trim());
            if (matcher.matches() && !matcher.group(4).trim().isEmpty()) {
                final String fraction = matcher.group(3);
                final long millis = Long.parseLong(matcher.group(1)) * 60000
                        + Long.parseLong(matcher.group(2)) * 1000
                        + (fraction == null ? 0
                        : (long) (Double.parseDouble("0." + fraction) * 1000));
                lines.add(new LyricLine(millis, matcher.group(4).trim()));
            }
        }
        return lines;
    }
    //endregion

    private static final class LyricLine {
        private final long millis;
        private final String text;

        LyricLine(final long millis, final String text) {
            this.millis = millis;
            this.text = text;
        }
    }

    private static final class Lyrics {
        private final String plain;
        private final List<LyricLine> synced;

        Lyrics(final String plain, final List<LyricLine> synced) {
            this.plain = plain;
            this.synced = synced;
        }
    }
}
