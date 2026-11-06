package org.schabi.newpipe.fragments.detail;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;

import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.views.MiniPlayerRoot;

/**
 * Il video ridotto come su YouTube: un mini-player arrotondato che resta sospeso sopra la barra
 * in basso e che, trascinandolo, si allarga fino a tornare la pagina intera.
 */
final class FloatingMiniPlayer {
    private final View sheet;
    private final MiniPlayerRoot root;
    private final int miniHeight;
    private final float margin;
    private final float radius;
    private int lift = 0;
    private float progress = 1f;

    FloatingMiniPlayer(@NonNull final View sheet, @NonNull final MiniPlayerRoot root,
                       final int miniHeight) {
        this.sheet = sheet;
        this.root = root;
        this.miniHeight = miniHeight;
        margin = DeviceUtils.dpToPx(8, sheet.getContext());
        radius = DeviceUtils.dpToPx(16, sheet.getContext());
        sheet.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(final View view, final Outline outline) {
                final float closed = 1f - progress;
                final int side = (int) (margin * closed);
                final int visible = (int) (miniHeight
                        + (view.getHeight() - miniHeight) * progress);
                outline.setRoundRect(side, 0, view.getWidth() - side, visible, radius * closed);
            }
        });
    }

    /**
     * @param pixels quanto tenere sollevato il mini-player dal fondo dello schermo
     */
    void setLift(final int pixels) {
        lift = pixels;
        apply(progress);
    }

    /**
     * @param slideOffset 0 = mini-player, 1 = pagina intera
     */
    void apply(final float slideOffset) {
        progress = Math.max(0f, Math.min(1f, slideOffset));
        sheet.setTranslationY(-lift * (1f - progress));
        sheet.setClipToOutline(progress < 1f);
        sheet.invalidateOutline();
        root.setTouchableHeight(progress == 0f ? miniHeight : 0);
    }

    /** Mini-player chiuso o pagina distrutta: non deve restare niente spostato o ritagliato. */
    void reset() {
        progress = 1f;
        sheet.setTranslationY(0f);
        sheet.setClipToOutline(false);
        root.setTouchableHeight(0);
    }
}
