package org.schabi.newpipe.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

/**
 * ViewPager a cui si può togliere lo scorrimento col dito: nella pagina del video le pagine
 * interne si aprono solo toccando i riquadri di commenti e descrizione.
 */
public class NoSwipeViewPager extends ViewPager {
    private boolean swipeEnabled = true;

    public NoSwipeViewPager(@NonNull final Context context) {
        super(context);
    }

    public NoSwipeViewPager(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @param enabled false per cambiare pagina solo dal codice
     */
    public void setSwipeEnabled(final boolean enabled) {
        swipeEnabled = enabled;
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        return swipeEnabled && super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        return swipeEnabled && super.onTouchEvent(ev);
    }
}
