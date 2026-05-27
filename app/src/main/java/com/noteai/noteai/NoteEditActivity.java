package com.noteai.noteai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.noteai.noteai.ai.AiService;
import com.noteai.noteai.ai.PlaceholderAiService;
import com.noteai.noteai.data.Note;
import com.noteai.noteai.data.NoteRepository;
import com.noteai.noteai.image.ImageInsertManager;
import com.noteai.noteai.image.InsertedImage;
import com.noteai.noteai.image.LocalImageInsertManager;
import com.noteai.noteai.widget.AiFloatingBall;
import com.noteai.noteai.widget.MarkdownRenderView;

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
    private EditText contentEdit;
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
        super.onCreate(savedInstanceState);

        repo = new NoteRepository();
        // TODO AI 同学：当前使用占位实现。真实接入时实现 AiService，并在这里替换 PlaceholderAiService。
        aiService = new PlaceholderAiService(this);
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

        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.setFitsSystemWindows(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        rootFrame.addView(root);

        LinearLayout controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setPadding(dp(4), dp(4), dp(8), dp(4));
        controlBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView backBtn = new TextView(this);
        backBtn.setText(" ← ");
        backBtn.setTextColor(0xFF1A73E8);
        backBtn.setTextSize(22);
        backBtn.setPadding(dp(8), dp(4), dp(4), dp(4));
        backBtn.setOnClickListener(v -> {
            saveIfDirty();
            finish();
        });

        topBarTitle = new TextView(this);
        topBarTitle.setText(isNew ? "新建笔记" : "编辑笔记");
        topBarTitle.setTextColor(0xFF333333);
        topBarTitle.setTextSize(17);
        topBarTitle.setTypeface(Typeface.DEFAULT_BOLD);

        View titleSpacer = new View(this);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 1, 1);

        modeToggleBtn = new TextView(this);
        modeToggleBtn.setText(" 预览 ");
        modeToggleBtn.setTextColor(0xFF1A73E8);
        modeToggleBtn.setTextSize(15);
        modeToggleBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        modeToggleBtn.setOnClickListener(v -> toggleMode());

        controlBar.addView(backBtn);
        controlBar.addView(topBarTitle);
        controlBar.addView(titleSpacer, spacerLp);
        controlBar.addView(modeToggleBtn);
        root.addView(controlBar);

        titleEdit = new EditText(this);
        titleEdit.setHint("标题");
        titleEdit.setText(note.title);
        titleEdit.setTextSize(20);
        titleEdit.setTextColor(0xFF222222);
        titleEdit.setHintTextColor(0xFFCCCCCC);
        titleEdit.setPadding(dp(16), dp(10), dp(16), dp(6));
        titleEdit.setBackgroundColor(0xFFFAFAFA);
        titleEdit.setTypeface(Typeface.DEFAULT_BOLD);
        titleEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                markDirty();
            }
        });
        root.addView(titleEdit);

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(dp(12), dp(6), dp(12), dp(8));

        TextView saveBtn = makeActionBtn("保存");
        TextView deleteBtn = makeActionBtn("删除");
        TextView categoryBtn = makeActionBtn("分类");
        TextView tagBtn = makeActionBtn("标签");
        TextView imageBtn = makeActionBtn("插图");

        saveBtn.setOnClickListener(v -> {
            saveIfDirty();
            showPlaceholder("已保存");
        });
        deleteBtn.setOnClickListener(v -> {
            repo.delete(noteId);
            showPlaceholder("已删除");
            finish();
        });
        categoryBtn.setOnClickListener(v -> showPlaceholder("分类选择界面待实现：对接 NoteDataSource.getAllCategories / setNoteCategory"));
        tagBtn.setOnClickListener(v -> showPlaceholder("标签编辑界面待实现：对接 getTagsForNote / addTagToNote / removeTagFromNote"));
        imageBtn.setOnClickListener(v -> startInsertImageFlow());

        actionBar.addView(saveBtn);
        actionBar.addView(deleteBtn);
        actionBar.addView(categoryBtn);
        actionBar.addView(tagBtn);
        actionBar.addView(imageBtn);
        root.addView(actionBar);

        View thinDiv = new View(this);
        thinDiv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        thinDiv.setBackgroundColor(0xFFE0E0E0);
        root.addView(thinDiv);

        FrameLayout mainArea = new FrameLayout(this);

        editScroll = new ScrollView(this);
        editScroll.setFillViewport(true);

        contentEdit = new EditText(this);
        contentEdit.setHint("开始写 Markdown 笔记...\n\n示例：# 标题\n**加粗** *斜体* `代码`\n- 列表\n\n更多语法请查看预览效果");
        contentEdit.setText(note.content);
        contentEdit.setTextSize(16);
        contentEdit.setTextColor(0xFF333333);
        contentEdit.setHintTextColor(0xFFCCCCCC);
        contentEdit.setPadding(dp(16), dp(12), dp(16), dp(12));
        contentEdit.setBackgroundColor(0x00FFFFFF);
        contentEdit.setGravity(Gravity.TOP);
        contentEdit.setVerticalScrollBarEnabled(true);
        contentEdit.setHorizontallyScrolling(false);
        contentEdit.setInputType(EditorInfo.TYPE_CLASS_TEXT
                | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
                | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        contentEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                markDirty();
                schedulePreview();
                updateWordCount();
            }
        });
        editScroll.addView(contentEdit);

        previewView = new MarkdownRenderView(this);
        previewView.setVisibility(View.GONE);

        mainArea.addView(editScroll);
        mainArea.addView(previewView);
        root.addView(mainArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        View bottomDiv = new View(this);
        bottomDiv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        bottomDiv.setBackgroundColor(0xFFE0E0E0);
        root.addView(bottomDiv);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(12), dp(6), dp(12), dp(12));
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);

        wordCountView = new TextView(this);
        wordCountView.setTextSize(12);
        wordCountView.setTextColor(0xFF999999);
        bottomBar.addView(wordCountView);
        root.addView(bottomBar);

        floatingBall = new AiFloatingBall(this);
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
        rootFrame.addView(floatingBall);

        setContentView(rootFrame);

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
            modeToggleBtn.setText(" 编辑 ");
        } else {
            mode = MODE_EDIT;
            editScroll.setVisibility(View.VISIBLE);
            previewView.setVisibility(View.GONE);
            modeToggleBtn.setText(" 预览 ");
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
        String content = contentEdit.getText().toString();
        repo.update(noteId, title, content);
        dirty = false;
    }

    private void schedulePreview() {
        if (previewRunnable != null) handler.removeCallbacks(previewRunnable);
        previewRunnable = () -> {
            if (mode == MODE_PREVIEW) {
                String content = contentEdit.getText().toString();
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

    private void showPlaceholder(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    private TextView makeActionBtn(String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(0xFF1A73E8);
        btn.setTextSize(13);
        btn.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        btn.setLayoutParams(lp);
        return btn;
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
