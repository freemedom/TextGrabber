package com.screen.textgrabber;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class MyAccessibilityService extends AccessibilityService {

    private boolean isFunctionEnabled = false;
    private DBManager dbManager;
    private DeduplicationLogic deduplicationLogic;
    private long lastProcessTime = 0;
    private static final long PROCESS_INTERVAL = 500; // Throttle interval in ms

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        dbManager = new DBManager(this);
        deduplicationLogic = new DeduplicationLogic();
        SharedPreferences sp = getSharedPreferences("app_config", MODE_PRIVATE);
        isFunctionEnabled = sp.getBoolean("is_active", false);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences sp = getSharedPreferences("app_config", MODE_PRIVATE);
        isFunctionEnabled = sp.getBoolean("is_active", false);

        if (!isFunctionEnabled) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessTime < PROCESS_INTERVAL) {
            return;
        }
        lastProcessTime = currentTime;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        traverseNode(rootNode);
    }

    private void traverseNode(AccessibilityNodeInfo node) {
        if (node == null) return;

        if (node.getText() != null && node.getText().length() > 0) {
            String content = node.getText().toString();
            String viewId = node.getViewIdResourceName();
            String className = node.getClassName().toString();
            String packageName = node.getPackageName() != null ? node.getPackageName().toString() : "";

            processAndSave(content, viewId, packageName, className);
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseNode(child);
                child.recycle();
            }
        }
    }

    private void processAndSave(String content, String viewId, String packageName, String className) {
        if (content.trim().isEmpty()) return;

        String uniqueKey = deduplicationLogic.generateKey(packageName, viewId, content);

        if (deduplicationLogic.isDuplicate(uniqueKey)) {
            return;
        }

        dbManager.insertText(content, packageName, viewId, System.currentTimeMillis(), uniqueKey);
    }

    @Override
    public void onInterrupt() {
    }
}
