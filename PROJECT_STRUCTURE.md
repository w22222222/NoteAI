# app-noteai 项目结构说明

`app-noteai` 现在已经整理成一个独立项目目录。它内部已经包含 App 源码、Markdown engine、依赖库、构建脚本、分工文档和 Git 仓库配置。

也就是说，后续团队开发、构建、提交 GitHub，都可以直接在 `app-noteai` 目录下完成，不再依赖外层 `NoteAI` 项目的 `engine/`、`libs/`、`build_compare.ps1`。

## 当前顶层结构

```text
app-noteai/
├── .git/                         # app-noteai 自己的 Git 仓库目录
├── .gitignore                    # 独立仓库忽略规则
├── build_app.ps1                 # 独立构建脚本
├── PROJECT_STRUCTURE.md          # 当前项目结构说明
├── docs/                         # 分工文档
├── engine/                       # 复制进来的 Markdown 渲染引擎源码
├── libs/                         # 复制进来的 AndroidX / RecyclerView 依赖
├── src/                          # NoteAI App 源码
└── build/                        # 构建产物，已被 .gitignore 忽略
```

其中 `build/` 是构建脚本生成的目录，不需要提交到 Git。

## docs 目录

```text
docs/
├── ai-division.md
├── database-division.md
└── markdown-division.md
```

| 文件 | 说明 |
|---|---|
| `database-division.md` | 数据库 3 人分工，包含 notes、tags、categories、note_tags、FTS5 |
| `ai-division.md` | AI 2 人分工，包含摘要和润色接口协议 |
| `markdown-division.md` | Markdown 1 人分工，包含渲染、图片协议、性能优化 |

## src 目录：App 主源码

```text
src/
└── main/
    ├── AndroidManifest.xml
    └── java/com/noteai/noteai/
        ├── MainActivity.java
        ├── NoteEditActivity.java
        ├── ai/
        ├── data/
        ├── image/
        └── widget/
```

### AndroidManifest.xml

路径：

```text
src/main/AndroidManifest.xml
```

当前注册页面：

```text
MainActivity       首页 / 笔记列表页
NoteEditActivity   编辑 / 预览页
```

当前包名：

```text
com.noteai.noteai
```

## MainActivity.java

路径：

```text
src/main/java/com/noteai/noteai/MainActivity.java
```

负责首页。

当前功能：

```text
笔记列表
搜索框
分类入口
标签入口
批量删除
右下角 + 新建笔记
点击笔记进入编辑页
```

已实现：

```text
基础笔记列表
搜索框基础过滤
批量删除完整交互
新建笔记入口
```

后续可完善：

```text
分类筛选正式 UI
标签筛选正式 UI
笔记卡片展示分类/标签
搜索结果 UI 优化
```

## NoteEditActivity.java

路径：

```text
src/main/java/com/noteai/noteai/NoteEditActivity.java
```

负责二级编辑页。

当前功能：

```text
标题编辑
正文编辑
保存
删除
分类入口
标签入口
插图
编辑/预览切换
AI 悬浮球
字数统计
自动保存
```

已实现：

```text
普通 EditText 编辑
手动保存
自动保存
删除当前笔记
编辑/预览切换
Markdown 预览入口
系统图片选择器
图片复制到 App 私有目录
插入带宽高的 Markdown 图片语法
AI 悬浮球基础流程
```

## src/main/java/com/noteai/noteai/ai

```text
ai/
├── AiService.java
└── PlaceholderAiService.java
```

### AiService.java

AI 功能统一接口。

```java
void summarize(String title, String content, Callback callback);

void polish(String title, String content, Callback callback);
```

### PlaceholderAiService.java

当前占位实现。

后续 AI 同学可以新增：

```text
RealAiService.java
```

然后在 `NoteEditActivity.java` 中替换：

```java
aiService = new PlaceholderAiService(this);
```

## src/main/java/com/noteai/noteai/data

```text
data/
├── Category.java
├── Note.java
├── NoteDataSource.java
├── NoteRepository.java
├── SearchQuery.java
├── SqliteNoteDataSource.java
└── Tag.java
```

### Note.java

笔记模型，对应数据库 `notes` 表。

### Tag.java

标签模型，对应数据库 `tags` 表。

### Category.java

分类模型，对应数据库 `categories` 表。

### SearchQuery.java

搜索条件模型，支持：

```text
关键字
分类
多标签
FTS5
分页
```

### NoteDataSource.java

数据层统一接口，包含：

```text
笔记 CRUD
批量删除
分类 CRUD
标签 CRUD
笔记-标签绑定
简单搜索
FTS5 搜索
```

### NoteRepository.java

当前仍是内存实现，用于 UI 联调。

后续数据库组需要把它切换为：

```text
NoteRepository -> SqliteNoteDataSource -> SQLite
```

### SqliteNoteDataSource.java

SQLite 正式实现位置。

数据库组三人主要在这里实现：

```text
notes
categories
tags
note_tags
notes_fts
```

## src/main/java/com/noteai/noteai/image

```text
image/
├── ImageInsertManager.java
├── ImageLoader.java
├── InsertedImage.java
├── LocalImageInsertManager.java
└── LocalImageLoader.java
```

### ImageInsertManager.java

图片插入接口。

职责：

```text
接收系统图片选择器返回的 Uri
复制图片到 App 私有目录
读取图片宽高
生成 Markdown 图片语法
```

### LocalImageInsertManager.java

当前已经实现：

```text
复制图片到 files/images/
读取图片原始宽高
生成带 width/height 的 Markdown 图片语法
```

示例输出：

```markdown
![图片](images/img_1710000000000.jpg){width=1080 height=720}
```

