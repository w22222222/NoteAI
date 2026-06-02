package com.noteai.noteai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.noteai.noteai.data.Category;
import com.noteai.noteai.data.Note;
import com.noteai.noteai.data.NoteRepository;
import com.noteai.noteai.data.Tag;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private NoteRepository repo;
    private NoteAdapter adapter;
    private TextView emptyView;
    private TextView countView;
    private TextView headerTitle;
    private TextView selectedCountView;
    private EditText searchEdit;
    private FrameLayout rootFrame;
    private View drawerOverlay;
    private LinearLayout drawerPanel;
    private boolean searchVisible = false;
    private LinearLayout batchBar;
    private TextView fab;
    private boolean batchMode = false;
    private Long filterCategoryId;
    private Long filterTagId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // --- 新增：隐藏系统自带标题栏 ---
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        super.onCreate(savedInstanceState);

        repo = new NoteRepository(this);

        rootFrame = new FrameLayout(this);
        rootFrame.setFitsSystemWindows(true);
        rootFrame.setBackgroundColor(0xFFF3F5F8);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF3F5F8);
        rootFrame.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(10));
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xFFF3F5F8);

        TextView menuBtn = new TextView(this);
        menuBtn.setText("☰");
        menuBtn.setTextColor(0xFF202124);
        menuBtn.setTextSize(24);
        menuBtn.setIncludeFontPadding(false);
        menuBtn.setGravity(Gravity.CENTER);
        menuBtn.setOnClickListener(v -> showDrawer());

        headerTitle = new TextView(this);
        headerTitle.setText("全部笔记");
        headerTitle.setTextColor(0xFF202124);
        headerTitle.setTextSize(22);
        headerTitle.setTypeface(Typeface.DEFAULT_BOLD);

        View spacer = new View(this);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 1, 1);

        countView = new TextView(this);
        countView.setTextColor(0xFF8B949E);
        countView.setTextSize(13);

        TextView searchBtn = new TextView(this);
        searchBtn.setText("⌕");
        searchBtn.setTextColor(0xFF202124);
        searchBtn.setTextSize(26);
        searchBtn.setIncludeFontPadding(false);
        searchBtn.setGravity(Gravity.CENTER);
        searchBtn.setOnClickListener(v -> toggleSearchBar());

        header.addView(menuBtn, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(dp(10), 0, 0, 0);
        header.addView(headerTitle, titleLp);
        header.addView(spacer, spacerLp);
        header.addView(countView);
        LinearLayout.LayoutParams searchBtnLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        searchBtnLp.setMargins(dp(10), 0, 0, 0);
        header.addView(searchBtn, searchBtnLp);
        root.addView(header);

        searchEdit = new EditText(this);
        searchEdit.setHint("搜索标题、标签、分类");
        searchEdit.setSingleLine(true);
        searchEdit.setTextSize(14);
        searchEdit.setTextColor(0xFF202124);
        searchEdit.setHintTextColor(0xFF9AA0A6);
        searchEdit.setPadding(dp(18), dp(10), dp(18), dp(10));
        searchEdit.setBackground(roundRect(0xFFFFFFFF, dp(14)));
        searchEdit.setVisibility(View.GONE);
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                refreshList();
            }
        });
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(dp(16), 0, dp(16), dp(8));
        root.addView(searchEdit, searchLp);

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterBar = new LinearLayout(this);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setPadding(dp(16), dp(4), dp(16), dp(12));

        TextView allBtn = makeFilterBtn("全部");
        TextView tagBtn = makeFilterBtn("标签");
        TextView batchBtn = makeFilterBtn("批量删除");

        allBtn.setOnClickListener(v -> showAllNotes());
        tagBtn.setOnClickListener(v -> showTagFilterPicker());
        batchBtn.setOnClickListener(v -> enterBatchMode());

        filterBar.addView(allBtn);
        filterBar.addView(tagBtn);
        filterBar.addView(batchBtn);
        filterScroll.addView(filterBar);
        root.addView(filterScroll);

        batchBar = new LinearLayout(this);
        batchBar.setOrientation(LinearLayout.HORIZONTAL);
        batchBar.setGravity(Gravity.CENTER_VERTICAL);
        batchBar.setPadding(dp(12), dp(6), dp(12), dp(6));
        batchBar.setVisibility(View.GONE);
        batchBar.setBackgroundColor(0xFFF5F7FF);

        selectedCountView = new TextView(this);
        selectedCountView.setTextColor(0xFF333333);
        selectedCountView.setTextSize(13);

        View batchSpacer = new View(this);
        LinearLayout.LayoutParams batchSpacerLp = new LinearLayout.LayoutParams(0, 1, 1);

        TextView selectAllBtn = makeFilterBtn("全选");
        TextView deleteBtn = makeFilterBtn("删除");
        TextView cancelBtn = makeFilterBtn("取消");

        selectAllBtn.setOnClickListener(v -> selectAllVisibleNotes());
        deleteBtn.setOnClickListener(v -> confirmDeleteSelected());
        cancelBtn.setOnClickListener(v -> exitBatchMode());

        batchBar.addView(selectedCountView);
        batchBar.addView(batchSpacer, batchSpacerLp);
        batchBar.addView(selectAllBtn);
        batchBar.addView(deleteBtn);
        batchBar.addView(cancelBtn);
        root.addView(batchBar);

        FrameLayout contentArea = new FrameLayout(this);
        contentArea.setBackgroundColor(0xFFF3F5F8);

        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setPadding(dp(12), dp(4), dp(12), dp(88));
        recyclerView.setClipToPadding(false);
        recyclerView.setBackgroundColor(0xFFF3F5F8);

        emptyView = new TextView(this);
        emptyView.setText("  还没有笔记\n  点击右下角 + 创建第一篇");
        emptyView.setTextColor(0xFFBBBBBB);
        emptyView.setTextSize(14);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);

        contentArea.addView(recyclerView);
        contentArea.addView(emptyView);
        root.addView(contentArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        adapter = new NoteAdapter();
        adapter.setSelectionChangedListener(this::updateSelectedCount);
        adapter.setItemLongClickListener(this::showNoteActionDialog);
        recyclerView.setAdapter(adapter);

        fab = new TextView(this);
        fab.setText("+");
        fab.setTextColor(Color.WHITE);
        fab.setTextSize(30);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(roundRect(0xFF2563EB, dp(18)));
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(dp(56), dp(56));
        fabLp.gravity = Gravity.BOTTOM | Gravity.END;
        fabLp.setMargins(0, 0, dp(20), dp(24));
        rootFrame.addView(fab, fabLp);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NoteEditActivity.class);
            intent.putExtra("noteId", -1L);
            startActivity(intent);
        });

        setupDrawer();

        setContentView(rootFrame);

        adapter.setItemClickListener(note -> {
            if (batchMode) return;
            Intent intent = new Intent(MainActivity.this, NoteEditActivity.class);
            intent.putExtra("noteId", note.id);
            startActivity(intent);
        });

        refreshList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void toggleSearchBar() {
        searchVisible = !searchVisible;
        searchEdit.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        if (searchVisible) {
            searchEdit.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            searchEdit.clearFocus();
            searchEdit.setText("");
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchEdit.getWindowToken(), 0);
            }
            refreshList();
        }
    }

    private void setupDrawer() {
        drawerOverlay = new View(this);
        drawerOverlay.setBackgroundColor(0x66000000);
        drawerOverlay.setVisibility(View.GONE);
        drawerOverlay.setOnClickListener(v -> hideDrawer());
        rootFrame.addView(drawerOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        drawerPanel = new LinearLayout(this);
        drawerPanel.setOrientation(LinearLayout.VERTICAL);
        drawerPanel.setPadding(dp(20), dp(28), dp(14), dp(20));
        drawerPanel.setBackgroundColor(0xFFFFFFFF);
        drawerPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams drawerLp = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.78f),
                FrameLayout.LayoutParams.MATCH_PARENT);
        drawerLp.gravity = Gravity.START;
        rootFrame.addView(drawerPanel, drawerLp);
    }

    private void showDrawer() {
        buildDrawerContent();
        drawerOverlay.setVisibility(View.VISIBLE);
        drawerPanel.setVisibility(View.VISIBLE);
        drawerPanel.bringToFront();
    }

    private void hideDrawer() {
        drawerOverlay.setVisibility(View.GONE);
        drawerPanel.setVisibility(View.GONE);
    }

    private void buildDrawerContent() {
        drawerPanel.removeAllViews();

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("目录");
        title.setTextColor(0xFF202124);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        View titleSpacer = new View(this);
        titleRow.addView(title);
        titleRow.addView(titleSpacer, new LinearLayout.LayoutParams(0, 1, 1));
        drawerPanel.addView(titleRow);

        addDrawerItem("全部笔记", repo.getAll().size(), filterCategoryId == null && filterTagId == null, () -> {
            showAllNotes();
            hideDrawer();
        });

        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("文件夹");
        sectionTitle.setTextColor(0xFF8B949E);
        sectionTitle.setTextSize(13);
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionLp.setMargins(0, dp(22), 0, dp(6));
        drawerPanel.addView(sectionTitle, sectionLp);

        addDrawerItem("未分类", countUncategorizedNotes(), filterCategoryId != null && filterCategoryId == -1L, () -> {
            filterCategoryId = -1L;
            filterTagId = null;
            headerTitle.setText("未分类");
            refreshList();
            hideDrawer();
        });

        for (Category category : repo.getAllCategories()) {
            addDrawerItem(category.name, repo.getNotesByCategory(category.id).size(), filterCategoryId != null && filterCategoryId.equals(category.id), () -> {
                filterCategoryId = category.id;
                filterTagId = null;
                headerTitle.setText(category.name);
                refreshList();
                hideDrawer();
            });
        }
    }

    private void addDrawerItem(String name, int count, boolean selected, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(roundRect(selected ? 0xFFF3EBD4 : 0xFFFFFFFF, dp(14)));
        row.setOnClickListener(v -> action.run());

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(0xFF202124);
        nameView.setTextSize(16);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);

        TextView countText = new TextView(this);
        countText.setText(String.valueOf(count));
        countText.setTextColor(0xFF9AA0A6);
        countText.setTextSize(14);
        countText.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(nameView);
        row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        row.addView(countText);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(6), 0, dp(2));
        drawerPanel.addView(row, rowLp);
    }

    private int countUncategorizedNotes() {
        int count = 0;
        for (Note note : repo.getAll()) {
            if (note.categoryId == null) {
                count++;
            }
        }
        return count;
    }

    private void refreshList() {
        if (adapter == null) return;

        String keyword = searchEdit == null ? "" : searchEdit.getText().toString().trim();
        List<Note> notes;

        if (!keyword.isEmpty()) {
            notes = new ArrayList<>(repo.searchNotes(keyword));

            if (filterCategoryId != null) {
                if (filterCategoryId == -1L) {
                    notes.removeIf(n -> n.categoryId != null);
                } else {
                    notes.removeIf(n -> n.categoryId == null || !n.categoryId.equals(filterCategoryId));
                }
            } else if (filterTagId != null) {
                notes.removeIf(n -> {
                    for (Tag tag : repo.getTagsForNote(n.id)) {
                        if (filterTagId.equals(tag.id)) {
                            return false;
                        }
                    }
                    return true;
                });
            }
        } else {
            // 如果没有关键词，按原来的分类/标签/全部逻辑显示
            if (filterCategoryId != null) {
                if (filterCategoryId == -1L) {
                    notes = new ArrayList<>();
                    for (Note note : repo.getAll()) {
                        if (note.categoryId == null) {
                            notes.add(note);
                        }
                    }
                } else {
                    notes = new ArrayList<>(repo.getNotesByCategory(filterCategoryId));
                }
            } else if (filterTagId != null) {
                notes = new ArrayList<>(repo.getNotesByTag(filterTagId));
            } else {
                notes = new ArrayList<>(repo.getAll());
            }
        }

        adapter.setNotes(notes);
        emptyView.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
        updateCount(notes.size());
        updateSelectedCount();
    }

    private void showAllNotes() {
        filterCategoryId = null;
        filterTagId = null;
        if (headerTitle != null) {
            headerTitle.setText("全部笔记");
        }
        refreshList();
        showPlaceholder("当前显示全部笔记");
    }

    private void showCategoryFilterPicker() {
        List<Category> categories = repo.getAllCategories();
        if (categories.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("分类筛选")
                    .setMessage("还没有分类，是否新建？")
                    .setPositiveButton("新建", (d, w) -> showCreateCategoryDialog(this::showCategoryFilterPicker))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        String[] names = new String[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            names[i] = categories.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle("按分类筛选")
                .setItems(names, (dialog, which) -> {
                    Category category = categories.get(which);
                    filterCategoryId = category.id;
                    filterTagId = null;
                    if (headerTitle != null) {
                        headerTitle.setText("分类：" + category.name);
                    }
                    refreshList();
                })
                .setNeutralButton("新建分类", (d, w) -> showCreateCategoryDialog(this::showCategoryFilterPicker))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTagFilterPicker() {
        List<Tag> tags = repo.getAllTags();
        if (tags.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("标签筛选")
                    .setMessage("还没有标签，是否新建？")
                    .setPositiveButton("新建", (d, w) -> showCreateTagDialog(this::showTagFilterPicker))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        String[] names = new String[tags.size()];
        for (int i = 0; i < tags.size(); i++) {
            names[i] = tags.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle("按标签筛选")
                .setItems(names, (dialog, which) -> {
                    Tag tag = tags.get(which);
                    filterTagId = tag.id;
                    filterCategoryId = null;
                    if (headerTitle != null) {
                        headerTitle.setText("标签：" + tag.name);
                    }
                    refreshList();
                })
                .setNeutralButton("新建标签", (d, w) -> showCreateTagDialog(this::showTagFilterPicker))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCreateCategoryDialog(Runnable onDone) {
        EditText input = new EditText(this);
        input.setHint("分类名称");
        int pad = dp(16);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("新建分类")
                .setView(input)
                .setPositiveButton("创建", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        showPlaceholder("分类名不能为空");
                        return;
                    }
                    try {
                        repo.createCategory(name);
                        if (onDone != null) {
                            onDone.run();
                        }
                    } catch (Exception e) {
                        showPlaceholder(e.getMessage() != null ? e.getMessage() : "创建失败");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCreateTagDialog(Runnable onDone) {
        EditText input = new EditText(this);
        input.setHint("标签名称");
        int pad = dp(16);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("新建标签")
                .setView(input)
                .setPositiveButton("创建", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        showPlaceholder("标签名不能为空");
                        return;
                    }
                    try {
                        repo.createTag(name, 0xFF1A73E8);
                        if (onDone != null) {
                            onDone.run();
                        }
                    } catch (Exception e) {
                        showPlaceholder(e.getMessage() != null ? e.getMessage() : "创建失败");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showNoteActionDialog(Note note) {
        String[] actions = {"分类管理", "标签管理", "删除笔记"};
        new AlertDialog.Builder(this)
                .setTitle(note.title.isEmpty() ? "未命名笔记" : note.title)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showNoteCategoryPicker(note);
                    } else if (which == 1) {
                        showNoteTagPicker(note);
                    } else {
                        confirmDeleteNote(note);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showNoteCategoryPicker(Note note) {
        List<Category> categories = repo.getAllCategories();
        if (categories.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("分类管理")
                    .setMessage("还没有分类，是否新建？")
                    .setPositiveButton("新建", (d, w) -> showCreateCategoryDialog(() -> showNoteCategoryPicker(note)))
                    .setNeutralButton("无分类", (d, w) -> {
                        repo.setNoteCategory(note.id, null);
                        note.categoryId = null;
                        refreshList();
                        showPlaceholder("已移出分类");
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        String[] names = new String[categories.size() + 1];
        names[0] = "无分类";
        for (int i = 0; i < categories.size(); i++) {
            names[i + 1] = categories.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle("分类管理")
                .setItems(names, (dialog, which) -> {
                    if (which == 0) {
                        repo.setNoteCategory(note.id, null);
                        note.categoryId = null;
                        refreshList();
                        showPlaceholder("已移出分类");
                        return;
                    }
                    Category category = categories.get(which - 1);
                    repo.setNoteCategory(note.id, category.id);
                    note.categoryId = category.id;
                    refreshList();
                    showPlaceholder("已设为分类：" + category.name);
                })
                .setNeutralButton("新建分类", (d, w) -> showCreateCategoryDialog(() -> showNoteCategoryPicker(note)))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showNoteTagPicker(Note note) {
        List<Tag> allTags = repo.getAllTags();
        if (allTags.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("标签管理")
                    .setMessage("还没有标签，是否新建？")
                    .setPositiveButton("新建", (d, w) -> showCreateTagDialog(() -> showNoteTagPicker(note)))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        List<Tag> noteTags = repo.getTagsForNote(note.id);
        Set<Long> originallySelected = new HashSet<>();
        for (Tag tag : noteTags) {
            originallySelected.add(tag.id);
        }
        String[] names = new String[allTags.size()];
        boolean[] checked = new boolean[allTags.size()];
        for (int i = 0; i < allTags.size(); i++) {
            names[i] = allTags.get(i).name;
            checked[i] = originallySelected.contains(allTags.get(i).id);
        }
        new AlertDialog.Builder(this)
                .setTitle("标签管理")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("确定", (dialog, which) -> {
                    for (int i = 0; i < allTags.size(); i++) {
                        Tag tag = allTags.get(i);
                        if (checked[i] && !originallySelected.contains(tag.id)) {
                            repo.addTagToNote(note.id, tag.id);
                        } else if (!checked[i] && originallySelected.contains(tag.id)) {
                            repo.removeTagFromNote(note.id, tag.id);
                        }
                    }
                    refreshList();
                    showPlaceholder("标签已更新");
                })
                .setNeutralButton("新建标签", (d, w) -> showCreateTagDialog(() -> showNoteTagPicker(note)))
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDeleteNote(Note note) {
        new AlertDialog.Builder(this)
                .setTitle("删除笔记")
                .setMessage("确定删除这篇笔记吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    repo.delete(note.id);
                    refreshList();
                    showPlaceholder("已删除笔记");
                })
                .show();
    }

    private void enterBatchMode() {
        if (batchMode) return;
        batchMode = true;
        headerTitle.setText("批量删除");
        batchBar.setVisibility(View.VISIBLE);
        fab.setVisibility(View.GONE);
        adapter.setSelectionMode(true);
        updateSelectedCount();
    }

    private void exitBatchMode() {
        batchMode = false;
        updateHeaderTitleForFilter();
        batchBar.setVisibility(View.GONE);
        fab.setVisibility(View.VISIBLE);
        adapter.clearSelection();
        adapter.setSelectionMode(false);
        updateSelectedCount();
    }

    private void selectAllVisibleNotes() {
        adapter.selectAllVisible();
        updateSelectedCount();
    }

    private void confirmDeleteSelected() {
        int count = adapter.getSelectedCount();
        if (count == 0) {
            showPlaceholder("请先选择要删除的笔记");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除笔记")
                .setMessage("确定删除选中的 " + count + " 篇笔记吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteSelectedNotes())
                .show();
    }

    private void deleteSelectedNotes() {
        java.util.ArrayList<Long> selectedIds = new java.util.ArrayList<>(adapter.getSelectedIds());
        try {
            repo.deleteMany(selectedIds);
            showPlaceholder("已删除选中笔记");
            exitBatchMode();
            refreshList();
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "批量删除失败";
            showPlaceholder(message);
        }
    }

    private void updateSelectedCount() {
        if (selectedCountView != null) {
            selectedCountView.setText("已选择 " + adapter.getSelectedCount() + " 篇");
        }
    }

    private void updateCount(int count) {
        if (countView != null) countView.setText(count + " 篇");
    }

    private void updateHeaderTitleForFilter() {
        if (headerTitle == null) return;
        if (filterCategoryId != null) {
            if (filterCategoryId == -1L) {
                headerTitle.setText("未分类");
                return;
            }
            for (Category category : repo.getAllCategories()) {
                if (category.id == filterCategoryId) {
                    headerTitle.setText("分类：" + category.name);
                    return;
                }
            }
        }
        if (filterTagId != null) {
            for (Tag tag : repo.getAllTags()) {
                if (tag.id == filterTagId) {
                    headerTitle.setText("标签：" + tag.name);
                    return;
                }
            }
        }
        headerTitle.setText("全部笔记");
    }

    private TextView makeFilterBtn(String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(0xFF2563EB);
        btn.setTextSize(13);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dp(14), dp(7), dp(14), dp(7));
        btn.setBackground(roundRect(0xFFEAF1FF, dp(14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private GradientDrawable roundRect(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable roundRect(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = roundRect(color, radius);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private void showPlaceholder(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.Holder> {

        private List<Note> notes = java.util.Collections.emptyList();
        private final Set<Long> selectedIds = new HashSet<>();
        private boolean selectionMode = false;
        private OnItemClickListener itemClickListener;
        private OnItemLongClickListener itemLongClickListener;
        private SelectionChangedListener selectionChangedListener;
        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        interface OnItemClickListener {
            void onClick(Note note);
        }

        interface OnItemLongClickListener {
            void onLongClick(Note note);
        }

        interface SelectionChangedListener {
            void onChanged();
        }

        void setItemClickListener(OnItemClickListener l) {
            this.itemClickListener = l;
        }

        void setItemLongClickListener(OnItemLongClickListener l) {
            this.itemLongClickListener = l;
        }

        void setSelectionChangedListener(SelectionChangedListener l) {
            this.selectionChangedListener = l;
        }

        void setSelectionMode(boolean enabled) {
            selectionMode = enabled;
            notifyDataSetChanged();
        }

        void clearSelection() {
            selectedIds.clear();
            notifyDataSetChanged();
        }

        void selectAllVisible() {
            for (Note note : notes) {
                selectedIds.add(note.id);
            }
            notifyDataSetChanged();
            notifySelectionChanged();
        }

        int getSelectedCount() {
            return selectedIds.size();
        }

        Set<Long> getSelectedIds() {
            return new HashSet<>(selectedIds);
        }

        void setNotes(List<Note> list) {
            this.notes = list;
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout item = new LinearLayout(parent.getContext());
            RecyclerView.LayoutParams itemLp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            itemLp.setMargins(0, 0, 0, dp(parent, 10));
            item.setLayoutParams(itemLp);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setPadding(dp(parent, 16), dp(parent, 14), dp(parent, 16), dp(parent, 14));
            item.setBackground(cardBg(parent.getContext(), 0xFFFFFFFF));
            item.setClickable(true);
            item.setGravity(Gravity.CENTER_VERTICAL);

            TextView checkView = new TextView(parent.getContext());
            checkView.setTextSize(20);
            checkView.setTextColor(0xFF1A73E8);
            checkView.setGravity(Gravity.CENTER);
            checkView.setVisibility(View.GONE);
            LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(dp(parent, 32), ViewGroup.LayoutParams.WRAP_CONTENT);
            item.addView(checkView, checkLp);

            LinearLayout textBox = new LinearLayout(parent.getContext());
            textBox.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textBoxLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);

            TextView titleView = new TextView(parent.getContext());
            titleView.setTextSize(16);
            titleView.setTextColor(0xFF202124);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setMaxLines(1);
            titleView.setEllipsize(TextUtils.TruncateAt.END);

            TextView previewView = new TextView(parent.getContext());
            previewView.setTextSize(13);
            previewView.setTextColor(0xFF6B7280);
            previewView.setMaxLines(1);
            previewView.setEllipsize(TextUtils.TruncateAt.END);
            previewView.setPadding(0, dp(parent, 4), 0, 0);

            TextView timeView = new TextView(parent.getContext());
            timeView.setTextSize(11);
            timeView.setTextColor(0xFFBBBBBB);
            timeView.setPadding(0, dp(parent, 2), 0, 0);

            textBox.addView(titleView);
            textBox.addView(previewView);
            textBox.addView(timeView);
            item.addView(textBox, textBoxLp);

            return new Holder(item, checkView, titleView, previewView, timeView);
        }

        @Override
        public void onBindViewHolder(Holder holder, int pos) {
            Note note = notes.get(pos);
            boolean selected = selectedIds.contains(note.id);
            holder.checkView.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            holder.checkView.setText(selected ? "✓" : "○");
            holder.itemView.setBackground(cardBg(holder.itemView.getContext(), selected ? 0xFFEAF1FF : 0xFFFFFFFF));
            holder.titleView.setText(note.title.isEmpty() ? "未命名笔记" : note.title);
            String preview = note.content.replace("\n", " ").trim();
            if (preview.length() > 60) preview = preview.substring(0, 60) + "...";
            holder.previewView.setText(preview);
            holder.timeView.setText(sdf.format(new Date(note.updatedAt)));
            holder.itemView.setOnClickListener(v -> {
                if (selectionMode) {
                    toggleSelected(note.id);
                } else if (itemClickListener != null) {
                    itemClickListener.onClick(note);
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (selectionMode) {
                    toggleSelected(note.id);
                } else if (itemLongClickListener != null) {
                    itemLongClickListener.onLongClick(note);
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return notes.size();
        }

        private void toggleSelected(long id) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id);
            } else {
                selectedIds.add(id);
            }
            notifyDataSetChanged();
            notifySelectionChanged();
        }

        private void notifySelectionChanged() {
            if (selectionChangedListener != null) selectionChangedListener.onChanged();
        }

        static int dp(ViewGroup parent) {
            return (int) (16 * parent.getContext().getResources().getDisplayMetrics().density + 0.5f);
        }

        static int dp(ViewGroup parent, int d) {
            return (int) (d * parent.getContext().getResources().getDisplayMetrics().density + 0.5f);
        }

        static GradientDrawable cardBg(android.content.Context context, int color) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(12 * context.getResources().getDisplayMetrics().density);
            return drawable;
        }

        static class Holder extends RecyclerView.ViewHolder {
            TextView checkView, titleView, previewView, timeView;
            Holder(View v, TextView c, TextView t, TextView p, TextView tm) {
                super(v);
                checkView = c;
                titleView = t;
                previewView = p;
                timeView = tm;
            }
        }
    }
}
