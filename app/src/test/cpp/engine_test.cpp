#include <cstdio>
#include <cstring>
#include <cassert>
#include "engine.hpp"

using namespace noteai;

static int passed = 0;
static int failed = 0;

#define TEST(name) printf("  [TEST] %s ... ", name)
#define OK() do { printf("OK\n"); passed++; } while(0)
#define FAIL(msg) do { printf("FAIL: %s\n", msg); failed++; } while(0)

#define ASSERT_EQ(a, b, label) do { \
    if ((a) != (b)) { printf("FAIL: %s expected=%d got=%d\n", label, (int)(b), (int)(a)); failed++; return; } \
} while(0)

#define ASSERT_STREQ(a, b, label) do { \
    if (strcmp((a), (b)) != 0) { \
        printf("FAIL: %s\nexpected: [%s]\ngot:      [%s]\n", label, (b), (a)); \
        failed++; return; \
    } \
} while(0)

void printResult(const ParseResult& r) {
    printf("  plainText (%zu chars): [%s]\n", r.plainText.size(), r.plainText.c_str());
    printf("  spans (%zu):\n", r.spans.size());
    for (size_t i = 0; i < r.spans.size(); ++i) {
        const auto& s = r.spans[i];
        printf("    [%zu] type=%d start=%d end=%d level=%d extraOff=%d\n",
               i, s.type, s.start, s.end, s.level, s.extraOffset);
    }
    printf("  extras (%zu chars): [", r.extras.size());
    for (char c : r.extras) {
        if (c == '\0') printf("\\0");
        else putchar(c);
    }
    printf("]\n");
}

// ==================== 测试用例 ====================

void test_empty() {
    TEST("empty input");
    MarkdownEngine e;
    auto r = e.parse("");
    ASSERT_EQ(r.plainText.size(), 0, "plainText size");
    ASSERT_EQ(r.spans.size(), 0, "spans count");
    OK();
}

void test_plain_text() {
    TEST("plain text");
    MarkdownEngine e;
    auto r = e.parse("hello world");
    ASSERT_STREQ(r.plainText.c_str(), "hello world\n", "plain text");
    ASSERT_EQ(r.spans.size(), 0, "no spans");
    OK();
}

void test_plain_multiline() {
    TEST("multiline plain text");
    MarkdownEngine e;
    auto r = e.parse("line1\nline2\nline3");
    ASSERT_STREQ(r.plainText.c_str(), "line1\nline2\nline3\n", "multiline");
    ASSERT_EQ(r.spans.size(), 0, "no spans");
    OK();
}

void test_bold_single() {
    TEST("bold single");
    MarkdownEngine e;
    auto r = e.parse("this is **bold** text");
    ASSERT_EQ(r.spans.size(), 1, "one span");
    ASSERT_EQ(r.spans[0].type, TYPE_BOLD, "type BOLD");
    ASSERT_EQ(r.spans[0].start, 8, "start");
    ASSERT_EQ(r.spans[0].end, 12, "end");
    OK();
}

void test_italic_single() {
    TEST("italic single");
    MarkdownEngine e;
    auto r = e.parse("this is *italic* text");
    ASSERT_EQ(r.spans.size(), 1, "one span");
    ASSERT_EQ(r.spans[0].type, TYPE_ITALIC, "type ITALIC");
    OK();
}

void test_bold_and_italic() {
    TEST("bold and italic mixed");
    MarkdownEngine e;
    auto r = e.parse("**bold** and *italic*");
    ASSERT_EQ(r.spans.size(), 2, "two spans");
    ASSERT_EQ(r.spans[0].type, TYPE_BOLD, "first BOLD");
    ASSERT_EQ(r.spans[1].type, TYPE_ITALIC, "second ITALIC");
    OK();
}

void test_unclosed_bold() {
    TEST("unclosed bold treated as literal");
    MarkdownEngine e;
    auto r = e.parse("this is **unclosed");
    ASSERT_STREQ(r.plainText.c_str(), "this is **unclosed\n", "has **");
    ASSERT_EQ(r.spans.size(), 0, "no spans");
    OK();
}

void test_inline_code() {
    TEST("inline code");
    MarkdownEngine e;
    auto r = e.parse("use `printf()` function");
    ASSERT_EQ(r.spans.size(), 1, "one span");
    ASSERT_EQ(r.spans[0].type, TYPE_CODE, "type CODE");
    ASSERT_STREQ(r.plainText.c_str(), "use printf() function\n", "code text");
    OK();
}

