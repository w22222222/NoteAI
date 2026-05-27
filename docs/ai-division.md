# AI 组两人分工说明

本文档只针对 `app-noteai` 版本。AI 组两个人分别负责“摘要”和“润色”。UI 已经通过悬浮球接入 `AiService`，AI 同学只需要实现接口，不需要改悬浮球绘制逻辑。

## 总原则

- AI 组统一实现 `AiService` 接口。
- 不要在 `AiFloatingBall` 里写 AI 请求逻辑。
- 不要在代码中硬编码 API Key。
- 所有回调必须回到主线程后再调用 `callback.onSuccess` 或 `callback.onError`。
- AI 返回内容必须保持 Markdown 兼容，尤其润色不能破坏代码块和图片语法。

## 核心文件

| 文件 | 作用 |
|---|---|
| `ai/AiService.java` | AI 功能统一接口 |
| `ai/PlaceholderAiService.java` | 当前占位实现，后续可替换为真实实现 |
| `NoteEditActivity.java` | 已经接好悬浮球和 AI 接口调用 |
| `widget/AiFloatingBall.java` | 悬浮球 UI，不建议 AI 同学修改 |

## 当前调用链

```text
用户点击 AI 悬浮球
    ↓
AiFloatingBall.Callback
    ↓
NoteEditActivity
    ↓
aiService.summarize(...) 或 aiService.polish(...)
    ↓
AI 实现返回结果
    ↓
UI 展示摘要 / 替换润色文本
```

## 接口协议

文件：`app-noteai/src/main/java/com/noteai/noteai/ai/AiService.java`

```java
void summarize(String title, String content, Callback callback);

void polish(String title, String content, Callback callback);

interface Callback {
    void onSuccess(String result);

    void onError(String message);
}
```

## AI A：摘要功能负责人

### 负责范围

AI A 只负责摘要功能：

```java
summarize(String title, String content, Callback callback)
```

### 要写的文件

建议新增：

```text
app-noteai/src/main/java/com/noteai/noteai/ai/RealAiService.java
```

或者直接修改：

```text
app-noteai/src/main/java/com/noteai/noteai/ai/PlaceholderAiService.java
```

正式项目推荐新增 `RealAiService`，保留 `PlaceholderAiService` 作为测试 mock。

### 输入示例

```java
String title = "机器学习笔记";
String content = "# 监督学习\n\n监督学习需要带标签的数据集...\n\n## 常见算法\n\n- 线性回归\n- 决策树\n- SVM";

summarize(title, content, callback);
```

### 输出示例

```text
本文主要介绍监督学习的基本概念。重点包括：

1. 监督学习依赖带标签的数据集。
2. 常见算法包括线性回归、决策树和 SVM。
3. 适合用于分类和回归任务。
```

### 成功回调

```java
callback.onSuccess(summaryText);
```

### 失败回调

```java
callback.onError("摘要生成失败，请稍后重试");
```

### 摘要结果要求

- 返回纯文本或 Markdown 文本都可以。
- 不要返回 JSON 字符串给 UI。
- 不要包含无关提示词。
- 长度建议控制在 100 到 500 字。
- 如果正文为空，返回错误或提示“正文为空，无法摘要”。

### UI 展示位置

摘要结果会显示在悬浮窗口里：

```java
floatingBall.showSummaryResult(result);
```

AI A 不需要直接操作悬浮窗口。

## AI B：润色功能负责人

### 负责范围

AI B 只负责润色功能：

```java
polish(String title, String content, Callback callback)
```

### 要写的文件

建议和 AI A 使用同一个真实实现类：

```text
app-noteai/src/main/java/com/noteai/noteai/ai/RealAiService.java
```

AI A 写 `summarize`，AI B 写 `polish`。

### 输入示例

```java
String title = "项目总结";
String content = "# 今天总结\n\n今天我们做了很多东西，然后有些地方还不太好，需要后面再改。";

polish(title, content, callback);
```

### 输出示例

```markdown
# 今日项目总结

今天我们完成了多个核心功能的开发，同时也发现了一些仍需优化的问题。后续将继续完善交互细节，并提升整体稳定性。
```

### 成功回调

```java
callback.onSuccess(polishedMarkdown);
```

### 失败回调

```java
callback.onError("润色失败，请稍后重试");
```

### 润色结果要求

- 必须返回完整 Markdown 正文。
- 不能只返回修改建议。
- 不能破坏 Markdown 标题层级。
- 不能破坏代码块。
- 不能改坏图片语法。
- 尽量保留原文结构。

### 重点保护协议

#### 代码块不能被改坏

输入：

````markdown
```java
System.out.println("hello");
```
````

输出必须仍然是合法代码块：

````markdown
```java
System.out.println("hello");
```
````

#### 图片语法不能被改坏

输入：

```markdown
![图片](images/img_1710000000000.jpg){width=1080 height=720}
```

输出必须保留路径和尺寸：

```markdown
![图片](images/img_1710000000000.jpg){width=1080 height=720}
```

不要改成：

```markdown
[图片](images/img_1710000000000.jpg)
```

也不要删除 `{width=1080 height=720}`。

### UI 使用方式

润色成功后，编辑页会直接替换正文：

```java
contentEdit.setText(result);
markDirty();
```

所以 AI B 返回内容必须是可以直接放进编辑框的完整正文。

## RealAiService 建议结构

```java
public class RealAiService implements AiService {
    @Override
    public void summarize(String title, String content, Callback callback) {
        // AI A 实现
    }

    @Override
    public void polish(String title, String content, Callback callback) {
        // AI B 实现
    }
}
```

接入时在 `NoteEditActivity.java` 中替换：

```java
aiService = new PlaceholderAiService(this);
```

为：

```java
aiService = new RealAiService(this);
```

如果真实 AI 需要网络请求，必须注意：

- 网络请求放后台线程。
- 回调 UI 前切回主线程。
- API Key 不要写死在客户端源码里。
- 网络失败要调用 `onError`。

## 两人协作边界

| 人员 | 函数 | 输出 |
|---|---|---|
| AI A | `summarize` | 摘要文本 |
| AI B | `polish` | 润色后的完整 Markdown 正文 |

## 完成标准

- 点击 AI 摘要，先显示“正在总结...”，随后显示摘要结果。
- 点击 AI 润色，返回润色后的正文并替换编辑框内容。
- 空正文、网络失败、接口异常都有错误提示。
- 润色不会破坏代码块和图片语法。
- 所有回调最终回到主线程。
