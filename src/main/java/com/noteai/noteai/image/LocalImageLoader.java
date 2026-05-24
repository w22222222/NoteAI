package com.noteai.noteai.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.noteai.engine.ImageResolver;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalImageLoader implements ImageResolver {

    private static final String TAG = "LocalImageLoader";

    private final File filesDir;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> cancelled = new ConcurrentHashMap<>();

    public LocalImageLoader(Context context) {
        this.filesDir = context.getFilesDir();
        Log.d(TAG, "filesDir=" + filesDir.getAbsolutePath());
    }

    public ImageLoader asImageLoader() {
        return new ImageLoader() {
            @Override
            public void loadImage(String source, int targetWidth, int targetHeight, Callback callback) {
                LocalImageLoader.this.loadImage(source, targetWidth, targetHeight, new ImageResolver.Callback() {
                    @Override
                    public void onSuccess(Bitmap bitmap, int originalWidth, int originalHeight) {
                        callback.onSuccess(bitmap, originalWidth, originalHeight);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void cancel(String source) {
                LocalImageLoader.this.cancel(source);
            }
        };
    }

    @Override
    public void loadImage(String source, int targetWidth, int targetHeight, final ImageResolver.Callback callback) {
        if (source == null || source.isEmpty()) {
            mainHandler.post(() -> callback.onError("图片路径为空"));
            return;
        }

        cancelled.remove(source);

        executor.execute(() -> {
            if (Boolean.TRUE.equals(cancelled.get(source))) {
                return;
            }

            try {
                File file = new File(filesDir, source);
                Log.d(TAG, "loadImage source=" + source + " path=" + file.getAbsolutePath() + " exists=" + file.exists());
                if (!file.exists()) {
                    file = new File(source);
                    Log.d(TAG, "loadImage fallback path=" + file.getAbsolutePath() + " exists=" + file.exists());
                }
                if (!file.exists()) {
                    Log.w(TAG, "loadImage file not found: " + source);
                    mainHandler.post(() -> callback.onError("图片文件不存在"));
                    return;
                }

                BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
                boundsOpts.inJustDecodeBounds = true;
                try (FileInputStream fis = new FileInputStream(file)) {
                    BitmapFactory.decodeStream(fis, null, boundsOpts);
                }
                int originalWidth = boundsOpts.outWidth;
                int originalHeight = boundsOpts.outHeight;
                Log.d(TAG, "loadImage originalWidth=" + originalWidth + " originalHeight=" + originalHeight);

                if (originalWidth <= 0 || originalHeight <= 0) {
                    Log.w(TAG, "loadImage invalid dimensions");
                    mainHandler.post(() -> callback.onError("图片尺寸无效"));
                    return;
                }

                if (Boolean.TRUE.equals(cancelled.get(source))) {
                    return;
                }

                BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
                decodeOpts.inSampleSize = calculateSampleSize(
                        originalWidth, originalHeight, targetWidth, targetHeight);
                Log.d(TAG, "loadImage inSampleSize=" + decodeOpts.inSampleSize + " targetW=" + targetWidth + " targetH=" + targetHeight);

                Bitmap bitmap;
                try (FileInputStream fis = new FileInputStream(file)) {
                    bitmap = BitmapFactory.decodeStream(fis, null, decodeOpts);
                }

                if (bitmap == null) {
                    Log.w(TAG, "loadImage bitmap is null after decode");
                    mainHandler.post(() -> callback.onError("图片解码失败"));
                    return;
                }
                Log.d(TAG, "loadImage bitmap decoded: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                int w = bitmap.getWidth();
                int h = bitmap.getHeight();

                if (targetWidth > 0 && targetHeight > 0 && w > 0 && h > 0) {
                    float scaleW = (float) targetWidth / w;
                    float scaleH = (float) targetHeight / h;
                    float scale = Math.min(scaleW, scaleH);
                    if (scale < 0.99f) {
                        int newW = Math.max(1, (int) (w * scale));
                        int newH = Math.max(1, (int) (h * scale));
                        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
                        bitmap.recycle();
                        bitmap = scaled;
                    }
                }

                if (Boolean.TRUE.equals(cancelled.get(source))) {
                    bitmap.recycle();
                    return;
                }

                final Bitmap result = bitmap;
                final int finalOrigW = originalWidth;
                final int finalOrigH = originalHeight;
                mainHandler.post(() -> callback.onSuccess(result, finalOrigW, finalOrigH));
            } catch (Exception e) {
                Log.e(TAG, "loadImage exception: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("图片加载失败: " + e.getMessage()));
            }
        });
    }

    @Override
    public void cancel(String source) {
        if (source != null) {
            cancelled.put(source, true);
        }
    }

    private static int calculateSampleSize(int originalW, int originalH, int targetW, int targetH) {
        int sampleSize = 1;
        if (targetW > 0 && targetH > 0) {
            int halfW = originalW / 2;
            int halfH = originalH / 2;
            while (halfW / sampleSize > targetW * 2 || halfH / sampleSize > targetH * 2) {
                sampleSize *= 2;
            }
        }
        return sampleSize;
    }
}