void test_link() {
    TEST("link");
    MarkdownEngine e;
    auto r = e.parse("[Google](https://google.com)");
    ASSERT_EQ(r.spans.size(), 1, "one span");
    ASSERT_EQ(r.spans[0].type, TYPE_LINK, "type LINK");
    ASSERT_STREQ(r.plainText.c_str(), "Google\n", "link text");

    // 验证 extra
    int off = r.spans[0].extraOffset;
    ASSERT_EQ(off >= 0, true, "has extra");
    const char* extra = r.extras.c_str() + off;
    ASSERT_STREQ(extra, "https://google.com", "url");
    OK();
}

void test_image() {
    TEST("image");
    MarkdownEngine e;
    auto r = e.parse("![screenshot](/path/img.png)");
    ASSERT_EQ(r.spans.size(), 1, "one span");
    ASSERT_EQ(r.spans[0].type, TYPE_IMAGE, "type IMAGE");

    int off = r.spans[0].extraOffset;
    ASSERT_EQ(off >= 0, true, "has extra");
    const char* extra = r.extras.c_str() + off;
    ASSERT_STREQ(extra, "/path/img.png", "image path");
    OK();
}

void test_heading_h1() {
    TEST("heading level 1");
    MarkdownEngine e;
    auto r = e.parse("# 标题一");
    ASSERT_EQ(r.spans.size(), 1, "one span");
    ASSERT_EQ(r.spans[0].type, TYPE_HEADING, "type HEADING");
    ASSERT_EQ(r.spans[0].level, 1, "level=1");
    ASSERT_STREQ(r.plainText.c_str(), "\xe6\xa0\x87\xe9\xa2\x98\xe4\xb8\x80\n", "heading text");
    OK();
}

void test_heading_h3() {
    TEST("heading level 3");
    MarkdownEngine e;
    auto r = e.parse("### Sub section");
    ASSERT_EQ(r.spans[0].level, 3, "level=3");
    ASSERT_STREQ(r.plainText.c_str(), "Sub section\n", "content");
    OK();
}

void test_heading_no_space() {
    TEST("heading without space is not heading");
    MarkdownEngine e;
    auto r = e.parse("#not-heading");
    ASSERT_STREQ(r.plainText.c_str(), "#not-heading\n", "plain");
    ASSERT_EQ(r.spans.size(), 0, "no spans");
    OK();
}

void test_unordered_list() {
    TEST("unordered list");
    MarkdownEngine e;
    auto r = e.parse("- item one\n- item two");
    ASSERT_EQ(r.spans.size(), 2, "two spans");
    ASSERT_EQ(r.spans[0].type, TYPE_LIST, "type LIST");
    ASSERT_EQ(r.spans[1].type, TYPE_LIST, "type LIST");
    OK();
}

void test_ordered_list() {
    TEST("ordered list");
    MarkdownEngine e;
    auto r = e.parse("1. first\n2. second");
    ASSERT_EQ(r.spans.size(), 2, "two spans");
    ASSERT_EQ(r.spans[0].type, TYPE_LIST, "type LIST");
    OK();
}

void test_quote() {
    TEST("blockquote");
    MarkdownEngine e;
    auto r = e.parse("> quoted text");
    ASSERT_EQ(r.spans.size(), 1, "one span");
    ASSERT_EQ(r.spans[0].type, TYPE_QUOTE, "type QUOTE");
    ASSERT_STREQ(r.plainText.c_str(), "quoted text\n", "quote");
    OK();
}

void test_quote_with_bold() {
    TEST("blockquote with nested bold");
    MarkdownEngine e;
    auto r = e.parse("> this is **important**");
    ASSERT_EQ(r.spans.size(), 2, "two spans: quote + bold");
    bool hasQuote = false, hasBold = false;
    for (const auto& s : r.spans) {
        if (s.type == TYPE_QUOTE) hasQuote = true;
        if (s.type == TYPE_BOLD) hasBold = true;
    }
    ASSERT_EQ(hasQuote, true, "has QUOTE");
    ASSERT_EQ(hasBold, true, "has BOLD");
    OK();
}

void test_rule() {
    TEST("horizontal rule");
    MarkdownEngine e;
    auto r = e.parse("---");
    ASSERT_EQ(r.spans.size(), 1, "one span");
    ASSERT_EQ(r.spans[0].type, TYPE_RULE, "type RULE");
    OK();
}

void test_rule_stars() {
    TEST("rule with stars");
    MarkdownEngine e;
    auto r = e.parse("***");
    ASSERT_EQ(r.spans[0].type, TYPE_RULE, "type RULE");
    OK();
}

