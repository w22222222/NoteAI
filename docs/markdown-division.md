# Markdown 渲染一人分工说明

本文档只针对 `app-noteai` 版本。Markdown 渲染由 1 人负责，重点是预览渲染、图片显示、长文档性能和与编辑页/图片插入协议的衔接。

## 总职责

Markdown 负责人只关心：

```text
Markdown 原文
    ↓
解析成块
    ↓
RecyclerView 渲染
    ↓
文本、标题、列表、代码块、引用、分割线、图片显示
```

不负责：

- SQLite 如何存储笔记。
- AI 如何生成摘要/润色。
- 首页标签分类搜索业务。
- 编辑框底层文本输入能力。

## 核心文件

| 文件 | 作用 |
|---|---|
| `widget/MarkdownRenderView.java` | App 内 Markdown 预览入口 |
| `engine/BlockExtractor.java` | 把解析结果拆成块 |
| `engine/BlockAdapter.java` | RecyclerView 渲染每个块 |
| `engine/Block.java` | 块类型定义，需要补图片块类型 |
| `engine/SpanInfo.java` | 行内 Span 信息，已有 `TYPE_IMAGE` |
| `engine/MarkdownParser.java` | Java Markdown 解析，已能识别 `![alt](path)` |
| `image/ImageLoader.java` | 图片加载接口 |
| `image/LocalImageLoader.java` | 本地图片加载实现位置 |
| `image/InsertedImage.java` | 插图返回协议，包含图片宽高 |
| `NoteEditActivity.java` | 编辑页通过 `previewView.submitMarkdown(content)` 触发预览 |

## 当前预览入口协议

文件：`app-noteai/src/main/java/com/noteai/noteai/widget/MarkdownRenderView.java`

```java
public void submitMarkdown(String markdown)
```

输入示例：

```markdown
# 标题

这是一段文字。

![图片](images/img_1710000000000.jpg){width=1080 height=720}

```java
System.out.println("hello");
```
```

输出：

```text
RecyclerView 中显示：
- 标题块
- 普通段落块
- 图片块
- 代码块
```

## 你要重点实现的功能

## 1. 图片块渲染

### 当前状态

编辑页插图按钮已经会生成：

```markdown
![图片](images/img_1710000000000.jpg){width=1080 height=720}
```

其中：

```text
images/img_1710000000000.jpg 是 App 私有目录 files/images/ 下的相对路径
width=1080 是图片原始宽度
height=720 是图片原始高度
```

### 要做的事

你需要让预览模式识别这种 Markdown，并显示图片。

建议流程：

```text
MarkdownParser 识别图片 Span
    ↓
BlockExtractor 把独占一行的图片转成 Image Block
    ↓
BlockAdapter 根据 Image Block 创建 ImageView ViewHolder
    ↓
ImageLoader 异步加载本地图片
    ↓
显示图片
```

### 推荐块协议

建议在 `engine/Block.java` 中增加：

```java
public static final int TYPE_IMAGE = 6;

public String imagePath;
public String imageAlt;
public int imageWidth;
public int imageHeight;
```

如果不想改太多字段，也可以先复用：

```java
block.text = "images/img_1710000000000.jpg";
block.lang = "1080x720";
```

但推荐新增明确字段，后续维护更清楚。

### 图片 Markdown 输入示例

```markdown
![图片](images/demo.jpg){width=1080 height=720}
```

解析结果建议：

```java
block.type = Block.TYPE_IMAGE;
block.imagePath = "images/demo.jpg";
block.imageAlt = "图片";
block.imageWidth = 1080;
block.imageHeight = 720;
```

### 图片路径协议

Markdown 里保存的是相对路径：

```text
images/demo.jpg
```

真实文件位置：

```text
context.getFilesDir()/images/demo.jpg
```

所以图片加载时要把相对路径解析成：

```java
new File(context.getFilesDir(), imagePath)
```

不要把绝对路径写进 Markdown 或数据库。

## 2. ImageLoader 实现

文件：`app-noteai/src/main/java/com/noteai/noteai/image/LocalImageLoader.java`

当前接口：

```java
void loadImage(String source, int targetWidth, int targetHeight, Callback callback);

void cancel(String source);
```

### 要实现的行为

输入：

```java
loadImage("images/img_1710000000000.jpg", 720, 480, callback)
```

处理：

