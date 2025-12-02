package com.screen.textgrabber;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBManager extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "text_grabber.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "captured_text";

    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_PKG_NAME = "pkg_name";
    private static final String COLUMN_VIEW_ID = "view_id";
    private static final String COLUMN_CAPTURE_TIME = "capture_time";
    private static final String COLUMN_HASH_KEY = "hash_key";

    public DBManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_CONTENT + " TEXT, " +
                COLUMN_PKG_NAME + " TEXT, " +
                COLUMN_VIEW_ID + " TEXT, " +
                COLUMN_CAPTURE_TIME + " INTEGER, " +
                COLUMN_HASH_KEY + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void insertText(String content, String pkgName, String viewId, long captureTime, String hashKey) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_CONTENT, content);
        values.put(COLUMN_PKG_NAME, pkgName);
        values.put(COLUMN_VIEW_ID, viewId);
        values.put(COLUMN_CAPTURE_TIME, captureTime);
        values.put(COLUMN_HASH_KEY, hashKey);
        db.insert(TABLE_NAME, null, values);
        db.close();
    }
}
