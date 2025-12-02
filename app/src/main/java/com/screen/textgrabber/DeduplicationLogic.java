package com.screen.textgrabber;

import android.util.LruCache;

/**
 * 去重逻辑类
 * 使用 LRU 缓存来防止重复保存相同的文字内容。
 */
public class DeduplicationLogic {

    // 使用 LRU (Least Recently Used) 缓存存储最近处理过的 Key
    private LruCache<String, Long> processedCache;

    public DeduplicationLogic() {
        // 缓存大小设置为 200 条
        processedCache = new LruCache<>(200);
    }

    /**
     * 生成唯一键 (Key)
     * 组合规则：包名 + 控件ID + 文字内容
     *
     * @param packageName APP 包名
     * @param viewId      控件 ID
     * @param content     文字内容
     * @return 唯一标识字符串
     */
    public String generateKey(String packageName, String viewId, String content) {
        return (packageName != null ? packageName : "") + "_" +
               (viewId != null ? viewId : "no_id") + "_" +
               content;
    }

    /**
     * 检查是否重复
     *
     * @param key 唯一键
     * @return 如果缓存中已存在（即最近处理过），返回 true；否则返回 false 并加入缓存。
     */
    public boolean isDuplicate(String key) {
        if (processedCache.get(key) != null) {
            // 缓存命中，说明是重复数据
            return true;
        }
        // 未命中，加入缓存，记录当前时间
        processedCache.put(key, System.currentTimeMillis());
        return false;
    }
}
