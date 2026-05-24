package com.noteai.engine;

import android.graphics.Bitmap;

public interface ImageResolver {

    void loadImage(String source, int targetWidth, int targetHeight, Callback callback);

    void cancel(String source);

    interface Callback {
        void onSuccess(Bitmap bitmap, int originalWidth, int originalHeight);
        void onError(String message);
    }
}
