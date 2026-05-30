package com.noteai.noteai.data;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FileStorageManager {
    /**
     * @brief the directory that stores all internal notes.
     */
    private final File notesDir;

    public FileStorageManager(Context context) {
        // by default, all notes are put under <package>/notes
        this.notesDir = new File(context.getFilesDir(), "notes");
    }

    /**
     * Save content to the Markdown file indicated by noteId.
     * If the file does not exist, create it.
     *
     * @param noteId  the ID of the note
     * @param content the contents of the note
     */
    public void save(long noteId, String content) {
        ensureDir(); // ensure directory existence

        File file = new File(notesDir, noteId + ".md"); // not creating file

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content != null ? content : "");
        } catch (IOException e) {
            throw new RuntimeException("Failed to save note " + noteId, e);
        }
    }

    /**
     * Returns the Markdown file content for editing in buffer.
     *
     * @param noteId the ID of the Markdown file. This should be handled by other DBs.
     * @return content of the Markdown file
     */
    public String load(long noteId) {
        File file = new File(notesDir, noteId + ".md");

        if (!file.exists()) return null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Delete a file.
     * @param noteId the Markdown file to be deleted.
     */
    public void delete(long noteId) {
        File file = new File(notesDir, noteId + ".md");
        file.delete();
    }

    public boolean hasSavedNotes() {
        File[] files = notesDir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.getName().endsWith(".md")) return true;
        }
        return false;
    }

    public List<Note> loadAllNotes(long currentTime) {
        List<Note> notes = new ArrayList<>();
        File[] files = notesDir.listFiles();
        if (files == null) return notes;
        for (File f : files) {
            String name = f.getName();
            if (!name.endsWith(".md")) continue;
            long id;
            try {
                id = Long.parseLong(name.substring(0, name.length() - 3));
            } catch (NumberFormatException e) {
                continue;
            }
            String content = load(id);
            if (content == null) continue;
            String title = extractTitle(content);
            notes.add(new Note(id, title, content, currentTime, currentTime));
        }
        return notes;
    }

    private String extractTitle(String content) {
        if (content == null || content.trim().isEmpty()) return "未命名笔记";
        String firstLine = content.trim().split("\n")[0].trim();
        while (firstLine.startsWith("#")) {
            firstLine = firstLine.substring(1).trim();
        }
        if (firstLine.isEmpty()) return "未命名笔记";
        if (firstLine.length() > 50) firstLine = firstLine.substring(0, 50);
        return firstLine;
    }

    /**
     * @brief Make sure that the directory exists. Create if not.
     */
    private void ensureDir() {
        if (!notesDir.exists()) {
            notesDir.mkdirs();
        }
    }
}
