package com.noteai.noteai.data;

public class Note {
    // 数据库表 notes 的 UI 层模型；SQLite 同学需要保证字段和数据库列稳定映射。
    public long id;
    public String title;
    public String content;
    public Long categoryId;
    public long createdAt;
    public long updatedAt;

    public Note() {}

    public Note(long id, String title, String content, long createdAt, long updatedAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.categoryId = null;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Note(long id, String title, String content, Long categoryId, long createdAt, long updatedAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.categoryId = categoryId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
