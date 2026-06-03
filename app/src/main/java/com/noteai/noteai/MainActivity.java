package com.noteai.noteai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.noteai.noteai.data.Category;
import com.noteai.noteai.data.Note;
import com.noteai.noteai.data.NoteRepository;
import com.noteai.noteai.data.SearchQuery;
import com.noteai.noteai.data.Tag;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class MainActivity extends Activity {

    private NoteRepository repo;
    private NoteAdapter adapter;
    private TextView emptyView;
    private TextView countView;
    private TextView headerTitle;
    private TextView selectedCountView;
    private EditText searchEdit;
    private View searchBar;
    private View advancedSearchPanel;
    private TextView advancedBtn;
    private TextView clearAdvancedBtn;
    private TextView titleOnlyBtn;
    private TextView fullTextBtn;
    private LinearLayout categoryFilterContainer;
    private LinearLayout tagSelectionDisplay;
    private TextView noSelectedTagsText;
    private HorizontalScrollView selectedTagsScroll;
    private LinearLayout selectedTagsContainer;
    private PopupWindow tagPopup;
    private View drawerOverlay;
    private LinearLayout drawerPanel;
    private boolean searchVisible = false;
    private boolean advancedSearchVisible = false;
    private boolean titleOnlySearch = false;
    private Long advancedCategoryId;
    private final Set<Long> selectedAdvancedTagIds = new HashSet<>();
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

        // initialize backend storage
        repo = new NoteRepository(this);

        setContentView(R.layout.activity_main);

        TextView menuBtn = findViewById(R.id.btnMenu);
        headerTitle = findViewById(R.id.headerTitle);
        countView = findViewById(R.id.countView);
        TextView searchBtn = findViewById(R.id.btnSearch);
        searchBar = findViewById(R.id.searchBar);
        searchEdit = findViewById(R.id.etSearch);
        advancedSearchPanel = findViewById(R.id.advancedSearchPanel);
        advancedBtn = findViewById(R.id.tvAdvanced);
        clearAdvancedBtn = findViewById(R.id.btnClearAdvancedSearch);
        titleOnlyBtn = findViewById(R.id.btnTitleOnlySearch);
        fullTextBtn = findViewById(R.id.btnFullTextSearch);
        categoryFilterContainer = findViewById(R.id.categoryFilterContainer);
        tagSelectionDisplay = findViewById(R.id.tagSelectionDisplay);
        noSelectedTagsText = findViewById(R.id.noSelectedTagsText);
        selectedTagsScroll = findViewById(R.id.selectedTagsScroll);
        selectedTagsContainer = findViewById(R.id.selectedTagsContainer);
        batchBar = findViewById(R.id.batchBar);
        selectedCountView = findViewById(R.id.selectedCountView);
        TextView selectAllBtn = findViewById(R.id.btnSelectAll);
        TextView deleteBtn = findViewById(R.id.btnDelete);
        TextView cancelBtn = findViewById(R.id.btnCancel);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);
        fab = findViewById(R.id.fab);

        menuBtn.setOnClickListener(v -> showDrawer());
        searchBtn.setOnClickListener(v -> toggleSearchBar());
        selectAllBtn.setOnClickListener(v -> selectAllVisibleNotes());
        deleteBtn.setOnClickListener(v -> confirmDeleteSelected());
        cancelBtn.setOnClickListener(v -> exitBatchMode());
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NoteEditActivity.class);
            intent.putExtra("noteId", -1L);
            startActivity(intent);
        });

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshList();
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NoteAdapter();
        adapter.setSelectionChangedListener(this::updateSelectedCount);
        // long click is multiple choice to delete
        // original: this::showNoteActionDialog
        adapter.setItemLongClickListener(v -> enterBatchMode());
        recyclerView.setAdapter(adapter);

        setupDrawer();
        setupAdvancedSearch();

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
        rebuildAdvancedOptions();
        refreshList();
    }

    private void toggleSearchBar() {
        searchVisible = !searchVisible;
        searchBar.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        if (searchVisible) {
            searchEdit.requestFocus();
            rebuildAdvancedOptions();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            dismissTagPopup();
            resetAdvancedSearch();
            advancedSearchVisible = false;
            advancedSearchPanel.setVisibility(View.GONE);
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

    private void setupAdvancedSearch() {
        if (advancedBtn != null) {
            advancedBtn.setOnClickListener(v -> toggleAdvancedSearchPanel());
        }
        if (clearAdvancedBtn != null) {
            clearAdvancedBtn.setOnClickListener(v -> {
                resetAdvancedSearch();
                refreshList();
            });
        }
        if (titleOnlyBtn != null) {
            titleOnlyBtn.setOnClickListener(v -> {
                titleOnlySearch = true;
                updateSearchScopeButtons();
                updateClearAdvancedVisibility();
                refreshList();
            });
        }
        if (fullTextBtn != null) {
            fullTextBtn.setOnClickListener(v -> {
                titleOnlySearch = false;
                updateSearchScopeButtons();
                updateClearAdvancedVisibility();
                refreshList();
            });
        }
        if (tagSelectionDisplay != null) {
            tagSelectionDisplay.setOnClickListener(v -> toggleTagPopup());
        }
        if (noSelectedTagsText != null) {
            noSelectedTagsText.setOnClickListener(v -> toggleTagPopup());
        }
        if (selectedTagsScroll != null) {
            selectedTagsScroll.setOnClickListener(v -> toggleTagPopup());
        }
        if (selectedTagsContainer != null) {
            selectedTagsContainer.setOnClickListener(v -> toggleTagPopup());
        }
        rebuildAdvancedOptions();
        updateSelectedTagsDisplay();
        updateSearchScopeButtons();
        updateClearAdvancedVisibility();
    }

    private void toggleAdvancedSearchPanel() {
        advancedSearchVisible = !advancedSearchVisible;
        advancedSearchPanel.setVisibility(advancedSearchVisible ? View.VISIBLE : View.GONE);
        if (!advancedSearchVisible) {
            dismissTagPopup();
        } else {
            rebuildAdvancedOptions();
        }
    }

    private void resetAdvancedSearch() {
        selectedAdvancedTagIds.clear();
        advancedCategoryId = null;
        titleOnlySearch = false;
        rebuildAdvancedOptions();
        updateSelectedTagsDisplay();
        updateSearchScopeButtons();
        updateClearAdvancedVisibility();
    }

    private void rebuildAdvancedOptions() {
        rebuildCategoryFilterOptions();
        updateSelectedTagsDisplay();
        updateClearAdvancedVisibility();
    }

    private void rebuildCategoryFilterOptions() {
        if (categoryFilterContainer == null) return;
        categoryFilterContainer.removeAllViews();
        categoryFilterContainer.addView(createCategoryChip(getString(R.string.all_categories), null));
        categoryFilterContainer.addView(createCategoryChip(getString(R.string.uncategorized), -1L));

        List<Category> categories = repo.getAllCategories();
        for (Category category : categories) {
            categoryFilterContainer.addView(createCategoryChip(category.name, category.id));
        }
    }

    private TextView createCategoryChip(String label, Long categoryId) {
        TextView chip = createFilterChip(label, categoryEquals(advancedCategoryId, categoryId), categoryFilterContainer);
        chip.setOnClickListener(v -> {
            advancedCategoryId = categoryId;
            rebuildCategoryFilterOptions();
            updateClearAdvancedVisibility();
            refreshList();
        });
        return chip;
    }

    private void toggleTagPopup() {
        if (tagPopup != null && tagPopup.isShowing()) {
            dismissTagPopup();
            return;
        }
        showTagPopup();
    }

    private void showTagPopup() {
        if (tagSelectionDisplay == null) return;

        ScrollView scrollView = (ScrollView) LayoutInflater.from(this)
                .inflate(R.layout.layout_tag_dropdown, null, false);
        LinearLayout list = scrollView.findViewById(R.id.tagDropdownContainer);
        TextView emptyHint = scrollView.findViewById(R.id.emptyTagHint);

        List<Tag> tags = repo.getAllTags();
        if (tags.isEmpty()) {
            emptyHint.setVisibility(View.VISIBLE);
        } else {
            emptyHint.setVisibility(View.GONE);
            for (Tag tag : tags) {
                list.addView(createTagDropdownRow(tag, list));
            }
        }

        tagPopup = new PopupWindow(
                scrollView,
                Math.max(tagSelectionDisplay.getWidth(), dp(180)),
                dp(180),
                true);
        tagPopup.setOutsideTouchable(true);
        tagPopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        tagPopup.setElevation(dp(8));
        tagPopup.showAsDropDown(tagSelectionDisplay, 0, dp(6));
    }

    private TextView createTagDropdownRow(Tag tag, ViewGroup parent) {
        boolean selected = selectedAdvancedTagIds.contains(tag.id);
        TextView row = createFilterChip(tag.name, selected, parent);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight(dp(36));
        row.setOnClickListener(v -> {
            if (selectedAdvancedTagIds.contains(tag.id)) {
                selectedAdvancedTagIds.remove(tag.id);
            } else {
                selectedAdvancedTagIds.add(tag.id);
            }
            updateSelectedTagsDisplay();
            updateClearAdvancedVisibility();
            refreshList();
            styleFilterChip(row, selectedAdvancedTagIds.contains(tag.id));
        });
        return row;
    }

    private void dismissTagPopup() {
        if (tagPopup != null) {
            tagPopup.dismiss();
            tagPopup = null;
        }
    }

    private void updateSelectedTagsDisplay() {
        if (selectedTagsContainer == null || selectedTagsScroll == null || noSelectedTagsText == null) return;
        selectedTagsContainer.removeAllViews();
        if (selectedAdvancedTagIds.isEmpty()) {
            noSelectedTagsText.setVisibility(View.VISIBLE);
            selectedTagsScroll.setVisibility(View.GONE);
            return;
        }
        noSelectedTagsText.setVisibility(View.GONE);
        selectedTagsScroll.setVisibility(View.VISIBLE);
        for (Tag tag : repo.getAllTags()) {
            if (selectedAdvancedTagIds.contains(tag.id)) {
                TextView chip = createFilterChip(tag.name, true, selectedTagsContainer);
                chip.setOnClickListener(v -> toggleTagPopup());
                ViewGroup.LayoutParams params = chip.getLayoutParams();
                if (params instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) params).bottomMargin = 0;
                    chip.setLayoutParams(params);
                }
                selectedTagsContainer.addView(chip);
            }
        }
    }

    private void updateSearchScopeButtons() {
        if (titleOnlyBtn != null) {
            styleFilterChip(titleOnlyBtn, titleOnlySearch);
        }
        if (fullTextBtn != null) {
            styleFilterChip(fullTextBtn, !titleOnlySearch);
        }
    }

    private void updateClearAdvancedVisibility() {
        if (clearAdvancedBtn == null) return;
        boolean hasAdvancedFilter = titleOnlySearch || advancedCategoryId != null || !selectedAdvancedTagIds.isEmpty();
        clearAdvancedBtn.setEnabled(hasAdvancedFilter);
        clearAdvancedBtn.setVisibility(hasAdvancedFilter ? View.VISIBLE : View.INVISIBLE);
    }

    private TextView createFilterChip(String label, boolean selected, ViewGroup parent) {
        TextView chip = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_filter_chip, parent, false);
        chip.setText(label);
        styleFilterChip(chip, selected);
        return chip;
    }

    private void styleFilterChip(TextView chip, boolean selected) {
        chip.setTextColor(selected ? 0xFF2563EB : 0xFF202124);
        chip.setBackground(roundRect(selected ? 0xFFEAF1FF : 0xFFF3F5F8, dp(14)));
    }

    private boolean categoryEquals(Long left, Long right) {
        return Objects.equals(left, right);
    }

    private void setupDrawer() {
        drawerOverlay = findViewById(R.id.drawerOverlay);
        drawerPanel = findViewById(R.id.drawerPanel);
        drawerOverlay.setOnClickListener(v -> hideDrawer());
        drawerOverlay.setAlpha(0f); // animation for smooth movement

        FrameLayout.LayoutParams drawerLp = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.78f),
                FrameLayout.LayoutParams.MATCH_PARENT);
        drawerLp.gravity = Gravity.START;
        drawerPanel.setLayoutParams(drawerLp);

        // initial out of screen for the sake of animation
        drawerPanel.post(() -> drawerPanel.setTranslationX(-drawerPanel.getWidth()));
    }

    // Sets the animation time for sidebar menu
    private int drawerPanelAnimationDuration = 125;

    private void showDrawer() {
        buildDrawerContent();

        // re-init animation start
        drawerOverlay.setVisibility(View.VISIBLE);
        drawerOverlay.setAlpha(0f);

        drawerPanel.setVisibility(View.VISIBLE);
        drawerPanel.setTranslationX(-drawerPanel.getWidth());
        drawerPanel.bringToFront();

        // animate
        drawerOverlay.animate().alpha(1f).setDuration(drawerPanelAnimationDuration).start();
        drawerPanel.animate().translationX(0f).setDuration(drawerPanelAnimationDuration).start();
    }

    private void hideDrawer() {
        drawerOverlay.animate().alpha(0f).setDuration(drawerPanelAnimationDuration).start();
        drawerPanel.animate()
                .translationX(-drawerPanel.getWidth())
                .setDuration(drawerPanelAnimationDuration)
                .withEndAction(() -> {
                    drawerOverlay.setVisibility(View.GONE);
                    drawerPanel.setVisibility(View.GONE);
                })
                .start();
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
        if (hasAdvancedSearchState(keyword)) {
            SearchQuery query = new SearchQuery();
            query.keyword = keyword;
            query.categoryId = advancedCategoryId != null ? advancedCategoryId : filterCategoryId;
            query.tagIds.addAll(selectedAdvancedTagIds);
            if (query.tagIds.isEmpty() && filterTagId != null) {
                query.tagIds.add(filterTagId);
            }
            query.titleOnly = titleOnlySearch;
            query.useFullTextSearch = !titleOnlySearch;
            notes = new ArrayList<>(repo.searchNotes(query));
        } else if (filterCategoryId != null) {
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

        adapter.setNotes(notes);
        emptyView.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
        updateCount(notes.size());
        updateSelectedCount();
    }

    private boolean hasAdvancedSearchState(String keyword) {
        return !keyword.isEmpty()
                || advancedCategoryId != null
                || !selectedAdvancedTagIds.isEmpty()
                || titleOnlySearch;
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

    private GradientDrawable roundRect(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
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
            View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
            return new Holder(item);
        }

        @Override
        public void onBindViewHolder(Holder holder, int pos) {
            Note note = notes.get(pos);
            boolean selected = selectedIds.contains(note.id);

            holder.checkView.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            holder.checkView.setText(selected ? "✓" : "○");
            holder.itemView.setBackgroundResource(selected ? R.drawable.bg_card_selected : R.drawable.bg_card);

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

        static class Holder extends RecyclerView.ViewHolder {
            TextView checkView, titleView, previewView, timeView;

            Holder(View v) {
                super(v);
                checkView = v.findViewById(R.id.checkView);
                titleView = v.findViewById(R.id.titleView);
                previewView = v.findViewById(R.id.previewView);
                timeView = v.findViewById(R.id.timeView);
            }
        }
    }
}
