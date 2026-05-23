package com.noteai.engine;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;

/**
 * Markdown 渲染预览器 — 纯 Java Swing 桌面端，直接调用本项目的 MarkdownParser + SpanInfo + StyleConfig。
 *
 * 调用链路（和 Android 端完全一致）：
 *   MarkdownParser.parse(md) → SpanInfo[] + plainText
 *   StyleConfig               → 颜色/字号
 *   Swing JTextPane           → 替代 Android TextView 做渲染
 *
 * NoteMarkdownView 依赖 Android API，桌面端由本文件的 renderContent() 替代。
 */
public class MarkdownPreviewer extends JFrame {

    private JTextArea inputArea;
    private JTextPane outputPane;
    private JLabel statusLabel;
    private StyleConfig styleConfig = StyleConfig.getDefault();

    private static final String DEMO_MD =
        "# NoteAI 智能笔记\n\n"
        + "## 项目简介\n\n"
        + "这是一个 **Markdown 渲染引擎** 的预览器，用纯 Java Swing 实现。\n"
        + "不依赖任何 Android API，可以在桌面直接运行。\n\n"
        + "## 功能演示\n\n"
        + "### 文本样式\n\n"
        + "- **粗体文字** 和 *斜体文字*\n"
        + "- `行内代码` 示例\n"
        + "- [链接到 GitHub](https://github.com)\n\n"
        + "### 引用\n\n"
        + "> 代码是写给人看的，顺便能在机器上运行。\n\n"
        + "### 代码块\n\n"
        + "```java\n"
        + "public class Hello {\n"
        + "    public static void main(String[] args) {\n"
        + "        System.out.println(\"Hello NoteAI!\");\n"
        + "    }\n"
        + "}\n"
        + "```\n\n"
        + "### 分割线\n\n"
        + "---\n\n"
        + "## 性能对比\n\n"
        + "| 方案 | 首次出现 | 全文可用 |\n"
        + "|------|---------|----------|\n"
        + "| 单线程 | 109ms | 109ms |\n"
        + "| 渐进式 | 5ms | 58ms |";

    public MarkdownPreviewer() {
        super("NoteAI Markdown 渲染预览器");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton lightBtn = new JButton("浅色");
        JButton darkBtn = new JButton("暗色");
        JButton resetBtn = new JButton("重置Demo");

        lightBtn.addActionListener(e -> {
            styleConfig = StyleConfig.getDefault();
            applyThemeToEditors(Color.WHITE, Color.BLACK);
            renderContent();
        });
        darkBtn.addActionListener(e -> {
            styleConfig = StyleConfig.getDark();
            applyThemeToEditors(new Color(0x1E1E1E), Color.LIGHT_GRAY);
            renderContent();
        });
        resetBtn.addActionListener(e -> inputArea.setText(DEMO_MD));

        toolbar.add(lightBtn);
        toolbar.add(darkBtn);
        toolbar.addSeparator();
        toolbar.add(resetBtn);

        statusLabel = new JLabel("  就绪");
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(statusLabel);

        inputArea = new JTextArea(20, 40);
        inputArea.setText(DEMO_MD);
        inputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        inputArea.setTabSize(4);
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createTitledBorder("Markdown 源码"));

