package com.noteai.engine;

import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.PrecomputedText;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spannable;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.BulletSpan;
import android.text.style.ImageSpan;
import android.os.Looper;
import android.os.MessageQueue;
import android.util.Log;
import android.util.TypedValue;
import android.view.ViewParent;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class NoteMarkdownView {

    private static final String TAG = "NoteMarkdownView";
    private static final int PREVIEW_LINES = 200;
    private static final int CHUNK_LINES = 1000;
    private static final String LOADING_INDICATOR = "\n\n--- 正在加载更多内容 ---";

    public static StyleConfig globalStyle = StyleConfig.getDefault();

    private static boolean nativeAvailable = false;
    static {
        try {
            System.loadLibrary("note_engine");
            nativeAvailable = true;
            Log.i(TAG, "C++ engine loaded");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "C++ engine not available: " + e.getMessage());
        }
    }

    // ==================== JNI 声明 ====================
    private static native ParseNativeResult nativeParse(String markdown);

    // ==================== 解析调度 ====================
    private static SpanInfo[] parse(String markdown) {
        if (nativeAvailable) {
            ParseNativeResult nr = nativeParse(markdown);
            if (nr != null) return flatToSpanInfo(nr);
        }
        return MarkdownParser.parse(markdown).spans;
    }

    private static String extractText(String markdown) {
        if (nativeAvailable) {
            ParseNativeResult nr = nativeParse(markdown);
            if (nr != null) return nr.plainText;
        }
        return MarkdownParser.extractPlainText(markdown);
    }

    /** 将 C++ 扁平 int[] 格式转换回 SpanInfo[] */
    static SpanInfo[] flatToSpanInfo(ParseNativeResult nr) {
        int spanCount = nr.spansFlat.length / 5;
        SpanInfo[] spans = new SpanInfo[spanCount];
        String extras = nr.extraData != null ? nr.extraData : "";

        Log.d(TAG, "flatToSpanInfo spanCount=" + spanCount + " extrasLen=" + extras.length() + " extras=[" + extras + "]");

        for (int i = 0; i < spanCount; i++) {
            int base = i * 5;
            int start = nr.spansFlat[base];
            int end = nr.spansFlat[base + 1];
            int type = nr.spansFlat[base + 2];
            int level = nr.spansFlat[base + 3];
            int extraOff = nr.spansFlat[base + 4];

            String extra = null;
            if (extraOff >= 0 && extraOff < extras.length()) {
                int endOff = extras.indexOf('\n', extraOff);
                extra = (endOff >= 0)
                    ? extras.substring(extraOff, endOff)
                    : extras.substring(extraOff);
            }
            if (type == SpanInfo.TYPE_IMAGE) {
                Log.d(TAG, "flatToSpanInfo IMAGE span: start=" + start + " end=" + end + " extraOff=" + extraOff + " extra=[" + extra + "]");
            }
            spans[i] = new SpanInfo(start, end, type, level, extra);
        }
        return spans;
    }

    // ==================== 对外 API ====================

    public static void render(TextView textView, String markdown) {
        render(textView, markdown, globalStyle);
    }

    public static void render(TextView textView, String markdown, StyleConfig style) {
        renderInternal(textView, null, markdown, style);
    }

    public static void render(TextView textView, ScrollView scrollView,
                              String markdown, StyleConfig style) {
        renderInternal(textView, scrollView, markdown, style);
    }

    /** IdleHandler 版入口：append 只在主线程空闲时执行，不抢触摸 */
    public static void renderIdle(TextView textView,
                                   String markdown, StyleConfig style) {
        if (markdown == null || markdown.isEmpty()) {
            textView.setText("");
            return;
        }
        int totalLines = MarkdownParser.countLines(markdown);
        if (totalLines <= PREVIEW_LINES) {
            renderFull(textView, markdown, style, textView.getTextMetricsParams());
            return;
        }
        Log.i(TAG, "Streaming render (Idle): total=" + totalLines + " lines");
        new Thread(() -> doStreamingRenderIdle(textView, markdown, style)).start();
    }

    // ==================== 核心渲染逻辑 ====================

    private static void renderInternal(TextView textView, ScrollView scrollView,
                                        String markdown, StyleConfig style) {
        if (markdown == null || markdown.isEmpty()) {
            textView.setText("");
            return;
        }

        int totalLines = MarkdownParser.countLines(markdown);
        if (totalLines <= PREVIEW_LINES) {
            renderFull(textView, markdown, style, textView.getTextMetricsParams());
            return;
        }

        Log.i(TAG, "Streaming render: total=" + totalLines + " lines");
        new Thread(() -> doStreamingRender(textView, scrollView, markdown, style)).start();
    }

    private static void renderFull(TextView textView, String markdown,
                                    StyleConfig style, PrecomputedText.Params params) {
        new Thread(() -> {
            MarkdownParser.ParseResult result = MarkdownParser.parse(markdown);
            SpannableString spannable = buildSpannable(result.plainText, result.spans, style);
            PrecomputedText precomputed = PrecomputedText.create(spannable, params);
            textView.post(() -> textView.setText(precomputed));
            Log.i(TAG, "Full render completed: " + result.spans.length + " spans");
        }).start();
    }

    /** 对外公开的解析入口：返回 plainText + SpanInfo[] */
    public static MarkdownParser.ParseResult parseDocument(String markdown) {
        return parseFull(markdown);
    }

    /** 一次解析：C++/Java 全量，同时返回 plainText 和 SpanInfo[] */
    private static MarkdownParser.ParseResult parseFull(String markdown) {
        if (nativeAvailable) {
            long t0 = System.currentTimeMillis();
            ParseNativeResult nr = nativeParse(markdown);
            long tNative = System.currentTimeMillis();
            if (nr != null) {
                MarkdownParser.ParseResult pr = new MarkdownParser.ParseResult();
                pr.plainText = nr.plainText;
                SpanInfo[] rawSpans = flatToSpanInfo(nr);
                long tFlat = System.currentTimeMillis();
                pr.spans = fixUtf8ByteOffsets(rawSpans, nr.plainText);
                long tFix = System.currentTimeMillis();
                Log.i(TAG, "parseFull detail: native=" + (tNative - t0)
                        + "ms flat=" + (tFlat - tNative)
                        + "ms utf8fix=" + (tFix - tFlat)
                        + "ms total=" + (tFix - t0)
                        + "ms spans=" + rawSpans.length
                        + " chars=" + nr.plainText.length());
                return pr;
            }
            Log.i(TAG, "parseFull detail: native returned null in " + (tNative - t0) + "ms");
        }
        long tJava = System.currentTimeMillis();
        MarkdownParser.ParseResult result = MarkdownParser.parse(markdown);
        Log.i(TAG, "parseFull detail: javaFallback="
                + (System.currentTimeMillis() - tJava)
                + "ms spans=" + result.spans.length
                + " chars=" + result.plainText.length());
        return result;
    }

    /** C++ 引擎在 UTF-8 字节流上计算 span 位置，需要转成 Java UTF-16 char 偏移 */
    private static SpanInfo[] fixUtf8ByteOffsets(SpanInfo[] spans, String plainText) {
        byte[] utf8 = plainText.getBytes(StandardCharsets.UTF_8);
        int[] b2c = new int[utf8.length + 1];
        int charIdx = 0;
        int byteIdx = 0;
        while (byteIdx < utf8.length) {
            b2c[byteIdx] = charIdx;
            int b = utf8[byteIdx] & 0xFF;
            if ((b & 0x80) == 0)        byteIdx += 1;
            else if ((b & 0xE0) == 0xC0) byteIdx += 2;
            else if ((b & 0xF0) == 0xE0) byteIdx += 3;
            else                          byteIdx += 4;
            charIdx++;
        }
        b2c[utf8.length] = charIdx;

        for (SpanInfo s : spans) {
            if (s.start >= 0 && s.start < utf8.length)
                s.start = b2c[s.start];
            if (s.end >= 0 && s.end <= utf8.length)
                s.end = b2c[s.end];
        }
        return spans;
    }

    /** 渐进渲染(post版)：首 200 行立刻显示 → 后台构建 chunk → post 到主线程逐块 append */
    private static void doStreamingRender(TextView textView, ScrollView scrollView,
                                           String markdown, StyleConfig style) {
        long t0 = System.currentTimeMillis();

        MarkdownParser.ParseResult fullResult;
        try {
            fullResult = parseFull(markdown);
        } catch (Exception e) {
            Log.e(TAG, "Parse crash", e);
            return;
        }
        String fullText = fullResult.plainText;
        SpanInfo[] fullSpans = fullResult.spans;

        long tParse = System.currentTimeMillis();
        Log.i(TAG, "Parse: " + (tParse - t0) + "ms, "
                + fullSpans.length + " spans");

        if (fullText.isEmpty()) return;

        int firstEnd = scanLines(fullText, 0, PREVIEW_LINES);
        SpannableString firstChunk = buildChunk(fullText, fullSpans, 0, firstEnd, style);
        final SpannableStringBuilder sb = new SpannableStringBuilder(firstChunk);

        textView.post(() -> {
            textView.setText(sb, TextView.BufferType.SPANNABLE);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16 * style.bodySize);
            textView.setLineSpacing(0, style.lineSpacing);
            textView.setPadding(style.paddingLeft, textView.getPaddingTop(),
                    style.paddingRight, textView.getPaddingBottom());
            Log.i(TAG, "Phase 1 (post): " + (System.currentTimeMillis() - t0) + "ms");
        });

        final int[] posRef = {firstEnd};
        new Thread(() -> {
            int chunkIdx = 1;
            while (posRef[0] < fullText.length()) {
                int p = posRef[0];
                int nextPos = scanLines(fullText, p, CHUNK_LINES);
                if (nextPos <= p) break;

                final SpannableString chunk = buildChunk(fullText, fullSpans, p, nextPos, style);
                final int ci = chunkIdx;

                textView.post(() -> {
                    textView.append(chunk);
                    if (ci % 10 == 0) {
                        Log.i(TAG, "Chunk " + ci + ": " + textView.getText().length() + " chars");
                    }
                });

                posRef[0] = nextPos;
                chunkIdx++;
                try { Thread.sleep(150); } catch (InterruptedException e) { break; }
            }
            textView.post(() -> {
                Log.i(TAG, "Phase 2 done: " + (System.currentTimeMillis() - t0) + "ms, "
                        + textView.getText().length() + " chars");
            });
        }, "phase2").start();
    }

    /** 渐进渲染(IdleHandler版)：首 200 行立刻显示 → 后台构建 chunk → IdleHandler 逐块追加（不抢主线程） */
    private static void doStreamingRenderIdle(TextView textView,
                                               String markdown, StyleConfig style) {
        long t0 = System.currentTimeMillis();

        MarkdownParser.ParseResult fullResult;
        try {
            fullResult = parseFull(markdown);
        } catch (Exception e) {
            Log.e(TAG, "Parse crash", e);
            return;
        }
        String fullText = fullResult.plainText;
        SpanInfo[] fullSpans = fullResult.spans;

        long tParse = System.currentTimeMillis();
        Log.i(TAG, "Parse: " + (tParse - t0) + "ms, "
                + fullSpans.length + " spans");

        if (fullText.isEmpty()) return;

        int firstEnd = scanLines(fullText, 0, PREVIEW_LINES);
        SpannableString firstChunk = buildChunk(fullText, fullSpans, 0, firstEnd, style);
        final SpannableStringBuilder sb = new SpannableStringBuilder(firstChunk);

        final LinkedBlockingQueue<SpannableString> chunkQueue = new LinkedBlockingQueue<>();
        final int[] posRef = {firstEnd};
        final boolean[] streamingDone = {false};
        final long startMs = t0;

        textView.post(() -> {
            textView.setText(sb, TextView.BufferType.SPANNABLE);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16 * style.bodySize);
            textView.setLineSpacing(0, style.lineSpacing);
            textView.setPadding(style.paddingLeft, textView.getPaddingTop(),
                    style.paddingRight, textView.getPaddingBottom());
            Log.i(TAG, "Phase 1: " + (System.currentTimeMillis() - t0) + "ms");

            // 注册 IdleHandler：主线程空闲时追加一个 chunk
            Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
                int chunkIdx = 1;
                public boolean queueIdle() {
                    SpannableString chunk = chunkQueue.poll();
                    if (chunk != null) {
                        textView.append(chunk);
                        if (chunkIdx % 20 == 0) {
                            Log.i(TAG, "Chunk " + chunkIdx + ": " + textView.getText().length() + " chars");
                        }
                        chunkIdx++;
                        return true; // 还有 chunk 没消费完，继续注册
                    }
                    if (streamingDone[0]) {
                        Log.i(TAG, "Idle stream done: " + (System.currentTimeMillis() - startMs)
                                + "ms, " + (chunkIdx - 1) + " chunks");
                        return false; // 全部完成
                    }
                    return true; // 队列为空但后台还在生产，保持注册
                }
            });
        });

        // Phase 2: 后台构建 chunk → 压入队列
        new Thread(() -> {
            int total = 0;
            while (posRef[0] < fullText.length()) {
                int p = posRef[0];
                int nextPos = scanLines(fullText, p, CHUNK_LINES);
                if (nextPos <= p) break;

                SpannableString chunk = buildChunk(fullText, fullSpans, p, nextPos, style);
                chunkQueue.add(chunk); // LinkedBlockingQueue.add() 不接受 null，无容量限制永不阻塞

                posRef[0] = nextPos;
                total++;
            }
            streamingDone[0] = true;
            Log.i(TAG, "Phase 2 built: " + total + " chunks, queue=" + chunkQueue.size());
        }, "phase2").start();
    }

    /** 从 start 位置开始扫描 n 个换行后的位置 */
    private static int scanLines(String text, int start, int lines) {
        int pos = start;
        int nl = 0;
        while (pos < text.length() && nl < lines) {
            if (text.charAt(pos) == '\n') nl++;
            pos++;
        }
        return pos;
    }

    /** 构建 [start, end) 范围的 SpannableString chunk */
    private static SpannableString buildChunk(String fullText, SpanInfo[] fullSpans,
                                               int start, int end, StyleConfig style) {
        ArrayList<SpanInfo> list = new ArrayList<>();
        for (SpanInfo s : fullSpans) {
            if (s.end <= start || s.start >= end) continue;
            int ls = Math.max(0, s.start - start);
            int le = Math.min(end - start, s.end - start);
            if (ls < le) {
                list.add(new SpanInfo(ls, le, s.type, s.level, s.extra));
            }
        }
        return buildSpannable(fullText.substring(start, end),
                list.toArray(new SpanInfo[0]), style);
    }

    // ==================== SpannableString 构建 ====================

    public static SpannableString buildSpannable(String plainText, SpanInfo[] spans,
                                           StyleConfig style) {
        SpannableString spannable = new SpannableString(plainText);
        applySpanArray(spannable, spans, style);
        return spannable;
    }

    private static void applySpanArray(Spannable spannable, SpanInfo[] spans,
                                        StyleConfig style) {
        for (SpanInfo span : spans) {
            if (span.start < 0 || span.end > spannable.length()
                    || span.start >= span.end) {
                continue;
            }
            applySingleSpan(spannable, span, style);
        }
    }

    private static void applySingleSpan(Spannable spannable, SpanInfo span,
                                         StyleConfig style) {
        int flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE;

        switch (span.type) {
            case SpanInfo.TYPE_HEADING:
                float sizeMult;
                if (span.level == 1) sizeMult = style.heading1Size;
                else if (span.level == 2) sizeMult = style.heading2Size;
                else sizeMult = style.heading3Size;
                spannable.setSpan(new RelativeSizeSpan(sizeMult), span.start, span.end, flag);
                spannable.setSpan(new StyleSpan(Typeface.BOLD), span.start, span.end, flag);
                spannable.setSpan(new ForegroundColorSpan(style.headingColor),
                        span.start, span.end, flag);
                break;

            case SpanInfo.TYPE_BOLD:
                spannable.setSpan(new StyleSpan(Typeface.BOLD), span.start, span.end, flag);
                spannable.setSpan(new ForegroundColorSpan(style.boldColor),
                        span.start, span.end, flag);
                break;

            case SpanInfo.TYPE_ITALIC:
                spannable.setSpan(new StyleSpan(Typeface.ITALIC), span.start, span.end, flag);
                spannable.setSpan(new ForegroundColorSpan(style.italicColor),
                        span.start, span.end, flag);
                break;

            case SpanInfo.TYPE_CODE:
                spannable.setSpan(new BackgroundColorSpan(style.codeBgColor),
                        span.start, span.end, flag);
                spannable.setSpan(new ForegroundColorSpan(style.codeTextColor),
                        span.start, span.end, flag);
                break;

            case SpanInfo.TYPE_CODE_BLOCK:
                spannable.setSpan(new TypefaceSpan("monospace"), span.start, span.end, flag);
                spannable.setSpan(new BackgroundColorSpan(style.codeBlockBgColor),
                        span.start, span.end, flag);
                spannable.setSpan(new ForegroundColorSpan(style.codeTextColor),
                        span.start, span.end, flag);
                spannable.setSpan(new LeadingMarginSpan.Standard(16, 0),
                        span.start, span.end, flag);
                break;

            case SpanInfo.TYPE_LIST:
                spannable.setSpan(new BulletSpan(16, style.bodyColor),
                        span.start, span.end, flag);
                break;

            case SpanInfo.TYPE_QUOTE:
                spannable.setSpan(new ForegroundColorSpan(style.quoteColor),
                        span.start, span.end, flag);
                spannable.setSpan(new LeadingMarginSpan.Standard(24, 16),
                        span.start, span.end, flag);
                break;

            case SpanInfo.TYPE_LINK:
                spannable.setSpan(new ForegroundColorSpan(style.linkColor),
                        span.start, span.end, flag);
                spannable.setSpan(new UnderlineSpan(), span.start, span.end, flag);
                break;

            case SpanInfo.TYPE_IMAGE:
                loadImageSpan(spannable, span);
                break;

            case SpanInfo.TYPE_RULE:
                break;
        }
    }

    // ==================== 图片加载 ====================

    private static void loadImageSpan(Spannable spannable, SpanInfo span) {
        if (span.extra == null || span.extra.isEmpty()) return;
        try {
            File file = new File(span.extra);
            if (file.exists()) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                Bitmap bitmap = BitmapFactory.decodeFile(span.extra, opts);
                if (bitmap != null) {
                    int maxWidth = 800;
                    int width = bitmap.getWidth();
                    float scale = (float) maxWidth / width;
                    if (scale < 1.0f) {
                        int h = (int) (bitmap.getHeight() * scale);
                        Bitmap scaled = Bitmap.createScaledBitmap(
                                bitmap, maxWidth, h, true);
                        bitmap.recycle();
                        bitmap = scaled;
                    }
                    BitmapDrawable drawable = new BitmapDrawable(bitmap);
                    drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                    spannable.setSpan(new ImageSpan(drawable),
                            span.start, span.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Image load failed: " + span.extra, e);
        }
    }
}
