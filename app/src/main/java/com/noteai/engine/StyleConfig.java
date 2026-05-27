package com.noteai.engine;

public class StyleConfig {

    public float heading1Size = 1.8f;
    public float heading2Size = 1.4f;
    public float heading3Size = 1.2f;
    public int headingColor = 0xFF333333;

    public float bodySize = 1.0f;
    public int bodyColor = 0xFF333333;

    public int boldColor = 0xFF333333;
    public int italicColor = 0xFF333333;

    public int codeBgColor = 0xFFF0F0F0;
    public int codeTextColor = 0xFF8B4513;
    public int codeBlockBgColor = 0xFFF5F5F5;

    public int linkColor = 0xFF1A73E8;

    public int quoteColor = 0xFF555555;
    public int quoteLineColor = 0xFFB0B0B0;

    public float lineSpacing = 1.5f;
    public int paddingLeft = 16;
    public int paddingRight = 16;
    public boolean isDarkMode = false;

    public StyleConfig() {}

    public static StyleConfig getDefault() {
        return new StyleConfig();
    }

    public static StyleConfig getDark() {
        StyleConfig config = new StyleConfig();
        config.headingColor = 0xFFFFFFFF;
        config.bodyColor = 0xFFCCCCCC;
        config.boldColor = 0xFFFFFFFF;
        config.italicColor = 0xFFCCCCCC;
        config.codeBgColor = 0xFF2D2D2D;
        config.codeTextColor = 0xFF88C0D0;
        config.codeBlockBgColor = 0xFF1E1E1E;
        config.linkColor = 0xFF88C0D0;
        config.quoteColor = 0xFFBBBBBB;
        config.quoteLineColor = 0xFF666666;
        config.isDarkMode = true;
        return config;
    }
}
