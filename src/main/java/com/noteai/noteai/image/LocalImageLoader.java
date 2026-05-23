package com.noteai.noteai.image;

import android.content.Context;

public class LocalImageLoader implements ImageLoader {

    public LocalImageLoader(Context context) {
        // TODO 图片渲染同学在这里处理本地图片路径、相对路径、Uri 权限和缓存目录。
        // TODO Markdown 中的 ![alt](path) 最终应该通过 ImageLoader 异步加载，避免主线程解码卡顿。
    }

    @Override
    public void loadImage(String source, int targetWidth, int targetHeight, Callback callback) {
        // TODO 后续实现采样解码：先读取原图宽高，再按目标尺寸 inSampleSize 解码。
        // TODO 解码必须放后台线程，完成后回主线程 callback.onSuccess。
        callback.onError("图片加载功能待实现");
    }

    @Override
    public void cancel(String source) {
        // TODO RecyclerView 复用或页面退出时取消未完成的图片解码任务。
    }
}
