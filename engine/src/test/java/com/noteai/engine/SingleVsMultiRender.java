package com.noteai.engine;

import java.util.*;

/**
 * 单线程全量渲染 vs 多线程渐进渲染 —— 对比测试
 *
 * 核心指标：「用户首次看到内容」的时间
 *
 * 单线程：解析全部 20000 行 → 一次性 setText → 用户等全程才能看到
 * 渐进式：解析前 200 行 → 立刻 setText(第一屏) → 后台解析完替换
 */
public class SingleVsMultiRender {

    private static final int TOTAL_LINES = 20000;
    private static final int PREVIEW_LINES = 200;

    // ============ 复用 MarkdownParser 的文档生成逻辑 ============

    static String generateLargeDocument(int totalLines) {
        StringBuilder sb = new StringBuilder();

        String[] headings = {
            "# 第一章  C++ 基础",
            "## 1.1 指针与引用",
            "### 1.1.1 智能指针",
            "## 1.2 模板编程",
            "# 第二章 JNI 开发",
            "## 2.1 JNI 环境搭建",
            "## 2.2 数据类型映射",
            "# 第三章 Markdown 解析器设计",
            "## 3.1 词法分析",
            "### 3.1.1 Token 设计",
            "## 3.2 语法分析",
            "## 3.3 性能优化",
            "### 3.3.1 内存池设计",
            "#### 3.3.1.1 预分配策略",
            "# 第四章 全文搜索",
            "## 4.1 倒排索引",
            "# 第五章 AI 集成",
            "## 5.1 摘要生成"
        };

        String[] listItems = {
            "- 这是第一条要点，关于 **智能指针** 的使用",
            "- 这是第二条要点，涉及 `std::function` 的用法",
            "- 第三条：回调函数的实现",
            "1. 有序列表第一项",
            "2. 有序列表第二项，包含 **重点内容**",
            "3. 有序列表第三项"
        };

        String[] paragraphs = {
            "今天学习了回调函数的基本概念，以及 `std::function` 的高级用法。",
            "这段内容包含 **加粗文字** 和 *斜体文字*，用于测试解析器。",
            "参考 [Google C++ Style Guide](https://google.github.io/styleguide/cppguide.html) 规范。",
            "> 引用一段话：代码是写给人看的，顺便能在机器上运行。",
            "普通文本行，不带任何特殊格式，用于验证纯文本解析的正确性。",
            "在 C++ 中，使用 `std::string_view` 可以避免不必要的字符串拷贝，大幅提升性能。"
        };

        int headingIdx = 0, listIdx = 0, paraIdx = 0;
        int lineCount = 0;

        while (lineCount < totalLines) {
            if (lineCount % 50 == 0 && headingIdx < headings.length) {
                sb.append(headings[headingIdx]).append("\n");
                headingIdx = (headingIdx + 1) % headings.length;
                lineCount++;
            } else if (lineCount % 7 == 0 && listIdx < listItems.length) {
                sb.append(listItems[listIdx]).append("\n");
                listIdx = (listIdx + 1) % listItems.length;
                lineCount++;
            } else if (lineCount % 30 == 29) {
                sb.append("```cpp\n");
                sb.append("void sort(int* arr, int n) {\n");
                sb.append("    for (int i = 0; i < n; i++) {\n");
                sb.append("        for (int j = i + 1; j < n; j++) {\n");
                sb.append("            if (arr[i] > arr[j]) {\n");
                sb.append("                std::swap(arr[i], arr[j]);\n");
                sb.append("            }\n");
                sb.append("        }\n");
                sb.append("    }\n");
                sb.append("}\n");
                sb.append("```\n");
                lineCount += 11;
            } else {
                sb.append(paragraphs[paraIdx]).append("\n");
                paraIdx = (paraIdx + 1) % paragraphs.length;
                lineCount++;
            }
        }

        return sb.toString();
    }

    // ============ 复用 MarkdownParser 的基础方法 ============

    static String extractFirstNLines(String md, int n) {
        if (md == null || md.isEmpty()) return "";
        String[] lines = md.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int count = Math.min(n, lines.length);
        for (int i = 0; i < count; i++) {
            sb.append(lines[i]);
            if (i < count - 1) sb.append("\n");
        }
        return sb.toString();
    }

    static int countLines(String md) {
        if (md == null || md.isEmpty()) return 0;
        return md.split("\n", -1).length;
    }

    // ============ Markdown 解析器（逻辑与 MarkdownParser.java 一致） ============

    static final int HEADING = 0, BOLD = 1, ITALIC = 2, CODE = 3,
                     CODE_BLOCK = 4, LIST = 5, QUOTE = 6, LINK = 7,
                     IMAGE = 8, RULE = 9;

