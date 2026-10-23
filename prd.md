为了让 AI Agent（如 Cursor、Devin 等）能够精确地生成代码，我们需要将 PRD 深入到**数据表结构（DDL）、UI 组件层级、具体的业务算法以及 WebDAV 同步机制**的伪代码级别。

这是一份**极度详细、面向 Agent 执行**的开发规格说明书。

---

# 📱 个人私密行为记录 App (Intimate Tracker) - Agent 开发规格说明书

## 1. 技术栈与依赖库版本要求
*   **语言**: Kotlin (2.0.0+)
*   **UI 框架**: Jetpack Compose (BOM 2024.06.00+), Material 3
*   **架构**: MVVM + Clean Architecture (UI -> ViewModel -> Repository -> DataSource)
*   **本地数据库**: Room (2.6.1+)
*   **依赖注入**: Hilt (2.51.1+)
*   **异步处理**: Coroutines & Flow
*   **导航**: Jetpack Navigation Compose
*   **图片加载**: Coil (2.6.0+)
*   **图表库**: Vico (Compose 版本)
*   **WebDAV 客户端**: `com.thegrizzlylabs.sardine-android:0.9`
*   **加密与安全**: `androidx.biometric:biometric:1.1.0` (生物识别锁)
*   **日期时间**: `kotlinx-datetime` 或 `java.time` (API 26+)

---

## 2. 数据库设计 (Room Database Schema)
Agent 必须严格按照以下结构定义 Entity 和关系。**所有表均包含 `createdAt` 和 `updatedAt` (Long) 字段，以便 WebDAV 同步时比对时间戳。**

### 2.1 核心实体表
1.  **`Partner` (伴侣表)**
    *   `id`: String (UUID, PK)
    *   `name`: String (必填)
    *   `gender`: String (Enum: MALE, FEMALE, OTHER, UNKNOWN)
    *   `relationshipType`: String (Enum: CASUAL, SERIOUS, MARRIED, OTHER)
    *   `avatarPath`: String? (本地私有文件相对路径)
    *   `notes`: String?
    *   `isArchived`: Boolean (默认 false，归档后不在新增记录中显示)
2.  **`SexType` (行为类型)**
    *   `id`: String (UUID, PK)
    *   `name`: String
    *   `emoji`: String (预设图标)
    *   `isCustom`: Boolean
3.  **`Position` (体位)**
    *   `id`: String (UUID, PK)
    *   `name`: String
    *   `intensity`: String (Enum: VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH)
    *   `notes`: String?
4.  **`Toy` (情趣用品)**
    *   `id`: String (UUID, PK)
    *   `name`: String
    *   `notes`: String?
5.  **`Location` (地点)**
    *   `id`: String (UUID, PK)
    *   `name`: String
6.  **`Record` (活动记录 - 核心表)**
    *   `id`: String (UUID, PK)
    *   `timestamp`: Long (活动发生时间)
    *   `durationMinutes`: Int? (时长，单位分钟)
    *   `satisfaction`: Float? (1.0 - 5.0 星)
    *   `initiator`: String (Enum: ME, PARTNER, BOTH)
    *   `myOrgasmCount`: Int?
    *   `partnerOrgasmCount`: Int?
    *   `protectionUsed`: String? (Enum: YES, NO, UNKNOWN)
    *   `notes`: String?
    *   `photoPaths`: String? (逗号分隔的本地相对路径，如 `img_123.jpg,img_456.jpg`)

### 2.2 多对多关联表
*   `RecordPartnerCrossRef` (`recordId`, `partnerId`)
*   `RecordSexTypeCrossRef` (`recordId`, `sexTypeId`)
*   `RecordPositionCrossRef` (`recordId`, `positionId`)
*   `RecordToyCrossRef` (`recordId`, `toyId`)
*   `RecordLocationCrossRef` (`recordId`, `locationId`)

### 2.3 DAO 核心查询需求
*   **日历数据**：`SELECT timestamp, emoji FROM Record JOIN RecordSexTypeCrossRef ... WHERE timestamp BETWEEN :startOfMonth AND :endOfMonth` (用于渲染日历上的小图标)。
*   **天数计算**：`SELECT MAX(timestamp) FROM Record` (求距上次天数)。
*   **平均频率计算**：`(MAX(timestamp) - MIN(timestamp)) / COUNT(id)`。
*   **卡路里估算算法**：粗略计算为 `durationMinutes * 4.5` 大卡。
*   **统计查询**：必须使用 SQL 的 `GROUP BY` 和 `COUNT` 完成，例如 `SELECT positionId, COUNT(*) FROM RecordPositionCrossRef GROUP BY positionId`。

---

