package com.screen.textgrabber;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 数据库管理类
 * 负责 SQLite 数据库的创建、升级以及数据的插入操作。
 */
public class DBManager extends SQLiteOpenHelper {

    // 数据库名称
    private static final String DATABASE_NAME = "text_grabber.db";
    // 数据库版本号
    private static final int DATABASE_VERSION = 1;
    // 表名
    private static final String TABLE_NAME = "captured_text";

    // 列名定义
    private static final String COLUMN_ID = "_id"; // 主键 ID
    private static final String COLUMN_CONTENT = "content"; // 抓取的文字内容
    private static final String COLUMN_PKG_NAME = "pkg_name"; // 来源 APP 包名
    private static final String COLUMN_VIEW_ID = "view_id"; // 控件 ID
    private static final String COLUMN_CAPTURE_TIME = "capture_time"; // 抓取时间戳
    private static final String COLUMN_HASH_KEY = "hash_key"; // 用于去重的唯一哈希键

    public DBManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * 创建数据库表
     */
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

    /**
     * 数据库升级时调用
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 简单处理：删除旧表，重新创建
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    /**
     * 插入一条抓取的文字记录
     *
     * @param content     文字内容
     * @param pkgName     来源包名
     * @param viewId      控件 ID
     * @param captureTime 抓取时间
     * @param hashKey     唯一哈希键
     */
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
