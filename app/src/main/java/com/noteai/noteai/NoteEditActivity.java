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
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import com.noteai.noteai.widget.AiFloatingBall;
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
    private AiFloatingBall floatingBall;


    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable saveRunnable;
    private Runnable previewRunnable;
    private String lastPreviewContent = "";

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
        FrameLayout rootFrame = findViewById(R.id.rootFrame);

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
        floatingBall = rootFrame.findViewById(R.id.floatingBall);

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

        floatingBall.setCallback(new AiFloatingBall.Callback() {
            @Override
            public void onPolish() {
                // TODO AI 同学：润色结果返回后，目前直接覆盖正文输入框；如果后续需要二次确认，可在这里弹出确认窗口。
                aiService.polish(titleEdit.getText().toString(), contentEdit.getText().toString(), new AiService.Callback() {
                    @Override
                    public void onSuccess(String result) {
                        contentEdit.setText(result);
                        markDirty();
                        showPlaceholder("AI 润色完成");
                    }
                    @Override
                    public void onError(String message) {
                        showPlaceholder(message);
                    }
                });
            }
            @Override
            public void onSummary() {
                // TODO AI 同学：摘要结果通过 floatingBall.showSummaryResult 展示，不需要直接操作悬浮窗口布局。
                aiService.summarize(titleEdit.getText().toString(), contentEdit.getText().toString(), new AiService.Callback() {
                    @Override
                    public void onSuccess(String result) {
                        floatingBall.showSummaryResult(result);
                    }
                    @Override
                    public void onError(String message) {
                        floatingBall.showSummaryError(message);
                    }
                });
            }
        });

        updateWordCount();

        if (!isNew) {
            schedulePreview();
        }
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
                .setNeutralButton("新建分类", (d, w) -> showCreateCategoryDialog(this::showCategoryPicker))
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
                .setNeutralButton("新建标签", (d, w) -> showCreateTagDialog(this::showTagPicker))
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
        saveIfDirty();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
