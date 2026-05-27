package com.noteai.noteai.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AiFloatingBall extends FrameLayout {

    private static final int STATE_BALL = 0;
    private static final int STATE_EXPANDED = 1;
    private static final int STATE_LOADING = 2;
    private static final int STATE_RESULT = 3;

    private int state = STATE_BALL;
    private final BallView ballView;
    private final LinearLayout optionPanel;
    private final TextView polishBtn;
    private final TextView summaryBtn;
    private final FrameLayout resultWindow;
    private final TextView resultTitle;
    private final TextView resultContent;

    private float ballX, ballY;
    private float dragStartRawX, dragStartRawY;
    private float ballStartX, ballStartY;
    private boolean isDragging;
    private final int ballSize;
    private final int screenWidth;
    private final int screenHeight;
    private Callback callback;

    public interface Callback {
        void onPolish();
        void onSummary();
    }

    public AiFloatingBall(Context context) {
        super(context);
        setClipChildren(false);

        screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        ballSize = dp(52);

        ballView = new BallView(context);
        ballView.setLayoutParams(new LayoutParams(ballSize, ballSize));
        ballView.setOnTouchListener(new BallTouchListener());
        addView(ballView);

        optionPanel = new LinearLayout(context);
        optionPanel.setOrientation(LinearLayout.VERTICAL);
        optionPanel.setVisibility(View.GONE);

        polishBtn = makeOptionBtn("AI 润色", 0xFF5C6BC0);
        summaryBtn = makeOptionBtn("AI 摘要", 0xFF26A69A);

        optionPanel.addView(polishBtn);
        optionPanel.addView(summaryBtn);
        addView(optionPanel);

        resultWindow = new FrameLayout(context);
        resultWindow.setVisibility(View.GONE);
        resultWindow.setBackgroundColor(0xFFF5F5F5);
        resultWindow.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout resultInner = new LinearLayout(context);
        resultInner.setOrientation(LinearLayout.VERTICAL);

        resultTitle = new TextView(context);
        resultTitle.setText("AI 摘要");
        resultTitle.setTextColor(0xFF333333);
        resultTitle.setTextSize(16);
        resultTitle.getPaint().setFakeBoldText(true);

        resultContent = new TextView(context);
        resultContent.setText("正在总结...");
        resultContent.setTextColor(0xFF666666);
        resultContent.setTextSize(14);
        resultContent.setLineSpacing(dp(4), 1);
        resultContent.setPadding(0, dp(6), 0, 0);

        TextView closeBtn = new TextView(context);
        closeBtn.setText("关闭 ✕");
        closeBtn.setTextColor(0xFF999999);
        closeBtn.setTextSize(13);
        closeBtn.setOnClickListener(v -> collapseToBall());

        resultInner.addView(closeBtn);
        resultInner.addView(resultTitle);
        resultInner.addView(resultContent);
        resultWindow.addView(resultInner);
        addView(resultWindow);

        polishBtn.setOnClickListener(v -> {
            collapseToBall();
            if (callback != null) callback.onPolish();
        });
        summaryBtn.setOnClickListener(v -> {
            showLoading();
            if (callback != null) callback.onSummary();
        });

        ballX = screenWidth - ballSize - dp(20);
        ballY = screenHeight / 2f;
        updatePositions();
    }

    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    private class BallTouchListener implements OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            float rawX = event.getRawX();
            float rawY = event.getRawY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartRawX = rawX;
                    dragStartRawY = rawY;
                    ballStartX = ballX;
                    ballStartY = ballY;
                    isDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = rawX - dragStartRawX;
                    float dy = rawY - dragStartRawY;
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) {
                        isDragging = true;
                    }
                    if (isDragging) {
                        ballX = clamp(ballStartX + dx, 0, screenWidth - ballSize);
                        ballY = clamp(ballStartY + dy, dp(60), screenHeight - dp(120));
                        updatePositions();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        toggleState();
                    } else {
                        snapToEdge();
                    }
                    return true;
            }
            return false;
        }
    }

    private void showLoading() {
        state = STATE_LOADING;

        int w = (int)(screenWidth * 0.88f);
        LayoutParams wlp = new LayoutParams(w, LayoutParams.WRAP_CONTENT);
        resultWindow.setLayoutParams(wlp);
        resultWindow.setVisibility(View.VISIBLE);
        optionPanel.setVisibility(View.GONE);
        ballView.setState(BallState.HIDDEN);
        resultTitle.setText("AI 摘要");
        resultContent.setText("正在总结...");

        float targetX = (screenWidth - w) / 2f;
        float targetY = screenHeight * 0.12f;

        animateWindow(targetX, targetY);
    }

    public void showSummaryResult(String text) {
        state = STATE_RESULT;
        resultContent.setText(text);
    }

    public void showSummaryError(String message) {
        state = STATE_RESULT;
        resultTitle.setText("AI 摘要失败");
        resultContent.setText(message);
    }

    private void collapseToBall() {
        state = STATE_BALL;
        resultWindow.setVisibility(View.GONE);
        optionPanel.setVisibility(View.GONE);
        ballView.setState(BallState.NORMAL);
        updatePositions();
    }

    private void animateWindow(float tx, float ty) {
        resultWindow.setX(ballX);
        resultWindow.setY(ballY);
        resultWindow.setAlpha(0f);
        resultWindow.setScaleX(0.3f);
        resultWindow.setScaleY(0.3f);
        resultWindow.setPivotX(0);
        resultWindow.setPivotY(0);

        resultWindow.animate()
                .x(tx).y(ty)
                .alpha(1f)
                .scaleX(1f).scaleY(1f)
                .setDuration(350)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void toggleState() {
        if (state == STATE_BALL) {
            state = STATE_EXPANDED;
            optionPanel.setVisibility(View.VISIBLE);
            ballView.setState(BallState.EXPANDED);
            updateExpandedPanel();
        } else if (state == STATE_EXPANDED) {
            collapseToBall();
        } else if (state == STATE_RESULT) {
            collapseToBall();
        }
    }

    private void snapToEdge() {
        float midX = screenWidth / 2f;
        ballX = (ballX + ballSize / 2f < midX) ? dp(8) : (screenWidth - ballSize - dp(8));
        updatePositions();
    }

    private void updatePositions() {
        ballView.setX(ballX);
        ballView.setY(ballY);
    }

    private void updateExpandedPanel() {
        int panelW = dp(140);
        int panelH = dp(100);

        float px = ballX + ballSize + dp(8);
        float py = ballY - dp(10);
        if (px + panelW > screenWidth - dp(8)) {
            px = ballX - panelW - dp(8);
        }
        if (py < dp(60)) py = dp(60);
        if (py + panelH > screenHeight - dp(80)) {
            py = screenHeight - dp(80) - panelH;
        }

        LayoutParams lp = new LayoutParams(panelW, LayoutParams.WRAP_CONTENT);
        optionPanel.setLayoutParams(lp);
        optionPanel.setX(px);
        optionPanel.setY(py);
    }

    private float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private TextView makeOptionBtn(String text, int color) {
        TextView btn = new TextView(getContext());
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(color);
        btn.setPadding(dp(14), dp(10), dp(14), dp(10));
        btn.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(6));
        btn.setLayoutParams(lp);
        return btn;
    }

    private enum BallState { NORMAL, EXPANDED, HIDDEN }

    private static class BallView extends View {
        private final Paint bgPaint;
        private final Paint textPaint;
        private final Paint shadowPaint;
        private BallState state = BallState.NORMAL;
        private float animProgress = 1f;
        private ValueAnimator animator;

        BallView(Context ctx) {
            super(ctx);
            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(0xFF1A73E8);
            shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setColor(0x22000000);
            shadowPaint.setShadowLayer(dp(ctx, 4), 0, dp(ctx, 2), 0x33000000);
            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(dp(ctx, 16));
            textPaint.setTextAlign(Paint.Align.CENTER);
            setLayerType(LAYER_TYPE_SOFTWARE, shadowPaint);
        }

        void setState(BallState s) {
            if (animator != null) animator.cancel();
            float target = (s == BallState.HIDDEN) ? 0f : 1f;
            animator = ValueAnimator.ofFloat(animProgress, target);
            animator.setDuration(200);
            animator.addUpdateListener(a -> {
                animProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
            this.state = s;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = (getWidth() / 2f - dp(getContext(), 2)) * animProgress;
            float alpha = animProgress;

            if (alpha < 0.05f) return;

            bgPaint.setAlpha((int) (255 * alpha));
            shadowPaint.setAlpha((int) (55 * alpha));
            canvas.drawCircle(cx + dp(getContext(), 1), cy + dp(getContext(), 2), r, shadowPaint);
            canvas.drawCircle(cx, cy, r, bgPaint);

            textPaint.setAlpha((int) (255 * alpha));
            if (state == BallState.EXPANDED) {
                textPaint.setTextSize(dp(getContext(), 18));
            } else {
                textPaint.setTextSize(dp(getContext(), 16));
            }
            float textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f;
            String sym = (state == BallState.EXPANDED) ? "✕" : "AI";
            canvas.drawText(sym, cx, textY, textPaint);
        }

        static int dp(Context ctx, int d) {
            return (int) (d * ctx.getResources().getDisplayMetrics().density + 0.5f);
        }
    }

    private int dp(int d) {
        return (int) (d * getResources().getDisplayMetrics().density + 0.5f);
    }
}
