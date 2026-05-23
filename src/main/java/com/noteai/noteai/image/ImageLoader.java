package com.noteai.noteai.image;

import android.graphics.Bitmap;

public interface ImageLoader {
    // TODO 图片渲染同学：Markdown 图片统一通过这个接口加载，避免把文件读取和解码逻辑写进 Activity。
    // TODO source 可以是相对路径、绝对路径或 Uri；targetWidth/targetHeight 用于采样解码，减少内存占用。

    void loadImage(String source, int targetWidth, int targetHeight, Callback callback);

    void cancel(String source);

    interface Callback {
        void onSuccess(Bitmap bitmap, int originalWidth, int originalHeight);

        void onError(String message);
    }
}
