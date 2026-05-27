package com.noteai.noteai.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class PlaceholderAiService implements AiService {
    private final Handler handler = new Handler(Looper.getMainLooper());

    public PlaceholderAiService(Context context) {
        // TODO AI 同学后续可以把这里替换成真实模型或后端接口初始化。
        // TODO 如果使用云端接口，请不要在代码里硬编码 API Key，应该走安全配置或后端转发。
    }

    @Override
    public void summarize(String title, String content, Callback callback) {
        // TODO AI 摘要同学实现：输入标题和 Markdown 正文，异步返回摘要文本。
        handler.postDelayed(() -> callback.onSuccess("这是 AI 摘要的占位内容。\n\n实际功能开发完成后，这里将显示笔记要点、关键信息和结构化提纲。"), 2000);
    }

    @Override
    public void polish(String title, String content, Callback callback) {
        // TODO AI 润色同学实现：输入标题和 Markdown 正文，异步返回润色后的正文。
        handler.postDelayed(() -> callback.onSuccess(content), 800);
    }
}