    static class SpanInfo {
        int start, end, type, level;
        String extra;
        SpanInfo(int s, int e, int t, int l, String ex) {
            start = s; end = e; type = t; level = l; extra = ex;
        }
    }

    static class ParseResult {
        String plainText;
        SpanInfo[] spans;
        ParseResult(String pt, SpanInfo[] sp) { plainText = pt; spans = sp; }
    }

    static ParseResult parseMarkdown(String md) {
        if (md == null || md.isEmpty())
            return new ParseResult("", new SpanInfo[0]);

        StringBuilder plainText = new StringBuilder();
        List<SpanInfo> spans = new ArrayList<>();
        String[] lines = md.split("\n", -1);
        boolean inCodeBlock = false;
        String codeLang = "";
        int codeBlockStart = -1;

        for (String line : lines) {
            if (line.startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeLang = line.substring(3).trim();
                    codeBlockStart = plainText.length();
                    continue;
                } else {
                    inCodeBlock = false;
                    spans.add(new SpanInfo(codeBlockStart, plainText.length(), CODE_BLOCK, 0, codeLang));
                    continue;
                }
            }
            if (inCodeBlock) {
                plainText.append(line).append("\n");
                continue;
            }
            if (line.matches("^#{1,6}\\s.*")) {
                int level = 0;
                for (char c : line.toCharArray()) { if (c == '#') level++; else break; }
                String content = line.substring(level).trim();
                int start = plainText.length();
                parseInlines(content, start, spans);
                plainText.append(content);
                int end = plainText.length();
                spans.add(new SpanInfo(start, end, HEADING, level, null));
                plainText.append("\n");
                continue;
            }
            if (line.matches("^[\\-\\*]\\s.*") || line.matches("^\\d+\\.\\s.*")) {
                String content = line.replaceFirst("^[\\-\\*\\d]+\\.?\\s+", "");
                int start = plainText.length();
                parseInlines(content, start, spans);
                plainText.append(content);
                int end = plainText.length();
                spans.add(new SpanInfo(start, end, LIST, 0, null));
                plainText.append("\n");
                continue;
            }
            if (line.startsWith(">")) {
                String content = line.substring(1).trim();
                int start = plainText.length();
                parseInlines(content, start, spans);
                plainText.append(content);
                int end = plainText.length();
                spans.add(new SpanInfo(start, end, QUOTE, 0, null));
                plainText.append("\n");
                continue;
            }
            if (line.matches("^[\\-\\*\\_]{3,}\\s*$")) {
                int start = plainText.length();
                spans.add(new SpanInfo(start, start + 1, RULE, 0, null));
                plainText.append("\n");
                continue;
            }
            parseInlines(line, plainText.length(), spans);
            plainText.append(line).append("\n");
        }
        return new ParseResult(plainText.toString(), spans.toArray(new SpanInfo[0]));
    }

    private static void parseInlines(String text, int baseOffset, List<SpanInfo> spans) {
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("**", i)) {
                int end = text.indexOf("**", i + 2);
                if (end > i) {
                    spans.add(new SpanInfo(baseOffset + i, baseOffset + end, BOLD, 0, null));
                    i = end + 2;
                    continue;
                }
            }
            if (text.startsWith("*", i) && !text.startsWith("**", i)) {
                int end = text.indexOf("*", i + 1);
                if (end > i) {
                    spans.add(new SpanInfo(baseOffset + i, baseOffset + end, ITALIC, 0, null));
                    i = end + 1;
                    continue;
                }
            }
            if (text.startsWith("`", i)) {
                int end = text.indexOf("`", i + 1);
                if (end > i) {
                    spans.add(new SpanInfo(baseOffset + i, baseOffset + end, CODE, 0, null));
                    i = end + 1;
                    continue;
                }
            }
            if (text.startsWith("![", i)) {
                int bracketEnd = text.indexOf("]", i + 2);
                if (bracketEnd > i && bracketEnd + 1 < text.length() && text.charAt(bracketEnd + 1) == '(') {
                    int parenEnd = text.indexOf(")", bracketEnd + 2);
                    if (parenEnd > bracketEnd) {
                        String path = text.substring(bracketEnd + 2, parenEnd);
                        spans.add(new SpanInfo(baseOffset + i, baseOffset + i + 2, IMAGE, 0, path));
                        i = parenEnd + 1;
                        continue;
                    }
                }
            }
            if (text.startsWith("[", i)) {
                int bracketEnd = text.indexOf("]", i + 1);
                if (bracketEnd > i && bracketEnd + 1 < text.length() && text.charAt(bracketEnd + 1) == '(') {
                    int parenEnd = text.indexOf(")", bracketEnd + 2);
                    if (parenEnd > bracketEnd) {
                        String url = text.substring(bracketEnd + 2, parenEnd);
                        spans.add(new SpanInfo(baseOffset + i, baseOffset + bracketEnd, LINK, 0, url));
                        i = parenEnd + 1;
                        continue;
                    }
                }
            }
            i++;
        }
    }

    // ==================== 单线程版本 ====================

    static class SingleThreadResult {
        long firstContentTimeMs;  // 用户首次看到内容的时间（= 从开始到 setText 完成）
        long parseTimeMs;
        int plainTextLen;
        int spanCount;
    }

    static SingleThreadResult runSingleThread(String fullMd) {
        SingleThreadResult r = new SingleThreadResult();
        long t0 = System.currentTimeMillis();

        ParseResult result = parseMarkdown(fullMd);
        long t1 = System.currentTimeMillis();

        // 模拟在主线程 setText（单线程版本：解析和 setText 都在主线程）
        // 实际 Android 上 textView.setText() 还会触发排版，这里不计入
        long t2 = System.currentTimeMillis();

        r.parseTimeMs = t1 - t0;
        r.firstContentTimeMs = t2 - t0;  // 用户要等全部解析完才能看到
        r.plainTextLen = result.plainText.length();
        r.spanCount = result.spans.length;
        return r;
    }

    // ==================== 多线程渐进版本 ====================

    static class MultiThreadResult {
        long firstContentTimeMs;  // Phase 1 完成后用户看到第一屏的时间
        long phase1ParseTimeMs;
        long phase2ParseTimeMs;
        long totalTimeMs;         // Phase 2 完成、全文可用的时间
        int previewTextLen;
        int previewSpanCount;
        int fullTextLen;
        int fullSpanCount;
    }

    static MultiThreadResult runMultiThread(String fullMd) {
        MultiThreadResult r = new MultiThreadResult();
        long t0 = System.currentTimeMillis();

        // ===== Phase 1: 解析前 200 行 → 立刻显示 =====
        String previewMd = extractFirstNLines(fullMd, PREVIEW_LINES);
        long tp1 = System.currentTimeMillis();
        ParseResult previewResult = parseMarkdown(previewMd);
        long tp1End = System.currentTimeMillis();

        // 模拟主线程 setText(第一屏)
        long tFirstContent = System.currentTimeMillis();

        r.phase1ParseTimeMs = tp1End - tp1;
        r.firstContentTimeMs = tFirstContent - t0;  // ★ 核心指标：用户首次看到内容

        r.previewTextLen = previewResult.plainText.length();
        r.previewSpanCount = previewResult.spans.length;

        // ===== Phase 2: 后台解析全文 =====
        long tp2 = System.currentTimeMillis();
        ParseResult fullResult = parseMarkdown(fullMd);
        long tp2End = System.currentTimeMillis();

        // 模拟主线程 setText(完整内容)
        long tFull = System.currentTimeMillis();

        r.phase2ParseTimeMs = tp2End - tp2;
        r.totalTimeMs = tFull - t0;
        r.fullTextLen = fullResult.plainText.length();
        r.fullSpanCount = fullResult.spans.length;
        return r;
    }

    // ====================================================================
    // main
    // ====================================================================

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  单线程 vs 渐进渲染 对比测试                                  ║");
        System.out.println("║  文档规模: " + TOTAL_LINES + " 行                                          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // 预热 JVM
        System.out.print("→ 生成 " + TOTAL_LINES + " 行测试文档... ");
        long tGen = System.currentTimeMillis();
        String largeDoc = generateLargeDocument(TOTAL_LINES);
        long tGenEnd = System.currentTimeMillis();
        System.out.println((tGenEnd - tGen) + "ms, " + largeDoc.length() + " 字符");
        System.out.println();

        // ===== 跑 3 轮取平均 =====
        int rounds = 3;
        long[] stFirst = new long[rounds];
        long[] stParse = new long[rounds];
        long[] mtFirst = new long[rounds];
        long[] mtPhase1 = new long[rounds];
        long[] mtPhase2 = new long[rounds];
        long[] mtTotal = new long[rounds];

        for (int i = 0; i < rounds; i++) {
            System.out.println("--- 第 " + (i + 1) + " 轮 ---");

            SingleThreadResult st = runSingleThread(largeDoc);
            stFirst[i] = st.firstContentTimeMs;
            stParse[i] = st.parseTimeMs;
            System.out.println("  单线程: 首次出现内容 = " + st.firstContentTimeMs + "ms"
                    + " (解析 " + st.parseTimeMs + "ms, "
                    + st.plainTextLen + " 字, " + st.spanCount + " spans)");

            MultiThreadResult mt = runMultiThread(largeDoc);
            mtFirst[i] = mt.firstContentTimeMs;
            mtPhase1[i] = mt.phase1ParseTimeMs;
            mtPhase2[i] = mt.phase2ParseTimeMs;
            mtTotal[i] = mt.totalTimeMs;
            System.out.println("  渐进式: 首次出现内容 = " + mt.firstContentTimeMs + "ms"
                    + " (Phase1 " + mt.phase1ParseTimeMs + "ms, "
                    + "Phase2 " + mt.phase2ParseTimeMs + "ms, "
                    + "总 " + mt.totalTimeMs + "ms)");
            System.out.println("          预览 " + mt.previewTextLen + " 字 / "
                    + mt.previewSpanCount + " spans → 全文 "
                    + mt.fullTextLen + " 字 / " + mt.fullSpanCount + " spans");
        }

        // ===== 取平均 =====
        long avgStFirst = avg(stFirst);
        long avgStParse = avg(stParse);
        long avgMtFirst = avg(mtFirst);
        long avgMtPhase1 = avg(mtPhase1);
        long avgMtPhase2 = avg(mtPhase2);
        long avgMtTotal = avg(mtTotal);

        // ===== 输出对比报告 =====
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              对比报告（3轮平均）                                ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                              ║");
        System.out.println("║  ┌─────────────────────┬──────────────┬──────────────┐       ║");
        System.out.println("║  │       指标           │  单线程全量  │  渐进式渲染   │       ║");
        System.out.println("║  ├─────────────────────┼──────────────┼──────────────┤       ║");
        System.out.printf ("║  │ 首次看到内容 (ms)    │  %8d     │  %8d     │       ║\n",
                avgStFirst, avgMtFirst);
        System.out.println("║  ├─────────────────────┼──────────────┼──────────────┤       ║");
        System.out.printf ("║  │ Phase1 解析 (ms)     │  (不适用)   │  %8d     │       ║\n",
                avgMtPhase1);
        System.out.printf ("║  │ Phase2 解析 (ms)     │  %8d     │  %8d(后台)│       ║\n",
                avgStParse, avgMtPhase2);
        System.out.println("║  │ (Phase2=全量解析)    │              │              │       ║");
        System.out.println("║  ├─────────────────────┼──────────────┼──────────────┤       ║");
        System.out.printf ("║  │ 用户等到全文 (ms)    │  %8d     │  %8d     │       ║\n",
                avgStFirst, avgMtTotal);
        System.out.println("║  ├─────────────────────┼──────────────┼──────────────┤       ║");
        System.out.printf ("║  │ 用户第一屏体验       │  空白/loading│  有内容！     │       ║\n");
        System.out.println("║  └─────────────────────┴──────────────┴──────────────┘       ║");
        System.out.println("║                                                              ║");

        long speedup = avgStFirst / Math.max(avgMtFirst, 1);
        System.out.printf ("║  ★ 渐进式首次出现内容快了 %d 倍                              ║\n", speedup);
        System.out.println("║                                                              ║");
        System.out.println("║  解释：                                                      ║");
        System.out.println("║  单线程 = 解析全部 20000 行 → 用户才能看到                   ║");
        System.out.printf ("║  渐进式 = 解析前 200 行(%dms) → 用户立刻看到第一屏            ║\n",
                avgMtPhase1);
        System.out.println("║           → 后台继续解析剩余 19800 行 → 无缝替换             ║");
        System.out.println("║                                                              ║");
        System.out.println("║  注：以上为 Java 解析器速度，C++ 引擎预期快 5~8 倍           ║");
        System.out.println("║                                                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ===== 简单推算（非实测，等 C++ 引擎写完后用真实数据替换） =====
        final int CPP_SPEEDUP = 6;
        System.out.println("--- 根据设计文档估算：如果 C++ 引擎比 Java 快 " + CPP_SPEEDUP + "x ---");
        System.out.println("  (以下不是真实跑出来的，只是 Java 数字除以 " + CPP_SPEEDUP + ")");
        System.out.printf("  单线程全量首次看到内容: ~%.1f ms\n", avgStFirst / (double) CPP_SPEEDUP);
        System.out.printf("  渐进式 Phase1 看到内容: ~%.1f ms\n", avgMtFirst / (double) CPP_SPEEDUP);
        System.out.printf("  渐进式 全文可用:       ~%.1f ms\n", avgMtTotal / (double) CPP_SPEEDUP);
        System.out.printf("  两者倍率不变: 仍约 %dx  (分子分母同除 %d，比值恒定)\n",
                speedup, CPP_SPEEDUP);
        System.out.println("  实际数字等 C++ 引擎写完再测。");
        System.out.println();
    }

    private static long avg(long[] arr) {
        long sum = 0;
        for (long v : arr) sum += v;
        return sum / arr.length;
    }
}