## 3. UI/UX 规范与 Compose 组件拆解

### 3.1 全局主题 (Theme)
*   **颜色**: 强制深色主题。背景 `#121212`，卡片背景 `#1E1E1E`，主色调（强调色）`#85B898`（低饱和度绿色）。
*   **文字**: 主文本纯白，次要文本 `#A0A0A0`。
*   **形状**: 所有卡片圆角 `16.dp`。

### 3.2 页面组件清单 (Routes)
1.  **`CalendarScreen` (日历)**
    *   **顶部**: `RhythmCard` (展示距上次天数、上次记录、平均间隔、最长间隔)。
    *   **中部**: `MonthCalendar` (自定义 Compose 日历，选中日期高亮，有记录的日期下方显示自定义 Emoji)。
    *   **底部**: 选中日期的 `RecordList` 或显示“这天没有记录”。
    *   **悬浮按钮 (FAB)**: `+` 号，跳转新增记录页。
2.  **`HistoryScreen` (历史)**
    *   **Tab**: [活动] / [相簿]
    *   **活动列表**: 按日期分组的 `LazyColumn`，每个 Item 是一个卡片，显示：时间、使用的体位、类型、地点、时长、卡路里。
    *   **相簿列表**: `LazyVerticalGrid`，网格展示所有包含照片的记录。
3.  **`StatisticsScreen` (统计)**
    *   **Tabs**: 总览、性爱类型、地点、姿势、情趣用品、主动方、活动、高潮、时长。
    *   **总览**: 网格布局的 `StatCard` (活动次数、伴侣人数、自慰次数、平均满意度、平均时长、总时长、卡路里、防护措施率、高潮次数)。
    *   **图表**: 柱状图（星期分布）、饼图（主动方占比）。
    *   **列表**: 带有进度条的排行（例如：正面女上位 5次 (83%)）。
4.  **`PartnerScreen` (伴侣)**
    *   列表展示伴侣卡片（头像、名字、关系）。
    *   点击进入详情：伴侣的基础信息 + 仅关于该伴侣的统计图表。
5.  **`MoreScreen` (更多/设置)**
    *   入口：姿势、地点、性爱类型、情趣用品（字典管理）。
    *   入口：WebDAV 设置、应用锁设置、简化输入设置。

### 3.3 新增/编辑记录页 (AddRecordScreen)
*   **UI 形态**: 推荐使用全屏页面或 `ModalBottomSheet`。
*   **表单字段交互**:
    *   `伴侣`: FlowRow 多选 Chip。
    *   `主动方`: 下拉单选。
    *   `时长`: 预设 Chips (10, 30, 45, 60, 120) + 步进器 (`-` `+`)。
    *   `满意度`: 5 个星标 Clickable。
    *   `类型/地点/姿势`: FlowRow 多选 Chip。
    *   `高潮/防护措施`: 步进器 / 下拉。
    *   `照片`: 唤起 `PhotoPicker`，选择后保存至 `context.filesDir/images/`，数据库存相对路径。
    *   `备注`: `OutlinedTextField`。
*   **顶部**: 记录图标选择（点击弹出 Emoji 选择器）、保存按钮。
*   **底部**: “储存为范本”开关、“简化输入”开关。

---

## 4. WebDAV 同步机制详细设计 (核心难点)

Agent 需要实现以下完整的同步流程，确保数据安全且不冲突。

### 4.1 同步策略
*   **手动同步**：设置页提供“立即同步”按钮。
*   **自动同步**：使用 `WorkManager`，在 App 退到后台且有网络时触发（每天最多一次）。

### 4.2 文件结构 (上传至 WebDAV)
```text
/IntimateTracker/
├── backup.json (包含所有 Room 数据表的 JSON 导出)
└── photos/
    ├── img_uuid1.jpg
    └── img_uuid2.jpg
```

### 4.3 同步算法逻辑 (Agent 伪代码)
```kotlin
suspend fun performSync(webDavClient: Sardine, localRepo: RecordRepository) {
    // 1. 检查远端目录是否存在，不存在则创建
    if (!webDavClient.exists("/IntimateTracker")) webDavClient.createDirectory("/IntimateTracker")

    // 2. 获取远端 backup.json 的 Last-Modified 时间戳
    val remoteFile = webDavClient.list("/IntimateTracker/backup.json").firstOrNull()
    val remoteTime = remoteFile?.modified?.time ?: 0L
    val localTime = localRepo.getLatestUpdateTime() // 取所有表 MAX(updatedAt)

    if (remoteTime == 0L) {
        // 远端为空，直接上传
        uploadLocalToRemote()
    } else if (localTime > remoteTime) {
        // 本地较新，执行上传覆盖 (需给用户提示)
        uploadLocalToRemote()
    } else if (remoteTime > localTime) {
        // 远端较新，执行下载覆盖 (需给用户提示)
        downloadRemoteToLocal()
    } else {
        // 时间一致，无需同步
    }
}

// 上传逻辑
fun uploadLocalToRemote() {
    // 1. 将 Room 数据库的数据转换为 Gson/Json 字符串
    // 2. PUT /IntimateTracker/backup.json
    // 3. 遍历本地 images 目录，比较远端 list 中缺少的文件，逐个 PUT 上传
}

// 下载逻辑
fun downloadRemoteToLocal() {
    // 1. GET /IntimateTracker/backup.json，解析并清空本地 Room 表后重新插入
    // 2. 获取远端 photos 目录列表，与本地比对，下载本地缺失的图片
}
```