### ImageLoader.java

Markdown 预览时的图片加载接口。

```java
void loadImage(String source, int targetWidth, int targetHeight, Callback callback);

void cancel(String source);
```

### LocalImageLoader.java

后续 Markdown 图片渲染时实现异步加载和采样解码。

## src/main/java/com/noteai/noteai/widget

```text
widget/
├── AiFloatingBall.java
└── MarkdownRenderView.java
```

### AiFloatingBall.java

AI 悬浮球组件。

功能：

```text
可拖拽
点击展开 AI 摘要 / AI 润色
摘要窗口动画
显示正在总结
显示摘要结果
```

AI 同学不需要改这个文件，只需要实现 `AiService`。

### MarkdownRenderView.java

Markdown 预览组件。

核心接口：

```java
submitMarkdown(String markdown)
```

负责：

```text
接收 Markdown 原文
后台解析
生成 Block 列表
RecyclerView 渲染预览
```

## engine 目录：Markdown 渲染引擎

```text
engine/
└── src/main/
    ├── cpp/
    │   ├── engine.cpp
    │   ├── engine.hpp
    │   └── jni_bridge.cpp
    ├── java/
    │   ├── androidx/
    │   └── com/noteai/engine/
    └── test/
```

### engine/src/main/java/com/noteai/engine

```text
Block.java
BlockAdapter.java
BlockExtractor.java
MarkdownGenerator.java
MarkdownParser.java
NoteMarkdownView.java
ParseNativeResult.java
SpanInfo.java
StyleConfig.java
```

这些是 Markdown 预览依赖的核心引擎代码。

重点文件：

| 文件 | 说明 |
|---|---|
| `Block.java` | Markdown 块模型 |
| `BlockAdapter.java` | RecyclerView 渲染每个块 |
| `BlockExtractor.java` | 把 Markdown 解析结果拆成块 |
| `MarkdownParser.java` | Java Markdown 解析器 |
| `NoteMarkdownView.java` | 旧版本 Markdown View，也提供解析入口 |
| `SpanInfo.java` | Span 类型定义，已有图片类型 |
| `StyleConfig.java` | 渲染样式配置 |

### engine/src/main/cpp

C++ 解析相关代码。

当前独立构建脚本会编译：

```text
engine.cpp
jni_bridge.cpp
```

输出：

```text
build/native/arm64-v8a/libnote_engine.so
```

然后打进 APK。

### engine/src/main/java/androidx

这里是为了手写构建脚本能编译 RecyclerView 依赖而复制的 AndroidX R/stub 相关代码。

不要随便删。

## libs 目录：第三方依赖

```text
libs/
├── recyclerview-1.3.2.jar
├── core-1.10.1.jar
├── collection-1.4.0.jar
├── customview-poolingcontainer-1.0.0.jar
├── customview-poolingcontainer-1.0.0.aar
├── markwon-core.jar
├── markwon-core-4.6.2.aar
├── commonmark-0.18.2.jar
├── rv_extracted/
├── core_extracted/
└── customview_pooling_extracted/
```

当前 `build_app.ps1` 主要使用：

```text
recyclerview-1.3.2.jar
core-1.10.1.jar
collection-1.4.0.jar
customview-poolingcontainer-1.0.0.jar
rv_extracted/res
```

`markwon` 和 `commonmark` 是历史依赖，目前 app-noteai 主流程不依赖它们，但保留在 libs 中，避免后续需要时缺失。

## build_app.ps1 独立构建脚本

路径：

```text
build_app.ps1
```

默认只构建 APK：

```powershell
powershell -ExecutionPolicy Bypass -File .\build_app.ps1
```

输出：

```text
build/noteai.apk
```

如果要构建并安装到设备/模拟器：

```powershell
$env:ANDROID_SERIAL='emulator-5554'
powershell -ExecutionPolicy Bypass -File .\build_app.ps1 -Install
```

构建脚本做的事情：

```text
1. 编译 C++ engine 生成 libnote_engine.so
2. aapt2 编译/链接资源
3. javac 编译 app + engine Java 源码
4. d8 生成 dex
5. 把 dex 和 native so 打进 APK
6. zipalign + apksigner 签名
7. 可选安装并启动 App
```

## .gitignore

路径：

```text
.gitignore
```

已忽略：

```text
build/
*.apk
*.idsig
*.dex
*.class
.gradle/
.idea/
.vscode/
*.log
*.tmp
```

因此构建产物不会被提交。


## Markdown 图片协议

插图按钮生成的 Markdown 格式：

```markdown
![图片](images/img_1710000000000.jpg){width=1080 height=720}
```

含义：

```text
images/img_1710000000000.jpg 是 App 私有目录 files/images/ 下的相对路径
width 是原始图片宽度
height 是原始图片高度
```

真实文件位置：

```text
context.getFilesDir()/images/img_1710000000000.jpg
```

数据库只保存 Markdown 文本，不保存图片二进制。

## 开发注意事项

- 现在 `app-noteai` 是独立项目，后续开发优先在这里进行。
- 不要再依赖外层 `NoteAI/build_compare.ps1` 来构建 app-noteai。
- 不要删除 `engine/`，Markdown 预览依赖它。
- 不要删除 `libs/`，RecyclerView 和 AndroidX 编译依赖它。
- 不要提交 `build/` 目录。
- UI 页面不要直接写 SQL。
- AI 逻辑不要写进悬浮球组件。
- 图片选择和图片渲染分开：选择图片在 `ImageInsertManager`，预览加载在 `ImageLoader`。
- Markdown 编辑模式显示原文，预览模式显示渲染结果。
