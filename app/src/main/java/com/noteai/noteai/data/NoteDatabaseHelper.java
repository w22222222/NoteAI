package com.noteai.noteai.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class NoteDatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "noteai.db";
    public static final int DATABASE_VERSION = 1;

    public static final String TABLE_NOTES = "notes";
    public static final String TABLE_CATEGORIES = "categories";
    public static final String TABLE_TAGS = "tags";
    public static final String TABLE_NOTE_TAGS = "note_tags";
    public static final String TABLE_NOTES_FTS = "notes_fts";

    private static final String SQL_CREATE_NOTES =
            "CREATE TABLE " + TABLE_NOTES + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "title TEXT NOT NULL DEFAULT '', "
                    + "content TEXT NOT NULL DEFAULT '', "
                    + "category_id INTEGER, "
                    + "created_at INTEGER NOT NULL, "
                    + "updated_at INTEGER NOT NULL, "
                    + "deleted INTEGER NOT NULL DEFAULT 0"
                    + ")";

    private static final String SQL_CREATE_CATEGORIES =
            "CREATE TABLE " + TABLE_CATEGORIES + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL UNIQUE, "
                    + "created_at INTEGER NOT NULL, "
                    + "updated_at INTEGER NOT NULL"
                    + ")";

    private static final String SQL_CREATE_TAGS =
            "CREATE TABLE " + TABLE_TAGS + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL UNIQUE, "
                    + "color INTEGER NOT NULL, "
                    + "created_at INTEGER NOT NULL, "
                    + "updated_at INTEGER NOT NULL"
                    + ")";

    private static final String SQL_CREATE_NOTE_TAGS =
            "CREATE TABLE " + TABLE_NOTE_TAGS + " ("
                    + "note_id INTEGER NOT NULL, "
                    + "tag_id INTEGER NOT NULL, "
                    + "PRIMARY KEY(note_id, tag_id)"
                    + ")";

    private static final String SQL_CREATE_NOTES_FTS =
            "CREATE VIRTUAL TABLE " + TABLE_NOTES_FTS + " USING fts5("
                    + "title, "
                    + "content, "
                    + "content='" + TABLE_NOTES + "', "
                    + "content_rowid='id'"
                    + ")";

    public NoteDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_NOTES);
        db.execSQL(SQL_CREATE_CATEGORIES);
        db.execSQL(SQL_CREATE_TAGS);
        db.execSQL(SQL_CREATE_NOTE_TAGS);
        db.execSQL(SQL_CREATE_NOTES_FTS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTE_TAGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTES_FTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TAGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATEGORIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTES);
        onCreate(db);
    }
}
