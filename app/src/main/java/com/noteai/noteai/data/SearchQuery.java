package com.noteai.noteai.data;

import java.util.ArrayList;
import java.util.List;

public class SearchQuery {
    // 首页搜索框输入的关键字；简单搜索要匹配标题、标签名、分类名，全文搜索要匹配标题和正文。
    public String keyword;

    // 首页分类筛选使用；null 表示不限分类。
    public Long categoryId;

    // 首页标签筛选使用；支持多标签组合过滤。
    public final List<Long> tagIds = new ArrayList<>();

    // true 时 SQLite 实现优先使用 FTS5；false 时使用 LIKE/JOIN 做简单搜索。
    public boolean useFullTextSearch = true;

    // true 表示只搜索标题；false 表示搜索标题和正文。
    public boolean titleOnly = false;

    // 搜索分页参数，避免一次加载过多笔记。
    public int limit = 50;
    public int offset = 0;

    public SearchQuery() {}

    public static SearchQuery keyword(String keyword) {
        SearchQuery query = new SearchQuery();
        query.keyword = keyword;
        return query;
    }
}
