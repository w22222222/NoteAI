package com.noteai.engine;

/**
 * 生成大规模 Markdown 测试文档，用于性能对比。
 */
public class MarkdownGenerator {

    private static final String[] HEADINGS = {
        "# 第一章  C++ 基础", "## 1.1 指针与引用", "### 1.1.1 智能指针",
        "## 1.2 模板编程", "# 第二章 JNI 开发", "## 2.1 JNI 环境搭建",
        "## 2.2 数据类型映射", "# 第三章 Markdown 解析器设计",
        "## 3.1 词法分析", "### 3.1.1 Token 设计", "## 3.2 语法分析",
        "## 3.3 性能优化", "### 3.3.1 内存池设计", "# 第四章 全文搜索",
        "## 4.1 倒排索引", "# 第五章 AI 集成", "## 5.1 摘要生成"
    };

    private static final String[] LIST_ITEMS = {
        "- 这是第一条要点，关于 **智能指针** 的使用",
        "- 第二条：`std::function` 和回调",
        "- 第三条：**性能优化** 的实际案例",
        "1. 有序列表第一项",
        "2. 有序列表第二项，包含 **重点内容**",
        "3. 有序列表第三项，参考 `std::string_view`"
    };

    private static final String[] PARAGRAPHS = {
        "今天学习了回调函数的基本概念，以及 `std::function` 的高级用法。",
        "这段内容包含 **加粗文字** 和 *斜体文字*，用于测试解析器。",
        "参考 [Google C++ Style Guide](https://google.github.io/styleguide/cppguide.html) 规范。",
        "> 代码是写给人看的，顺便能在机器上运行。",
        "在 C++ 中，使用 `std::string_view` 可以避免不必要的字符串拷贝。",
        "`auto` 关键字让类型推导变得简单高效。",
        "这里有一个 [链接](https://example.com) 可以点击。"
    };

    public static String generate(int totalLines) {
        StringBuilder sb = new StringBuilder(totalLines * 50);
        int hi = 0, li = 0, pi = 0;
        int lc = 0;

        while (lc < totalLines) {
            if (lc % 40 == 0) {
                sb.append(HEADINGS[hi % HEADINGS.length]).append("\n");
                hi++;
                lc++;
            } else if (lc % 6 == 0) {
                sb.append(LIST_ITEMS[li % LIST_ITEMS.length]).append("\n");
                li++;
                lc++;
            } else if (lc % 25 == 24) {
                sb.append("```cpp\n");
                sb.append("void sort(int* arr, int n) {\n");
                sb.append("    for (int i = 0; i < n; i++) {\n");
                sb.append("        for (int j = i + 1; j < n; j++) {\n");
                sb.append("            if (arr[i] > arr[j]) {\n");
                sb.append("                std::swap(arr[i], arr[j]);\n");
                sb.append("            }\n");
                sb.append("        }\n");
                sb.append("    }\n");
                sb.append("}\n");
                sb.append("```\n");
                lc += 11;
            } else {
                sb.append(PARAGRAPHS[pi % PARAGRAPHS.length]).append("\n");
                pi++;
                lc++;
            }
        }
        return sb.toString();
    }
}
