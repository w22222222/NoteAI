# 数据库组三人分工说明

本文档只针对 `app-noteai` 版本。数据库组负责把当前内存存储替换为 SQLite，并实现笔记、标签、分类、搜索等数据能力。

## 总原则

- UI 层只调用 `NoteRepository`，不要在 `MainActivity` 或 `NoteEditActivity` 里写 SQL。
- SQLite 正式实现集中在 `app-noteai/src/main/java/com/noteai/noteai/data/SqliteNoteDataSource.java`。
- 三个人不要各自创建数据库入口，统一使用同一个 SQLite 数据库。
- `NoteDataSource` 是协议边界，不要随意改方法签名；如果必须改，先同步其他组。

## 核心文件

| 文件 | 作用 |
|---|---|
| `data/NoteDataSource.java` | 数据层统一接口，UI 和 Repository 依赖它 |
| `data/SqliteNoteDataSource.java` | SQLite 正式实现位置 |
| `data/NoteRepository.java` | 当前内存实现，后续切换为转发到 SQLite |
| `data/Note.java` | 笔记模型，对应 notes 表 |
| `data/Tag.java` | 标签模型，对应 tags 表 |
| `data/Category.java` | 分类模型，对应 categories 表 |
| `data/SearchQuery.java` | 搜索条件模型 |

## 推荐数据库表结构

```sql
CREATE TABLE notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL DEFAULT '',
    content TEXT NOT NULL DEFAULT '',
    category_id INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    color INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE note_tags (
    note_id INTEGER NOT NULL,
    tag_id INTEGER NOT NULL,
    PRIMARY KEY(note_id, tag_id)
);

CREATE VIRTUAL TABLE notes_fts USING fts5(
    title,
    content,
    content='notes',
    content_rowid='id'
);
```

## 数据库 A：基础笔记存储

### 负责范围

数据库 A 负责 notes 表和基础笔记持久化。

### 要写的文件

主要写：

```text
app-noteai/src/main/java/com/noteai/noteai/data/SqliteNoteDataSource.java
app-noteai/src/main/java/com/noteai/noteai/data/NoteRepository.java
```

### 要实现的函数

```java
List<Note> getAllNotes();

Note getNoteById(long id);

Note createNote(String title, String content);

void updateNote(long id, String title, String content);

void deleteNote(long id);

void deleteNotes(List<Long> ids);
```

### 输入输出示例

#### createNote

输入：

```java
createNote("会议记录", "# 周会\n\n今天讨论项目进度")
```

输出：

```java
new Note(1, "会议记录", "# 周会\n\n今天讨论项目进度", null, 1710000000000L, 1710000000000L)
```

要求：

- 返回的 `id` 必须是 SQLite 真实自增 id。
- `createdAt` 和 `updatedAt` 使用当前时间戳。
- 同步写入 `notes_fts`，或者通知数据库 C 的 FTS 同步逻辑。

#### updateNote

输入：

```java
updateNote(1, "会议记录修改版", "新的正文")
```

要求：

- 更新 `notes.title`。
- 更新 `notes.content`。
- 更新 `notes.updated_at`。
- 同步更新 `notes_fts`。

#### deleteNote

输入：

```java
deleteNote(1)
```

建议：

- 使用软删除：`deleted = 1`。
- 从 `note_tags` 删除该笔记的标签关系。
- 从 `notes_fts` 删除该笔记索引。

#### deleteNotes

输入：

```java
deleteNotes(Arrays.asList(1L, 2L, 3L))
```

要求：

- 必须使用事务。
- 任何一个步骤失败时回滚。
- 删除 notes、note_tags、notes_fts 的相关数据。

伪代码：

```java
db.beginTransaction();
try {
    for (Long id : ids) {
        // notes.deleted = 1
        // delete from note_tags where note_id = id
        // delete from notes_fts where rowid = id
    }
    db.setTransactionSuccessful();
} finally {
    db.endTransaction();
}
```

### 完成标准

- 新建笔记后首页能显示。
- 编辑保存后退出 App 再打开内容不丢。
- 删除当前笔记后首页不再显示。
- 批量删除后选中笔记不再显示。
- 首页列表按 `updated_at DESC` 排序。

## 数据库 B：标签、分类、多对多关系

### 负责范围

数据库 B 负责 categories、tags、note_tags 表，以及笔记和标签/分类的关联。

### 要写的文件

主要写：

```text
app-noteai/src/main/java/com/noteai/noteai/data/SqliteNoteDataSource.java
app-noteai/src/main/java/com/noteai/noteai/data/Tag.java
app-noteai/src/main/java/com/noteai/noteai/data/Category.java
```

### 要实现的函数

分类：

```java
void setNoteCategory(long noteId, Long categoryId);

List<Category> getAllCategories();

Category createCategory(String name);

void updateCategory(long categoryId, String name);

void deleteCategory(long categoryId);

List<Note> getNotesByCategory(long categoryId);
```

标签：

```java
List<Tag> getAllTags();

Tag createTag(String name, int color);

void updateTag(long tagId, String name, int color);

void deleteTag(long tagId);

void addTagToNote(long noteId, long tagId);

void removeTagFromNote(long noteId, long tagId);

List<Tag> getTagsForNote(long noteId);

List<Note> getNotesByTag(long tagId);
```

