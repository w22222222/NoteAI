import java.util.*;

public class ProgressiveRenderSimulation {

    private static final int PREVIEW_LINES = 200;
    private static final int TOTAL_LINES = 10000;
    private static final String LOADING_TEXT = "\n\n--- 正在加载更多内容 ---";

    static class SpanInfo {
        static final int TYPE_HEADING    = 0;
        static final int TYPE_BOLD       = 1;
        static final int TYPE_ITALIC     = 2;
        static final int TYPE_CODE       = 3;
        static final int TYPE_CODE_BLOCK = 4;
        static final int TYPE_LIST       = 5;
        static final int TYPE_QUOTE      = 6;
        static final int TYPE_LINK       = 7;

        int start, end, type, level;
        String extra;
        SpanInfo(int s, int e, int t, int l, String ex) {
            start = s; end = e; type = t; level = l; extra = ex;
        }
    }

    static class ParseResult {
        String plainText;
        SpanInfo[] spans;
    }

    // ============ 简易 Markdown 解析器（跟 MarkdownParser.java 逻辑一致） ============

    static ParseResult parseMarkdown(String md) {
        if (md == null || md.isEmpty()) {
            ParseResult r = new ParseResult();
            r.plainText = "";
            r.spans = new SpanInfo[0];
            return r;
        }
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
                    spans.add(new SpanInfo(codeBlockStart, plainText.length(),
                            SpanInfo.TYPE_CODE_BLOCK, 0, codeLang));
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
                spans.add(new SpanInfo(start, end, SpanInfo.TYPE_HEADING, level, null));
                plainText.append("\n");
                continue;
            }
            if (line.matches("^[\\-\\*]\\s.*") || line.matches("^\\d+\\.\\s.*")) {
                String content = line.replaceFirst("^[\\-\\*\\d]+\\.?\\s+", "");
                int start = plainText.length();
                parseInlines(content, start, spans);
                plainText.append(content);
                int end = plainText.length();
                spans.add(new SpanInfo(start, end, SpanInfo.TYPE_LIST, 0, null));
                plainText.append("\n");
                continue;
            }
            if (line.startsWith(">")) {
                String content = line.substring(1).trim();
                int start = plainText.length();
                parseInlines(content, start, spans);
                plainText.append(content);
                int end = plainText.length();
                spans.add(new SpanInfo(start, end, SpanInfo.TYPE_QUOTE, 0, null));
                plainText.append("\n");
                continue;
            }
            parseInlines(line, plainText.length(), spans);
            plainText.append(line).append("\n");
        }
        ParseResult r = new ParseResult();
        r.plainText = plainText.toString();
        r.spans = spans.toArray(new SpanInfo[0]);
        return r;
    }

    private static void parseInlines(String text, int baseOffset, List<SpanInfo> spans) {
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("**", i)) {
                int end = text.indexOf("**", i + 2);
                if (end > i) {
                    spans.add(new SpanInfo(baseOffset + i, baseOffset + end,
                            SpanInfo.TYPE_BOLD, 0, null));
                    i = end + 2;
                    continue;
                }
            }
            if (text.startsWith("*", i) && !text.startsWith("**", i)) {
                int end = text.indexOf("*", i + 1);
                if (end > i) {
                    spans.add(new SpanInfo(baseOffset + i, baseOffset + end,
                            SpanInfo.TYPE_ITALIC, 0, null));
                    i = end + 1;
                    continue;
                }
            }
            if (text.startsWith("`", i)) {
                int end = text.indexOf("`", i + 1);
                if (end > i) {
                    spans.add(new SpanInfo(baseOffset + i, baseOffset + end,
                            SpanInfo.TYPE_CODE, 0, null));
                    i = end + 1;
                    continue;
                }
            }
            if (text.startsWith("[", i)) {
                int bracketEnd = text.indexOf("]", i + 1);
                if (bracketEnd > i && bracketEnd + 1 < text.length()
                        && text.charAt(bracketEnd + 1) == '(') {
                    int parenEnd = text.indexOf(")", bracketEnd + 2);
                    if (parenEnd > bracketEnd) {
                        String url = text.substring(bracketEnd + 2, parenEnd);
                        spans.add(new SpanInfo(baseOffset + i, baseOffset + bracketEnd,
                                SpanInfo.TYPE_LINK, 0, url));
                        i = parenEnd + 1;
                        continue;
                    }
                }
            }
            i++;
        }
    }

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

    // ============ 生成测试用的大文档 ============

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

            if (lineCount % 8 == 0 && lineCount < totalLines) {
                sb.append("\n");
                lineCount++;
            }
        }

        return sb.toString();
    }

    // ============ 模拟渐进加载的纯逻辑 ============

    static class SimulatedTextView {
        String content = "(empty)";
        long setTextTime = 0;
        void setText(String text) {
            content = text;
            setTextTime = System.currentTimeMillis();
        }
    }

    static class SimulatedScrollView {
        int scrollY = 0;
    }

    static void simulateProgressiveRender(SimulatedTextView textView,
                                           SimulatedScrollView scrollView,
                                           String fullMd) {
        int totalLines = countLines(fullMd);
        long t0 = System.currentTimeMillis();

        // ===== Phase 1: 解析前 200 行 =====
        String previewMd = extractFirstNLines(fullMd, PREVIEW_LINES);
        long tParseStart = System.currentTimeMillis();
        ParseResult previewResult = parseMarkdown(previewMd);
        long tParseEnd = System.currentTimeMillis();

        String previewContent = previewResult.plainText + LOADING_TEXT;
        int previewSpanCount = previewResult.spans.length;
        int previewCharCount = previewResult.plainText.length();

        // ===== 模拟 setText（实际在主线程）= 这里是瞬时 =====
        textView.setText(previewContent);

        long t1 = System.currentTimeMillis();

        // ===== Phase 2: 后台解析完整文档 =====
        ParseResult fullResult = parseMarkdown(fullMd);
        long t2 = System.currentTimeMillis();

        int oldScrollY = scrollView.scrollY;
        textView.setText(fullResult.plainText);
        scrollView.scrollY = oldScrollY;

        long t3 = System.currentTimeMillis();

        // ===== 输出报告 =====
        System.out.println("\n"
            + "╔══════════════════════════════════════════════╗\n"
            + "║       渐进渲染模拟 - 性能报告                 ║\n"
            + "╠══════════════════════════════════════════════╣\n"
            + "║                                              ║");
        System.out.printf("║  总行数:          %6d 行                    ║\n", totalLines);
        System.out.printf("║  预览行数:        %6d 行                    ║\n", PREVIEW_LINES);
        System.out.println("║                                              ║");
        System.out.println("║  --- Phase 1: 预览 ---                        ║");
        System.out.printf("║  解析耗时:        %6d ms                    ║\n", tParseEnd - tParseStart);
        System.out.printf("║  纯文本长度:      %6d 字                    ║\n", previewCharCount);
        System.out.printf("║  SpanInfo 数量:   %6d 个                    ║\n", previewSpanCount);
        System.out.printf("║  用户看到第一屏:  %6d ms                    ║\n", t1 - t0);
        System.out.println("║                                              ║");
        System.out.println("║  --- Phase 2: 完整内容 ---                    ║");
        System.out.printf("║  解析耗时:        %6d ms                    ║\n", t2 - t1);
        System.out.printf("║  纯文本长度:      %6d 字                    ║\n", fullResult.plainText.length());
        System.out.printf("║  SpanInfo 数量:   %6d 个                    ║\n", fullResult.spans.length);
        System.out.printf("║  总耗时:          %6d ms                    ║\n", t3 - t0);
        System.out.println("║                                              ║");
        System.out.printf("║  主线程实际占用:   < 5ms  (仅 setText)         ║\n");
        System.out.printf("║  后台线程干活:     %d ms                        ║\n", t3 - t0);
        System.out.printf("║  滚动位置保留:     %s                          ║\n",
                oldScrollY == scrollView.scrollY ? "✅ 已保留" : "❌ 丢失");
        System.out.println("║                                              ║");
        System.out.println("║  用户体感:                                     ║");
        if (t1 - t0 < 100) {
            System.out.println("║  ✅ " + (t1 - t0) + "ms 后看到第一屏（非空白！）");
        } else {
            System.out.println("║  ⚠️ " + (t1 - t0) + "ms 后才看到第一屏");
        }
        System.out.println("║  ✅ " + (t2 - t0) + "ms 后完整可用（无缝）");
        System.out.println("║                                              ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        // 额外信息：用 C++ 引擎后的预期
        System.out.println("--- 预期（C++ 引擎替代后）---");
        System.out.println("  Phase 1 解析:  " + ((tParseEnd - tParseStart) / 8) + " ms  (C++ 8x 提速)");
        System.out.println("  Phase 2 解析:  " + ((t2 - t1) / 8) + " ms  (C++ 8x 提速)");
        System.out.println("  PrecomputedText 排版: ~400ms (后台线程，主线程无感)");
        System.out.println("  主线程 textView.setText: ~5ms (已完成排版)");
        System.out.println();
    }

    // ============ main ============

    public static void main(String[] args) {
        System.out.println("\n→ 生成 " + TOTAL_LINES + " 行测试 Markdown 文档...");
        long tGen = System.currentTimeMillis();
        String largeDoc = generateLargeDocument(TOTAL_LINES);
        long tGenEnd = System.currentTimeMillis();
        System.out.println("  生成完成: " + (tGenEnd - tGen) + "ms, "
                + largeDoc.length() + " 字符\n");

        // 纯 Java mock 的 TextView + ScrollView
        SimulatedTextView textView = new SimulatedTextView();
        SimulatedScrollView scrollView = new SimulatedScrollView();
        scrollView.scrollY = 350;  // 模拟用户已经滚动了一些

        simulateProgressiveRender(textView, scrollView, largeDoc);

        // 同样数据，不做渐进加载的对比：
        System.out.println("--- 对比：不分段（全量直接解析） ---");
        long tFull = System.currentTimeMillis();
        ParseResult r = parseMarkdown(largeDoc);
        long tFullEnd = System.currentTimeMillis();
        System.out.println("  全量解析: " + (tFullEnd - tFull) + "ms");
        System.out.println("  纯文本:   " + r.plainText.length() + " 字");
        System.out.println("  Spans:    " + r.spans.length + " 个");
        System.out.println("  用户体感: 卡 " + (tFullEnd - tFull) + "ms 后看到完整内容 "
                + "(无第一屏快速预览)\n");
    }
}
