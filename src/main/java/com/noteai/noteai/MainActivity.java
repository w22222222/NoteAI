package com.noteai.noteai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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

import com.noteai.noteai.data.Note;
import com.noteai.noteai.data.NoteRepository;

import java.text.SimpleDateFormat;
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
    private LinearLayout batchBar;
    private TextView fab;
    private boolean batchMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repo = new NoteRepository();

        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.setFitsSystemWindows(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        rootFrame.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(8));
        header.setGravity(Gravity.CENTER_VERTICAL);

        headerTitle = new TextView(this);
        headerTitle.setText("全部笔记");
        headerTitle.setTextColor(0xFF333333);
        headerTitle.setTextSize(18);
        headerTitle.setTypeface(Typeface.DEFAULT_BOLD);

        View spacer = new View(this);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 1, 1);

        countView = new TextView(this);
        countView.setTextColor(0xFF999999);
        countView.setTextSize(13);

        header.addView(headerTitle);
        header.addView(spacer, spacerLp);
        header.addView(countView);
        root.addView(header);

        searchEdit = new EditText(this);
        searchEdit.setHint("搜索标题、标签、分类");
        searchEdit.setSingleLine(true);
        searchEdit.setTextSize(14);
        searchEdit.setTextColor(0xFF333333);
        searchEdit.setHintTextColor(0xFFBBBBBB);
        searchEdit.setPadding(dp(16), dp(6), dp(16), dp(6));
        searchEdit.setBackgroundColor(0xFFF5F5F5);
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                refreshList();
            }
        });
        root.addView(searchEdit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterBar = new LinearLayout(this);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView allBtn = makeFilterBtn("全部");
        TextView categoryBtn = makeFilterBtn("分类");
        TextView tagBtn = makeFilterBtn("标签");
        TextView batchBtn = makeFilterBtn("批量删除");

        allBtn.setOnClickListener(v -> showPlaceholder("当前显示全部笔记"));
        categoryBtn.setOnClickListener(v -> showPlaceholder("分类筛选界面待实现：对接 NoteDataSource.getAllCategories / getNotesByCategory"));
        tagBtn.setOnClickListener(v -> showPlaceholder("标签筛选界面待实现：对接 NoteDataSource.getAllTags / getNotesByTag"));
        batchBtn.setOnClickListener(v -> enterBatchMode());

        filterBar.addView(allBtn);
        filterBar.addView(categoryBtn);
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

        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setPadding(0, dp(4), 0, dp(80));
        recyclerView.setClipToPadding(false);

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
        recyclerView.setAdapter(adapter);

        fab = new TextView(this);
        fab.setText("+");
        fab.setTextColor(Color.WHITE);
        fab.setTextSize(30);
        fab.setGravity(Gravity.CENTER);
        fab.setBackgroundColor(0xFF1A73E8);
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(dp(56), dp(56));
        fabLp.gravity = Gravity.BOTTOM | Gravity.END;
        fabLp.setMargins(0, 0, dp(20), dp(24));
        rootFrame.addView(fab, fabLp);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NoteEditActivity.class);
            intent.putExtra("noteId", -1L);
            startActivity(intent);
        });

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

    private void refreshList() {
        if (adapter == null) return;
        String keyword = searchEdit == null ? "" : searchEdit.getText().toString();
        List<Note> notes = keyword.trim().isEmpty() ? repo.getAll() : repo.searchNotes(keyword);
        adapter.setNotes(notes);
        emptyView.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        updateCount(notes.size());
        updateSelectedCount();
    }

    private void enterBatchMode() {
        batchMode = true;
        headerTitle.setText("批量删除");
        batchBar.setVisibility(View.VISIBLE);
        fab.setVisibility(View.GONE);
        adapter.setSelectionMode(true);
        updateSelectedCount();
    }

    private void exitBatchMode() {
        batchMode = false;
        headerTitle.setText("全部笔记");
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
        repo.deleteMany(new java.util.ArrayList<>(adapter.getSelectedIds()));
        showPlaceholder("已删除选中笔记");
        exitBatchMode();
        refreshList();
    }

    private void updateSelectedCount() {
        if (selectedCountView != null) {
            selectedCountView.setText("已选择 " + adapter.getSelectedCount() + " 篇");
        }
    }

    private void updateCount(int count) {
        if (countView != null) countView.setText(count + " 篇");
    }

    private TextView makeFilterBtn(String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(0xFF1A73E8);
        btn.setTextSize(13);
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        btn.setLayoutParams(lp);
        return btn;
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
        private SelectionChangedListener selectionChangedListener;
        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        interface OnItemClickListener {
            void onClick(Note note);
        }

        interface SelectionChangedListener {
            void onChanged();
        }

        void setItemClickListener(OnItemClickListener l) {
            this.itemClickListener = l;
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
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setPadding(dp(parent), 14, dp(parent), 14);
            item.setBackgroundColor(0xFFFFFFFF);
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
            titleView.setTextColor(0xFF222222);
            titleView.setMaxLines(1);
            titleView.setEllipsize(TextUtils.TruncateAt.END);

            TextView previewView = new TextView(parent.getContext());
            previewView.setTextSize(13);
            previewView.setTextColor(0xFF999999);
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
            holder.checkView.setText(selected ? "☑" : "☐");
            holder.itemView.setBackgroundColor(selected ? 0xFFE8F0FE : 0xFFFFFFFF);
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