---

## 5. 给 AI Agent 的按步执行指南 (直接复制使用)

请使用 **Cursor Composer (Cmd+I / Ctrl+I)**，逐段输入以下 Prompt 进行构建：

### 阶段一：项目脚手架与基础配置
> "创建 Android 项目，包名 `com.mysecret.intimatetracker`。配置 Gradle 依赖：Compose, Room, Hilt, Vico, Sardine-Android, Biometric, Coil。配置深色主题，背景色 #121212，主色 #85B898。实现包含 5 个 Tab 的底部导航栏（日历、历史、统计、伴侣、更多），使用 Navigation Compose，并为每个 Tab 创建基础 Empty Screen。"

### 阶段二：数据层 (Room + Repository)
> "根据 PRD 中的[2. 数据库设计]，创建所有的 Room Entity 和交叉引用表。为每个 Entity 编写 DAO 接口，包含基础 CRUD 方法。创建 `AppDatabase`。使用 Hilt 提供依赖注入。实现 `RecordRepository`，包含以下方法：插入记录并绑定多对多关系、按日期查询记录、获取最新记录的日期、获取总记录数。"

### 阶段三：资料库管理界面 (CRUD 界面)
> "使用 Compose 实现伴侣管理 (`PartnerScreen`)、体位管理、地点管理、情趣用品管理的界面。要求列表页使用 `LazyColumn` 展示。点击 FAB 弹出 `AlertDialog` 或 `ModalBottomSheet` 输入名称、备注等字段。伴侣需支持上传头像（使用 Coil 加载本地路径），并支持归档操作。"

### 阶段四：核心日历与记录添加
> "实现 `CalendarScreen`。顶部实现一个 '节奏卡片'，使用 SQL 查询计算：距上次记录天数 `(currentTime - maxTime) / 86400000`，以及平均间隔。日历使用 Compose 的 `HorizontalPager` 实现按月滑动，在日期下方渲染记录中绑定的 Emoji。点击日期下方弹出 FAB 触发新增记录。
> 实现 `AddRecordScreen`，包含 PRD 中[3.3]描述的所有字段。注意：伴侣、类型、体位、地点均为 FlowRow 多选 Chip。支持调用系统相册选择多张图片，将图片拷贝到 `context.filesDir/images/` 并保存路径到数据库。"

### 阶段五：统计视图与图表 (Vico)
> "实现 `StatisticsScreen`。使用 SQL 聚合查询计算各项统计数据。顶部使用 Grid 布局展示 StatCard（总次数、平均时长、总卡路里等）。使用 Vico Compose 库绘制 '星期分布柱状图'、'主动方饼图'。实现按姿势、地点、类型排名的列表，使用 `LinearProgressIndicator` 表示百分比。支持顶部 Tab 切换查看不同的统计指标。"

### 阶段六：WebDAV 同步与安全锁 (最终难点)
> "引入 Sardine-Android。在设置页添加 WebDAV 配置表单（URL, 账号, 密码），提供 '测试连接' 按钮。
> 编写 `SyncManager` 类，实现 PRD 中[4.3]的同步算法：将 Room 数据序列化为 JSON，遍历上传本地图片。拉取时解析 JSON 覆盖本地数据库。
> 引入 Biometric 库。在 `MainActivity` 中添加 `BiometricPrompt`，要求每次冷启动或从后台唤醒超过 5 分钟时，必须验证指纹/面容才能进入 App。"

### 阶段七：细节优化与安全检查
> "审查所有代码：1. 确保所有文件读写和网络请求都在 `Dispatchers.IO` 下执行。2. 检查照片加载是否使用 Coil 且带有缓存策略。3. 检查数据库多对多关联关系的插入逻辑是否会导致外键冲突。4. 增加数据导出功能（将数据库导出为 CSV 保存到手机 Download 目录）。"