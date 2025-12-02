包名 com.screen.textgrabber



我要开发一个安卓app（java，不用kotlin）
实现功能，通过无障碍接口，读取屏幕上的全部文字，然后根据某种方法，某种键，保存到数据库中
实现去重功能，比如说，我往下滑动一段距离，某个控件里的文字不会重复保存
app主界面有一个按钮，用于切换这个实时保存文字功能是否开启
帮我筹划一个方案


这是一个非常经典的安卓辅助功能（AccessibilityService）应用场景。使用 Java 开发完全没有问题。

为了实现你的需求，我们需要解决三个核心问题：
1.  **数据获取**：如何无死角地拿到屏幕上所有的文字。
2.  **数据去重**：如何在滚动页面时，不重复保存已经抓取过的、尚未移出屏幕的、或者内容完全一致的控件文字。
3.  **控制逻辑**：如何通过主界面按钮控制后台服务的“抓取状态”。

以下是为你筹划的详细开发方案：

---

### 1. 架构设计

*   **UI层 (MainActivity)**：包含一个开关按钮。用于跳转系统设置开启服务，以及通过 `SharedPreferences` 修改一个全局标志位（Flag），告诉服务是“工作”还是“休眠”。
*   **服务层 (MyAccessibilityService)**：核心组件。监听屏幕变化，递归遍历视图树，提取文字。
*   **逻辑层 (DeduplicationLogic)**：负责计算内容的唯一标识（Key），并判断是否重复。
*   **数据层 (SQLite/Room)**：保存抓取到的数据。

---

### 2. 核心实现步骤

#### 第一步：配置 AndroidManifest 和 XML

这是无障碍服务的基础。

**AndroidManifest.xml:**
```xml
<service
    android:name=".MyAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

**res/xml/accessibility_service_config.xml:**
关键在于 `canRetrieveWindowContent="true"`。
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100" />
```
*   `notificationTimeout`: 设置为100ms左右，避免由于事件太频繁导致处理不过来，起到一定的节流作用。

#### 第二步：实现无障碍服务 (MyAccessibilityService.java)

这是最复杂的部分。我们需要递归遍历屏幕节点。

```java
public class MyAccessibilityService extends AccessibilityService {

    private boolean isFunctionEnabled = false; // 控制开关
    private DBManager dbManager; // 数据库管理类
    
    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        dbManager = new DBManager(this);
        // 初始化读取开关状态
        SharedPreferences sp = getSharedPreferences("app_config", MODE_PRIVATE);
        isFunctionEnabled = sp.getBoolean("is_active", false);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 1. 检查开关：如果用户在主界面关闭了功能，直接返回
        SharedPreferences sp = getSharedPreferences("app_config", MODE_PRIVATE);
        isFunctionEnabled = sp.getBoolean("is_active", false);
        
        if (!isFunctionEnabled) return;

        // 2. 获取根节点
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // 3. 遍历并处理
        traverseNode(rootNode);
    }

    private void traverseNode(AccessibilityNodeInfo node) {
        if (node == null) return;

        // 获取文字
        if (node.getText() != null && node.getText().length() > 0) {
            String content = node.getText().toString();
            String viewId = node.getViewIdResourceName(); // 控件ID (可能为空)
            String className = node.getClassName().toString();
            
            // 4. 执行去重和保存逻辑
            processAndSave(content, viewId, className, node);
        }

        // 递归子节点
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseNode(child);
                child.recycle(); // 记得回收，防止内存泄漏
            }
        }
    }

    @Override
    public void onInterrupt() { }
}
```

#### 第三步：去重策略 (Deduplication Strategy)

你提到的“滑动一段距离，不会重复保存”，这是最难点。

**常见的重复场景：**
1.  **事件触发频繁**：屏幕微小变动会触发多次 `onAccessibilityEvent`，导致同一屏内容被扫描多次。
2.  **列表复用**：RecyclerView 滑动时，ViewID 是重复的，但内容变了；或者内容没变，只是位置变了。

**解决方案：生成唯一指纹 (Fingerprint)**

我们需要构建一个“指纹”来定义什么是“重复”。
建议的指纹组合：`当前Activity包名 + 控件内容(Text) + 控件ID(ViewId)`。

**去重逻辑实现：**
我们在内存中维护一个 `LRU Cache` (最近最少使用缓存) 或者一个简单的 `HashSet`，用于存储最近 5-10 秒内或者最近 100 条保存过的指纹。

