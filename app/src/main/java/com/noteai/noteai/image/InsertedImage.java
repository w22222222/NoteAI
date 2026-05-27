package com.noteai.noteai.image;

public class InsertedImage {
    // 图片插入完成后返回给编辑页的结果。
    // localPath 是 App 内部可长期访问的图片路径；markdownText 是插入到正文里的 Markdown 图片语法。
    // width/height 是图片原始像素尺寸，Markdown 渲染同学后续可以据此提前占位，减少预览跳动。
    public String localPath;
    public String markdownText;
    public String altText;
    public int width;
    public int height;

    public InsertedImage(String localPath, String markdownText, String altText, int width, int height) {
        this.localPath = localPath;
        this.markdownText = markdownText;
        this.altText = altText;
        this.width = width;
        this.height = height;
    }
}
