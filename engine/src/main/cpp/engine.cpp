#include "engine.hpp"
#include <cstring>
#include <algorithm>

namespace noteai {

// ==================== 块级语法定界 ====================

MarkdownEngine::LineInfo MarkdownEngine::nextLine(std::string_view& remaining) {
    LineInfo info{};
    if (remaining.empty()) return info;

    auto nl = remaining.find('\n');
    if (nl != std::string_view::npos) {
        size_t len = nl;
        if (len > 0 && remaining[len - 1] == '\r') --len;
        info.content = remaining.substr(0, len);
        info.hasTrailingNewline = true;
        remaining = remaining.substr(nl + 1);
    } else {
        info.content = remaining;
        info.hasTrailingNewline = false;
        remaining = {};
    }
    return info;
}

bool MarkdownEngine::isHeading(std::string_view line, int& level,
                                std::string_view& content) {
    if (line.size() < 2) return false;
    if (line[0] != '#') return false;

    level = 0;
    while (level < 6 && level < (int)line.size() && line[level] == '#') {
        ++level;
    }
    if (level >= (int)line.size() || line[level] != ' ') return false;

    content = line.substr(level + 1);
    return true;
}

bool MarkdownEngine::isCodeFence(std::string_view line, std::string_view& lang) {
    if (line.size() < 3) return false;
    if (line[0] != '`' || line[1] != '`' || line[2] != '`') return false;

    lang = line.substr(3);
    while (!lang.empty() && lang.front() == ' ') lang.remove_prefix(1);
    return true;
}

bool MarkdownEngine::isOrderedList(std::string_view line, std::string_view& content) {
    if (line.empty()) return false;
    if (line[0] < '1' || line[0] > '9') return false;

    auto dot = line.find(". ");
    if (dot == std::string_view::npos || dot < 1) return false;

    for (size_t i = 0; i < dot; ++i) {
        if (line[i] < '0' || line[i] > '9') return false;
    }
    content = line.substr(dot + 2);
    return true;
}

bool MarkdownEngine::isUnorderedList(std::string_view line, std::string_view& content) {
    if (line.size() < 2) return false;
    char first = line[0];
    if ((first == '-' || first == '*' || first == '+') && line[1] == ' ') {
        content = line.substr(2);
        return true;
    }
    return false;
}

bool MarkdownEngine::isQuote(std::string_view line, std::string_view& content) {
    if (line.size() >= 2 && line[0] == '>' && line[1] == ' ') {
        content = line.substr(2);
        return true;
    }
    if (line.size() >= 1 && line[0] == '>') {
        content = line.substr(1);
        return true;
    }
    return false;
}

bool MarkdownEngine::isRule(std::string_view line) {
    if (line.size() < 3) return false;
    char c = line[0];
    if (c != '-' && c != '*' && c != '_') return false;

    size_t count = 0;
    for (char ch : line) {
        if (ch == c) ++count;
        else if (ch != ' ') return false;
    }
    return count >= 3;
}

bool MarkdownEngine::isTableRow(std::string_view line) {
    if (line.size() < 3) return false;
    size_t pipes = 0;
    for (char c : line) {
        if (c == '|') {
            ++pipes;
            if (pipes >= 2) return true;
        }
    }
    return false;
}

bool MarkdownEngine::isTableSeparator(std::string_view line) {
    if (line.size() < 3) return false;
    // 格式: | --- | --- | 或 |:---|:---:|
    for (char c : line) {
        if (c != '|' && c != '-' && c != ':' && c != ' ') return false;
    }
    // 至少包含一个 '-'
    return line.find('-') != std::string_view::npos;
}

// ==================== Extra 字符串管理 ====================

int MarkdownEngine::appendExtra(std::string_view str) {
    if (str.empty()) return -1;
    int offset = (int)extras_.size();
    extras_.append(str.data(), str.size());
    extras_.push_back('\n');
    return offset;
}

// ==================== 行内解析 ====================

void MarkdownEngine::parseInlines(std::string_view text, std::string& plainOut,
                                   std::vector<SpanData>& spans) {
    size_t i = 0;
    while (i < text.size()) {
        // **bold**
        if (i + 1 < text.size() && text[i] == '*' && text[i+1] == '*') {
            size_t end = text.find("**", i + 2);
            if (end != std::string_view::npos && end > i + 2) {
                int spanStart = (int)plainOut.size();
                plainOut.append(text.data() + i + 2, end - i - 2);
                int spanEnd = (int)plainOut.size();
                spans.push_back({spanStart, spanEnd, TYPE_BOLD, 0, -1});
                i = end + 2;
                continue;
            }
        }

        // *italic* (单 *, 非 **)
        if (text[i] == '*') {
            size_t end = text.find('*', i + 1);
            if (end != std::string_view::npos && end > i + 1) {
                int spanStart = (int)plainOut.size();
                plainOut.append(text.data() + i + 1, end - i - 1);
                int spanEnd = (int)plainOut.size();
                spans.push_back({spanStart, spanEnd, TYPE_ITALIC, 0, -1});
                i = end + 1;
                continue;
            }
        }

        // `code`
        if (text[i] == '`') {
            size_t end = text.find('`', i + 1);
            if (end != std::string_view::npos && end > i + 1) {
                int spanStart = (int)plainOut.size();
                plainOut.append(text.data() + i + 1, end - i - 1);
                int spanEnd = (int)plainOut.size();
                spans.push_back({spanStart, spanEnd, TYPE_CODE, 0, -1});
                i = end + 1;
                continue;
            }
        }

        // ![alt](path)
        if (i + 1 < text.size() && text[i] == '!' && text[i+1] == '[') {
            size_t bracketEnd = text.find(']', i + 2);
            if (bracketEnd != std::string_view::npos
                && bracketEnd + 1 < text.size()
                && text[bracketEnd + 1] == '(') {
                size_t parenEnd = text.find(')', bracketEnd + 2);
                if (parenEnd != std::string_view::npos) {
                    std::string_view alt = text.substr(i + 2, bracketEnd - i - 2);
                    std::string_view path = text.substr(bracketEnd + 2, parenEnd - bracketEnd - 2);

                    int spanStart = (int)plainOut.size();
                    plainOut.push_back('[');
                    if (alt.empty()) {
                        plainOut.append("image");
                    } else {
                        plainOut.append(alt.data(), alt.size());
                    }
                    plainOut.push_back(']');
                    int spanEnd = (int)plainOut.size();

                    spans.push_back({spanStart, spanEnd, TYPE_IMAGE, 0, appendExtra(path)});
                    i = parenEnd + 1;
                    continue;
                }
            }
        }

        // [text](url)
        if (text[i] == '[') {
            size_t bracketEnd = text.find(']', i + 1);
            if (bracketEnd != std::string_view::npos
                && bracketEnd + 1 < text.size()
                && text[bracketEnd + 1] == '(') {
                size_t parenEnd = text.find(')', bracketEnd + 2);
                if (parenEnd != std::string_view::npos) {
                    std::string_view linkText = text.substr(i + 1, bracketEnd - i - 1);
                    std::string_view url = text.substr(bracketEnd + 2, parenEnd - bracketEnd - 2);

                    int spanStart = (int)plainOut.size();
                    plainOut.append(linkText.data(), linkText.size());
                    int spanEnd = (int)plainOut.size();

                    spans.push_back({spanStart, spanEnd, TYPE_LINK, 0, appendExtra(url)});
                    i = parenEnd + 1;
                    continue;
                }
            }
        }

        // 普通字符
        plainOut.push_back(text[i]);
        ++i;
    }
}

// ==================== 主解析 ====================

ParseResult MarkdownEngine::parse(std::string_view markdown) {
    ParseResult result;
    extras_.clear();

    if (markdown.empty()) return result;

    result.plainText.reserve(markdown.size());
    result.spans.reserve(markdown.size() / 20);

    std::string_view remaining = markdown;

    bool inCodeBlock = false;
    std::string_view codeLang;
    int codeBlockStart = -1;

    while (!remaining.empty()) {
        // 保存 remaining 快照以防需要 peek ahead
        std::string_view snapshot = remaining;
        auto line = nextLine(remaining);

        if (line.content.empty()) {
            result.plainText.push_back('\n');
            continue;
        }

        // 代码块
        std::string_view fenceLang;
        if (!inCodeBlock && isCodeFence(line.content, fenceLang)) {
            inCodeBlock = true;
            codeLang = fenceLang;
            codeBlockStart = (int)result.plainText.size();
            continue;
        }
        if (inCodeBlock) {
            if (isCodeFence(line.content, fenceLang)) {
                inCodeBlock = false;
                int extraOff = appendExtra(codeLang);
                result.spans.push_back({codeBlockStart, (int)result.plainText.size(),
                                        TYPE_CODE_BLOCK, 0, extraOff});
                codeBlockStart = -1;
            } else {
                result.plainText.append(line.content.data(), line.content.size());
                result.plainText.push_back('\n');
            }
            continue;
        }

        // 表格：先看当前行是否是表格行，再看下一行是否是分隔符
        if (isTableRow(line.content) && !isTableSeparator(line.content)) {
            std::string_view peekRemaining = remaining;
            auto peek = nextLine(peekRemaining);
            if (!peek.content.empty() && isTableSeparator(peek.content)) {
                // 确认表格：解析表头
                int startPos = (int)result.plainText.size();
                parseInlines(line.content, result.plainText, result.spans);
                int endPos = (int)result.plainText.size();
                result.spans.push_back({startPos, endPos, TYPE_TABLE, 0, -1});
                result.plainText.push_back('\n');

                // 跳过后续表格行
                remaining = peekRemaining;
                while (!remaining.empty()) {
                    auto row = nextLine(remaining);
                    if (row.content.empty() || !isTableRow(row.content)
                        || isTableSeparator(row.content)) {
                        // 表格结束，当前行不是表格行，需要回退
                        // 但 remaining 已经前进过了... 
                        // 简单处理: 表格行结束条件宽松即可
                        break;
                    }
                    int rowStart = (int)result.plainText.size();
                    parseInlines(row.content, result.plainText, result.spans);
                    int rowEnd = (int)result.plainText.size();
                    result.spans.push_back({rowStart, rowEnd, TYPE_TABLE, 0, -1});
                    result.plainText.push_back('\n');
                }
                continue;
            }
            // 不是表格，回退并正常处理
            remaining = snapshot;
            line = nextLine(remaining);
        }

        // 标题
        int headingLevel;
        std::string_view headingContent;
        if (isHeading(line.content, headingLevel, headingContent)) {
            int startPos = (int)result.plainText.size();
            parseInlines(headingContent, result.plainText, result.spans);
            int endPos = (int)result.plainText.size();
            result.spans.push_back({startPos, endPos, TYPE_HEADING, headingLevel, -1});
            result.plainText.push_back('\n');
            continue;
        }

        // 有序列表
        std::string_view olContent;
        if (isOrderedList(line.content, olContent)) {
            int startPos = (int)result.plainText.size();
            parseInlines(olContent, result.plainText, result.spans);
            int endPos = (int)result.plainText.size();
            result.spans.push_back({startPos, endPos, TYPE_LIST, 0, -1});
            result.plainText.push_back('\n');
            continue;
        }

        // 无序列表
        std::string_view ulContent;
        if (isUnorderedList(line.content, ulContent)) {
            int startPos = (int)result.plainText.size();
            parseInlines(ulContent, result.plainText, result.spans);
            int endPos = (int)result.plainText.size();
            result.spans.push_back({startPos, endPos, TYPE_LIST, 0, -1});
            result.plainText.push_back('\n');
            continue;
        }

        // 引用
        std::string_view quoteContent;
        if (isQuote(line.content, quoteContent)) {
            int startPos = (int)result.plainText.size();
            parseInlines(quoteContent, result.plainText, result.spans);
            int endPos = (int)result.plainText.size();
            result.spans.push_back({startPos, endPos, TYPE_QUOTE, 0, -1});
            result.plainText.push_back('\n');
            continue;
        }

        // 分割线
        if (isRule(line.content)) {
            int startPos = (int)result.plainText.size();
            result.plainText.push_back('\n');
            result.spans.push_back({startPos, startPos + 1, TYPE_RULE, 0, -1});
            continue;
        }

        // 普通段落
        parseInlines(line.content, result.plainText, result.spans);
        result.plainText.push_back('\n');
    }

    // 未闭合的代码块
    if (inCodeBlock && codeBlockStart >= 0) {
        int extraOff = appendExtra(codeLang);
        result.spans.push_back({codeBlockStart, (int)result.plainText.size(),
                                TYPE_CODE_BLOCK, 0, extraOff});
    }

    result.extras = std::move(extras_);
    return result;
}

} // namespace noteai