### 输入输出示例

#### createCategory

输入：

```java
createCategory("学习")
```

输出：

```java
new Category(1, "学习", 1710000000000L, 1710000000000L)
```

#### setNoteCategory

输入：

```java
setNoteCategory(10, 1L)
```

含义：

```text
把 id=10 的笔记设置到 id=1 的分类下
```

输入：

```java
setNoteCategory(10, null)
```

含义：

```text
把 id=10 的笔记移出分类
```

#### createTag

输入：

```java
createTag("Markdown", 0xFF1A73E8)
```

输出：

```java
new Tag(1, "Markdown", 0xFF1A73E8, 1710000000000L, 1710000000000L)
```

#### addTagToNote

输入：

```java
addTagToNote(10, 1)
```

含义：

```text
给 id=10 的笔记添加 id=1 的标签
```

要求：

- `note_tags` 使用联合主键 `(note_id, tag_id)`。
- 重复添加同一个标签不能崩溃。

#### getTagsForNote

输入：

```java
getTagsForNote(10)
```

输出示例：

```java
[
    new Tag(1, "Markdown", 0xFF1A73E8, ...),
    new Tag(2, "项目", 0xFF26A69A, ...)
]
```

### 删除策略

#### deleteCategory

建议：

```text
删除分类时，不删除笔记，只把 notes.category_id 置空。
```

#### deleteTag

建议：

```text
删除标签时，同时删除 note_tags 中该 tag_id 的所有关联。
```

### 完成标准

- 能创建、编辑、删除分类。
- 能给笔记设置/移除分类。
- 能按分类查询笔记。
- 能创建、编辑、删除标签。
- 一篇笔记能绑定多个标签。
- 能查询某篇笔记的所有标签。
- 能按标签查询笔记。

## 数据库 C：搜索、FTS5、数据联调

### 负责范围

数据库 C 负责搜索能力、FTS5 索引、搜索与分类/标签过滤的组合，以及数据库组内部联调。

### 要写的文件

主要写：

```text
app-noteai/src/main/java/com/noteai/noteai/data/SqliteNoteDataSource.java
app-noteai/src/main/java/com/noteai/noteai/data/SearchQuery.java
```

### 要实现的函数

```java
List<Note> searchNotes(String keyword);

List<Note> searchNotes(SearchQuery query);
```

同时协助数据库 A 在以下函数里维护 FTS：

```java
createNote(...)
updateNote(...)
deleteNote(...)
deleteNotes(...)
```

### SearchQuery 协议

```java
SearchQuery query = new SearchQuery();
query.keyword = "Markdown";
query.categoryId = 1L;
query.tagIds.add(2L);
query.useFullTextSearch = true;
query.limit = 50;
query.offset = 0;
```

含义：

```text
搜索关键字 Markdown
只查 category_id = 1 的笔记
只查包含 tag_id = 2 的笔记
优先使用 FTS5 全文搜索
最多返回 50 条，从第 0 条开始
```

### 简单搜索要求

`searchNotes(String keyword)` 用于首页搜索框。需求里写的是：

```text
简单搜索：标题 / 标签 / 分类
```

所以至少要匹配：

```sql
notes.title LIKE ?
OR tags.name LIKE ?
OR categories.name LIKE ?
```

可以额外匹配正文：

```sql
OR notes.content LIKE ?
```

### FTS5 全文搜索要求

当：

```java
query.useFullTextSearch == true
```

并且：

```java
query.keyword 非空
```

优先使用：

```sql
notes_fts MATCH ?
```

FTS 主要搜索：

```text
title
content
```

### 组合搜索要求

要支持以下组合：

```text
keyword
categoryId
tagIds
keyword + categoryId
keyword + tagIds
categoryId + tagIds
keyword + categoryId + tagIds
```

示例：

```java
SearchQuery query = new SearchQuery();
query.keyword = "项目";
query.categoryId = 3L;
query.tagIds.add(5L);
```

期望：

```text
返回分类为 3，绑定标签 5，并且标题/正文/标签/分类中匹配“项目”的笔记。
```

### FTS 同步要求

#### 新建笔记

```text
insert notes
insert notes_fts(rowid, title, content)
```

#### 更新笔记

```text
update notes
update notes_fts
```

#### 删除笔记

```text
notes.deleted = 1
delete notes_fts where rowid = noteId
```

### 完成标准

- 搜标题能搜到笔记。
- 搜分类名能搜到该分类下笔记。
- 搜标签名能搜到绑定该标签的笔记。
- 搜正文能通过 FTS5 搜到。
- 删除笔记后搜索结果不再出现。
- 更新笔记后搜索结果实时更新。
- 搜索可以和分类/标签过滤组合使用。

## 三人协作顺序

1. 数据库 A 先建数据库、notes 表、基础 CRUD。
2. 数据库 B 在同一个数据库里补 categories、tags、note_tags。
3. 数据库 C 加 notes_fts 和搜索逻辑。
4. 三人共同把 `NoteRepository` 从内存实现切到 `SqliteNoteDataSource`。

## 不要做的事

- 不要在 Activity 里写 SQL。
- 不要每个人各自创建一个数据库 helper。
- 不要让标签、分类、搜索分别使用不同数据库文件。
- 不要把图片二进制数据存进 notes 表；Markdown 里只保存图片相对路径。