```java
// 在 Service 或 逻辑类中
private LruCache<String, Long> processedCache = new LruCache<>(200); // 缓存最近200条

private void processAndSave(String content, String viewId, String className, AccessibilityNodeInfo node) {
    // 过滤无效数据
    if (content.trim().isEmpty()) return;

    // 生成唯一Key
    // 组合：包名 + 控件ID + 内容。
    // 这样如果同一个列表中有两条一模一样的文字，这个Key会视为重复。
    // 如果你想允许列表中出现两条一样的文字（只要位置不同），你需要加入 node.getBoundsInScreen(rect) 的坐标信息作为Key的一部分。
    String packageName = node.getPackageName() != null ? node.getPackageName().toString() : "";
    String uniqueKey = packageName + "_" + (viewId != null ? viewId : "no_id") + "_" + content;

    // 检查缓存
    if (processedCache.get(uniqueKey) != null) {
        // 缓存中存在，说明最近刚处理过，跳过（去重）
        return;
    }

    // 保存到数据库
    saveToDatabase(content, viewId, packageName, System.currentTimeMillis());

    // 加入缓存，标记已处理
    processedCache.put(uniqueKey, System.currentTimeMillis());
}
```

**关于“滑动”的特殊处理：**
如果你一直往下滑，新的内容会出现，旧的内容会消失。
*   因为我们的 Key 包含了 `content` (文字内容)，所以当新的一行文字滑入屏幕，它的 Key 是新的，会被保存。
*   当已经在屏幕上的文字随手指移动位置时，因为它的 `content` 和 `viewId` 没变，Key 也没变，所以会被缓存拦截，**不会重复保存**。这完美符合你的需求。

#### 第四步：数据库设计 (Database)

使用 Android 原生 SQLiteOpenHelper 即可。

**表结构建议：**
*   `_id`: Integer, Primary Key, Auto Increment
*   `content`: Text (抓取的文字)
*   `pkg_name`: Text (来源App包名)
*   `view_id`: Text (控件ID)
*   `capture_time`: Long (时间戳)
*   `hash_key`: Text (用于长期去重的唯一指纹，可选)

#### 第五步：主界面 (MainActivity)

主界面需要两个核心功能：
1.  **跳转设置**：引导用户去系统设置里开启你的无障碍服务（APP无法通过代码强制开启，必须用户手动点）。
2.  **业务开关**：控制是否进行抓取。

```java
public class MainActivity extends AppCompatActivity {

    private Switch toggleSwitch;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("app_config", MODE_PRIVATE);
        toggleSwitch = findViewById(R.id.btn_toggle);

        // 初始化开关状态
        toggleSwitch.setChecked(sp.getBoolean("is_active", false));

        toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存状态，Service 会读取这个状态
            sp.edit().putBoolean("is_active", isChecked).apply();
            
            if (isChecked && !isAccessibilitySettingsOn(this)) {
                Toast.makeText(this, "请先在设置中开启无障碍服务", Toast.LENGTH_LONG).show();
                // 跳转到系统设置
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
    }

    // 检查服务是否开启的辅助方法
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
```

---

### 3. 开发中可能遇到的坑与优化建议

1.  **WebView 和 游戏界面**：
    *   对于标准的 Android 控件（TextView, Button等），上述方法完美有效。
    *   对于 **WebView**（浏览器、H5页面）或 **Flutter/Unity** 开发的应用，有时候 `AccessibilityNodeInfo` 拿不到内部文字，或者拿到的是一大坨没有结构的文字。这是系统机制决定的。
2.  **性能问题**：
    *   `getRootInActiveWindow()` 和递归遍历非常消耗性能。如果用户快速滑动列表，事件会像雪崩一样触发。
    *   **优化**：一定要在 `onAccessibilityEvent` 里加节流（Throttle）。例如：每次处理完一次全屏扫描，强制 `Thread.sleep(500)` 或者记录上次处理时间，500ms 内的新事件直接丢弃。
3.  **内存泄漏**：
    *   `AccessibilityNodeInfo` 使用完必须调用 `.recycle()`，否则服务运行几分钟就会因为内存溢出崩溃。
4.  **隐私合规**：
    *   读取屏幕文字属于极其敏感的权限。如果你要上架应用市场，必须在隐私政策里详细说明，并且不能读取密码框（`node.isPassword()` 为 true 的要跳过）。

### 总结

你的方案核心在于 **AccessibilityService 的递归遍历** 配合 **基于内容哈希的内存缓存去重**。

*   **键 (Key)** = 包名 + 文字内容。
*   **去重方法** = LRU 缓存（内存中保留最近200条记录），如果在缓存中则不存数据库。

按照这个路线图开发，你可以得到一个稳定运行的文字抓取工具。