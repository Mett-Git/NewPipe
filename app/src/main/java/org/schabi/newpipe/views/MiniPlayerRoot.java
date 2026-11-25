package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Radice della pagina del video. Quando è ridotta a mini-player sospeso, sotto al mini-player
 * c'è la barra in basso: i tocchi fuori dal mini-player devono arrivare a lei. Ridotta a
 * finestrella, la si sposta di lato col dito (in su e in giù la muove già il BottomSheet).
 */
public class MiniPlayerRoot extends FrameLayout {
    /** Chi sposta la finestrella mentre la trascini di lato. */
    public interface WindowDragListener {
        /**
         * @param dx di quanto è stata spostata dal punto in cui l'hai presa, in pixel
         */
        void onWindowDrag(float dx);

        /**
         * @param velocityX velocità al rilascio, in pixel al secondo (positiva = a destra)
         */
        void onWindowRelease(float velocityX);
    }

    private int touchableHeight = 0;
    @Nullable
    private WindowDragListener dragListener;
    private final int touchSlop;
    private float downRawX;
    private float downRawY;
    private boolean draggingSideways = false;
    @Nullable
    private VelocityTracker velocity;

    public MiniPlayerRoot(@NonNull final Context context) {
        this(context, null);
    }

    public MiniPlayerRoot(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    /**
     * @param height fin dove accettare i tocchi, in pixel dall'alto; 0 = ovunque
     */
    public void setTouchableHeight(final int height) {
        touchableHeight = height;
    }

    /**
     * @param listener chi sposta la finestrella di lato; null = non si sposta
     */
    public void setWindowDragListener(@Nullable final WindowDragListener listener) {
        dragListener = listener;
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent ev) {
        if (touchableHeight > 0 && ev.getActionMasked() == MotionEvent.ACTION_DOWN
                && ev.getY() > touchableHeight) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        if (dragListener == null || touchableHeight == 0) {
            return super.onInterceptTouchEvent(ev);
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = ev.getRawX();
                downRawY = ev.getRawY();
                draggingSideways = false;
                trackVelocity(ev, true);
                break;
            case MotionEvent.ACTION_MOVE:
                trackVelocity(ev, false);
                final float dx = ev.getRawX() - downRawX;
                final float dy = ev.getRawY() - downRawY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    // di lato: la finestrella è nostra, il BottomSheet non deve prenderla
                    draggingSideways = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        if (!draggingSideways || dragListener == null) {
            return super.onTouchEvent(ev);
        }
        trackVelocity(ev, false);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                dragListener.onWindowDrag(ev.getRawX() - downRawX);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float vx = 0f;
                if (velocity != null) {
                    velocity.computeCurrentVelocity(1000);
                    vx = velocity.getXVelocity();
                    velocity.recycle();
                    velocity = null;
                }
                draggingSideways = false;
                dragListener.onWindowRelease(vx);
                return true;
            default:
                return true;
        }
    }

    // la velocità va misurata sulle coordinate dello schermo: la finestrella si muove col dito
    private void trackVelocity(final MotionEvent ev, final boolean restart) {
        if (restart && velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        if (velocity == null) {
            velocity = VelocityTracker.obtain();
        }
        final MotionEvent screen = MotionEvent.obtain(ev);
        screen.setLocation(ev.getRawX(), ev.getRawY());
        velocity.addMovement(screen);
        screen.recycle();
    }
}
