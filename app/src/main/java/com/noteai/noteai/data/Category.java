package com.noteai.noteai.data;

public class Category {
    // 数据库表 categories 的 UI 层模型；一篇笔记最多属于一个 Category。
    public long id;
    public String name;
    public long createdAt;
    public long updatedAt;

    public Category() {}

    public Category(long id, String name, long createdAt, long updatedAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
