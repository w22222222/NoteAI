package com.noteai.noteai.data;

public class Tag {
    // 数据库表 tags 的 UI 层模型；一篇笔记可以通过 note_tags 绑定多个 Tag。
    public long id;
    public String name;
    public int color;
    public long createdAt;
    public long updatedAt;

    public Tag() {}

    public Tag(long id, String name, int color, long createdAt, long updatedAt) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
