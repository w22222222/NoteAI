package com.noteai.noteai.ai;

public interface AiService {
    // TODO AI 同学：真实摘要/润色功能实现这个接口即可，NoteEditActivity 不需要关心底层是本地模型还是后端接口。
    // TODO 注意所有回调都应回到主线程，避免直接在后台线程修改 UI。

    void summarize(String title, String content, Callback callback);

    void polish(String title, String content, Callback callback);

    interface Callback {
        void onSuccess(String result);

        void onError(String message);
    }
}
