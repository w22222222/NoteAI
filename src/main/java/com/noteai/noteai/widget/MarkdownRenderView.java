package com.noteai.noteai.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.noteai.engine.Block;
import com.noteai.engine.BlockAdapter;
import com.noteai.engine.BlockExtractor;
import com.noteai.engine.MarkdownParser;
import com.noteai.engine.MarkdownGenerator;
import com.noteai.engine.NoteMarkdownView;
import com.noteai.engine.SpanInfo;
import com.noteai.engine.StyleConfig;
import com.noteai.noteai.image.ImageLoader;
import com.noteai.noteai.image.LocalImageLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MarkdownRenderView extends FrameLayout {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StyleConfig style = StyleConfig.getDefault();

    private RecyclerView recyclerView;
    private BlockAdapter adapter;
    private TextView emptyView;
    private ImageLoader imageLoader;
    private int renderVersion = 0;
    private List<Block> blocks = new ArrayList<>();

    public MarkdownRenderView(Context context) {
        super(context);
        init(context);
    }

    public MarkdownRenderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // TODO 图片渲染同学：当前先创建 LocalImageLoader 占位。后续实现 ImageLoader 后，在 BlockAdapter/图片 ViewHolder 中使用它异步加载 Markdown 图片。
        // TODO 推荐做法：MarkdownRenderView 只负责解析和列表展示，图片解码、缓存、取消任务放在 image 包内。
        imageLoader = new LocalImageLoader(context);

        emptyView = new TextView(context);
        emptyView.setText("  暂无内容");
        emptyView.setTextColor(0xFF999999);
        emptyView.setTextSize(14);
        emptyView.setPadding(dp(16), dp(32), dp(16), dp(32));

        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setPadding(dp(8), dp(4), dp(8), dp(4));
        recyclerView.setClipToPadding(false);
        recyclerView.setVisibility(View.GONE);

        adapter = new BlockAdapter(context, blocks, style);
        recyclerView.setAdapter(adapter);

        addView(emptyView);
        addView(recyclerView);
    }

    public void submitMarkdown(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            blocks = new ArrayList<>();
            handler.post(() -> {
                adapter = new BlockAdapter(getContext(), blocks, style);
                recyclerView.setAdapter(adapter);
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            });
            return;
        }

        final int version = ++renderVersion;

        executor.execute(() -> {
            MarkdownParser.ParseResult result;
            try {
                result = NoteMarkdownView.parseDocument(markdown);
            } catch (Exception e) {
                handler.post(() -> {
                    if (version == renderVersion) {
                        blocks = new ArrayList<>();
                        adapter = new BlockAdapter(getContext(), blocks, style);
                        recyclerView.setAdapter(adapter);
                        emptyView.setText("  解析失败: " + e.getMessage());
                        emptyView.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    }
                });
                return;
            }

            List<Block> newBlocks = BlockExtractor.extract(result.plainText, result.spans);

            handler.post(() -> {
                if (version != renderVersion) return;
                blocks = newBlocks;
                adapter = new BlockAdapter(getContext(), blocks, style);
                recyclerView.setAdapter(adapter);
                if (newBlocks.isEmpty()) {
                    emptyView.setText("  暂无内容");
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
