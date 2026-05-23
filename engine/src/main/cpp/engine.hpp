#pragma once

#include <string>
#include <string_view>
#include <vector>

namespace noteai {

enum SpanType : int {
    TYPE_HEADING    = 0,
    TYPE_BOLD       = 1,
    TYPE_ITALIC     = 2,
    TYPE_CODE       = 3,
    TYPE_CODE_BLOCK = 4,
    TYPE_LIST       = 5,
    TYPE_QUOTE      = 6,
    TYPE_LINK       = 7,
    TYPE_IMAGE      = 8,
    TYPE_RULE       = 9,
    TYPE_TABLE      = 10
};

struct SpanData {
    int start;
    int end;
    int type;
    int level;
    int extraOffset;  // byte offset into extras string, -1 means none
};

struct ParseResult {
    std::string plainText;
    std::vector<SpanData> spans;
    std::string extras;  // null-separated concatenation of all extra strings
};

class MarkdownEngine {
public:
    ParseResult parse(std::string_view markdown);

private:
    void parseInlines(std::string_view text, std::string& plainOut,
                      std::vector<SpanData>& spans);

    int appendExtra(std::string_view str);

    // 逐行扫描辅助
    struct LineInfo {
        std::string_view content;
        bool hasTrailingNewline;
    };
    LineInfo nextLine(std::string_view& remaining);

    // 块级语法判断
    bool isHeading(std::string_view line, int& level, std::string_view& content);
    bool isCodeFence(std::string_view line, std::string_view& lang);
    bool isOrderedList(std::string_view line, std::string_view& content);
    bool isUnorderedList(std::string_view line, std::string_view& content);
    bool isQuote(std::string_view line, std::string_view& content);
    bool isRule(std::string_view line);
    bool isTableRow(std::string_view line);
    bool isTableSeparator(std::string_view line);

    std::string extras_;
};

} // namespace noteai
