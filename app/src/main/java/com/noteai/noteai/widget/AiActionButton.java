package com.noteai.noteai.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

public class AiActionButton extends TextView {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private ValueAnimator animator;
    private float phase;

    public AiActionButton(Context context) {
        super(context);
        init();
    }

    public AiActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AiActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setIncludeFontPadding(false);
        fillPaint.setColor(Color.WHITE);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1));
        shinePaint.setAlpha(90);
        textPaint.setColor(0xFF2563EB);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startShine();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    private void startShine() {
        if (animator != null) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float radius = dp(14);
        rect.set(dp(0.5f), dp(0.5f), getWidth() - dp(0.5f), getHeight() - dp(0.5f));
        canvas.drawRoundRect(rect, radius, radius, fillPaint);

        float shift = getWidth() * phase;
        strokePaint.setShader(new LinearGradient(
                -getWidth() + shift, 0, shift, getHeight(),
                new int[]{0xFF60A5FA, 0xFF22C55E, 0xFFE879F9, 0xFF60A5FA},
                new float[]{0f, 0.36f, 0.72f, 1f},
                Shader.TileMode.MIRROR));
        canvas.drawRoundRect(rect, radius, radius, strokePaint);

        shinePaint.setShader(new LinearGradient(
                -getWidth() + shift * 2f, 0, shift * 2f, getHeight(),
                new int[]{0x00FFFFFF, 0x70FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        RectF shine = new RectF(dp(2), dp(2), getWidth() - dp(2), getHeight() - dp(2));
        canvas.drawRoundRect(shine, radius - dp(2), radius - dp(2), shinePaint);

        if (getHeight() > getWidth() * 1.35f) {
            drawVerticalText(canvas);
        } else {
            super.onDraw(canvas);
        }
    }

    private void drawVerticalText(Canvas canvas) {
        CharSequence text = getText();
        if (text == null || text.length() == 0) {
            return;
        }
        textPaint.setTextSize(getTextSize());
        textPaint.setAlpha(isEnabled() ? 255 : 145);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float lineHeight = metrics.descent - metrics.ascent + dp(2);
        float totalHeight = lineHeight * text.length() - dp(2);
        float x = getWidth() / 2f;
        float y = (getHeight() - totalHeight) / 2f - metrics.ascent;
        for (int i = 0; i < text.length(); i++) {
            canvas.drawText(String.valueOf(text.charAt(i)), x, y + i * lineHeight, textPaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
