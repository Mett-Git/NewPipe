package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Radice della pagina del video. Quando è ridotta a mini-player sospeso, sotto al mini-player
 * c'è la barra in basso: i tocchi fuori dal mini-player devono arrivare a lei.
 */
public class MiniPlayerRoot extends FrameLayout {
    private int touchableHeight = 0;

    public MiniPlayerRoot(@NonNull final Context context) {
        super(context);
    }

    public MiniPlayerRoot(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @param height fin dove accettare i tocchi, in pixel dall'alto; 0 = ovunque
     */
    public void setTouchableHeight(final int height) {
        touchableHeight = height;
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent ev) {
        if (touchableHeight > 0 && ev.getActionMasked() == MotionEvent.ACTION_DOWN
                && ev.getY() > touchableHeight) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }
}
