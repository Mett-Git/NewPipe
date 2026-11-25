package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;

/**
 * La barra in alto con il logo e "NewPipe" sopra il nome della pagina in cui sei.
 * Le pagine continuano a chiamare setTitle() come sempre: il loro titolo diventa la riga
 * grande sotto, e "NewPipe" resta sopra. Solo dove è accesa con {@link #setBrand(boolean)}
 * (pagine principali e impostazioni, non le finestre di dialogo).
 */
public class VetroToolbar extends Toolbar {
    private static final String BRAND = "NewPipe";
    /** Grandezza di "NewPipe" (deve restare uguale a VetroToolbarBrand). */
    private static final float BRAND_SIZE_SP = 16f;
    private boolean brand = false;
    @Nullable
    private CharSequence pageTitle;

    public VetroToolbar(@NonNull final Context context) {
        super(context);
    }

    public VetroToolbar(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public VetroToolbar(@NonNull final Context context, @Nullable final AttributeSet attrs,
                        final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * @param on true = "NewPipe" sopra il nome della pagina
     */
    public void setBrand(final boolean on) {
        brand = on;
        if (on) {
            setTitleTextAppearance(getContext(), R.style.VetroToolbarBrand);
            setSubtitleTextAppearance(getContext(), R.style.VetroToolbarPage);
        }
        show();
    }

    @Override
    public void setTitle(@Nullable final CharSequence title) {
        pageTitle = title;
        show();
    }

    @Override
    public CharSequence getTitle() {
        return pageTitle;
    }

    // "NewPipe" col logo dell'app davanti (il triangolo dell'icona), del colore del testo
    private CharSequence brandWithLogo() {
        final Drawable logo = ContextCompat.getDrawable(getContext(),
                R.drawable.ic_newpipe_triangle_white);
        if (logo == null) {
            return BRAND;
        }
        final TypedValue color = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, color, true);
        final Drawable tinted = logo.mutate();
        tinted.setTint(color.resourceId != 0
                ? ContextCompat.getColor(getContext(), color.resourceId) : color.data);
        final int size = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                BRAND_SIZE_SP, getResources().getDisplayMetrics()));
        tinted.setBounds(0, 0, size * logo.getIntrinsicWidth()
                / Math.max(1, logo.getIntrinsicHeight()), size);
        final SpannableString text = new SpannableString("  " + BRAND);
        text.setSpan(new ImageSpan(tinted, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? ImageSpan.ALIGN_CENTER : ImageSpan.ALIGN_BASELINE),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private void show() {
        if (!brand) {
            super.setTitle(pageTitle);
            return;
        }
        super.setTitle(brandWithLogo());
        // la pagina che si chiama già "NewPipe" non lo ripete
        super.setSubtitle(TextUtils.isEmpty(pageTitle) || BRAND.contentEquals(pageTitle)
                ? null : pageTitle);
    }
}
