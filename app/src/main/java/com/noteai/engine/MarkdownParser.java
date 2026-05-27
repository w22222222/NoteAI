package com.noteai.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class MarkdownParser {

    private static final Pattern HEADING_PAT = Pattern.compile("^#{1,6}\\s.*");
    private static final Pattern UNORDERED_LIST_PAT = Pattern.compile("^[\\-\\*]\\s.*");
    private static final Pattern ORDERED_LIST_PAT = Pattern.compile("^\\d+\\.\\s.*");
    private static final Pattern RULE_PAT = Pattern.compile("^[\\-\\*\\_]{3,}\\s*$");

    public static class ParseResult {
        public String plainText;
        public SpanInfo[] spans;
    }

    public static ParseResult parse(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            ParseResult r = new ParseResult();
            r.plainText = "";
            r.spans = new SpanInfo[0];
            return r;
        }

        StringBuilder plainText = new StringBuilder();
        List<SpanInfo> spans = new ArrayList<>();

        String[] lines = markdown.split("\n", -1);
        boolean inCodeBlock = false;
        String codeLang = "";
        int codeBlockStart = -1;

        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];

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

            if (HEADING_PAT.matcher(line).matches()) {
                int level = 0;
                for (char c : line.toCharArray()) {
                    if (c == '#') level++; else break;
                }
                String content = line.substring(level).trim();
                int start = plainText.length();
                parseInlines(content, plainText, spans);
                int end = plainText.length();
                spans.add(new SpanInfo(start, end, SpanInfo.TYPE_HEADING, level, null));
                plainText.append("\n");
                continue;
            }

            if (UNORDERED_LIST_PAT.matcher(line).matches()) {
                String content = line.substring(1).trim();
                int start = plainText.length();
                parseInlines(content, plainText, spans);
                int end = plainText.length();
                spans.add(new SpanInfo(start, end, SpanInfo.TYPE_LIST, 0, null));
                plainText.append("\n");
                continue;
            }

            if (ORDERED_LIST_PAT.matcher(line).matches()) {
                String content = line.replaceFirst("^\\d+\\.\\s+", "");
                int start = plainText.length();
                parseInlines(content, plainText, spans);
                int end = plainText.length();
                spans.add(new SpanInfo(start, end, SpanInfo.TYPE_LIST, 0, null));
                plainText.append("\n");
                continue;
            }

            if (line.startsWith(">")) {
                String content = line.substring(1).trim();
                int start = plainText.length();
                parseInlines(content, plainText, spans);
                int end = plainText.length();
                spans.add(new SpanInfo(start, end, SpanInfo.TYPE_QUOTE, 0, null));
                plainText.append("\n");
                continue;
            }

            if (RULE_PAT.matcher(line).matches()) {
                int start = plainText.length();
                int end = start + 1;
                spans.add(new SpanInfo(start, end, SpanInfo.TYPE_RULE, 0, null));
                plainText.append("\n");
                continue;
            }

            parseInlines(line, plainText, spans);
            plainText.append("\n");
        }

        ParseResult result = new ParseResult();
        result.plainText = plainText.toString();
        result.spans = spans.toArray(new SpanInfo[0]);
        return result;
    }

    private static void parseInlines(String text, StringBuilder cleanOut, List<SpanInfo> spans) {
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("**", i)) {
                int end = text.indexOf("**", i + 2);
                if (end > i) {
                    int spanStart = cleanOut.length();
                    cleanOut.append(text, i + 2, end);
                    int spanEnd = cleanOut.length();
                    spans.add(new SpanInfo(spanStart, spanEnd,
                            SpanInfo.TYPE_BOLD, 0, null));
                    i = end + 2;
                    continue;
                }
            }
            if (text.startsWith("*", i) && !text.startsWith("**", i)) {
                int end = text.indexOf("*", i + 1);
                if (end > i) {
                    int spanStart = cleanOut.length();
                    cleanOut.append(text, i + 1, end);
                    int spanEnd = cleanOut.length();
                    spans.add(new SpanInfo(spanStart, spanEnd,
                            SpanInfo.TYPE_ITALIC, 0, null));
                    i = end + 1;
                    continue;
                }
            }
            if (text.startsWith("`", i)) {
                int end = text.indexOf("`", i + 1);
                if (end > i) {
                    int spanStart = cleanOut.length();
                    cleanOut.append(text, i + 1, end);
                    int spanEnd = cleanOut.length();
                    spans.add(new SpanInfo(spanStart, spanEnd,
                            SpanInfo.TYPE_CODE, 0, null));
                    i = end + 1;
                    continue;
                }
            }
            if (text.startsWith("![", i)) {
                int bracketEnd = text.indexOf("]", i + 2);
                if (bracketEnd > i && bracketEnd + 1 < text.length()
                        && text.charAt(bracketEnd + 1) == '(') {
                    int parenEnd = text.indexOf(")", bracketEnd + 2);
                    if (parenEnd > bracketEnd) {
                        String alt = text.substring(i + 2, bracketEnd);
                        String path = text.substring(bracketEnd + 2, parenEnd);
                        int spanStart = cleanOut.length();
                        cleanOut.append("[").append(alt.isEmpty() ? "image" : alt).append("]");
                        int spanEnd = cleanOut.length();
                        spans.add(new SpanInfo(spanStart, spanEnd,
                                SpanInfo.TYPE_IMAGE, 0, path));
                        i = parenEnd + 1;
                        continue;
                    }
                }
            }
            if (text.startsWith("[", i)) {
                int bracketEnd = text.indexOf("]", i + 1);
                if (bracketEnd > i && bracketEnd + 1 < text.length()
                        && text.charAt(bracketEnd + 1) == '(') {
                    int parenEnd = text.indexOf(")", bracketEnd + 2);
                    if (parenEnd > bracketEnd) {
                        String url = text.substring(bracketEnd + 2, parenEnd);
                        int spanStart = cleanOut.length();
                        cleanOut.append(text, i + 1, bracketEnd);
                        int spanEnd = cleanOut.length();
                        spans.add(new SpanInfo(spanStart, spanEnd,
                                SpanInfo.TYPE_LINK, 0, url));
                        i = parenEnd + 1;
                        continue;
                    }
                }
            }
            cleanOut.append(text.charAt(i));
            i++;
        }
    }

    public static String extractPlainText(String markdown) {
        ParseResult r = parse(markdown);
        return r.plainText;
    }

    public static String extractFirstNLines(String markdown, int n) {
        if (markdown == null || markdown.isEmpty()) return "";
        String[] lines = markdown.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int count = Math.min(n, lines.length);
        for (int i = 0; i < count; i++) {
            sb.append(lines[i]);
            if (i < count - 1) sb.append("\n");
        }
        return sb.toString();
    }

    public static int countLines(String markdown) {
        if (markdown == null || markdown.isEmpty()) return 0;
        return markdown.split("\n", -1).length;
    }
}
