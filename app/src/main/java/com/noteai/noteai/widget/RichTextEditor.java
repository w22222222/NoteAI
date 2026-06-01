package com.noteai.noteai.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

import com.noteai.noteai.image.LocalImageLoader;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RichTextEditor extends EditText {

    private static final String TAG = "RichTextEditor";
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(\\s*([^\\)]+?)\\s*\\)(\\s*\\{\\s*width\\s*=\\s*(\\d+)\\s+height\\s*=\\s*(\\d+)\\s*\\})?");

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> loadingImages = new ConcurrentHashMap<>();
    private final Map<String, Drawable> cachedImages = new ConcurrentHashMap<>();
    
    private LocalImageLoader imageLoader;
    private int displayWidth;
    private int maxImageHeight = 400; // dp
    private boolean isProcessing = false;
    private Runnable processRunnable;

    public RichTextEditor(Context context) {
        super(context);
        this.context = context;
        init();
    }

    public RichTextEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    public RichTextEditor(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }

    private void init() {
        imageLoader = new LocalImageLoader(context);
        displayWidth = context.getResources().getDisplayMetrics().widthPixels - dp(32); // 减去 padding
        maxImageHeight = dp(maxImageHeight);
        
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                processImages(s);
            }
        });
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void processImages(final Editable editable) {
        if (isProcessing) return;
        if (processRunnable != null) {
            mainHandler.removeCallbacks(processRunnable);
        }
        processRunnable = () -> {
            if (isProcessing) return;
            isProcessing = true;
            try {
                String text = editable.toString();
                Matcher matcher = IMAGE_PATTERN.matcher(text);
                
                while (matcher.find()) {
                    String altText = matcher.group(1);
                    String imagePath = matcher.group(2).trim();
                    int start = matcher.start();
                    int end = matcher.end();
                    
                    // 检查是否已经有图片span
                    ImageSpan[] existingSpans = editable.getSpans(start, end, ImageSpan.class);
                    if (existingSpans.length > 0) {
                        continue; // 已经有图片了
                    }
                    
                    loadAndInsertImage(editable, imagePath, altText, start, end);
                }
            } finally {
                isProcessing = false;
            }
        };
        mainHandler.postDelayed(processRunnable, 100);
    }

    private void loadAndInsertImage(final Editable editable, final String imagePath, 
                                   final String altText, final int start, final int end) {
        if (loadingImages.containsKey(imagePath)) {
            return; // 正在加载中
        }
        
        loadingImages.put(imagePath, true);

        imageLoader.loadImage(imagePath, displayWidth, maxImageHeight, new com.noteai.engine.ImageResolver.Callback() {
            @Override
            public void onSuccess(Bitmap bitmap, int originalWidth, int originalHeight) {
                loadingImages.remove(imagePath);
                
                // 计算缩放后的尺寸，遵循预览时的原则：小图不变，大图缩小
                int scaledWidth, scaledHeight;
                if (bitmap.getWidth() <= displayWidth) {
                    // 小图保持原始大小
                    scaledWidth = bitmap.getWidth();
                    scaledHeight = bitmap.getHeight();
                } else {
                    // 大图按比例缩小到显示宽度
                    float scale = (float) displayWidth / bitmap.getWidth();
                    scaledWidth = displayWidth;
                    scaledHeight = Math.max(dp(40), (int) (bitmap.getHeight() * scale));
                    // 限制最大高度
                    if (scaledHeight > maxImageHeight) {
                        scale = (float) maxImageHeight / bitmap.getHeight();
                        scaledHeight = maxImageHeight;
                        scaledWidth = Math.max(dp(40), (int) (bitmap.getWidth() * scale));
                    }
                }
                
                Bitmap scaledBitmap;
                if (bitmap.getWidth() == scaledWidth && bitmap.getHeight() == scaledHeight) {
                    scaledBitmap = bitmap;
                } else {
                    scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
                    bitmap.recycle();
                }
                
                // 创建Drawable并设置边界
                BitmapDrawable drawable = new BitmapDrawable(getResources(), scaledBitmap);
                drawable.setBounds(0, 0, scaledWidth, scaledHeight);
                
                // 缓存图片
                cachedImages.put(imagePath, drawable);
                
                int spanStart = start;
                int spanEnd = end;
                if (spanStart < 0 || spanEnd > editable.length()
                        || !editable.subSequence(spanStart, spanEnd).toString().contains(imagePath)) {
                    Matcher currentMatcher = IMAGE_PATTERN.matcher(editable.toString());
                    spanStart = -1;
                    spanEnd = -1;
                    while (currentMatcher.find()) {
                        if (imagePath.equals(currentMatcher.group(2).trim())) {
                            spanStart = currentMatcher.start();
                            spanEnd = currentMatcher.end();
                            break;
                        }
                    }
                }

                // 确保文本范围仍然有效
                if (spanStart >= 0 && spanEnd <= editable.length()) {
                    // 创建ImageSpan
                    ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BASELINE);
                    
                    // 清除该范围内可能存在的旧span
                    ImageSpan[] oldSpans = editable.getSpans(spanStart, spanEnd, ImageSpan.class);
                    for (ImageSpan oldSpan : oldSpans) {
                        editable.removeSpan(oldSpan);
                    }
                    
                    // 设置新的ImageSpan
                    editable.setSpan(imageSpan, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            @Override
            public void onError(String message) {
                loadingImages.remove(imagePath);
                Log.w(TAG, "Failed to load image: " + imagePath + ", error: " + message);
                // 加载失败时显示占位符
                showImagePlaceholder(editable, altText, start, end);
            }
        });
    }

    private void showImagePlaceholder(Editable editable, String altText, int start, int end) {
        if (start < 0 || end > editable.length()) return;
        
        String placeholder = "[图片: " + (altText.isEmpty() ? "未加载" : altText) + "]";
        editable.replace(start, end, placeholder);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection target = super.onCreateInputConnection(outAttrs);
        if (target == null) {
            return null;
        }
        return new InputConnectionWrapper(target, true) {
            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (beforeLength > 0 && deleteImageNearCursor()) {
                    return true;
                }
                return super.deleteSurroundingText(beforeLength, afterLength);
            }

            @Override
            public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                if (beforeLength > 0 && deleteImageNearCursor()) {
                    return true;
                }
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
            }
        };
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DEL && deleteImageNearCursor()) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean deleteImageNearCursor() {
        Editable editable = getText();
        if (editable == null || editable.length() == 0) {
            return false;
        }

        int selectionStart = Math.max(0, getSelectionStart());
        int selectionEnd = Math.max(0, getSelectionEnd());
        int min = Math.min(selectionStart, selectionEnd);
        int max = Math.max(selectionStart, selectionEnd);

        if (min != max) {
            ImageSpan[] spans = editable.getSpans(min, max, ImageSpan.class);
            if (spans.length > 0) {
                int deleteStart = editable.getSpanStart(spans[0]);
                int deleteEnd = editable.getSpanEnd(spans[0]);
                if (deleteStart >= 0 && deleteEnd > deleteStart) {
                    editable.delete(deleteStart, deleteEnd);
                    return true;
                }
            }
            return false;
        }

        ImageSpan[] beforeSpans = editable.getSpans(Math.max(0, min - 1), min, ImageSpan.class);
        for (ImageSpan span : beforeSpans) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);
            if (spanStart >= 0 && spanEnd == min) {
                editable.delete(spanStart, spanEnd);
                return true;
            }
        }

        ImageSpan[] touchingSpans = editable.getSpans(min, Math.min(editable.length(), min + 1), ImageSpan.class);
        for (ImageSpan span : touchingSpans) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);
            if (spanStart < min && min <= spanEnd) {
                editable.delete(spanStart, spanEnd);
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        // 当用户删除时，清理相关的图片缓存
        if (lengthBefore > lengthAfter) {
            cleanupUnusedImages(text.toString());
        }
    }

    private void cleanupUnusedImages(String currentText) {
        Iterator<String> iterator = cachedImages.keySet().iterator();
        while (iterator.hasNext()) {
            String path = iterator.next();
            if (!currentText.contains(path)) {
                iterator.remove();
            }
        }
    }

    /**
     * 获取纯文本内容（不含图片Span）
     */
    public String getPlainText() {
        return getText().toString();
    }

    public void refreshImages() {
        Editable editable = getText();
        if (editable != null) {
            processImages(editable);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // 清理资源
        for (Drawable drawable : cachedImages.values()) {
            if (drawable instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        }
        cachedImages.clear();
        loadingImages.clear();
    }
}
