package org.schabi.newpipe.fragments.detail;

import android.graphics.Outline;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.views.MiniPlayerRoot;

/**
 * Il video ridotto come sul YouTube nuovo: una finestrella col solo video, sospesa in basso a
 * destra (o a sinistra, spostandola col dito). Non è una vista a parte: è la pagina del video
 * intera rimpicciolita verso l'angolo, ritagliata al solo video. Così trascinandola il video
 * scende e si rimpicciolisce intero, senza sparire a metà, e il video continua a scorrere.
 * Su Android prima del 7 una SurfaceView rimpicciolita non si disegna bene: lì resta la
 * striscia arrotondata di prima, sopra la barra.
 */
final class FloatingMiniPlayer implements MiniPlayerRoot.WindowDragListener {
    /** Larghezza della finestrella rispetto allo schermo. */
    private static final float WINDOW_WIDTH = 0.48f;

    private final View sheet;
    private final MiniPlayerRoot root;
    private final BottomSheetBehavior<?> behavior;
    private final View video;
    private final View overlay;
    private final View windowControls;
    private final View closeButton;
    private final View playButton;
    private final int stripHeight;
    private final float margin;
    private final float radius;
    private final boolean window = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    private int lift = 0;
    private float progress = 1f;
    private boolean onRight = true;
    private float dragX = 0f;

    /**
     * @param sheet          la pagina del video che scorre (fragment_player_holder)
     * @param root           la radice della pagina
     * @param behavior       chi fa scorrere la pagina
     * @param video          il riquadro del video in cima alla pagina
     * @param overlay        la striscia del mini-player di NewPipe
     * @param windowControls tasti della finestrella (chiudi e play/pausa al centro)
     * @param stripHeight    altezza della striscia
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    FloatingMiniPlayer(@NonNull final View sheet, @NonNull final MiniPlayerRoot root,
                       @NonNull final BottomSheetBehavior<?> behavior, @NonNull final View video,
                       @NonNull final View overlay, @NonNull final View windowControls,
                       final int stripHeight) {
        this.sheet = sheet;
        this.root = root;
        this.behavior = behavior;
        this.video = video;
        this.overlay = overlay;
        this.windowControls = windowControls;
        this.closeButton = windowControls.findViewById(R.id.mini_window_close);
        this.playButton = windowControls.findViewById(R.id.mini_window_play);
        this.stripHeight = stripHeight;
        margin = DeviceUtils.dpToPx(10, sheet.getContext());
        radius = DeviceUtils.dpToPx(window ? 12 : 16, sheet.getContext());
        sheet.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(final View view, final Outline outline) {
                final float closed = 1f - progress;
                if (window) {
                    // il solo video, poi sotto si scopre la pagina man mano che si allarga
                    final int visible = (int) (videoHeight()
                            + (view.getHeight() - videoHeight()) * progress * progress);
                    outline.setRoundRect(0, 0, view.getWidth(), visible,
                            radius / scale() * closed);
                } else {
                    final int side = (int) (margin * closed);
                    final int visible = (int) (stripHeight
                            + (view.getHeight() - stripHeight) * progress);
                    outline.setRoundRect(side, 0, view.getWidth() - side, visible,
                            radius * closed);
                }
            }
        });
        if (window) {
            root.setWindowDragListener(this);
            video.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (b - t != ob - ot) {
                    updatePeekHeight();
                    apply(progress);
                }
            });
        }
    }

    /** @return true se il video ridotto è la finestrella (false = striscia di NewPipe) */
    boolean isWindow() {
        return window;
    }

    /**
     * @return quanto è alto il video ridotto sullo schermo: la finestrella o la striscia
     */
    int miniHeight() {
        return window ? Math.round(videoHeight() * WINDOW_WIDTH) : stripHeight;
    }

    /**
     * @return quanto deve spuntare la pagina dal fondo quando è ridotta (il "peek" del
     *         BottomSheet): con la finestrella comprende lo spazio della barra sotto
     */
    int peekHeight() {
        return window ? miniHeight() + lift : stripHeight;
    }

