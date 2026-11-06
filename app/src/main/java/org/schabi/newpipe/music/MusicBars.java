package org.schabi.newpipe.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;

/** Le quattro barrette che ballano accanto al brano in riproduzione. */
public class MusicBars extends View {
    private static final int BARS = 4;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean moving = true;

    public MusicBars(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(ContextCompat.getColor(context, R.color.vm_accent));
    }

    /**
     * @param value true se la musica va, false per fermare le barrette
     */
    public void setMoving(final boolean value) {
        moving = value;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);
        final float width = getWidth() / (BARS * 2f - 1f);
        final long now = SystemClock.uptimeMillis();
        for (int i = 0; i < BARS; i++) {
            final double wave = Math.sin(now / (170d + i * 45d) + i * 1.7d);
            final float level = moving ? (float) (0.55d + 0.45d * wave) : 0.3f;
            final float left = i * width * 2f;
            canvas.drawRoundRect(left, getHeight() * (1f - level), left + width, getHeight(),
                    width / 2f, width / 2f, paint);
        }
        if (moving && getVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }
}