```text
1. source 是相对路径。
2. 拼成 files/images/img_1710000000000.jpg。
3. 后台线程读取图片。
4. 使用 BitmapFactory.Options.inJustDecodeBounds 先读原图宽高。
5. 根据 targetWidth / targetHeight 计算 inSampleSize。
6. 后台解码 Bitmap。
7. 回主线程 callback.onSuccess(bitmap, originalWidth, originalHeight)。
```

成功输出：

```java
callback.onSuccess(bitmap, 1080, 720);
```

失败输出：

```java
callback.onError("图片文件不存在");
```

### 为什么要异步

图片解码可能耗时较长，不能在主线程做，否则滑动会卡顿。

### cancel 协议

RecyclerView 复用时，如果旧图片还在加载，要调用：

```java
cancel(oldSource)
```

最简单版本可以先不真正取消线程，但必须用版本号或 source 校验，避免旧图片加载结果设置到新 ViewHolder 上。

## 3. 图片显示尺寸策略

图片 Markdown 带原始尺寸：

```markdown
![图片](images/a.jpg){width=1080 height=720}
```

预览时不能按原始像素直接显示，因为手机屏幕放不下。

建议策略：

```text
显示宽度 = min(屏幕可用宽度, 原始宽度)
显示高度 = 显示宽度 * 原始高度 / 原始宽度
```

例子：

```text
原图：1080 x 720
屏幕可用宽度：360dp
显示：360dp x 240dp
```

这样能保持比例，并提前占位，减少图片加载完成后的布局跳动。

## 4. 编辑模式是否显示图片

当前 App 采用：

```text
编辑 / 预览切换模式
```

所以编辑模式只显示 Markdown 原文：

```markdown
![图片](images/a.jpg){width=1080 height=720}
```

预览模式才显示图片。

不要在 `EditText` 里做图片富文本显示，除非后期明确改需求。

## 5. 代码块和其他 Markdown 渲染

你还需要继续维护：

```text
标题
段落
加粗
斜体
行内代码
代码块
列表
引用
分割线
链接
```

重点文件：

```text
engine/BlockAdapter.java
engine/BlockExtractor.java
engine/MarkdownParser.java
```

代码块复制按钮目前已经是中文：

```text
复制
已复制
```

## 6. MarkdownRenderView 职责边界

`MarkdownRenderView` 只做：

```text
接收 Markdown 字符串
异步解析
更新 RecyclerView
```

不要在里面写：

- 数据库存取。
- AI 请求。
- 编辑框输入逻辑。
- 图片选择器逻辑。

图片选择器已经在编辑页和 `ImageInsertManager` 中处理。

## 图片插入与图片渲染的完整协议

### 插入阶段

文件：`LocalImageInsertManager.java`

输入：

```java
Uri sourceUri
```

输出：

```java
InsertedImage image
```

示例：

```java
image.localPath = "images/img_1710000000000.jpg";
image.markdownText = "![图片](images/img_1710000000000.jpg){width=1080 height=720}";
image.width = 1080;
image.height = 720;
```

### 存储阶段

数据库只保存 Markdown 正文：

```markdown
今天的截图：

![图片](images/img_1710000000000.jpg){width=1080 height=720}
```

不要单独把图片二进制存进 SQLite。

### 渲染阶段

Markdown 渲染读取：

```markdown
![图片](images/img_1710000000000.jpg){width=1080 height=720}
```

解析成：

```java
path = "images/img_1710000000000.jpg"
width = 1080
height = 720
```

再调用：

```java
imageLoader.loadImage(path, targetWidth, targetHeight, callback)
```

## 推荐开发顺序

1. 在 `Block.java` 加图片块类型和字段。
2. 在 `BlockExtractor.java` 识别独占一行图片。
3. 在 `BlockAdapter.java` 增加 ImageView ViewHolder。
4. 在 `LocalImageLoader.java` 实现本地图片异步解码。
5. 使用示例笔记中的图片语法测试解析。
6. 使用编辑页“插图”按钮插入真实图片测试。
7. 优化长文档滑动和图片加载取消。

## 完成标准

- 示例笔记中的图片语法能被识别为图片块。
- 用户插入真实图片后，编辑框出现带宽高 Markdown。
- 切换到预览后能看到图片。
- 图片按比例显示，不撑破屏幕。
- 图片加载不阻塞主线程。
- 快速滑动不会错图或闪图。
- 图片文件不存在时显示友好占位，而不是崩溃。
