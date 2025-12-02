package com.screen.textgrabber;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Switch;
import android.widget.Toast;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 应用主界面
 * 负责提供用户界面来开启/关闭抓取功能，并引导用户开启无障碍服务权限。
 */
public class MainActivity extends AppCompatActivity {

    private Switch toggleSwitch; // 控制开关
    private SharedPreferences sp; // 用于保存开关状态
    private android.widget.TextView tvCounter; // 显示保存计数
    private android.widget.TextView tvRecentTexts; // 显示最近的文本
    private android.os.Handler handler; // 用于定时更新计数
    private Runnable updateCounterRunnable; // 更新计数的任务
    private Runnable updateRecentTextsRunnable; // 更新最近文本的任务
    private DBManager dbManager; // 数据库管理器

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化 SharedPreferences
        sp = getSharedPreferences("app_config", MODE_PRIVATE);
        toggleSwitch = findViewById(R.id.btn_toggle);
        tvCounter = findViewById(R.id.tv_counter);
        tvRecentTexts = findViewById(R.id.tv_recent_texts);
        
        // 初始化数据库管理器
        dbManager = new DBManager(this);

        // 初始化 Handler
        handler = new android.os.Handler();
        updateCounterRunnable = new Runnable() {
            @Override
            public void run() {
                updateCounter();
                handler.postDelayed(this, 1000); // 每 1000ms 更新一次
            }
        };
        
        updateRecentTextsRunnable = new Runnable() {
            @Override
            public void run() {
                updateRecentTexts();
                handler.postDelayed(this, 5000); // 每 5000ms 更新一次
            }
        };
        Toast.makeText(this, "TextGrabber 服务已启动", Toast.LENGTH_SHORT).show();

        // 初始化 SharedPreferences
        sp = getSharedPreferences("app_config", MODE_PRIVATE);
        toggleSwitch = findViewById(R.id.btn_toggle);

        // 恢复上次保存的开关状态
        toggleSwitch.setChecked(sp.getBoolean("is_active", false));

        // 监听开关变化
        toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d("MainActivity", "Switch state changed: " + isChecked);
            System.out.println("TextGrabber: Switch state changed: " + isChecked);
            
            // 显式创建并显示 Toast
            Toast toast = Toast.makeText(MainActivity.this, "开关状态: " + isChecked, Toast.LENGTH_LONG);
            toast.show();
            
            // 保存当前状态
            sp.edit().putBoolean("is_active", isChecked).apply();

            // 如果开启了功能，但系统无障碍服务未开启，则提示并跳转设置
            if (isChecked && !isAccessibilitySettingsOn(this)) {
                Toast.makeText(this, R.string.permission_hint, Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 开始定时更新计数
        handler.post(updateCounterRunnable);
        // 开始定时更新最近文本
        handler.post(updateRecentTextsRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 停止定时更新
        handler.removeCallbacks(updateCounterRunnable);
        handler.removeCallbacks(updateRecentTextsRunnable);
    }

    /**
     * 更新计数显示
     */
    private void updateCounter() {
        int count = sp.getInt("saved_count", 0);
        tvCounter.setText("已保存: " + count + " 条");
    }

    /**
     * 更新最近文本显示
     */
    private void updateRecentTexts() {
        new Thread(() -> {
            java.util.List<String> recentTexts = dbManager.getRecentTexts(30);
            
            StringBuilder sb = new StringBuilder("最近 30 条记录:\n");
            if (recentTexts.isEmpty()) {
                sb.append("(暂无数据)");
            } else {
                for (int i = 0; i < recentTexts.size(); i++) {
                    String text = recentTexts.get(i);
                    // 截断过长的文本
                    if (text.length() > 50) {
                        text = text.substring(0, 50) + "...";
                    }
                    sb.append((i + 1)).append(". ").append(text).append("\n");
                }
            }
            
            final String displayText = sb.toString();
            runOnUiThread(() -> tvRecentTexts.setText(displayText));
        }).start();
    }

    /**
     * 检查当前应用的无障碍服务是否已开启
     *
     * @param mContext 上下文
     * @return true 表示已开启，false 表示未开启
     */
    private boolean isAccessibilitySettingsOn(Context mContext) {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + MyAccessibilityService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    mContext.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    mContext.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessibilityService = mStringColonSplitter.next();
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
