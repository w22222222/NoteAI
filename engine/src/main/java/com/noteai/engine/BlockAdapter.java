package com.noteai.engine;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BlockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VT_HEADING    = 0;
    private static final int VT_PARAGRAPH  = 1;
    private static final int VT_CODE_BLOCK = 2;
    private static final int VT_LIST       = 3;
    private static final int VT_QUOTE      = 4;
    private static final int VT_RULE       = 5;

    private final List<Block> blocks;
    private final StyleConfig style;
    private final Context context;

    public BlockAdapter(Context context, List<Block> blocks, StyleConfig style) {
        this.context = context;
        this.blocks = blocks;
        this.style = style;
    }

    @Override
    public int getItemViewType(int position) {
        switch (blocks.get(position).type) {
            case Block.TYPE_HEADING:    return VT_HEADING;
            case Block.TYPE_PARAGRAPH:  return VT_PARAGRAPH;
            case Block.TYPE_CODE_BLOCK: return VT_CODE_BLOCK;
            case Block.TYPE_LIST:       return VT_LIST;
            case Block.TYPE_QUOTE:      return VT_QUOTE;
            case Block.TYPE_RULE:       return VT_RULE;
            default:                    return VT_PARAGRAPH;
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();
        switch (viewType) {
            case VT_HEADING: {
                TextView tv = new TextView(ctx);
                tv.setPadding(0, dp(8), 0, dp(2));
                return new TextHolder(tv);
            }
            case VT_PARAGRAPH: {
                TextView tv = new TextView(ctx);
                tv.setPadding(0, dp(4), 0, dp(4));
                return new TextHolder(tv);
            }
            case VT_CODE_BLOCK:
                return new CodeBlockHolder(ctx);
            case VT_LIST: {
                TextView tv = new TextView(ctx);
                tv.setPadding(0, dp(1), 0, dp(1));
                return new TextHolder(tv);
            }
            case VT_QUOTE: {
                TextView tv = new TextView(ctx);
                tv.setPadding(dp(8), dp(4), 0, dp(4));
                return new TextHolder(tv);
            }
            case VT_RULE: {
                View v = new View(ctx);
                v.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, dp(1)));
                return new RuleHolder(v);
            }
            default:
                TextView tv = new TextView(ctx);
                return new TextHolder(tv);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Block block = blocks.get(position);
        switch (holder.getItemViewType()) {
            case VT_HEADING:
                bindHeading((TextHolder) holder, block);
                break;
            case VT_PARAGRAPH:
                bindText((TextHolder) holder, block);
                break;
            case VT_CODE_BLOCK:
                bindCodeBlock((CodeBlockHolder) holder, block);
                break;
            case VT_LIST:
                bindList((TextHolder) holder, block);
                break;
            case VT_QUOTE:
                bindQuote((TextHolder) holder, block);
                break;
            case VT_RULE:
                bindRule((RuleHolder) holder, block);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    // ==================== 绑定逻辑 ====================

    private void bindHeading(TextHolder holder, Block block) {
        SpannableString ss = NoteMarkdownView.buildSpannable(block.text, block.spans, style);
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, ss.length(), 0);
        float mult = block.level == 1 ? style.heading1Size
                   : block.level == 2 ? style.heading2Size
                   : style.heading3Size;
        holder.tv.setTextSize(16 * mult);
        holder.tv.setTextColor(style.headingColor);
        holder.tv.setText(ss);
    }

    private void bindText(TextHolder holder, Block block) {
        SpannableString ss = NoteMarkdownView.buildSpannable(block.text, block.spans, style);
        holder.tv.setText(ss);
        holder.tv.setTextSize(16);
        holder.tv.setTextColor(style.bodyColor);
    }

    private void bindCodeBlock(CodeBlockHolder holder, Block block) {
        holder.langLabel.setText(block.lang != null && !block.lang.isEmpty()
                ? block.lang : "code");
        holder.codeView.setTextSize(13);
        holder.codeView.setTypeface(Typeface.MONOSPACE);
        holder.codeView.setText(block.text);
        holder.copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("代码", block.text));
            holder.copyBtn.setText("已复制");
            holder.copyBtn.postDelayed(() -> holder.copyBtn.setText("复制"), 1500);
        });
    }

    private void bindList(TextHolder holder, Block block) {
        SpannableString ss = NoteMarkdownView.buildSpannable(block.text, block.spans, style);
        ss.setSpan(new android.text.style.BulletSpan(dp(12), style.bodyColor),
                0, ss.length(), 0);
        holder.tv.setText(ss);
        holder.tv.setTextSize(16);
        holder.tv.setTextColor(style.bodyColor);
    }

    private void bindQuote(TextHolder holder, Block block) {
        SpannableString ss = NoteMarkdownView.buildSpannable(block.text, block.spans, style);
        holder.tv.setText(ss);
        holder.tv.setTextSize(16);
        holder.tv.setTextColor(style.quoteColor);
        holder.tv.setBackgroundColor(style.codeBlockBgColor);
    }

    private void bindRule(RuleHolder holder, Block block) {
        holder.v.setBackgroundColor(0xFFCCCCCC);
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    // ==================== ViewHolder 类型 ====================

    static class TextHolder extends RecyclerView.ViewHolder {
        final TextView tv;
        TextHolder(TextView tv) {
            super(tv);
            this.tv = tv;
        }
    }

    static class RuleHolder extends RecyclerView.ViewHolder {
        final View v;
        RuleHolder(View v) { super(v); this.v = v; }
    }

    static class CodeBlockHolder extends RecyclerView.ViewHolder {
        final TextView langLabel;
        final TextView copyBtn;
        final TextView codeView;

        CodeBlockHolder(Context ctx) {
            super(new FrameLayout(ctx));
            FrameLayout root = (FrameLayout) itemView;
            root.setBackgroundColor(0xFFF5F5F5);
            root.setPadding(dp(ctx, 0), dp(ctx, 6), dp(ctx, 0), dp(ctx, 6));

            LinearLayout inner = new LinearLayout(ctx);
            inner.setOrientation(LinearLayout.VERTICAL);

            LinearLayout titleBar = new LinearLayout(ctx);
            titleBar.setOrientation(LinearLayout.HORIZONTAL);
            titleBar.setPadding(dp(ctx, 12), dp(ctx, 4), dp(ctx, 12), dp(ctx, 4));
            titleBar.setGravity(Gravity.CENTER_VERTICAL);

            langLabel = new TextView(ctx);
            langLabel.setTextSize(12);
            langLabel.setTextColor(0xFF888888);
            LinearLayout.LayoutParams langLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            titleBar.addView(langLabel, langLp);

            copyBtn = new TextView(ctx);
            copyBtn.setText("复制");
            copyBtn.setTextSize(12);
            copyBtn.setTextColor(0xFF1A73E8);
            copyBtn.setPadding(dp(ctx, 8), 0, 0, 0);
            titleBar.addView(copyBtn);

            inner.addView(titleBar);

            View sep = new View(ctx);
            sep.setBackgroundColor(0xFFE0E0E0);
            sep.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1)));
            inner.addView(sep);

            codeView = new TextView(ctx);
            codeView.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
            codeView.setTextColor(0xFF333333);
            inner.addView(codeView);

            root.addView(inner);
        }

        private static int dp(Context ctx, int dp) {
            return (int) (dp * ctx.getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
