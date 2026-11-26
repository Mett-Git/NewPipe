package org.schabi.newpipe.fragments.detail;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.util.DeviceUtils;

import java.util.function.Supplier;

/**
 * L'effetto luce di YouTube ("modalità ambient"): dietro la pagina del video una sfumatura del
 * colore di quello che si vede, che cambia piano piano col video. Il colore si prende ogni
 * secondo e mezzo da un fotogramma rimpicciolito (PixelCopy, Android 7+).
 */
final class AmbientGlow {
    private static final long EVERY_MILLIS = 1500;
    private static final long FADE_MILLIS = 1200;

    private final View glow;
    private final GradientDrawable gradient;
    private final Supplier<SurfaceView> surface;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Bitmap sample = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888);
    private int current = Color.TRANSPARENT;
    @Nullable
    private ValueAnimator fade;
    private boolean running = false;

    /**
     * @param root    la radice della pagina del video (la luce va sotto a tutto)
     * @param surface dove si disegna il video adesso (null = nessun video)
     */
    AmbientGlow(@NonNull final ViewGroup root, @NonNull final Supplier<SurfaceView> surface) {
        this.surface = surface;
        gradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT});
        glow = new View(root.getContext());
        glow.setBackground(gradient);
        glow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(glow, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                DeviceUtils.dpToPx(560, root.getContext())));
    }

    void start() {
        if (running || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        running = true;
        handler.post(tick);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            handler.postDelayed(this, EVERY_MILLIS);
            final SurfaceView view = surface.get();
            if (view == null || !view.isShown() || view.getHolder().getSurface() == null
                    || !view.getHolder().getSurface().isValid()) {
                fadeTo(Color.TRANSPARENT);
                return;
            }
            try {
                PixelCopy.request(view, sample, result -> {
                    if (result == PixelCopy.SUCCESS) {
                        fadeTo(average(sample));
                    }
                }, handler);
            } catch (final IllegalArgumentException e) {
                // superficie non pronta: si riprova al prossimo giro
            }
        }
    };

    // il colore medio del fotogramma, un po' più vivo e più scuro (sennò copre i testi)
    private static int average(@NonNull final Bitmap bitmap) {
        long r = 0;
        long g = 0;
        long b = 0;
        final int n = bitmap.getWidth() * bitmap.getHeight();
        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                final int c = bitmap.getPixel(x, y);
                r += Color.red(c);
                g += Color.green(c);
                b += Color.blue(c);
            }
        }
        final float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (b / n), hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.35f);
        hsv[2] = Math.min(0.75f, Math.max(0.25f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    private void fadeTo(final int color) {
        if (color == current) {
            return;
        }
        if (fade != null) {
            fade.cancel();
        }
        final int from = current;
        current = color;
        fade = ValueAnimator.ofObject(new ArgbEvaluator(), from, color);
        fade.setDuration(FADE_MILLIS);
        fade.addUpdateListener(a -> {
            final int c = (int) a.getAnimatedValue();
            gradient.setColors(new int[]{withAlpha(c, 0x66), withAlpha(c, 0x26),
                    Color.TRANSPARENT});
        });
        fade.start();
    }

    private static int withAlpha(final int color, final int alpha) {
        return Color.argb(Color.alpha(color) == 0 ? 0 : alpha,
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
