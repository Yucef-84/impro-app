package com.codetone.impro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Two-octave chord-tone display, independent of the current voicing octave. */
public final class KeyboardView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean[] active = new boolean[12];
    private static final int[] WHITE = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] BLACK_AFTER = {1, 3, -1, 6, 8, 10, -1};
    public KeyboardView(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
    public void setChord(MusicTheory.Chord chord) {
        java.util.Arrays.fill(active, false);
        if (chord != null && !chord.rest) for (int note : chord.midiNotes()) active[note % 12] = true;
        setContentDescription(chord == null || chord.rest ? "쉼" : chord.symbol + " 코드 구성음");
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth() / 14f;
        float height = getHeight();
        for (int i = 0; i < 14; i++) {
            paint.setColor(active[WHITE[i % 7]] ? Color.rgb(194, 238, 140) : Color.rgb(230, 236, 227));
            canvas.drawRoundRect(i * width + 1, 0, (i + 1) * width - 1, height, 3, 3, paint);
        }
        for (int i = 0; i < 13; i++) {
            int pitch = BLACK_AFTER[i % 7];
            if (pitch < 0) continue;
            paint.setColor(active[pitch] ? Color.rgb(137, 193, 95) : Color.rgb(20, 27, 23));
            canvas.drawRoundRect((i + 1) * width - width * .32f, 0, (i + 1) * width + width * .32f,
                    height * .62f, 3, 3, paint);
        }
    }
}
