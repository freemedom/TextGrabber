package com.screen.textgrabber;

import android.util.LruCache;

public class DeduplicationLogic {

    private LruCache<String, Long> processedCache;

    public DeduplicationLogic() {
        // Cache size 200 items
        processedCache = new LruCache<>(200);
    }

    public String generateKey(String packageName, String viewId, String content) {
        return (packageName != null ? packageName : "") + "_" +
               (viewId != null ? viewId : "no_id") + "_" +
               content;
    }

    public boolean isDuplicate(String key) {
        if (processedCache.get(key) != null) {
            return true;
        }
        processedCache.put(key, System.currentTimeMillis());
        return false;
    }
}
