package com.noteai.engine;

public class Block {
    public static final int TYPE_HEADING = 0;
    public static final int TYPE_PARAGRAPH = 1;
    public static final int TYPE_CODE_BLOCK = 2;
    public static final int TYPE_LIST = 3;
    public static final int TYPE_QUOTE = 4;
    public static final int TYPE_RULE = 5;

    public final int type;
    public final String text;
    public final SpanInfo[] spans;
    public final int level;
    public final String lang;

    private Block(int type, String text, SpanInfo[] spans, int level, String lang) {
        this.type = type;
        this.text = text;
        this.spans = spans;
        this.level = level;
        this.lang = lang;
    }

    public static Block heading(String text, SpanInfo[] spans, int level) {
        return new Block(TYPE_HEADING, text, spans, level, null);
    }

    public static Block paragraph(String text, SpanInfo[] spans) {
        return new Block(TYPE_PARAGRAPH, text, spans, 0, null);
    }

    public static Block codeBlock(String text, SpanInfo[] spans, String lang) {
        return new Block(TYPE_CODE_BLOCK, text, spans, 0, lang);
    }

    public static Block list(String text, SpanInfo[] spans) {
        return new Block(TYPE_LIST, text, spans, 0, null);
    }

    public static Block quote(String text, SpanInfo[] spans) {
        return new Block(TYPE_QUOTE, text, spans, 0, null);
    }

    public static Block rule() {
        return new Block(TYPE_RULE, "", new SpanInfo[0], 0, null);
    }

    public boolean isEmpty() {
        return text.isEmpty() && spans.length == 0 && type != TYPE_RULE;
    }

    public String typeName() {
        switch (type) {
            case TYPE_HEADING: return "H";
            case TYPE_PARAGRAPH: return "P";
            case TYPE_CODE_BLOCK: return "C";
            case TYPE_LIST: return "L";
            case TYPE_QUOTE: return "Q";
            case TYPE_RULE: return "R";
            default: return "?";
        }
    }
}
