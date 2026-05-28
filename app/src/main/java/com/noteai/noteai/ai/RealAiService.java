package com.noteai.noteai.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.noteai.noteai.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RealAiService implements AiService {
    private static final String MODE_PROXY = "proxy";
    private static final String MODE_DIRECT = "direct";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public RealAiService(Context context) {
        // Keep constructor stable for the current Activity wiring and future dependency injection.
    }

    @Override
    public void summarize(String title, String content, Callback callback) {
        if (isBlank(content)) {
            postError(callback, "正文为空，无法摘要");
            return;
        }
        execute("summarize", title, content, callback);
    }

    @Override
    public void polish(String title, String content, Callback callback) {
        if (isBlank(content)) {
            postError(callback, "正文为空，无法润色");
            return;
        }
        execute("polish", title, content, callback);
    }

    private void execute(String task, String title, String content, Callback callback) {
        executor.execute(() -> {
            try {
                String result;
                if (MODE_PROXY.equalsIgnoreCase(BuildConfig.AI_MODE)) {
                    result = requestProxy(task, safe(title), content);
                } else if (MODE_DIRECT.equalsIgnoreCase(BuildConfig.AI_MODE)) {
                    result = requestDirect(task, safe(title), content);
                } else {
                    throw new AiException("AI 配置错误：ai.mode 只能是 proxy 或 direct");
                }
                postSuccess(callback, normalizeModelText(result));
            } catch (AiException e) {
                postError(callback, e.getMessage());
            } catch (Exception e) {
                postError(callback, "AI 请求失败，请稍后重试");
            }
        });
    }

    private String requestProxy(String task, String title, String content) throws Exception {
        if (isBlank(BuildConfig.AI_PROXY_BASE_URL)) {
            throw new AiException("AI 代理未配置，请在 ai.properties 中设置 ai.proxyBaseUrl");
        }

        MarkdownProtector.ProtectedMarkdown protectedMarkdown = null;
        String requestContent = content;
        boolean protectMarkdown = "polish".equals(task);
        if (protectMarkdown) {
            protectedMarkdown = MarkdownProtector.protect(content);
            requestContent = protectedMarkdown.text();
        }

        JSONObject body = new JSONObject();
        body.put("task", task);
        body.put("title", title);
        body.put("content", requestContent);
        body.put("markdownProtection", protectMarkdown);

        String url = appendPath(BuildConfig.AI_PROXY_BASE_URL, "/api/ai/" + task);
        String text = extractAiText(postJson(url, body, null));
        return restoreProtectedMarkdown(protectedMarkdown, text);
    }

    private String requestDirect(String task, String title, String content) throws Exception {
        if (isBlank(BuildConfig.AI_DIRECT_BASE_URL) || isBlank(BuildConfig.AI_DIRECT_API_KEY) || isBlank(BuildConfig.AI_DIRECT_MODEL)) {
            throw new AiException("Direct AI 配置缺失，请检查 ai.properties");
        }

        MarkdownProtector.ProtectedMarkdown protectedMarkdown = null;
        String requestContent = content;
        if ("polish".equals(task)) {
            protectedMarkdown = MarkdownProtector.protect(content);
            requestContent = protectedMarkdown.text();
        }

        JSONObject body = new JSONObject();
        body.put("model", BuildConfig.AI_DIRECT_MODEL);
        body.put("temperature", "summarize".equals(task) ? 0.2 : 0.35);
        body.put("messages", buildMessages(task, title, requestContent, protectedMarkdown != null && protectedMarkdown.hasTokens()));

        String auth = "Bearer " + BuildConfig.AI_DIRECT_API_KEY;
        String text = extractAiText(postJson(appendPath(BuildConfig.AI_DIRECT_BASE_URL, "/chat/completions"), body, auth));
        return restoreProtectedMarkdown(protectedMarkdown, text);
    }

    private String restoreProtectedMarkdown(MarkdownProtector.ProtectedMarkdown protectedMarkdown, String text) throws AiException {
        if (protectedMarkdown != null && protectedMarkdown.hasTokens()) {
            try {
                return protectedMarkdown.restore(text);
            } catch (MarkdownProtector.MissingProtectedTokenException e) {
                throw new AiException("润色结果缺少受保护的 Markdown 片段，已取消覆盖正文");
            }
        }
        return text;
    }

    private JSONArray buildMessages(String task, String title, String content, boolean hasProtectedTokens) throws JSONException {
        JSONArray messages = new JSONArray();
        if ("summarize".equals(task)) {
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", "你是 NoteAI 的中文笔记摘要助手。只返回摘要正文，不要返回 JSON、解释或提示词。摘要应准确、简洁，长度控制在 100 到 500 字，可使用 Markdown 要点。"));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", "标题：" + title + "\n\n正文：\n" + content));
        } else {
            String protectionRule = hasProtectedTokens
                    ? "正文中形如 NOTEAI_PROTECTED_MARKDOWN_0 的占位符代表代码块或本地图片语法，必须逐字原样保留，不要翻译、删除、重排或包裹它们。"
                    : "";
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", "你是 NoteAI 的中文 Markdown 润色助手。返回润色后的完整 Markdown 正文，不要返回修改建议、解释、JSON 或额外包裹。保留原文结构，优化表达、错别字和标点。" + protectionRule));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", "标题：" + title + "\n\n请润色以下 Markdown 正文：\n" + content));
        }
        return messages;
    }

    private JSONObject postJson(String urlText, JSONObject body, String authorization) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(BuildConfig.AI_TIMEOUT_MS);
            connection.setReadTimeout(BuildConfig.AI_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            if (!isBlank(authorization)) {
                connection.setRequestProperty("Authorization", authorization);
            }

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bytes);
            }

            int code = connection.getResponseCode();
            String response = readAll(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new AiException(parseErrorMessage(response, code));
            }
            return new JSONObject(response);
        } catch (IOException e) {
            throw new AiException("网络连接失败，请检查 AI 服务配置");
        } catch (JSONException e) {
            throw new AiException("AI 返回格式异常");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String extractAiText(JSONObject response) throws JSONException, AiException {
        String text = firstNonBlank(
                response.optString("result", null),
                response.optString("summary", null),
                response.optString("content", null),
                response.optString("text", null)
        );
        if (!isBlank(text)) {
            return text;
        }

        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            text = firstNonBlank(
                    data.optString("result", null),
                    data.optString("summary", null),
                    data.optString("content", null),
                    data.optString("text", null)
            );
            if (!isBlank(text)) {
                return text;
            }
        }

        JSONArray choices = response.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject first = choices.optJSONObject(0);
            if (first != null) {
                JSONObject message = first.optJSONObject("message");
                if (message != null && !isBlank(message.optString("content"))) {
                    return message.optString("content");
                }
                if (!isBlank(first.optString("text"))) {
                    return first.optString("text");
                }
            }
        }
        throw new AiException("AI 返回内容为空");
    }

    private String parseErrorMessage(String response, int code) {
        if (!isBlank(response)) {
            try {
                JSONObject json = new JSONObject(response);
                JSONObject error = json.optJSONObject("error");
                if (error != null && !isBlank(error.optString("message"))) {
                    return "AI 请求失败：" + error.optString("message");
                }
                if (!isBlank(json.optString("message"))) {
                    return "AI 请求失败：" + json.optString("message");
                }
            } catch (JSONException ignored) {
                // Fall through to a compact HTTP message.
            }
        }
        return "AI 请求失败（HTTP " + code + "）";
    }

    private String normalizeModelText(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.startsWith("```") && normalized.endsWith("```")) {
            int firstNewline = normalized.indexOf('\n');
            int lastFence = normalized.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                normalized = normalized.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return normalized;
    }

    private String appendPath(String base, String path) {
        String cleanBase = base == null ? "" : base.trim();
        while (cleanBase.endsWith("/")) {
            cleanBase = cleanBase.substring(0, cleanBase.length() - 1);
        }
        if (cleanBase.endsWith(path)) {
            return cleanBase;
        }
        return cleanBase + path;
    }

    private String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private void postSuccess(Callback callback, String result) {
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private void postError(Callback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static final class AiException extends Exception {
        private AiException(String message) {
            super(message);
        }
    }
}
