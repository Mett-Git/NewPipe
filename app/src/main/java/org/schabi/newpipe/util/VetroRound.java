package org.schabi.newpipe.util;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;

/** Angoli arrotondati per le anteprime dei video (stile YouTube). */
public final class VetroRound {
    private VetroRound() {
    }

    /**
     * @param view     la vista da arrotondare
     * @param radiusDp il raggio degli angoli, in dp
     */
    public static void apply(@NonNull final View view, final int radiusDp) {
        final float radius = DeviceUtils.dpToPx(radiusDp, view.getContext());
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(final View v, final Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        view.setClipToOutline(true);
    }
}
