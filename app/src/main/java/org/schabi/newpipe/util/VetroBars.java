package org.schabi.newpipe.util;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;

/**
 * Stile Vetro: le barre sospese (in basso nella Home e nella pagina del video) lasciano
 * passare le liste dietro di sé. Le liste però devono avere in fondo lo spazio della barra,
 * così l'ultimo elemento si può portare sopra di lei.
 */
public final class VetroBars {
    private VetroBars() {
    }

    /**
     * Aggiunge {@code space} sotto ogni lista o pagina che scorre dentro {@code view}.
     *
     * @param view  la vista di una scheda (si cercano le liste al suo interno)
     * @param space lo spazio in pixel; 0 rimette tutto com'era
     */
    public static void setListsBottomSpace(final View view, final int space) {
        if (view instanceof RecyclerView || view instanceof ScrollView
                || view instanceof NestedScrollView) {
            Object original = view.getTag(R.id.vetro_bar_space);
            if (original == null) {
                original = view.getPaddingBottom();
                view.setTag(R.id.vetro_bar_space, original);
            }
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), (Integer) original + space);
            ((ViewGroup) view).setClipToPadding(space == 0);
            return;
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setListsBottomSpace(group.getChildAt(i), space);
            }
        }
    }
}
