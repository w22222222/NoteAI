package com.noteai.engine;

public class SpanInfo {
    public static final int TYPE_HEADING    = 0;
    public static final int TYPE_BOLD       = 1;
    public static final int TYPE_ITALIC     = 2;
    public static final int TYPE_CODE       = 3;
    public static final int TYPE_CODE_BLOCK = 4;
    public static final int TYPE_LIST       = 5;
    public static final int TYPE_QUOTE      = 6;
    public static final int TYPE_LINK       = 7;
    public static final int TYPE_IMAGE      = 8;
    public static final int TYPE_RULE       = 9;
    public static final int TYPE_TABLE      = 10;

    public int start;
    public int end;
    public int type;
    public int level;
    public String extra;

    public SpanInfo() {}

    public SpanInfo(int start, int end, int type, int level, String extra) {
        this.start = start;
        this.end = end;
        this.type = type;
        this.level = level;
        this.extra = extra;
    }
}
