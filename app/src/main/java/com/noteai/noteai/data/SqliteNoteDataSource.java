package com.noteai.noteai.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class SqliteNoteDataSource implements NoteDataSource {

    private final NoteDatabaseHelper dbHelper;

    public SqliteNoteDataSource(Context context) {
        dbHelper = new NoteDatabaseHelper(context.getApplicationContext());
    }

    private SQLiteDatabase writableDb() {
        return dbHelper.getWritableDatabase();
    }

    private SQLiteDatabase readableDb() {
        return dbHelper.getReadableDatabase();
    }

    private static String requireNonEmptyName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        return trimmed;
    }

    private static Category categoryFromCursor(Cursor cursor) {
        Category category = new Category();
        category.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        category.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        category.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        category.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return category;
    }

    private static Tag tagFromCursor(Cursor cursor) {
        Tag tag = new Tag();
        tag.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        tag.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        tag.color = cursor.getInt(cursor.getColumnIndexOrThrow("color"));
        tag.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        tag.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return tag;
    }

    private static Note noteFromCursor(Cursor cursor) {
        Note note = new Note();
        note.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        note.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        note.content = cursor.getString(cursor.getColumnIndexOrThrow("content"));
        int categoryIndex = cursor.getColumnIndex("category_id");
        if (categoryIndex >= 0 && !cursor.isNull(categoryIndex)) {
            note.categoryId = cursor.getLong(categoryIndex);
        } else {
            note.categoryId = null;
        }
        note.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        note.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return note;
    }

    @Override
    public List<Note> getAllNotes() {
        // TODO 查询未删除笔记，按 updated_at 倒序返回。
        List<Note> notes = new ArrayList<>();
        // 查询条件：没有被删除的，按照更新时间倒序排列
        try (Cursor cursor = readableDb().query(
                NoteDatabaseHelper.TABLE_NOTES,
                null,
                "deleted = 0",
                null,
                null,
                null,
                "updated_at DESC"
        )) {
            while (cursor.moveToNext()) {
                notes.add(noteFromCursor(cursor));
            }
        }
        return notes;
    }

    @Override
    public Note getNoteById(long id) {
        // TODO 根据 id 查询单篇笔记，查不到时返回 null。
        try (Cursor cursor = readableDb().query(
                NoteDatabaseHelper.TABLE_NOTES,
                null,
                "id = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                return noteFromCursor(cursor);
            }
        }
        return null;
    }

    @Override
    public Note createNote(String title, String content) {
        // TODO 插入新笔记，created_at 和 updated_at 使用当前时间，返回带真实 id 的 Note。
        // TODO 插入或更新 notes_fts，保证全文搜索可搜到新笔记。
        long now = System.currentTimeMillis();
        String finalTitle = (title == null || title.trim().isEmpty()) ? "未命名笔记" : title;

        ContentValues values = new ContentValues();
        values.put("title", finalTitle);
        values.put("content", content);
        values.put("created_at", now);
        values.put("updated_at", now);
        values.put("deleted", 0);

        // insert 方法会返回这条数据在数据库里的真实 id
        long id = writableDb().insert(NoteDatabaseHelper.TABLE_NOTES, null, values);

        // 返回一个带有真实 ID 的 Note 对象给上层使用
        return new Note(id, title, content, now, now);
    }

    @Override
    public void updateNote(long id, String title, String content) {
        // TODO 根据 id 更新标题、正文、updated_at。
        // TODO 同步更新 notes_fts 中对应行。
        long now = System.currentTimeMillis();
        String finalTitle = (title == null || title.trim().isEmpty()) ? "未命名笔记" : title;

        ContentValues values = new ContentValues();
        values.put("title", finalTitle);
        values.put("content", content);
        values.put("updated_at", now);

        // 更新 notes 表
        writableDb().update(
                NoteDatabaseHelper.TABLE_NOTES,
                values,
                "id = ?",
                new String[]{String.valueOf(id)}
        );
    }

    @Override
    public void deleteNote(long id) {
        // TODO 推荐软删除，将 deleted 标记为 1。
        // TODO 同时删除 note_tags 关联，并从 notes_fts 删除对应索引。
        ContentValues values = new ContentValues();
        values.put("deleted", 1); // 软删除，标记为 1
        writableDb().update(
                NoteDatabaseHelper.TABLE_NOTES,
                values,
                "id = ?",
                new String[]{String.valueOf(id)}
        );
    }

    @Override
    public void deleteNotes(List<Long> ids) {
        // TODO 首页批量删除调用这里。必须使用 SQLite 事务：beginTransaction -> 循环软删除/删除索引 -> setTransactionSuccessful。
        // TODO ids 为空时直接返回；实现时同时处理 notes、note_tags、notes_fts。
        throw new UnsupportedOperationException("SQLite 批量删除笔记待实现");
    }

    @Override
    public void setNoteCategory(long noteId, Long categoryId) {
        ContentValues values = new ContentValues();
        if (categoryId == null) {
            values.putNull("category_id");
        } else {
            values.put("category_id", categoryId);
        }
        writableDb().update(
                NoteDatabaseHelper.TABLE_NOTES,
                values,
                "id = ?",
                new String[]{String.valueOf(noteId)}
        );
    }

    @Override
    public List<Category> getAllCategories() {
        List<Category> categories = new ArrayList<>();
        try (Cursor cursor = readableDb().query(
                NoteDatabaseHelper.TABLE_CATEGORIES,
                null,
                null,
                null,
                null,
                null,
                "name ASC"
        )) {
            while (cursor.moveToNext()) {
                categories.add(categoryFromCursor(cursor));
            }
        }
        return categories;
    }

    @Override
    public Category createCategory(String name) {
        String trimmedName = requireNonEmptyName(name);
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("name", trimmedName);
        values.put("created_at", now);
        values.put("updated_at", now);
        long id = writableDb().insert(NoteDatabaseHelper.TABLE_CATEGORIES, null, values);
        return new Category(id, trimmedName, now, now);
    }

    @Override
    public void updateCategory(long categoryId, String name) {
        String trimmedName = requireNonEmptyName(name);
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("name", trimmedName);
        values.put("updated_at", now);
        writableDb().update(
                NoteDatabaseHelper.TABLE_CATEGORIES,
                values,
                "id = ?",
                new String[]{String.valueOf(categoryId)}
        );
    }

    @Override
    public void deleteCategory(long categoryId) {
        SQLiteDatabase db = writableDb();
        db.beginTransaction();
        try {
            ContentValues clearCategory = new ContentValues();
            clearCategory.putNull("category_id");
            db.update(
                    NoteDatabaseHelper.TABLE_NOTES,
                    clearCategory,
                    "category_id = ?",
                    new String[]{String.valueOf(categoryId)}
            );
            db.delete(
                    NoteDatabaseHelper.TABLE_CATEGORIES,
                    "id = ?",
                    new String[]{String.valueOf(categoryId)}
            );
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public List<Note> getNotesByCategory(long categoryId) {
        List<Note> notes = new ArrayList<>();
        try (Cursor cursor = readableDb().query(
                NoteDatabaseHelper.TABLE_NOTES,
                null,
                "category_id = ? AND deleted = 0",
                new String[]{String.valueOf(categoryId)},
                null,
                null,
                "updated_at DESC"
        )) {
            while (cursor.moveToNext()) {
                notes.add(noteFromCursor(cursor));
            }
        }
        return notes;
    }

    @Override
    public List<Tag> getAllTags() {
        List<Tag> tags = new ArrayList<>();
        try (Cursor cursor = readableDb().query(
                NoteDatabaseHelper.TABLE_TAGS,
                null,
                null,
                null,
                null,
                null,
                "name ASC"
        )) {
            while (cursor.moveToNext()) {
                tags.add(tagFromCursor(cursor));
            }
        }
        return tags;
    }

    @Override
    public Tag createTag(String name, int color) {
        String trimmedName = requireNonEmptyName(name);
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("name", trimmedName);
        values.put("color", color);
        values.put("created_at", now);
        values.put("updated_at", now);
        long id = writableDb().insert(NoteDatabaseHelper.TABLE_TAGS, null, values);
        return new Tag(id, trimmedName, color, now, now);
    }

    @Override
    public void updateTag(long tagId, String name, int color) {
        String trimmedName = requireNonEmptyName(name);
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("name", trimmedName);
        values.put("color", color);
        values.put("updated_at", now);
        writableDb().update(
                NoteDatabaseHelper.TABLE_TAGS,
                values,
                "id = ?",
                new String[]{String.valueOf(tagId)}
        );
    }

    @Override
    public void deleteTag(long tagId) {
        SQLiteDatabase db = writableDb();
        db.beginTransaction();
        try {
            db.delete(
                    NoteDatabaseHelper.TABLE_NOTE_TAGS,
                    "tag_id = ?",
                    new String[]{String.valueOf(tagId)}
            );
            db.delete(
                    NoteDatabaseHelper.TABLE_TAGS,
                    "id = ?",
                    new String[]{String.valueOf(tagId)}
            );
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void addTagToNote(long noteId, long tagId) {
        ContentValues values = new ContentValues();
        values.put("note_id", noteId);
        values.put("tag_id", tagId);
        writableDb().insertWithOnConflict(
                NoteDatabaseHelper.TABLE_NOTE_TAGS,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
    }

    @Override
    public void removeTagFromNote(long noteId, long tagId) {
        writableDb().delete(
                NoteDatabaseHelper.TABLE_NOTE_TAGS,
                "note_id = ? AND tag_id = ?",
                new String[]{String.valueOf(noteId), String.valueOf(tagId)}
        );
    }

    @Override
    public List<Tag> getTagsForNote(long noteId) {
        List<Tag> tags = new ArrayList<>();
        String sql = "SELECT t.* FROM " + NoteDatabaseHelper.TABLE_TAGS + " t "
                + "INNER JOIN " + NoteDatabaseHelper.TABLE_NOTE_TAGS + " nt ON t.id = nt.tag_id "
                + "WHERE nt.note_id = ? "
                + "ORDER BY t.name ASC";
        try (Cursor cursor = readableDb().rawQuery(sql, new String[]{String.valueOf(noteId)})) {
            while (cursor.moveToNext()) {
                tags.add(tagFromCursor(cursor));
            }
        }
        return tags;
    }

    @Override
    public List<Note> getNotesByTag(long tagId) {
        List<Note> notes = new ArrayList<>();
        String sql = "SELECT n.* FROM " + NoteDatabaseHelper.TABLE_NOTES + " n "
                + "INNER JOIN " + NoteDatabaseHelper.TABLE_NOTE_TAGS + " nt ON n.id = nt.note_id "
                + "WHERE nt.tag_id = ? AND n.deleted = 0 "
                + "ORDER BY n.updated_at DESC";
        try (Cursor cursor = readableDb().rawQuery(sql, new String[]{String.valueOf(tagId)})) {
            while (cursor.moveToNext()) {
                notes.add(noteFromCursor(cursor));
            }
        }
        return notes;
    }

    @Override
    public List<Note> searchNotes(String keyword) {
        // TODO 简单搜索：标题/正文 LIKE，也可扩展标签名、分类名匹配。
        // 1. 把简单的关键词包装成专业的 Query 对象
        SearchQuery query = new SearchQuery();
        query.keyword = keyword;

        query.useFullTextSearch = true;

        return searchNotes(query);
    }

    @Override
    public List<Note> searchNotes(SearchQuery query) {
        // TODO 搜索同学：统一搜索入口。
        // TODO query.keyword + useFullTextSearch=true 时，优先走 notes_fts MATCH。
        // TODO query.categoryId 不为空时，加 category_id 条件。
        // TODO query.tagIds 非空时，通过 note_tags 过滤多标签。
        // TODO 简单搜索需求：标题/标签/分类，可在这里通过 JOIN categories/tags 实现。
        // TODO 本地全文搜索需求：推荐 SQLite FTS5，维护 notes_fts(title, content)。
        String keyword = (query != null) ? query.keyword : null;
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllNotes();
        }

        List<Note> results = new ArrayList<>();
        String searchKey = keyword.trim() + "*"; // FTS 用的前缀匹配
        String pattern = "%" + keyword.trim() + "%"; // LIKE 用的模糊匹配

        // 【核心 SQL：全能搜索引擎】
        // 逻辑：找出 (FTS 命中的内容) OR (LIKE 命中的分类) OR (LIKE 命中的标签)
        String sql = "SELECT * FROM " + NoteDatabaseHelper.TABLE_NOTES +
                " n " + "WHERE (" + "  n.title LIKE ? " + // 1. 标题直接用 LIKE，支持任意位置匹配 (满足用户对标题的直觉)
                "  OR id IN (SELECT docid FROM " +
                NoteDatabaseHelper.TABLE_NOTES_FTS + " WHERE " +
                NoteDatabaseHelper.TABLE_NOTES_FTS + " MATCH ?) " + // 2. 正文用 FTS，保证海量数据下的速度
                "  OR category_id IN (SELECT id FROM " +
                NoteDatabaseHelper.TABLE_CATEGORIES +
                " WHERE name LIKE ?) " +
                "  OR id IN (SELECT nt.note_id FROM " +
                NoteDatabaseHelper.TABLE_NOTE_TAGS + " nt " +
                "            JOIN " + NoteDatabaseHelper.TABLE_TAGS +
                " t ON nt.tag_id = t.id WHERE t.name LIKE ?) " + ") " +
                "AND deleted = 0 " + "ORDER BY updated_at DESC";
    // 对应四个问号：1. 标题LIKE, 2. FTS关键词, 3. 分类名LIKE, 4. 标签名LIKE
        String[] args = {pattern, searchKey, pattern, pattern};

        try (Cursor cursor = readableDb().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                results.add(noteFromCursor(cursor));
            }
        } catch (Exception e) {
            android.util.Log.e("SQL_ERROR", "Universal Search failed!", e);
        }
        return results;
    }
}