    /**
     * @param pixels quanto tenere sollevato il video ridotto dal fondo dello schermo
     */
    void setLift(final int pixels) {
        lift = pixels;
        updatePeekHeight();
        apply(progress);
    }

    // con la finestrella la pagina ridotta deve spuntare quanto finestrella + barra
    private void updatePeekHeight() {
        if (window && behavior.getPeekHeight() > 0
                && behavior.getPeekHeight() != peekHeight()) {
            behavior.setPeekHeight(peekHeight());
        }
    }

    /**
     * @param slideOffset 0 = video ridotto, 1 = pagina intera
     */
    void apply(final float slideOffset) {
        progress = Math.max(0f, Math.min(1f, slideOffset));
        final float closed = 1f - progress;
        if (window) {
            final float scale = scale();
            sheet.setPivotX(0f);
            sheet.setPivotY(0f);
            sheet.setScaleX(scale);
            sheet.setScaleY(scale);
            sheet.setTranslationX((windowLeft() + dragX) * closed);
            sheet.setTranslationY(0f);
            overlay.setVisibility(View.GONE);
            showWindowControls(scale);
        } else {
            sheet.setTranslationY(-lift * closed);
        }
        sheet.setClipToOutline(progress < 1f);
        sheet.invalidateOutline();
        root.setTouchableHeight(progress == 0f ? (window ? videoHeight() : stripHeight) : 0);
    }

    // i tasti della finestrella: si vedono solo da ridotta, della loro grandezza normale
    private void showWindowControls(final float scale) {
        final boolean shown = progress < 0.15f;
        windowControls.setVisibility(shown ? View.VISIBLE : View.GONE);
        if (!shown) {
            return;
        }
        windowControls.setAlpha(1f - progress / 0.15f);
        if (windowControls.getLayoutParams().height != videoHeight()) {
            windowControls.getLayoutParams().height = videoHeight();
            windowControls.requestLayout();
        }
        for (final View button : new View[]{closeButton, playButton}) {
            if (button != null) {
                button.setScaleX(1f / scale);
                button.setScaleY(1f / scale);
            }
        }
        if (closeButton != null) {
            // ingrandito dall'angolo in alto a destra, non dal centro
            closeButton.setPivotX(closeButton.getWidth());
            closeButton.setPivotY(0f);
        }
    }

    /** Video ridotto chiuso o pagina distrutta: non deve restare niente spostato o ritagliato. */
    void reset() {
        progress = 1f;
        dragX = 0f;
        sheet.setTranslationX(0f);
        sheet.setTranslationY(0f);
        sheet.setScaleX(1f);
        sheet.setScaleY(1f);
        sheet.setClipToOutline(false);
        windowControls.setVisibility(View.GONE);
        if (window) {
            overlay.setVisibility(View.VISIBLE);
        }
        root.setTouchableHeight(0);
    }

    private int videoHeight() {
        final int height = video.getHeight();
        return height > 0 ? height : sheet.getWidth() * 9 / 16;
    }

    // quanto è rimpicciolita la pagina: da finestrella a intera
    private float scale() {
        return WINDOW_WIDTH + (1f - WINDOW_WIDTH) * progress;
    }

    // dove sta la finestrella, a destra o a sinistra
    private float windowLeft() {
        final float width = sheet.getWidth() * WINDOW_WIDTH;
        return onRight ? sheet.getWidth() - width - margin : margin;
    }

    @Override
    public void onWindowDrag(final float dx) {
        if (progress == 0f) {
            dragX = dx;
            sheet.setTranslationX(windowLeft() + dragX);
        }
    }

    @Override
    public void onWindowRelease(final float velocityX) {
        if (progress != 0f) {
            return;
        }
        // va dalla parte dove l'hai lanciata, o dove si trova quando la lasci
        final float center = windowLeft() + dragX + sheet.getWidth() * WINDOW_WIDTH / 2;
        final float fling = DeviceUtils.dpToPx(600, sheet.getContext());
        onRight = velocityX > fling || (velocityX > -fling && center > sheet.getWidth() / 2f);
        dragX = 0f;
        sheet.animate().translationX(windowLeft()).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }
}
