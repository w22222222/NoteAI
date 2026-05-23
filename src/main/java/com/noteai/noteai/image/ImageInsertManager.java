package com.noteai.noteai.image;

import android.net.Uri;

public interface ImageInsertManager {
    // 图片插入流程：编辑页拿到系统图片选择器返回的 Uri 后调用这里。
    // 实现类负责复制图片到 App 私有目录、读取图片宽高、生成 Markdown 图片语法。
    // 编辑页只负责把 callback 返回的 markdownText 插入当前光标位置。

    void importImage(Uri sourceUri, Callback callback);

    interface Callback {
        void onSuccess(InsertedImage image);

        void onError(String message);
    }
}
