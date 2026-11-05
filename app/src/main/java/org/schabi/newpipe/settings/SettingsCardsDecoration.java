package org.schabi.newpipe.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.DeviceUtils;

/**
 * Impostazioni in stile Vetro: le voci di ogni gruppo stanno in un unico riquadro arrotondato.
 * Il riquadro si disegna dietro alle voci consecutive, fino al titolo del gruppo dopo.
 */
final class SettingsCardsDecoration extends RecyclerView.ItemDecoration {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private final int margin;
    private final RectF card = new RectF();

    SettingsCardsDecoration(@NonNull final Context context) {
        fill.setColor(ContextCompat.getColor(context, R.color.vetro_card_fill));
        border.setColor(ContextCompat.getColor(context, R.color.vetro_card_border));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(DeviceUtils.dpToPx(1, context));
        radius = DeviceUtils.dpToPx(18, context);
        margin = DeviceUtils.dpToPx(12, context);
    }

    private static boolean isHeader(final RecyclerView parent, final int position) {
        if (!(parent.getAdapter() instanceof PreferenceGroupAdapter)) {
            return false;
        }
        final PreferenceGroupAdapter adapter = (PreferenceGroupAdapter) parent.getAdapter();
        if (position < 0 || position >= adapter.getItemCount()) {
            return true; // prima della prima voce e dopo l'ultima il riquadro si chiude
        }
        final Preference preference = adapter.getItem(position);
        return preference instanceof PreferenceCategory;
    }

    @Override
    public void getItemOffsets(@NonNull final Rect outRect, @NonNull final View view,
                               @NonNull final RecyclerView parent,
                               @NonNull final RecyclerView.State state) {
        final int position = parent.getChildAdapterPosition(view);
        if (position != RecyclerView.NO_POSITION && !isHeader(parent, position)) {
            outRect.left = margin;
            outRect.right = margin;
        }
    }

    @Override
    public void onDraw(@NonNull final Canvas canvas, @NonNull final RecyclerView parent,
                       @NonNull final RecyclerView.State state) {
        float top = 0;
        boolean open = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View child = parent.getChildAt(i);
            final int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION || isHeader(parent, position)) {
                continue;
            }
            if (!open) {
                // se il gruppo inizia fuori schermo, il bordo alto resta nascosto
                top = isHeader(parent, position - 1) ? child.getTop() : -radius * 2;
                open = true;
            }
            final boolean lastVisible = i == parent.getChildCount() - 1;
            if (isHeader(parent, position + 1) || lastVisible) {
                final float bottom = isHeader(parent, position + 1)
                        ? child.getBottom() : parent.getHeight() + radius * 2;
                card.set(margin, top, parent.getWidth() - margin, bottom);
                canvas.drawRoundRect(card, radius, radius, fill);
                canvas.drawRoundRect(card, radius, radius, border);
                open = false;
            }
        }
    }
}
