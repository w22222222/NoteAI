package com.noteai.noteai.data;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class NoteRepository implements NoteDataSource {
    private static final List<Note> store = new ArrayList<>();
    private static long nextId = 1;
    private static boolean initialized = false;

    private final FileStorageManager fileStorage;

    private static final String SAMPLE_TITLE = "示例笔记";
    private static final String SAMPLE_CONTENT = "# 你好\n\n这是一篇示例笔记。\n\n## 二级标题\n\n- 列表 1\n- 列表 2\n\n**加粗文本** *斜体文本* `代码`\n\n[链接](https://example.com)\n\n## 图片示例\n\n下面是插图功能生成的 Markdown 图片格式：\n\n![图片](images/demo.jpg){width=1080 height=720}\n\n当前示例图片文件还不存在，后续插图按钮会复制真实图片到 images 目录并自动生成这类语法。\n\n```java\nSystem.out.println(\"hello\");\n```\n\n> 引用文本\n\n---\n\n更多内容请编辑此笔记。";

    public NoteRepository(Context context) {
        // create file storage instance
        this.fileStorage = new FileStorageManager(context);

        // NOTE: The following logic is for bridging FileStorage and current workaround
        synchronized (NoteRepository.class) {
            if (!initialized) {
                long now = System.currentTimeMillis();
                if (fileStorage.hasSavedNotes()) {
                    List<Note> saved = fileStorage.loadAllNotes(now);
                    store.addAll(saved);
                    for (Note n : saved) {
                        if (n.id >= nextId) nextId = n.id + 1;
                    }
                } else {
                    Note sample = new Note(nextId++, SAMPLE_TITLE, SAMPLE_CONTENT, now, now);
                    store.add(sample);
                    fileStorage.save(sample.id, SAMPLE_CONTENT);
                }
                initialized = true;
            }
        }
    }

    public List<Note> getAll() { return getAllNotes(); }

    public Note getById(long id) { return getNoteById(id); }

    public Note create(String title, String content) { return createNote(title, content); }

    public void update(long id, String title, String content) { updateNote(id, title, content); }

    public void delete(long id) { deleteNote(id); }

    public void deleteMany(List<Long> ids) { deleteNotes(ids); }

    @Override
    public List<Note> getAllNotes() {
        return new ArrayList<>(store);
    }

    @Override
    public Note getNoteById(long id) {
        for (Note n : store) {
            if (n.id == id) return n;
        }
        return null;
    }

    @Override
    public Note createNote(String title, String content) {
        long now = System.currentTimeMillis();
        Note note = new Note(nextId++, title, content, now, now);
        store.add(0, note);
        fileStorage.save(note.id, content);
        return note;
    }

    @Override
    public void updateNote(long id, String title, String content) {
        Note note = getNoteById(id);
        if (note != null) {
            note.title = title;
            note.content = content;
            note.updatedAt = System.currentTimeMillis();
            fileStorage.save(id, content);
        }
    }

    @Override
    public void deleteNote(long id) {
        store.removeIf(n -> n.id == id);
        fileStorage.delete(id);
    }

    @Override
    public void deleteNotes(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (long id : ids) {
            fileStorage.delete(id);
        }
        store.removeIf(n -> ids.contains(n.id));
    }

    @Override
    public void setNoteCategory(long noteId, Long categoryId) {
        // TODO 数据库同学：正式版本在 notes.category_id 字段更新分类；内存版只做 UI 联调用。
        Note note = getNoteById(noteId);
        if (note != null) note.categoryId = categoryId;
    }

    @Override
    public List<Category> getAllCategories() {
        // TODO 分类同学：正式版本从 categories 表读取。
        return new ArrayList<>();
    }

    @Override
    public Category createCategory(String name) {
        // TODO 分类同学：正式版本插入 categories 表并返回真实 id。
        long now = System.currentTimeMillis();
        return new Category(-1, name, now, now);
    }

    @Override
    public void updateCategory(long categoryId, String name) {
        // TODO 分类同学：正式版本更新 categories.name 和 updated_at。
    }

    @Override
    public void deleteCategory(long categoryId) {
        // TODO 分类同学：正式版本删除分类时需要处理该分类下笔记，建议将 notes.category_id 置空。
    }

    @Override
    public List<Note> getNotesByCategory(long categoryId) {
        List<Note> result = new ArrayList<>();
        for (Note note : store) {
            if (note.categoryId != null && note.categoryId == categoryId) {
                result.add(note);
            }
        }
        return result;
    }

    @Override
    public List<Tag> getAllTags() {
        // TODO 标签同学：正式版本从 tags 表读取。
        return new ArrayList<>();
    }

    @Override
    public Tag createTag(String name, int color) {
        // TODO 标签同学：正式版本插入 tags 表，name 建议唯一。
        long now = System.currentTimeMillis();
        return new Tag(-1, name, color, now, now);
    }

    @Override
    public void updateTag(long tagId, String name, int color) {
        // TODO 标签同学：正式版本更新 tags.name、tags.color 和 updated_at。
    }

    @Override
    public void deleteTag(long tagId) {
        // TODO 标签同学：正式版本删除标签时，同时删除 note_tags 中的关联关系。
    }

    @Override
    public void addTagToNote(long noteId, long tagId) {
        // TODO 标签同学：正式版本向 note_tags(note_id, tag_id) 插入关联，避免重复插入。
    }

    @Override
    public void removeTagFromNote(long noteId, long tagId) {
        // TODO 标签同学：正式版本从 note_tags 删除关联。
    }

    @Override
    public List<Tag> getTagsForNote(long noteId) {
        // TODO 标签同学：正式版本通过 note_tags 联表查询某篇笔记的所有标签。
        return new ArrayList<>();
    }

    @Override
    public List<Note> getNotesByTag(long tagId) {
        // TODO 标签同学：正式版本通过 note_tags 联表查询某标签下的笔记。
        return new ArrayList<>();
    }

    @Override
    public List<Note> searchNotes(String keyword) {
        SearchQuery query = SearchQuery.keyword(keyword);
        query.useFullTextSearch = false;
        return searchNotes(query);
    }

    @Override
    public List<Note> searchNotes(SearchQuery query) {
        // TODO 搜索同学：正式版本根据 query.useFullTextSearch 决定走 notes_fts MATCH 还是普通 LIKE。
        List<Note> result = new ArrayList<>();
        String keyword = query == null ? null : query.keyword;
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllNotes();
        }
        String key = keyword.toLowerCase();
        for (Note note : store) {
            String title = note.title == null ? "" : note.title.toLowerCase();
            String content = note.content == null ? "" : note.content.toLowerCase();
            if (title.contains(key) || content.contains(key)) {
                result.add(note);
            }
        }
        return result;
    }
}
