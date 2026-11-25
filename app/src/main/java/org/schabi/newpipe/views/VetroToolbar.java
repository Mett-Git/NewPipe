package org.schabi.newpipe.views;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import org.schabi.newpipe.R;

/**
 * La barra in alto con "NewPipe" scritto piccolo sopra il nome della pagina in cui sei.
 * Le pagine continuano a chiamare setTitle() come sempre: il loro titolo diventa la riga
 * grande sotto, e "NewPipe" resta sopra. Solo dove è accesa con {@link #setBrand(boolean)}
 * (pagine principali e impostazioni, non le finestre di dialogo).
 */
public class VetroToolbar extends Toolbar {
    private static final String BRAND = "NewPipe";
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

    private void show() {
        if (!brand) {
            super.setTitle(pageTitle);
            return;
        }
        super.setTitle(BRAND);
        // la pagina che si chiama già "NewPipe" non lo ripete
        super.setSubtitle(TextUtils.isEmpty(pageTitle) || BRAND.contentEquals(pageTitle)
                ? null : pageTitle);
    }
}