        outputPane = new JTextPane();
        outputPane.setEditable(false);
        JScrollPane outputScroll = new JScrollPane(outputPane);
        outputScroll.setBorder(BorderFactory.createTitledBorder("渲染效果"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                inputScroll, outputScroll);
        splitPane.setResizeWeight(0.45);
        splitPane.setDividerLocation(480);

        getContentPane().add(toolbar, BorderLayout.NORTH);
        getContentPane().add(splitPane, BorderLayout.CENTER);

        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { renderContent(); }
            public void removeUpdate(DocumentEvent e)  { renderContent(); }
            public void changedUpdate(DocumentEvent e) { renderContent(); }
        });

        renderContent();
    }

    private void applyThemeToEditors(Color bg, Color fg) {
        inputArea.setBackground(bg);
        inputArea.setForeground(fg);
        inputArea.setCaretColor(fg);
        outputPane.setBackground(bg);
    }

    // ==================== 核心渲染逻辑 ====================
    // 这里对标 Android 端 NoteMarkdownView.render() 的完整链路：
    //   MarkdownParser.parse(md) → SpanInfo[] → 拼样式 → 设置到 View
    // 区别只是 View 从 Android TextView 换成了 Swing JTextPane

    private void renderContent() {
        String md = inputArea.getText();
        if (md == null || md.isEmpty()) {
            outputPane.setText("");
            statusLabel.setText("  就绪");
            return;
        }

        long t0 = System.currentTimeMillis();
        MarkdownParser.ParseResult result = MarkdownParser.parse(md);
        long tParse = System.currentTimeMillis() - t0;

        String plainText = result.plainText;
        SpanInfo[] spans = result.spans;

        StyledDocument doc = outputPane.getStyledDocument();
        SimpleAttributeSet bodyAttrs = makeBodyAttrs();

        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, plainText, bodyAttrs);

            for (SpanInfo span : spans) {
                if (span.start < 0 || span.end > plainText.length()
                        || span.start >= span.end) {
                    continue;
                }

                SimpleAttributeSet attrs = makeSpanAttrs(span);
                doc.setCharacterAttributes(span.start, span.end - span.start, attrs, false);
            }

            statusLabel.setText(String.format("  解析 %dms | %d 字 | %d 个 Span",
                    tParse, plainText.length(), spans.length));

        } catch (BadLocationException e) {
            statusLabel.setText("  渲染出错: " + e.getMessage());
        }
    }

    private SimpleAttributeSet makeBodyAttrs() {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setFontFamily(s, Font.SANS_SERIF);
        StyleConstants.setFontSize(s, (int)(16 * styleConfig.bodySize));
        StyleConstants.setForeground(s, toAwtColor(styleConfig.bodyColor));
        return s;
    }

    private SimpleAttributeSet makeSpanAttrs(SpanInfo span) {
        SimpleAttributeSet s = new SimpleAttributeSet();

        switch (span.type) {
            case SpanInfo.TYPE_HEADING:
                float sizeMult;
                if (span.level == 1) sizeMult = styleConfig.heading1Size;
                else if (span.level == 2) sizeMult = styleConfig.heading2Size;
                else sizeMult = styleConfig.heading3Size;
                StyleConstants.setFontFamily(s, Font.SANS_SERIF);
                StyleConstants.setFontSize(s, (int)(16 * sizeMult));
                StyleConstants.setBold(s, true);
                StyleConstants.setForeground(s, toAwtColor(styleConfig.headingColor));
                break;

            case SpanInfo.TYPE_BOLD:
                StyleConstants.setBold(s, true);
                StyleConstants.setForeground(s, toAwtColor(styleConfig.boldColor));
                break;

            case SpanInfo.TYPE_ITALIC:
                StyleConstants.setItalic(s, true);
                StyleConstants.setForeground(s, toAwtColor(styleConfig.italicColor));
                break;

            case SpanInfo.TYPE_CODE:
                StyleConstants.setFontFamily(s, Font.MONOSPACED);
                StyleConstants.setFontSize(s, 14);
                StyleConstants.setForeground(s, toAwtColor(styleConfig.codeTextColor));
                StyleConstants.setBackground(s, toAwtColor(styleConfig.codeBgColor));
                break;

            case SpanInfo.TYPE_CODE_BLOCK:
                StyleConstants.setFontFamily(s, Font.MONOSPACED);
                StyleConstants.setFontSize(s, 14);
                StyleConstants.setBackground(s, toAwtColor(styleConfig.codeBlockBgColor));
                break;

            case SpanInfo.TYPE_LIST:
                StyleConstants.setForeground(s, toAwtColor(styleConfig.bodyColor));
                break;

            case SpanInfo.TYPE_QUOTE:
                StyleConstants.setItalic(s, true);
                StyleConstants.setForeground(s, toAwtColor(styleConfig.quoteColor));
                StyleConstants.setLeftIndent(s, 12f);
                break;

            case SpanInfo.TYPE_LINK:
                StyleConstants.setUnderline(s, true);
                StyleConstants.setForeground(s, toAwtColor(styleConfig.linkColor));
                break;

            case SpanInfo.TYPE_IMAGE:
                StyleConstants.setForeground(s, toAwtColor(styleConfig.quoteColor));
                StyleConstants.setItalic(s, true);
                break;

            case SpanInfo.TYPE_RULE:
                StyleConstants.setForeground(s, Color.LIGHT_GRAY);
                break;

            case SpanInfo.TYPE_TABLE:
                StyleConstants.setFontFamily(s, Font.MONOSPACED);
                break;

            default:
                break;
        }

        StyleConstants.setFontFamily(s,
                (span.type == SpanInfo.TYPE_CODE || span.type == SpanInfo.TYPE_CODE_BLOCK
                        || span.type == SpanInfo.TYPE_TABLE)
                        ? Font.MONOSPACED : Font.SANS_SERIF);
        return s;
    }

    /**
     * StyleConfig 的颜色用的是 Android ARGB int（如 0xFF333333），
     * 和 java.awt.Color(int, true) 的格式完全一致，直接转换。
     */
    private static Color toAwtColor(int argb) {
        return new Color(argb, true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MarkdownPreviewer previewer = new MarkdownPreviewer();
            previewer.setVisible(true);
        });
    }
}
