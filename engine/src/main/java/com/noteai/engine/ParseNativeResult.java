package com.noteai.engine;

/**
 * C++ JNI 返回的扁平化解析结果。
 * spansFlat: 每 5 个 int 代表一个 SpanInfo，顺序 {start, end, type, level, extraOffset}
 * extraData: 所有 Span 的额外字符串，以 \0 分隔，extraOffset 是该串在 extraData 中的字节偏移
 */
public class ParseNativeResult {
    public String plainText;
    public int[] spansFlat;
    public String extraData;
}
