package com.noteai.noteai.data;

import java.util.List;

public interface NoteDataSource {
    // TODO 数据库同学实现入口：正式版本由 SqliteNoteDataSource 实现本接口，UI 不直接写 SQL。
    // TODO UI 调用位置：MainActivity 负责列表/搜索/批量删除，NoteEditActivity 负责编辑/保存/删除/设置分类标签。
    // TODO 推荐数据库表：notes、categories、tags、note_tags、notes_fts。

    // 笔记列表：MainActivity 首页加载和刷新时调用，返回未删除笔记，建议按 updatedAt 倒序。
    List<Note> getAllNotes();

    // 单篇查询：NoteEditActivity 打开已有笔记时调用，查不到返回 null。
    Note getNoteById(long id);

    // 新建笔记：点击首页 + 进入编辑页时调用，返回带真实 id 的 Note。
    Note createNote(String title, String content);

    // 保存笔记：编辑页自动保存和手动保存时调用。
    void updateNote(long id, String title, String content);

    // 删除单篇：编辑页删除按钮调用。
    void deleteNote(long id);

    // 批量删除：首页批量删除模式调用，SQLite 实现时建议使用事务。
    void deleteNotes(List<Long> ids);

    // 设置分类：编辑页分类按钮后续选择分类后调用；categoryId 为 null 表示移出分类。
    void setNoteCategory(long noteId, Long categoryId);

    // 分类列表：首页分类筛选、编辑页分类选择弹窗调用。
    List<Category> getAllCategories();

    // 新建分类：分类管理页或分类选择弹窗中的“新建分类”调用。
    Category createCategory(String name);

    // 编辑分类：分类管理页调用。
    void updateCategory(long categoryId, String name);

    // 删除分类：分类管理页调用；实现时建议将该分类下笔记 category_id 置空。
    void deleteCategory(long categoryId);

    // 分类筛选：首页点击某个分类后调用。
    List<Note> getNotesByCategory(long categoryId);

    // 标签列表：首页标签筛选、编辑页标签选择弹窗调用。
    List<Tag> getAllTags();

    // 新建标签：标签管理页或标签选择弹窗中的“新建标签”调用。
    Tag createTag(String name, int color);

    // 编辑标签：标签管理页调用。
    void updateTag(long tagId, String name, int color);

    // 删除标签：标签管理页调用；实现时同步删除 note_tags 关联。
    void deleteTag(long tagId);

    // 给笔记加标签：编辑页标签弹窗勾选标签时调用。
    void addTagToNote(long noteId, long tagId);

    // 移除笔记标签：编辑页标签弹窗取消勾选时调用。
    void removeTagFromNote(long noteId, long tagId);

    // 查询笔记标签：编辑页打开标签弹窗时调用，用于显示当前已选标签。
    List<Tag> getTagsForNote(long noteId);

    // 标签筛选：首页点击某个标签后调用。
    List<Note> getNotesByTag(long tagId);

    // 简单搜索：首页搜索框调用。正式实现要覆盖标题、标签名、分类名；也可以额外搜正文。
    List<Note> searchNotes(String keyword);

    // 高级搜索：首页搜索框 + 分类/标签筛选组合时调用；useFullTextSearch=true 时优先走 FTS5。
    List<Note> searchNotes(SearchQuery query);
}
