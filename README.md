# TextGrabber

TextGrabber 是一个安卓无障碍服务应用，用于实时抓取屏幕上的文字并保存到本地数据库。

## 文件结构说明

### 核心代码 (Java)
*   **`app/src/main/java/com/screen/textgrabber/MyAccessibilityService.java`**
    *   **作用**: 项目的核心服务，继承自 `AccessibilityService`。
    *   **功能**: 监听屏幕内容变化事件 (`onAccessibilityEvent`)，递归遍历屏幕节点树，提取文字内容。它调用 `DeduplicationLogic` 进行去重，并调用 `DBManager` 将有效数据保存到数据库。

*   **`app/src/main/java/com/screen/textgrabber/DeduplicationLogic.java`**
    *   **作用**: 负责数据去重逻辑。
    *   **功能**: 维护一个 LRU 缓存 (最近最少使用)，通过生成唯一键 (包名 + 控件ID + 内容) 来判断当前抓取的文字是否是最近已经保存过的，防止重复存储。

*   **`app/src/main/java/com/screen/textgrabber/DBManager.java`**
    *   **作用**: 数据库管理类，继承自 `SQLiteOpenHelper`。
    *   **功能**: 负责创建 SQLite 数据库 `text_grabber.db` 和表 `captured_text`，提供插入数据的方法。

*   **`app/src/main/java/com/screen/textgrabber/MainActivity.java`**
    *   **作用**: 应用的主界面。
    *   **功能**: 提供一个开关按钮，用于控制是否开启抓取功能。同时负责引导用户跳转到系统设置开启无障碍服务权限。

### 资源文件 (Resources)
*   **`app/src/main/res/xml/accessibility_service_config.xml`**
    *   **作用**: 无障碍服务的配置文件。
    *   **功能**: 定义了服务监听的事件类型 (窗口变化、滚动等)、反馈类型以及 `canRetrieveWindowContent="true"` (允许获取窗口内容) 等关键属性。

*   **`app/src/main/res/layout/activity_main.xml`**
    *   **作用**: 主界面的布局文件。
    *   **功能**: 包含一个 `Switch` 开关控件。

*   **`app/src/main/res/values/strings.xml`**
    *   **作用**: 字符串资源文件。
    *   **功能**: 定义应用名称、提示信息等文字。

### 配置文件 (Config)
*   **`app/src/main/AndroidManifest.xml`**
    *   **作用**: 安卓应用的清单文件。
    *   **功能**: 声明了应用组件 (Activity, Service) 和权限 (`BIND_ACCESSIBILITY_SERVICE`)。

*   **`app/build.gradle`**
    *   **作用**: 模块构建脚本。
    *   **功能**: 定义 SDK 版本、依赖库等构建配置。

*   **`.gitignore`**
    *   **作用**: Git 忽略规则文件。
    *   **功能**: 配置了安卓开发中不需要提交到版本控制的文件 (如 build 目录, .idea 目录等)。
