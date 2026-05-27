package com.noteai.noteai.data;

import android.content.Context;

import java.util.List;

public class SqliteNoteDataSource implements NoteDataSource {

    public SqliteNoteDataSource(Context context) {
        // TODO 数据库同学在这里初始化 SQLiteOpenHelper 或 Room Database。
        // TODO 建议表结构：notes、categories、tags、note_tags、notes_fts。
        // TODO notes: id, title, content, category_id, created_at, updated_at, deleted。
        // TODO categories: id, name, created_at, updated_at。
        // TODO tags: id, name, color, created_at, updated_at。
        // TODO note_tags: note_id, tag_id，联合主键，支持一篇笔记多个标签。
        // TODO notes_fts: 使用 SQLite FTS5 建虚拟表，建议索引 title、content。
    }

    @Override
    public List<Note> getAllNotes() {
        // TODO 查询未删除笔记，按 updated_at 倒序返回。
        throw new UnsupportedOperationException("SQLite 笔记列表查询待实现");
    }

    @Override
    public Note getNoteById(long id) {
        // TODO 根据 id 查询单篇笔记，查不到时返回 null。
        throw new UnsupportedOperationException("SQLite 单篇笔记查询待实现");
    }

    @Override
    public Note createNote(String title, String content) {
        // TODO 插入新笔记，created_at 和 updated_at 使用当前时间，返回带真实 id 的 Note。
        // TODO 插入或更新 notes_fts，保证全文搜索可搜到新笔记。
        throw new UnsupportedOperationException("SQLite 新建笔记待实现");
    }

    @Override
    public void updateNote(long id, String title, String content) {
        // TODO 根据 id 更新标题、正文、updated_at。
        // TODO 同步更新 notes_fts 中对应行。
        throw new UnsupportedOperationException("SQLite 更新笔记待实现");
    }

    @Override
    public void deleteNote(long id) {
        // TODO 推荐软删除，将 deleted 标记为 1。
        // TODO 同时删除 note_tags 关联，并从 notes_fts 删除对应索引。
        throw new UnsupportedOperationException("SQLite 删除笔记待实现");
    }

    @Override
    public void deleteNotes(List<Long> ids) {
        // TODO 首页批量删除调用这里。必须使用 SQLite 事务：beginTransaction -> 循环软删除/删除索引 -> setTransactionSuccessful。
        // TODO ids 为空时直接返回；实现时同时处理 notes、note_tags、notes_fts。
        throw new UnsupportedOperationException("SQLite 批量删除笔记待实现");
    }

    @Override
    public void setNoteCategory(long noteId, Long categoryId) {
        // TODO 更新 notes.category_id；categoryId 为 null 表示移出分类。
        throw new UnsupportedOperationException("SQLite 设置笔记分类待实现");
    }

    @Override
    public List<Category> getAllCategories() {
        // TODO 查询所有分类，按 updated_at 或 name 排序。
        throw new UnsupportedOperationException("SQLite 分类列表待实现");
    }

    @Override
    public Category createCategory(String name) {
        // TODO 新建分类，name 建议非空且可做唯一约束。
        throw new UnsupportedOperationException("SQLite 新建分类待实现");
    }

    @Override
    public void updateCategory(long categoryId, String name) {
        // TODO 编辑分类名称并更新 updated_at。
        throw new UnsupportedOperationException("SQLite 编辑分类待实现");
    }

    @Override
    public void deleteCategory(long categoryId) {
        // TODO 删除分类，建议同时把该分类下笔记的 category_id 置空。
        throw new UnsupportedOperationException("SQLite 删除分类待实现");
    }

    @Override
    public List<Note> getNotesByCategory(long categoryId) {
        // TODO 查询某个分类下的笔记，按 updated_at 倒序。
        throw new UnsupportedOperationException("SQLite 分类笔记查询待实现");
    }

    @Override
    public List<Tag> getAllTags() {
        // TODO 查询所有标签，按名称或更新时间排序。
        throw new UnsupportedOperationException("SQLite 标签列表待实现");
    }

    @Override
    public Tag createTag(String name, int color) {
        // TODO 新建标签，color 用 int 保存 ARGB 色值。
        throw new UnsupportedOperationException("SQLite 新建标签待实现");
    }

    @Override
    public void updateTag(long tagId, String name, int color) {
        // TODO 编辑标签名称、颜色和 updated_at。
        throw new UnsupportedOperationException("SQLite 编辑标签待实现");
    }

    @Override
    public void deleteTag(long tagId) {
        // TODO 删除标签，同时删除 note_tags 中该标签的所有关联。
        throw new UnsupportedOperationException("SQLite 删除标签待实现");
    }

    @Override
    public void addTagToNote(long noteId, long tagId) {
        // TODO 给笔记添加标签，向 note_tags 插入关联；需要避免重复关联。
        throw new UnsupportedOperationException("SQLite 添加笔记标签待实现");
    }

    @Override
    public void removeTagFromNote(long noteId, long tagId) {
        // TODO 删除笔记和标签之间的关联。
        throw new UnsupportedOperationException("SQLite 移除笔记标签待实现");
    }

    @Override
    public List<Tag> getTagsForNote(long noteId) {
        // TODO 查询某篇笔记绑定的所有标签。
        throw new UnsupportedOperationException("SQLite 查询笔记标签待实现");
    }

    @Override
    public List<Note> getNotesByTag(long tagId) {
        // TODO 查询某个标签下的所有笔记，需要联表 note_tags 和 notes。
        throw new UnsupportedOperationException("SQLite 标签笔记查询待实现");
    }

    @Override
    public List<Note> searchNotes(String keyword) {
        // TODO 简单搜索：标题/正文 LIKE，也可扩展标签名、分类名匹配。
        return searchNotes(SearchQuery.keyword(keyword));
    }

    @Override
    public List<Note> searchNotes(SearchQuery query) {
        // TODO 搜索同学：统一搜索入口。
        // TODO query.keyword + useFullTextSearch=true 时，优先走 notes_fts MATCH。
        // TODO query.categoryId 不为空时，加 category_id 条件。
        // TODO query.tagIds 非空时，通过 note_tags 过滤多标签。
        // TODO 简单搜索需求：标题/标签/分类，可在这里通过 JOIN categories/tags 实现。
        // TODO 本地全文搜索需求：推荐 SQLite FTS5，维护 notes_fts(title, content)。
        throw new UnsupportedOperationException("SQLite 搜索待实现");
    }
}