void test_code_block() {
    TEST("code block");
    MarkdownEngine e;
    auto r = e.parse("```cpp\nint main() {\n    return 0;\n}\n```");
    ASSERT_EQ(r.spans.size(), 1, "one span");
    ASSERT_EQ(r.spans[0].type, TYPE_CODE_BLOCK, "type CODE_BLOCK");

    int off = r.spans[0].extraOffset;
    ASSERT_EQ(off >= 0, true, "has extra");
    ASSERT_STREQ(r.extras.c_str() + off, "cpp", "language");

    ASSERT_STREQ(r.plainText.c_str(), "int main() {\n    return 0;\n}\n", "code content");
    OK();
}

void test_unclosed_code_block() {
    TEST("unclosed code block");
    MarkdownEngine e;
    auto r = e.parse("```js\nconsole.log(1);\n");
    ASSERT_EQ(r.spans.size(), 1, "one CODE_BLOCK span even if unclosed");
    ASSERT_EQ(r.spans[0].type, TYPE_CODE_BLOCK, "type CODE_BLOCK");
    OK();
}

void test_table_simple() {
    TEST("simple table");
    MarkdownEngine e;
    auto r = e.parse("| Name | Age |\n|------|-----|\n| Alice | 25 |");
    ASSERT_EQ(r.spans.size() >= 1, true, "at least one TABLE span");
    ASSERT_EQ(r.spans[0].type, TYPE_TABLE, "type TABLE");
    OK();
}

void test_list_with_inline() {
    TEST("list item with bold");
    MarkdownEngine e;
    auto r = e.parse("- **bold** item");
    ASSERT_EQ(r.spans.size(), 2, "LIST + BOLD");
    ASSERT_EQ(r.spans[0].type, TYPE_BOLD, "first is BOLD");
    ASSERT_EQ(r.spans[1].type, TYPE_LIST, "second is LIST");
    OK();
}

void test_mixed_document() {
    TEST("full mixed document");
    MarkdownEngine e;
    auto r = e.parse(
        "# 标题\n\n"
        "这是 **粗体** 和 *斜体*。\n\n"
        "> 引用中的 `代码`\n\n"
        "```\n"
        "raw code\n"
        "```\n\n"
        "- 列表项\n"
        "1. 有序项\n\n"
        "[链接](https://example.com)\n\n"
        "---\n"
    );
    printf("\n");
    printResult(r);

    int types[11] = {};
    for (const auto& s : r.spans) {
        if (s.type >= 0 && s.type < 11) types[s.type]++;
    }
    ASSERT_EQ(types[TYPE_HEADING], 1, "HEADING count");
    ASSERT_EQ(types[TYPE_BOLD], 1, "BOLD count");
    ASSERT_EQ(types[TYPE_ITALIC], 1, "ITALIC count");
    ASSERT_EQ(types[TYPE_CODE], 1, "CODE count");
    ASSERT_EQ(types[TYPE_CODE_BLOCK], 1, "CODE_BLOCK count");
    ASSERT_EQ(types[TYPE_LIST], 2, "LIST count");
    ASSERT_EQ(types[TYPE_QUOTE], 1, "QUOTE count");
    ASSERT_EQ(types[TYPE_LINK], 1, "LINK count");
    ASSERT_EQ(types[TYPE_RULE], 1, "RULE count");
    OK();
}

void test_newline_variants() {
    TEST("CRLF line endings");
    MarkdownEngine e;
    auto r = e.parse("line1\r\nline2\r\nline3");
    ASSERT_STREQ(r.plainText.c_str(), "line1\nline2\nline3\n", "CRLF text");
    OK();
}

int main() {
    printf("=== MarkdownEngine C++ Tests ===\n\n");

    test_empty();
    test_plain_text();
    test_plain_multiline();
    test_bold_single();
    test_italic_single();
    test_bold_and_italic();
    test_unclosed_bold();
    test_inline_code();
    test_link();
    test_image();
    test_heading_h1();
    test_heading_h3();
    test_heading_no_space();
    test_unordered_list();
    test_ordered_list();
    test_quote();
    test_quote_with_bold();
    test_rule();
    test_rule_stars();
    test_code_block();
    test_unclosed_code_block();
    test_table_simple();
    test_list_with_inline();
    test_mixed_document();
    test_newline_variants();

    printf("\n=== Results: %d passed, %d failed ===\n", passed, failed);
    return failed > 0 ? 1 : 0;
}
