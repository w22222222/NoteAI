package com.noteai.noteai.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownProtector {
    private static final Pattern CODE_BLOCK = Pattern.compile("(?ms)^```.*?^```");
    private static final Pattern LOCAL_IMAGE = Pattern.compile("!\\[[^\\]\\n]*\\]\\(images/[^)\\n]+\\)\\{width=\\d+ height=\\d+\\}");

    static ProtectedMarkdown protect(String markdown) {
        ProtectedMarkdown protectedMarkdown = new ProtectedMarkdown(markdown);
        protectedMarkdown.text = protectMatches(protectedMarkdown.text, CODE_BLOCK, protectedMarkdown.tokens);
        protectedMarkdown.text = protectMatches(protectedMarkdown.text, LOCAL_IMAGE, protectedMarkdown.tokens);
        return protectedMarkdown;
    }

    private static String protectMatches(String input, Pattern pattern, List<Token> tokens) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String placeholder = makePlaceholder(tokens.size());
            tokens.add(new Token(placeholder, matcher.group()));
            matcher.appendReplacement(out, Matcher.quoteReplacement(placeholder));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String makePlaceholder(int index) {
        return "NOTEAI_PROTECTED_MARKDOWN_" + index;
    }

    static final class ProtectedMarkdown {
        private String text;
        private final List<Token> tokens = new ArrayList<>();

        private ProtectedMarkdown(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }

        boolean hasTokens() {
            return !tokens.isEmpty();
        }

        String restore(String modelOutput) throws MissingProtectedTokenException {
            String restored = modelOutput == null ? "" : modelOutput;
            for (Token token : tokens) {
                if (!restored.contains(token.placeholder)) {
                    throw new MissingProtectedTokenException();
                }
                restored = restored.replace(token.placeholder, token.original);
            }
            return restored;
        }
    }

    static final class MissingProtectedTokenException extends Exception {
    }

    private static final class Token {
        private final String placeholder;
        private final String original;

        private Token(String placeholder, String original) {
            this.placeholder = placeholder;
            this.original = original;
        }
    }
}
