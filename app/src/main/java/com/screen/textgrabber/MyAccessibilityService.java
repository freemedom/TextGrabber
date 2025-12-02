package com.screen.textgrabber;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;

/**
 * 核心无障碍服务类
 * 负责监听屏幕事件，遍历视图树，提取文字并保存。
 */
public class MyAccessibilityService extends AccessibilityService {

    private boolean isFunctionEnabled = false; // 功能开关状态
    private DBManager dbManager; // 数据库管理器
    private DeduplicationLogic deduplicationLogic; // 去重逻辑
    private long lastProcessTime = 0; // 上次处理时间
    private static final long PROCESS_INTERVAL = 500; // 节流间隔 (毫秒)，防止处理过于频繁
    private int savedCount = 0; // 本次启动保存的文本数量

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("MyAccessibilityService", "onCreate: Service created");
        // 重置计数器
        savedCount = 0;
        getSharedPreferences("app_config", MODE_PRIVATE).edit().putInt("saved_count", 0).apply();
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d("MyAccessibilityService", "onServiceConnected: Service connected");
        // 显示 Toast 提示，确保用户能看到服务启动了
        android.widget.Toast.makeText(this, "TextGrabber 服务已启动", android.widget.Toast.LENGTH_SHORT).show();
        
        // 初始化组件
        dbManager = new DBManager(this);
        deduplicationLogic = new DeduplicationLogic();
        // 读取初始开关状态
        SharedPreferences sp = getSharedPreferences("app_config", MODE_PRIVATE);
        isFunctionEnabled = sp.getBoolean("is_active", false);
        Log.d("MyAccessibilityService", "Initial function enabled state: " + isFunctionEnabled);
    }

    /**
     * 当系统检测到与无障碍服务匹配的事件时调用
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d("MyAccessibilityService", "onAccessibilityEvent: " + AccessibilityEvent.eventTypeToString(event.getEventType()));
        
        // 每次事件触发都检查一下开关状态（虽然有点耗性能，但能保证实时响应开关）
        SharedPreferences sp = getSharedPreferences("app_config", MODE_PRIVATE);
        isFunctionEnabled = sp.getBoolean("is_active", false);

        if (!isFunctionEnabled) return;

        // 节流处理：如果距离上次处理时间太短，则跳过
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessTime < PROCESS_INTERVAL) {
            return;
        }
        lastProcessTime = currentTime;

        // 获取当前活动窗口的根节点
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // 开始递归遍历
        traverseNode(rootNode);
    }

    /**
     * 递归遍历视图树
     * @param node 当前节点
     */
    private void traverseNode(AccessibilityNodeInfo node) {
        if (node == null) return;

        // 如果节点包含文字，则进行处理
        if (node.getText() != null && node.getText().length() > 0) {
            String content = node.getText().toString();
            String viewId = node.getViewIdResourceName();
            String className = node.getClassName().toString();
            String packageName = node.getPackageName() != null ? node.getPackageName().toString() : "";

            // 处理并保存
            processAndSave(content, viewId, packageName, className);
        }

        // 遍历子节点
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseNode(child);
                // 记得回收子节点，防止内存泄漏
                child.recycle();
            }
        }
    }

    /**
     * 处理文字并保存到数据库
     */
    private void processAndSave(String content, String viewId, String packageName, String className) {
        // 过滤空字符串
        if (content.trim().isEmpty()) return;

        // 生成唯一键
        String uniqueKey = deduplicationLogic.generateKey(packageName, viewId, content);

        // 检查是否重复
        if (deduplicationLogic.isDuplicate(uniqueKey)) {
            return;
        }
        // log
        Log.d("MyAccessibilityService", "Processing text: " + content);

        // 保存到数据库
        dbManager.insertText(content, packageName, viewId, System.currentTimeMillis(), uniqueKey);
        
        // 增加计数并保存到 SharedPreferences
        savedCount++;
        getSharedPreferences("app_config", MODE_PRIVATE).edit().putInt("saved_count", savedCount).apply();
    }

    @Override
    public void onInterrupt() {
        // 服务中断时的回调，通常不需要处理
    }
}
