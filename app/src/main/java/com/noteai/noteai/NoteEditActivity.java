package com.noteai.noteai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.noteai.noteai.ai.AiService;
import com.noteai.noteai.ai.RealAiService;
import com.noteai.noteai.data.Category;
import com.noteai.noteai.data.Note;
import com.noteai.noteai.data.NoteRepository;
import com.noteai.noteai.data.Tag;
import com.noteai.noteai.image.ImageInsertManager;
import com.noteai.noteai.image.InsertedImage;
import com.noteai.noteai.image.LocalImageInsertManager;
import com.noteai.noteai.widget.MarkdownRenderView;
import com.noteai.noteai.widget.RichTextEditor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NoteEditActivity extends Activity {

    private static final int MODE_EDIT = 0;
    private static final int MODE_PREVIEW = 1;
    private static final int REQUEST_PICK_IMAGE = 1001;

    private NoteRepository repo;
    private AiService aiService;
    private ImageInsertManager imageInsertManager;
    private Note note;
    private long noteId;
    private boolean isNew;

    private int mode = MODE_EDIT;
    private boolean dirty = false;

    private TextView topBarTitle;
    private TextView modeToggleBtn;
    private EditText titleEdit;
    private RichTextEditor contentEdit;
    private ScrollView editScroll;
    private MarkdownRenderView previewView;
    private TextView wordCountView;
    private LinearLayout aiPanel;
    private LinearLayout aiPolishActions;
    private LinearLayout aiSkeletonContainer;
    private TextView aiPanelTitle;
    private TextView aiPanelStatus;
    private TextView aiPanelContent;
    private TextView aiPanelClose;
    private TextView btnAiUndo;
    private TextView btnAiRegenerate;
    private TextView btnAiConfirm;
    private TextView btnAiSummaryAction;
    private TextView btnAiPolishAction;
    private View[] aiSkeletonRows;


    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable saveRunnable;
    private Runnable previewRunnable;
    private Runnable aiThinkingRunnable;
    private String lastPreviewContent = "";
    private String pendingPolishSource = "";
    private String pendingPolishResult = "";
    private int aiRequestVersion = 0;
    private int aiThinkingFrame = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        super.onCreate(savedInstanceState);

        repo = new NoteRepository(this);
        aiService = new RealAiService(this);
        // TODO 图片插入同学：真实插图流程实现 LocalImageInsertManager 后，编辑页只调用 imageInsertManager，不直接处理文件复制。
        imageInsertManager = new LocalImageInsertManager(this);
        noteId = getIntent().getLongExtra("noteId", -1);
        isNew = noteId <= 0;

        if (isNew) {
            note = repo.create("", "");
            noteId = note.id;
        } else {
            note = repo.getById(noteId);
            if (note == null) {
                note = repo.create("", "");
                noteId = note.id;
            }
        }

        setContentView(R.layout.activity_note_edit);

        TextView backBtn = findViewById(R.id.btnBack);
        topBarTitle = findViewById(R.id.topBarTitle);
        titleEdit = findViewById(R.id.titleEdit);
        TextView categoryBtn = findViewById(R.id.btnCategory);
        TextView tagBtn = findViewById(R.id.btnTag);
        TextView imageBtn = findViewById(R.id.btnImage);
        modeToggleBtn = findViewById(R.id.modeToggleBtn);
        TextView saveBtn = findViewById(R.id.btnSave);
        editScroll = findViewById(R.id.editScroll);
        contentEdit = findViewById(R.id.contentEdit);
        previewView = findViewById(R.id.previewView);
        wordCountView = findViewById(R.id.wordCountView);
        aiPanel = findViewById(R.id.aiPanel);
        aiPolishActions = findViewById(R.id.aiPolishActions);
        aiSkeletonContainer = findViewById(R.id.aiSkeletonContainer);
        aiPanelTitle = findViewById(R.id.aiPanelTitle);
        aiPanelStatus = findViewById(R.id.aiPanelStatus);
        aiPanelContent = findViewById(R.id.aiPanelContent);
        aiPanelClose = findViewById(R.id.aiPanelClose);
        btnAiUndo = findViewById(R.id.btnAiUndo);
        btnAiRegenerate = findViewById(R.id.btnAiRegenerate);
        btnAiConfirm = findViewById(R.id.btnAiConfirm);
        btnAiSummaryAction = findViewById(R.id.btnAiSummaryAction);
        btnAiPolishAction = findViewById(R.id.btnAiPolishAction);
        aiSkeletonRows = new View[]{
                findViewById(R.id.aiSkeletonRow1),
                findViewById(R.id.aiSkeletonRow2),
                findViewById(R.id.aiSkeletonRow3),
                findViewById(R.id.aiSkeletonRow4)
        };
        aiPanelContent.setMovementMethod(new ScrollingMovementMethod());

        backBtn.setOnClickListener(v -> {
            saveIfDirty();
            finish();
        });

        topBarTitle.setText(isNew ? R.string.new_note : R.string.edit_note);
        titleEdit.setText(note.title);
        titleEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                markDirty();
            }
        });

        categoryBtn.setOnClickListener(v -> showCategoryPicker());
        tagBtn.setOnClickListener(v -> showTagPicker());
        imageBtn.setOnClickListener(v -> startInsertImageFlow());
        modeToggleBtn.setOnClickListener(v -> toggleMode());
        saveBtn.setOnClickListener(v -> {
            saveIfDirty();
            showPlaceholder("已保存");
        });
        aiPanelClose.setOnClickListener(v -> hideAiPanel());
        btnAiUndo.setOnClickListener(v -> discardPolishResult());
        btnAiRegenerate.setOnClickListener(v -> regeneratePolish());
        btnAiConfirm.setOnClickListener(v -> applyPolishResult());
        btnAiSummaryAction.setOnClickListener(v -> requestSummary());
        btnAiPolishAction.setOnClickListener(v -> {
            pendingPolishSource = contentEdit.getPlainText();
            requestPolish(pendingPolishSource);
        });

        contentEdit.setText(note.content);
        contentEdit.refreshImages();
        contentEdit.setHorizontallyScrolling(false);
        contentEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                markDirty();
                schedulePreview();
                updateWordCount();
            }
        });

        updateWordCount();

        if (!isNew) {
            schedulePreview();
        }
    }

    private void requestSummary() {
        int requestVersion = ++aiRequestVersion;
        showAiThinking("AI 总结", "正在生成摘要");
        aiService.summarize(titleEdit.getText().toString(), contentEdit.getPlainText(), new AiService.Callback() {
            @Override
            public void onSuccess(String result) {
                if (requestVersion != aiRequestVersion) return;
                showAiResult("AI 总结", "已生成摘要", result, false);
            }
            @Override
            public void onError(String message) {
                if (requestVersion != aiRequestVersion) return;
                showAiResult("AI 总结失败", message, "", false);
            }
        });
    }

    private void requestPolish(String source) {
        int requestVersion = ++aiRequestVersion;
        pendingPolishResult = "";
        showAiThinking("AI 润色", "正在生成润色建议");
        aiService.polish(titleEdit.getText().toString(), source, new AiService.Callback() {
                    @Override
                    public void onSuccess(String result) {
                        if (requestVersion != aiRequestVersion) return;
                        pendingPolishResult = result;
                        showAiResult("AI 润色建议", "确认后才会替换正文", result, true);
                    }
                    @Override
                    public void onError(String message) {
                        if (requestVersion != aiRequestVersion) return;
                        showAiResult("AI 润色失败", message, "", false);
                    }
                });
    }

    private void regeneratePolish() {
        if (pendingPolishSource == null || pendingPolishSource.trim().isEmpty()) {
            pendingPolishSource = contentEdit.getPlainText();
        }
        requestPolish(pendingPolishSource);
    }

    private void applyPolishResult() {
        if (pendingPolishResult == null || pendingPolishResult.trim().isEmpty()) {
            showPlaceholder("没有可应用的润色内容");
            return;
        }
        contentEdit.setText(pendingPolishResult);
        contentEdit.refreshImages();
        markDirty();
        updateWordCount();
        schedulePreview();
        hideAiPanel();
        showPlaceholder("AI 润色已应用");
    }

    private void discardPolishResult() {
        pendingPolishResult = "";
        hideAiPanel();
        showPlaceholder("已保留原文");
    }

    private void showAiThinking(String title, String status) {
        stopAiThinking();
        setAiActionsEnabled(false);
        aiPanel.setVisibility(View.VISIBLE);
        aiPolishActions.setVisibility(View.GONE);
        aiSkeletonContainer.setVisibility(View.VISIBLE);
        aiPanelContent.setVisibility(View.GONE);
        aiPanelTitle.setText(title);
        aiPanelStatus.setText(status);
        aiPanelContent.setText("");
        for (View row : aiSkeletonRows) {
            row.setPivotX(0f);
            row.setAlpha(0.45f);
            row.setScaleX(0.82f);
        }
        aiThinkingFrame = 0;
        aiThinkingRunnable = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < aiSkeletonRows.length; i++) {
                    int wave = Math.floorMod(aiThinkingFrame - i, aiSkeletonRows.length);
                    float alpha = wave == 0 ? 0.95f : (wave == 1 ? 0.72f : 0.45f);
                    float scale = wave == 0 ? 1f : (wave == 1 ? 0.92f : 0.82f);
                    aiSkeletonRows[i].animate()
                            .alpha(alpha)
                            .scaleX(scale)
                            .setDuration(360)
                            .start();
                }
                aiThinkingFrame++;
                handler.postDelayed(this, 420);
            }
        };
        aiThinkingRunnable.run();
    }

    private void showAiResult(String title, String status, String content, boolean showPolishActions) {
        stopAiThinking();
        setAiActionsEnabled(true);
        aiPanel.setVisibility(View.VISIBLE);
        aiSkeletonContainer.setVisibility(View.GONE);
        aiPanelContent.setVisibility(View.VISIBLE);
        aiPanelTitle.setText(title);
        aiPanelStatus.setText(status);
        aiPanelContent.setText((content == null || content.trim().isEmpty()) ? status : content.trim());
        aiPolishActions.setVisibility(showPolishActions ? View.VISIBLE : View.GONE);
    }

    private void hideAiPanel() {
        aiRequestVersion++;
        stopAiThinking();
        setAiActionsEnabled(true);
        aiPanel.setVisibility(View.GONE);
        aiPolishActions.setVisibility(View.GONE);
        aiSkeletonContainer.setVisibility(View.GONE);
        aiPanelContent.setVisibility(View.VISIBLE);
    }

    private void stopAiThinking() {
        if (aiThinkingRunnable != null) {
            handler.removeCallbacks(aiThinkingRunnable);
            aiThinkingRunnable = null;
        }
    }

    private void setAiActionsEnabled(boolean enabled) {
        btnAiSummaryAction.setEnabled(enabled);
        btnAiPolishAction.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.55f;
        btnAiSummaryAction.setAlpha(alpha);
        btnAiPolishAction.setAlpha(alpha);
    }

    private void toggleMode() {
        if (mode == MODE_EDIT) {
            mode = MODE_PREVIEW;
            String content = contentEdit.getText().toString();
            lastPreviewContent = content;
            previewView.submitMarkdown(content);
            editScroll.setVisibility(View.GONE);
            previewView.setVisibility(View.VISIBLE);
            modeToggleBtn.setText(R.string.edit);
        } else {
            mode = MODE_EDIT;
            editScroll.setVisibility(View.VISIBLE);
            previewView.setVisibility(View.GONE);
            modeToggleBtn.setText(R.string.preview);
        }
    }

    private void markDirty() {
        dirty = true;
        scheduleSave();
    }

    private void scheduleSave() {
        if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
        saveRunnable = () -> {
            saveIfDirty();
        };
        handler.postDelayed(saveRunnable, 1500);
    }

    private void saveIfDirty() {
        if (!dirty) return;
        String title = titleEdit.getText().toString().trim();
        String content = contentEdit.getPlainText();
        repo.update(noteId, title, content);
        dirty = false;
    }

    private void schedulePreview() {
        if (previewRunnable != null) handler.removeCallbacks(previewRunnable);
        previewRunnable = () -> {
            if (mode == MODE_PREVIEW) {
                String content = contentEdit.getPlainText();
                if (!content.equals(lastPreviewContent)) {
                    lastPreviewContent = content;
                    previewView.submitMarkdown(content);
                }
            }
        };
        handler.postDelayed(previewRunnable, 500);
    }

    private void updateWordCount() {
        String text = contentEdit.getText().toString();
        wordCountView.setText("字数 " + text.length());
    }

    private void startInsertImageFlow() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            handlePickedImage(data.getData());
        }
    }

    private void handlePickedImage(Uri sourceUri) {
        imageInsertManager.importImage(sourceUri, new ImageInsertManager.Callback() {
            @Override
            public void onSuccess(InsertedImage image) {
                insertMarkdownAtCursor(image.markdownText);
                showPlaceholder("已插入图片 " + image.width + "×" + image.height);
            }
            @Override
            public void onError(String message) {
                showPlaceholder(message);
            }
        });
    }

    private void insertMarkdownAtCursor(String markdownText) {
        int start = Math.max(contentEdit.getSelectionStart(), 0);
        int end = Math.max(contentEdit.getSelectionEnd(), 0);
        int min = Math.min(start, end);
        int max = Math.max(start, end);
        contentEdit.getText().replace(min, max, "\n" + markdownText + "\n");
        markDirty();
        updateWordCount();
    }

    private void showCategoryPicker() {
        saveIfDirty();
        List<Category> categories = repo.getAllCategories();
        if (categories.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("选择分类")
                    .setMessage("还没有分类，是否新建？")
                    .setPositiveButton("新建", (d, w) -> showCreateCategoryDialog(this::showCategoryPicker))
                    .setNeutralButton("无分类", (d, w) -> {
                        repo.setNoteCategory(noteId, null);
                        note.categoryId = null;
                        showPlaceholder("已移出分类");
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        int itemCount = categories.size() + 1;
        String[] names = new String[itemCount];
        names[0] = "无分类";
        for (int i = 0; i < categories.size(); i++) {
            names[i + 1] = categories.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择分类")
                .setItems(names, (dialog, which) -> {
                    if (which == 0) {
                        repo.setNoteCategory(noteId, null);
                        note.categoryId = null;
                        showPlaceholder("已移出分类");
                        return;
                    }
                    Category category = categories.get(which - 1);
                    repo.setNoteCategory(noteId, category.id);
                    note.categoryId = category.id;
                    showPlaceholder("已设为分类：" + category.name);
                })
                .setPositiveButton("编辑分类", (d, w) -> showCategoryManageDialog(this::showCategoryPicker))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTagPicker() {
        saveIfDirty();
        List<Tag> allTags = repo.getAllTags();
        if (allTags.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("标签")
                    .setMessage("还没有标签，是否新建？")
                    .setPositiveButton("新建", (d, w) -> showCreateTagDialog(this::showTagPicker))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        List<Tag> noteTags = repo.getTagsForNote(noteId);
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
                .setTitle("选择标签")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("确定", (dialog, which) -> {
                    for (int i = 0; i < allTags.size(); i++) {
                        Tag tag = allTags.get(i);
                        if (checked[i] && !originallySelected.contains(tag.id)) {
                            repo.addTagToNote(noteId, tag.id);
                        } else if (!checked[i] && originallySelected.contains(tag.id)) {
                            repo.removeTagFromNote(noteId, tag.id);
                        }
                    }
                    showPlaceholder("标签已更新");
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("编辑标签", (d, w) -> showTagManageDialog(this::showTagPicker))
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

    private void showCategoryManageDialog(Runnable onDone) {
        List<Category> categories = repo.getAllCategories();
        if (categories.isEmpty()) {
            showPlaceholder("暂无可管理分类");
            return;
        }
        String[] names = new String[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            names[i] = categories.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle("分类管理")
                .setItems(names, (dialog, which) -> confirmDeleteCategory(categories.get(which), onDone))
                .setPositiveButton("新建分类", (d, w) -> showCreateCategoryDialog(onDone))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void confirmDeleteCategory(Category category, Runnable onDone) {
        if (category == null) return;
        new AlertDialog.Builder(this)
                .setTitle("删除分类")
                .setMessage("删除“" + category.name + "”后不可恢复，是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    repo.deleteCategory(category.id);
                    if (note.categoryId != null && note.categoryId.equals(category.id)) {
                        note.categoryId = null;
                    }
                    showPlaceholder("已删除分类：" + category.name);
                    if (onDone != null) onDone.run();
                })
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

    private void showTagManageDialog(Runnable onDone) {
        List<Tag> tags = repo.getAllTags();
        if (tags.isEmpty()) {
            showPlaceholder("暂无可管理标签");
            return;
        }
        String[] names = new String[tags.size()];
        for (int i = 0; i < tags.size(); i++) {
            names[i] = tags.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle("标签管理")
                .setItems(names, (dialog, which) -> confirmDeleteTag(tags.get(which), onDone))
                .setPositiveButton("新建标签", (d, w) -> showCreateTagDialog(onDone))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void confirmDeleteTag(Tag tag, Runnable onDone) {
        if (tag == null) return;
        new AlertDialog.Builder(this)
                .setTitle("删除标签")
                .setMessage("删除“" + tag.name + "”后不可恢复，是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    repo.deleteTag(tag.id);
                    showPlaceholder("已删除标签：" + tag.name);
                    if (onDone != null) onDone.run();
                })
                .show();
    }

    private void showPlaceholder(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveIfDirty();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAiThinking();
        saveIfDirty();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
