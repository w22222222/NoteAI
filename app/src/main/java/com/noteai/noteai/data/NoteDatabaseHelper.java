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

    // 把字段名 content 改为 body
    // 1. 修改建表语句 (USING fts4)
    private static final String SQL_CREATE_NOTES_FTS =
            "CREATE VIRTUAL TABLE " + TABLE_NOTES_FTS + " USING fts4("
                    + "title, "
                    + "content"
                    + ")";

    // 2. 修改触发器 (FTS4 使用 docid 而不是 rowid，且语法略有不同)
    private static final String SQL_CREATE_TRIGGER_INSERT =
            "CREATE TRIGGER notes_ai AFTER INSERT ON " + TABLE_NOTES + " BEGIN " +
                    "INSERT INTO " + TABLE_NOTES_FTS + "(docid, title, content) " +
                    "VALUES (new.id, new.title, new.content); " +
                    "END;";

    private static final String SQL_CREATE_TRIGGER_DELETE =
            "CREATE TRIGGER notes_ad AFTER DELETE ON " + TABLE_NOTES +
                    " BEGIN " + "DELETE FROM " + TABLE_NOTES_FTS +
                    " WHERE docid = old.id; " +
                    "END;";

    private static final String SQL_CREATE_TRIGGER_UPDATE =
            "CREATE TRIGGER notes_au AFTER UPDATE ON " + TABLE_NOTES +
                    " BEGIN " + "UPDATE " + TABLE_NOTES_FTS +
                    " SET title = new.title, content = new.content WHERE docid = old.id; " +
                    "END;";

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
        // 2. 关键一步：执行触发器 SQL，让它们真正“活”起来
        db.execSQL(SQL_CREATE_TRIGGER_INSERT);
        db.execSQL(SQL_CREATE_TRIGGER_UPDATE);
        db.execSQL(SQL_CREATE_TRIGGER_DELETE);

        android.util.Log.d("DB_DEBUG", "数据库及触发器创建成功！");
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
