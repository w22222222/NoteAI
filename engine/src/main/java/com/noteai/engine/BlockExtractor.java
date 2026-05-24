package com.noteai.engine;

import java.util.ArrayList;
import java.util.List;

public class BlockExtractor {

    public static List<Block> extract(String plainText, SpanInfo[] allSpans) {
        List<Block> blocks = new ArrayList<>();
        if (plainText == null || plainText.isEmpty()) return blocks;

        List<SpanInfo> blockSpans = new ArrayList<>();
        List<SpanInfo> inlineSpans = new ArrayList<>();
        for (SpanInfo s : allSpans) {
            if (s == null) continue;
            if (isBlockType(s.type)) {
                blockSpans.add(s);
            } else {
                inlineSpans.add(s);
            }
        }

        blockSpans.sort((a, b) -> Integer.compare(a.start, b.start));
        inlineSpans.sort((a, b) -> Integer.compare(a.start, b.start));

        SpanCursor cursor = new SpanCursor(inlineSpans);
        int pos = 0;
        for (SpanInfo bs : blockSpans) {
            int blockStart = clamp(bs.start, 0, plainText.length());
            int blockEnd = clamp(bs.end, blockStart, plainText.length());

            if (pos < blockStart) {
                Block para = makeParagraph(plainText, cursor, pos, blockStart);
                if (para != null) blocks.add(para);
            }

            if (bs.type == SpanInfo.TYPE_RULE) {
                blocks.add(Block.rule());
                cursor.skipBefore(blockEnd);
                pos = blockEnd;
                continue;
            }

            String text = plainText.substring(blockStart, blockEnd);
            SpanInfo[] rebased = cursor.collect(blockStart, blockEnd, blockStart, blockEnd - blockStart);
            switch (bs.type) {
                case SpanInfo.TYPE_HEADING:
                    blocks.add(Block.heading(text, rebased, bs.level));
                    break;
                case SpanInfo.TYPE_CODE_BLOCK:
                    blocks.add(Block.codeBlock(text, rebased, bs.extra));
                    break;
                case SpanInfo.TYPE_LIST:
                    blocks.add(Block.list(text, rebased));
                    break;
                case SpanInfo.TYPE_QUOTE:
                    blocks.add(Block.quote(text, rebased));
                    break;
                case SpanInfo.TYPE_IMAGE:
                    blocks.add(makeImageBlock(plainText, bs));
                    pos = skipImageMeta(plainText, blockEnd);
                    continue;
                default:
                    blocks.add(Block.paragraph(text, rebased));
                    break;
            }
            pos = blockEnd;
        }

        if (pos < plainText.length()) {
            Block para = makeParagraph(plainText, cursor, pos, plainText.length());
            if (para != null) blocks.add(para);
        }

        return blocks;
    }

    private static Block makeParagraph(String text, SpanCursor cursor, int start, int end) {
        int contentStart = start;
        int contentEnd = end;
        while (contentStart < contentEnd && text.charAt(contentStart) == '\n') contentStart++;
        while (contentEnd > contentStart && text.charAt(contentEnd - 1) == '\n') contentEnd--;
        if (contentStart >= contentEnd) {
            cursor.skipBefore(end);
            return null;
        }
        String trimmed = text.substring(contentStart, contentEnd);
        SpanInfo[] rebased = cursor.collect(contentStart, contentEnd, contentStart, trimmed.length());
        cursor.skipBefore(end);
        return Block.paragraph(trimmed, rebased);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static Block makeImageBlock(String plainText, SpanInfo span) {
        String path = span.extra != null ? span.extra : "";

        String alt = "";
        if (span.start < plainText.length() && span.end <= plainText.length()
                && span.end > span.start) {
            String spanText = plainText.substring(span.start, span.end);
            alt = spanText.replaceAll("^\\[|\\]$", "");
        }

        int width = 0;
        int height = 0;

        if (span.end < plainText.length() && plainText.charAt(span.end) == '{') {
            int closeBrace = plainText.indexOf('}', span.end);
            if (closeBrace > span.end) {
                String meta = plainText.substring(span.end + 1, closeBrace);
                width = parseMetaInt(meta, "width");
                height = parseMetaInt(meta, "height");
            }
        }

        return Block.image(path, alt, width, height);
    }

    private static int skipImageMeta(String plainText, int from) {
        if (from < plainText.length() && plainText.charAt(from) == '{') {
            int closeBrace = plainText.indexOf('}', from);
            if (closeBrace >= from) {
                return closeBrace + 1;
            }
        }
        return from;
    }

    private static int parseMetaInt(String meta, String key) {
        int idx = meta.indexOf(key + "=");
        if (idx < 0) return 0;
        int start = idx + key.length() + 1;
        int end = start;
        while (end < meta.length() && Character.isDigit(meta.charAt(end))) {
            end++;
        }
        if (end > start) {
            try {
                return Integer.parseInt(meta.substring(start, end));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean isBlockType(int type) {
        return type == SpanInfo.TYPE_HEADING
            || type == SpanInfo.TYPE_CODE_BLOCK
            || type == SpanInfo.TYPE_LIST
            || type == SpanInfo.TYPE_QUOTE
            || type == SpanInfo.TYPE_RULE
            || type == SpanInfo.TYPE_TABLE
            || type == SpanInfo.TYPE_IMAGE;
    }

    private static class SpanCursor {
        private final List<SpanInfo> spans;
        private int index;

        SpanCursor(List<SpanInfo> spans) {
            this.spans = spans;
        }

        void skipBefore(int position) {
            while (index < spans.size() && spans.get(index).end <= position) {
                index++;
            }
        }

        SpanInfo[] collect(int rangeStart, int rangeEnd, int baseStart, int maxEnd) {
            skipBefore(rangeStart);
            ArrayList<SpanInfo> result = new ArrayList<>();
            int i = index;
            while (i < spans.size()) {
                SpanInfo s = spans.get(i);
                if (s.start >= rangeEnd) break;
                if (s.end > rangeStart) {
                    int ls = Math.max(0, s.start - baseStart);
                    int le = Math.min(maxEnd, s.end - baseStart);
                    if (ls < le) {
                        result.add(new SpanInfo(ls, le, s.type, s.level, s.extra));
                    }
                }
                i++;
            }
            return result.toArray(new SpanInfo[0]);
        }
    }
}
