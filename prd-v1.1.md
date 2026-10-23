# Intimate Tracker PRD v1.1（修订版）

> 基于 `prd.md`（v0）+ 2026-10-21 的三项决策。
> 本版目标：**让 Agent 可以不再猜测地逐段落地**。凡 v0 没写或算法算不通的地方，本版都给了确定答案。

## 0. 本次决策（变更摘要）

| # | 你的决策 | 本版落法 | 影响面 |
|---|---|---|---|
| D-1 | 更多页只保留字典与设置，**不引入账号体系与任何增值模块** | 移入 §1"Out 范围"，更多页重排（§5.7） | 无账号、无后端 |
| D-2 | **卡路里统计功能删除** | `Record` 不存卡路里；历史卡片不显示；总览卡 **12 → 11 张**；不做任何卡路里系数/MET 配置 | 删 1 个字段语义 + 1 张卡 + 1 条公式；同时消掉第五节"反证 1" |
| D-3 | 同步**走 v2** | OkHttp 自写 WebDAV + 整库 zip 快照 + ETag + 原子替换 + 逐行 LWW + 墓碑 + 可选加密（§7） | 删除 Sardine 依赖；同步从"会丢数据"改为"可验证不丢" |
| D-4 | **默认语言 = 简体中文** | `values/` 即简体（基准文案），另出 `values-en`；繁中降为可选、默认不打包 | 全站文案基准从繁中改为简中（§9） |

---

## 1. 范围（In / Out）

| 功能 | 状态 | 说明 |
|---|---|---|
| 行事历（节奏卡 + 月历 + 当日列表 + 筛选） | ✅ In | §5.1 |
| 新增/编辑记录（14 个可配字段） | ✅ In | §5.2 |
| 历史（活动 / 相册） | ✅ In | §5.3 |
| 统计（9 页签，无卡路里） | ✅ In | §5.4 |
| 伴侣（列表 + 详情 + 统计） | ✅ In | §5.5 |
| 字典管理（类型/地点/姿势/情趣用品，内置可隐藏） | ✅ In | §5.6 |
| 更多：WebDAV 同步、应用锁、简化输入、导出、清空数据、关于 | ✅ In | §5.7 |
| 账号体系 / 云同步 | ❌ Out | 只做 WebDAV 文件同步 |
| 社交 / 商店 / 评分 | ❌ Out | |
| **卡路里（任何形式）** | ❌ Out | 不存、不算、不显示、不做开关 |
| 多语言 | ✅ In（**zh-CN 默认/基准** + en；zh-TW 可选，默认不打包） | §9 |

---

## 2. 技术栈（锁定版本，均已实测）

```toml
# gradle/libs.versions.toml
[versions]
agp = "9.2.1"
kotlin = "2.2.10"          # AGP 9 内建 Kotlin，勿再加 kotlin-android 插件
ksp = "2.2.10-2.0.2"
composeBom = "2025.12.00"  # 2026.09 需要 Kotlin 2.4 元数据，不可用
room = "2.8.5"
hilt = "2.60.1"
hiltExt = "1.3.0"
navigation = "2.9.6"       # 2.10.x 要求 compileSdk 37 的更多库，先保守
work = "2.12.0"
biometric = "1.1.0"
coil = "3.3.0"             # io.coil-kt.coil3
vico = "3.2.3"             # compileSdk 36 世代
okhttp = "4.12.0"
kotlinxSerialization = "1.9.0"
```

```properties
# gradle.properties
android.disallowKotlinSourceSets=false
android.useAndroidX=true
android.nonTransitiveRClass=true
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
```

```kotlin
// app/build.gradle.kts 关键段
android {
    compileSdk { version = release(37) }   // AGP 自动下载 android-37.0
    defaultConfig { minSdk = 26; targetSdk = 36 }
    buildFeatures { compose = true }
}
```

**不引入**：Sardine / Gson（用 kotlinx.serialization）/ 任何账号 SDK。

**包结构**（单模块）：
```
com.mysecret.intimatetracker
├── data
│   ├── db           (AppDatabase, entity, dao, converter, migration, seed)
│   ├── repo         (RecordRepository, DictionaryRepository, PartnerRepository, MediaRepository)
│   └── sync         (WebDavClient, SyncManager, BackupArchive, ConflictMerger, CryptoBox)
├── domain           (model, StatsCalculator, StatsSpec, RhythmCalculator)
├── ui
│   ├── theme        (深色主题、色板、形状)
│   ├── calendar / record / history / stats / partner / more / lock
│   └── component    (ChipGroup, Stepper, StarRating, EmojiPicker, EmptyState, StatCard)
└── di               (Hilt modules)
```

---

## 3. 数据模型 v1.1（完整 DDL）

约定：`createdAt` / `updatedAt` / `deletedAt` 均为 **Long（epoch millis）**；`deletedAt != null` 表示墓碑（软删），**所有查询默认过滤 `deletedAt IS NULL`**。

### 3.1 主表

```sql
CREATE TABLE partner (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  gender TEXT NOT NULL DEFAULT 'UNKNOWN',        -- MALE | FEMALE | OTHER | UNKNOWN
  relationship_type TEXT NOT NULL DEFAULT 'OTHER',-- CASUAL | SERIOUS | MARRIED | OTHER
  avatar_path TEXT,
  notes TEXT,
  is_archived INTEGER NOT NULL DEFAULT 0,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER
);

CREATE TABLE sex_type (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  emoji TEXT NOT NULL DEFAULT '❤️',
  is_builtin INTEGER NOT NULL DEFAULT 0,
  is_hidden INTEGER NOT NULL DEFAULT 0,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER
);

CREATE TABLE position (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  intensity TEXT NOT NULL DEFAULT 'MEDIUM',      -- VERY_LOW|LOW|MEDIUM|HIGH|VERY_HIGH
  notes TEXT,
  is_builtin INTEGER NOT NULL DEFAULT 0,
  is_hidden INTEGER NOT NULL DEFAULT 0,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER
);

CREATE TABLE toy (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  notes TEXT,
  is_builtin INTEGER NOT NULL DEFAULT 0,
  is_hidden INTEGER NOT NULL DEFAULT 0,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER
);

CREATE TABLE location (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  is_builtin INTEGER NOT NULL DEFAULT 0,
  is_hidden INTEGER NOT NULL DEFAULT 0,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER
);
```

### 3.2 记录表（核心）

```sql
CREATE TABLE record (
  id TEXT PRIMARY KEY NOT NULL,
  timestamp INTEGER NOT NULL,              -- 活动发生时间
  icon TEXT,                               -- 新增：记录级 emoji（日历/历史用），NULL 时回落为类型 emoji
  duration_minutes INTEGER,                -- NULL = 未填（统计分母靠这个区分）
  satisfaction INTEGER,                    -- 1..5，NULL = 未填
  initiator TEXT,                          -- ME | PARTNER | BOTH，NULL = 未填
  protection_used TEXT,                    -- YES | NO | UNKNOWN，NULL = 未填
  my_orgasm_count INTEGER NOT NULL DEFAULT 0,
  partner_orgasm_count INTEGER NOT NULL DEFAULT 0,
  is_solo INTEGER NOT NULL DEFAULT 0,      -- 自慰/单人：无伴侣时置 1
  notes TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER
);
CREATE INDEX idx_record_timestamp ON record(timestamp DESC);
CREATE INDEX idx_record_updated ON record(updated_at);
```

> **字段必须有 NULL 语义**：`duration_minutes` / `satisfaction` / `initiator` / `protection_used` 的 NULL 表示"用户没填"，统计时**不进分母**（见 §6）。v0 用 `Int?` 但没定义口径，这里定死。

### 3.3 关联表（全部带外键 + 索引）

```sql
CREATE TABLE record_partner_cross_ref (
  record_id TEXT NOT NULL,
  partner_id TEXT NOT NULL,
  partner_orgasm_count INTEGER,           -- 该伴侣本次的高潮次数（解决多伴侣语义）
  PRIMARY KEY (record_id, partner_id),
  FOREIGN KEY (record_id) REFERENCES record(id) ON DELETE CASCADE,
  FOREIGN KEY (partner_id) REFERENCES partner(id) ON DELETE CASCADE
);
CREATE INDEX idx_rp_partner ON record_partner_cross_ref(partner_id);

-- record_sex_type_cross_ref / record_position_cross_ref /
-- record_toy_cross_ref / record_location_cross_ref 结构同上（无额外列）
```

### 3.4 照片 / 模板 / 设置 / 同步

```sql
CREATE TABLE photo (
  id TEXT PRIMARY KEY NOT NULL,
  record_id TEXT NOT NULL,
  rel_path TEXT NOT NULL,                 -- 相对 filesDir：images/2026/10/<uuid>.jpg
  width INTEGER, height INTEGER, size_bytes INTEGER,
  created_at INTEGER NOT NULL,
  deleted_at INTEGER,
  FOREIGN KEY (record_id) REFERENCES record(id) ON DELETE CASCADE
);
CREATE INDEX idx_photo_record ON photo(record_id);

CREATE TABLE template (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  payload_json TEXT NOT NULL,             -- 简化版记录快照（字段 id 列表 + 数值）
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER
);

CREATE TABLE settings (                    -- 键值对，见 §8.3 键表
  key TEXT PRIMARY KEY NOT NULL,
  value TEXT NOT NULL
);

CREATE TABLE sync_meta (                   -- 单行
  id INTEGER PRIMARY KEY CHECK (id = 1),
  device_id TEXT NOT NULL,
  remote_etag TEXT,
  last_sync_at INTEGER,
  last_sync_result TEXT
);

CREATE TABLE tombstone (
  table_name TEXT NOT NULL,
  row_id TEXT NOT NULL,
  deleted_at INTEGER NOT NULL,
  PRIMARY KEY (table_name, row_id)
);
```

### 3.5 建表约束清单（验收用）

| 约定 | 检查方式 |
|---|---|
| 所有表有 `created_at`/`updated_at`（除 settings/sync_meta/tombstone） | `MigrationTestHelper` |
| 交叉表 `ON DELETE CASCADE` 双向 | 删 record/partner 后交叉表计数为 0 |
| 所有查询过滤 `deleted_at IS NULL` | DAO 单测逐条断言 |
| 种子数据首启写入且 `is_builtin=1` | 首启单测断言 11/23/6 |
| `updated_at` 在任意写操作后更新 | Repository 单测 |

---

## 4. 种子数据（内置字典，首启写入）

### 4.1 性爱类型（11 项，`is_builtin=1`）

| # | name | emoji |
|---|---|---|
| 1 | 阴道性交 | ❤️ |
| 2 | 口交 | 👄 |
| 3 | 手交 | 🖐️ |
| 4 | 自慰 | 💦 |
| 5 | 指交 | 👆 |
| 6 | 情趣用品 | 🍆 |
| 7 | 肛交 | 🍑 |
| 8 | 多人性爱 | 👥 |
| 9 | 主动 | 🔥 |
| 10 | 被动 | 🌙 |
| 11 | BDSM | ⛓️ |

### 4.2 地点（23 项，`is_builtin=1`）

卧室、厨房、淋浴间、厕所、客厅、车库、后院、屋顶、按摩浴缸、泳池、海滩、家、饭店、酒吧、电影院、剧院、学校、博物馆、车上、飞机、火车、船上、公共场所

（"沙发""窗前"一类属用户自建项，不预置。）

### 4.3 姿势（6 项）

| name | intensity | 显示文案 |
|---|---|---|
| 正面女上位 | VERY_LOW | 强度：非常低 |
| 背身女上位 | VERY_LOW | 强度：非常低 |
| 传教士 | MEDIUM | 强度：中 |
| 跪姿后入 | MEDIUM | 强度：中 |
| 趴着后入 | MEDIUM | 强度：中 |
| 站姿后入 | MEDIUM | 强度：中 |

### 4.4 枚举映射（存 DB 值 ↔ 显示文案）

| 枚举 | DB 值 | 简体文案（默认） | 未填时显示 |
|---|---|---|---|
| gender | MALE / FEMALE / OTHER / UNKNOWN | 男 / 女 / 其他 / 未设置 | 未设置 |
| relationship | CASUAL / SERIOUS / MARRIED / OTHER | 随性关系 / 认真交往 / 已婚 / 其他 | 其他 |
| initiator | ME / PARTNER / BOTH | 我 / 伴侣 / 双方 | 未设置 |
| protection | YES / NO / UNKNOWN | 有 / 没有 / 不确定 | 未设置 |
| intensity | VERY_LOW / LOW / MEDIUM / HIGH / VERY_HIGH | 非常低 / 低 / 中 / 高 / 非常高 | — |

---

## 5. 界面规格

> 通用：深色主题 `#121212` 背景、`#1E1E1E` 卡片、`#85B898` 强调色、`#A0A0A0` 次要文字、卡片圆角 16dp。所有列表有加载态/空态/错误态。

### 5.1 行事历 `CalendarScreen`

```
Scaffold
├── RhythmCard                     // 顶部卡片
│   ├── 左：环形进度（2 天 / 距上次），中心大数字 = 距上次天数
│   ├── 右上：上次记录 10月19日     // 无记录时显示 "—"
│   ├── 右上：通常 每4-5天          // 中位数区间，样本 <3 时显示 "—"
│   ├── 分隔线
│   ├── 最长间隔 5天  ⓘ            // ⓘ → 底部抽屉（同下）
│   └── 点击卡片 → ModalBottomSheet「你的节奏」
│       ├── 距上次天数 / 最长间隔 / 通常 三行
│       ├── 星期活动分布（7 根迷你柱 + 星期标签）
│       └── [自定义] 按钮 → 简化输入设置页
├── MonthSelector                   // "2026年10月 ▾"（点开年月滚轮）+ [筛选] 按钮
├── MonthCalendar                   // 自绘 7 列网格，HorizontalPager 左右翻月
│   └── DayCell: 数字 + 当日 emoji（多条记录取第一条的 icon，右下角 dot 标记多条）
├── SelectedDayHeader               // "10月21日星期三" + "0条记录"
├── RecordList or EmptyState        // "这天没有记录"
└── FAB "+"                         // → AddRecordScreen(date = 选中日)
```

- 选中日默认今天；切月保留选中日（不存在则回落到 1 号）。
- 日历 emoji：`record.icon`，为空时取第一条关联 `sex_type.emoji`，再为空不显示。
- 筛选：日期范围 + 伴侣 + 类型；筛选态在标题下显示 chip，可一键清除。

### 5.2 新增/编辑记录 `AddRecordScreen`

- 形态：全屏（`NavHost` 一级页），顶部 `[×] 新增记录 [保存]`，副标题为日期（点击弹月历选择器）。
- 字段顺序：**记录图标 → 照片 → 伴侣 → 主动方 → 时长 → 满意度 → 类型 → 情趣用品 → 地点 → 姿势 → 防护措施 → 你的高潮 → 伴侣的高潮 → 备注**，底部：保存为模板开关 + 简化输入入口。
- 简化输入：若某字段被关闭（§8.3 `field.<key>.visible=false`），**不渲染该卡片**；保存时保留其旧值（编辑态）或 NULL（新建）。至少保留 2 个字段可见（设置页校验）。

控件规范：

| 字段 | 组件 | 规则 |
|---|---|---|
| 记录图标 | `EmojiPickerDialog` | 5 分类 63 个（基本 6 / 行为 14 / 地点 14 / 情感 12 / 趣味 17），含「重设」「保存」；默认取主类型 emoji |
| 照片 | `PhotoPicker`（系统多选）→ 复制到 `filesDir/images/yyyy/MM/<uuid>.jpg` | 单张压到长边 ≤2048、质量 85；写入 `photo` 表 |
| 伴侣 | `FlowRow` 多选 Chip（含"无/单人"项） | 选"无/单人"时 `is_solo=1` 且清空伴侣 |
| 主动方 | 下拉 | 我 / 伴侣 / 双方 / 未设置 |
| 时长 | 5 个预设 Chip(10/30/45/60/120) + `−` `+` 步进（步长 5，0–600） | 点 Chip 覆盖；`− +` 从当前值增减；未设置为 NULL |
| 满意度 | 5 个星标 | 点第 n 颗 = n；再点同颗清零 |
| 类型/情趣用品/地点/姿势 | `FlowRow` 多选 Chip（多选生效即写交叉表） | 类型至少 1 项？**否**，允许空（空时 icon 用记录级 emoji） |
| 防护措施 | 下拉 | 有 / 没有 / 不确定 / 未设置 |
| 你的高潮 / 伴侣的高潮 | 步进器 | 0–20，默认 0 |
| 备注 | `OutlinedTextField`（多行，max 2000 字） | 占位「想记下什么…」 |
| 保存为模板 | Switch | 打开时保存后写入 `template` |
| 简化输入 | 行内跳转 | 进设置页 |

保存校验：`timestamp` 必须有值；其余全可空。保存成功后回到来源页并刷新。

### 5.3 历史 `HistoryScreen`

```
Scaffold
├── TopAppBar「历史」 + 筛选 icon
├── TabRow [活动] [相册]
├── 活动：LazyColumn
│   ├── 月份头 "2026年10月"
│   ├── 日期头 "10月19日星期一"
│   └── RecordCard：圆形图标(记录 icon) + 类型 emoji + 标题(类型名/自定义) + 副行 + ">"
│       副行规则：拼接非空项「{时长}分钟 · {地点} · {姿势}」，卡路里已删除
└── 相册：LazyVerticalGrid(3 列)，按月份分组头，缩略图点击进大图预览（左右滑动 + 删除）
```

> 卡片上"两个图标"的语义定死：**左圆底 = 记录图标（`record.icon`）**，**右 emoji = 主类型 emoji**；标题 = 主类型名（多类型时取第一个，空则"未分类"）。

### 5.4 统计 `StatisticsScreen`

```
TopBar：ⓘ（口径说明）+「统计」+ 刷新
TabRow（可滚动，9 项）：总览 / 性爱类型 / 地点 / 姿势 / 情趣用品 / 主动方 / 活动 / 高潮 / 时长
筛选行：期间[全部时间 ▾] 伴侣[所有伴侣 ▾]
内容：按 tab 切换
```

| Tab | 内容 |
|---|---|
| 总览 | **11 张 StatCard**（2 列网格）：活动、伴侣人数、自慰、平均满意度、平均时长、总时长、防护措施、高潮、高潮（伴侣）、平均高潮次数、平均高潮次数（伴侣）+ 「各伴侣的活动」排行 + 「星期活动分布」柱图 |
| 性爱类型 / 地点 / 姿势 / 情趣用品 | 头部「{N}条记录中，有{M}条填了这项」+ 免责说明 + 排行条（名称 / 次数 / 百分比） |
| 主动方 | 环形图（我/伴侣/双方）+ 图例（次数 + 百分比） |
| 活动 / 高潮 / 时长 | 月度柱状图，顶部「总计：{N}」，Y 轴 5 档虚线网格 |

空态：该 tab 的分子为 0 → 居中「数据还不够」。

### 5.5 伴侣 `PartnerScreen` / `PartnerDetailScreen`

- 列表：`LazyColumn`，卡片 = 首字圆形头像（无头像时取 name 首字）+ 名字 + 关系文案 + `>`；FAB `+`。
- 详情：头像、名称、性别、关系、备注（可编辑）；下方「与 TA 的统计」：活动次数、平均满意度、平均时长、类型排行（Top5）+ 最近 5 条记录。
- 归档：详情右上菜单「封存」→ `is_archived=1`；归档后不出现在新增记录的伴侣 Chip 中，但历史记录仍显示。

### 5.6 字典管理（4 个同类页面）

结构：`你的{类型}`（用户自建，可删除 🗑）+ `内建`（点击即切换 `is_hidden`，显示 `−`，不删数据）+ FAB `+` 新建。

- 新建弹窗：名称（必填，≤20 字）、备注（姿势/用具）；姿势额外要选强度。
- 删除自建项：若已被记录引用 → 二次确认「将从 {N} 条记录中移除这个标签」→ 软删 + 解除关联。
- 内置项不可删除，只能隐藏；隐藏后不出现在新增记录，但历史照常显示。

### 5.7 更多 `MoreScreen`（按 D-1 重排）

```
更多
├── 浏览：姿势 / 地点 / 性爱类型 / 情趣用品
├── App：偏好设置 / 简化输入 / 应用锁 / WebDAV 同步 / 数据汇出 / 清空所有数据
├── 关于：版本 / 隐私政策 / 使用条款
```
**没有**：账号、登出、社交、商店、评分。

### 5.8 简化输入设置

14 个开关（记录图标、照片、伴侣、主动方、时长、满意度、类型、情趣用品、地点、姿势、防护措施、你的高潮、伴侣的高潮、备注），每项右侧「自定义 >」进入字典/样式设置；关闭开关时若已开启数 ≤2，弹出提示「至少要保留 2 个栅位」并回滚。

---

## 6. 统计口径 v1.1（无卡路里）

**总原则**：分母 = **该字段已填写的记录数**（不是总记录数）。所有百分比按此计算。

### 6.1 总览 11 张卡

| 卡片 | 公式 | 分母 |
|---|---|---|
| 活动 | `COUNT(record)` | — |
| 伴侣人数 | `COUNT(DISTINCT partner_id)`（排除已归档/已删） | — |
| 自慰 | `COUNT(record WHERE is_solo=1)` | 全部记录（百分比用） |
| 平均满意度 | `AVG(satisfaction)` | `satisfaction IS NOT NULL` |
| 平均时长 | `AVG(duration_minutes)` | `duration_minutes IS NOT NULL` |
| 总时长 | `SUM(duration_minutes)` | — |
| 防护措施 | `SUM(protection_used='YES') / COUNT(*)` | `protection_used IS NOT NULL` 且**有伴侣**（`is_solo=0`） |
| 高潮 | `SUM(my_orgasm_count)` | — |
| 高潮（伴侣） | `SUM(partner_orgasm_count)` | — |
| 平均高潮次数 | `SUM(my_orgasm_count) / COUNT(record)` | 全部记录 |
| 平均高潮次数（伴侣） | `SUM(partner_orgasm_count) / COUNT(record WHERE is_solo=0)` | 有伴侣的记录 |

### 6.2 各 tab 百分比分母

| tab | 分母 |
|---|---|
| 性爱类型 | 全部记录 |
| 地点 | `EXISTS(location 关联)` 的记录数 |
| 姿势 | `EXISTS(position 关联)` 的记录数 |
| 情趣用品 | `EXISTS(toy 关联)` 的记录数 |
| 主动方 | `initiator IS NOT NULL` 的记录数 |

### 6.3 节奏卡

```kotlin
距上次天数 = (today0点 · 00:00 - lastRecordDate 00:00) / 86400000
最长间隔   = 相邻记录日期间隔的最大值（同一天多条算 0）
通常       = 相邻间隔的中位数；显示为中位数 ±1 的区间（如 "每4-5天"）；样本 <3 显示 "—"
```

### 6.4 固定口径常量

```kotlin
object StatsSpec {
    enum class Denominator { ALL, FIELD_FILLED, PARTNER_ONLY }
    const val EMPTY_THRESHOLD = 1        // 分子为 0 → 显示「数据还不够」
    const val MIN_SAMPLES_FOR_RHYTHM = 3 // 节奏卡样本下限
}
```

### 6.5 黄金用例（必须写成单测）

Fixture = 17 条固定记录，断言：

| 断言项 | 期望 |
|---|---|
| 活动 / 伴侣人数 / 自慰 | 17 / 4 / 10 |
| 平均满意度 / 平均时长 / 总时长 | 4.3 / 15 / 215 |
| 防护措施 | 100% |
| 高潮 / 高潮（伴侣） | 20 / 10 |
| 平均高潮 / 平均高潮（伴侣） | 1.2 / 1.7 |
| 类型百分比 | 59/41/35/12/6/6/6 |
| 地点百分比 | 67/47/7/7/7/7 |
| 姿势百分比 | 83/50/33/17 |
| 主动方 | 86% / 14% |
| 星期分布 | 4,0,2,1,3,4,3 |

---

## 7. 同步 v2 规格（D-3）

### 7.1 先用大白话说清楚（为什么不用 v0）

| | v0（原 PRD） | v2（本版） |
|---|---|---|
| 怎么判断谁新 | 比服务器文件时间戳 | 比**内容指纹**（ETag），时间不可靠 |
| 冲突怎么办 | 谁新谁把对方整本覆盖 | 一条一条比修改时间，**各留各的新版** |
| 删除怎么办 | 不知道对方删了什么 | 删掉的记一块"墓碑"，不会被重新上传回来 |
| 网络断了怎么办 | 直接清空本地表再写 | 先下载到临时文件，**校验通过才替换**，失败等于没发生 |
| 安全 | 明文 JSON + 明文照片 | 可选整包加密（口令） |

一句话：**v0 是"谁晚谁赢、输家全没"，v2 是"逐条合并、断网不伤库、删除不复活"。**

### 7.2 远端结构

```
/IntimateTracker/
├── backup.zip           # 或 backup.zip.enc（启用加密时）
└── backup.zip.prev      # 上一次成功版本（回滚用）
```

`backup.zip` 内容：

```
manifest.json    { schemaVersion, appVersion, deviceId, exportedAt, rev, counts:{...}, dbSha256 }
tracker.db       # WAL checkpoint 后的副本
photos/…         # 与 rel_path 一致的图片
```

### 7.3 OkHttp WebDAV 客户端（替代 Sardine）

```kotlin
interface WebDavClient {
    suspend fun propfind(path: String): List<RemoteEntry>          // name, etag, size, isDirectory
    suspend fun mkcol(path: String): Boolean
    suspend fun get(path: String, sink: BufferedSink): String?     // 返回 etag
    suspend fun put(path: String, source: BufferedSource, ifMatch: String? = null): String? // 返回新 etag
    suspend fun move(from: String, to: String): Boolean
    suspend fun delete(path: String)
}
class RemoteEntry(val name: String, val etag: String?, val size: Long, val isDirectory: Boolean)
```

实现要点：`OkHttpClient` 超时 connect 15s / read 60s / write 60s；PROPFIND 用 `Depth: 1` + XML 解析（用 `XmlPullParser`，不引入 XML 库）；401/403 → `E_AUTH`；`ifMatch` 头用于条件 PUT 防并发覆盖。

### 7.4 同步状态机

```
IDLE
 └─(用户点同步/WorkManager)─> CHECKING ── 无配置/无网 ─> ERROR(E_CONFIG/E_NET)
        │
        ├─ remote.etag == sync_meta.remote_etag ─> NO_CHANGE
        ├─ remote 不存在 ─> PUSHING（全量上传）
        └─ remote 更新 ─> PULLING ─> MERGING ─> PUSHING ─> DONE
                                   │
                                   └─ 冲突时 → CONFLICT（若策略=保留双份）或自动 LWW
任意阶段异常 → ERROR(code, message)，**本地库不变**
```

### 7.5 合并算法（逐行 LWW + 墓碑）

```text
输入：localDb、remoteDb（已解压校验）、lastSyncAt
对每张同步表（partner, sex_type, position, toy, location, record, photo, template, 各 cross_ref）：
  for id in union(local.ids, remote.ids):
     l = local[id], r = remote[id]
     when {
       l == null && r == null -> skip
       l == null -> if (墓碑存在且 deletedAt >= r.updatedAt) 跳过 else 插入 r
       r == null -> if (l.deletedAt != null) 删除远端副本; else if (l.updatedAt > lastSyncAt) 保留并待上传
       l.deletedAt != null && r.deletedAt == null -> 删除 r（墓碑更晚则）
       r.deletedAt != null && l.deletedAt == null -> 比较 r.deletedAt 与 l.updatedAt：新则删 l，否则保留 l
       else -> 比较 updatedAt：新者胜；若 |差| < 1000ms 且内容不同 → 记录冲突（保留本地 + 远端另存为 conflict 副本）
     }
合并后：清理已同步的 tombstone（只保留最近 90 天）
```

冲突副本规则：`id = "<原id>-conflict-<deviceShort>-<epoch>"`，`notes` 前缀「⚠️ 同步冲突」；只在 record / partner 上启用。

### 7.6 上传顺序（避免悬空引用）

1. `checkpoint WAL` → 复制 DB 到 `cache/snapshot.db`
2. 生成 zip（DB + 需要的 photos + manifest）
3. `PUT backup.zip.tmp`（整包）→ 校验返回 ETag
4. `MOVE backup.zip → backup.zip.prev`（若存在）
5. `MOVE backup.zip.tmp → backup.zip`
6. 写 `sync_meta(remote_etag, last_sync_at)`

> 图片已包含在 zip 内，**不再单独 PUT 逐张图**，从根上消除"JSON 先到、图没到"的悬空引用。

### 7.7 加密（可选，默认关）

- 口令 → `PBKDF2WithHmacSHA256`（200k 次，16B 随机 salt）→ 256-bit key
- 文件体：`AES/GCM/NoPadding`，12B 随机 nonce，头部 8 字节 magic `ITB1` + salt + nonce
- 口令错误 → `E_AUTH`，**不覆盖本地**
- 口令只存内存，不落盘；忘记口令 = 无法恢复（UI 明示）

### 7.8 错误码与文案

| code | 场景 | 简体文案（默认） |
|---|---|---|
| `E_CONFIG` | 未配置 WebDAV | 「请先设置 WebDAV」 |
| `E_NET` | 网络失败 | 「网络连接失败，请稍后再试」 |
| `E_AUTH` | 401/403 或口令错 | 「账号或密码错误」/「备份口令错误」 |
| `E_SPACE` | 507 / 磁盘不足 | 「存储空间不足」 |
| `E_CORRUPT` | zip 校验失败 | 「备份文件损坏，未覆盖本机数据」 |
| `E_CONFLICT` | 无法自动合并 | 「有 N 条记录发生冲突，已保留两份」 |

### 7.9 同步测试用例（必须写成仪器/单测）

| # | 场景 | 期望 |
|---|---|---|
| T1 | 首次同步（远端空） | 上传成功，远端 2 个文件 |
| T2 | 双方无变化 | `NO_CHANGE`，不发 PUT |
| T3 | 本机加 1 条，云端旧 | 上传成功，云端记录 +1 |
| T4 | 云端加 1 条，本机无 | 下载成功，本地记录 +1 |
| T5 | 双方各加 1 条 | 合并后两边都是 19 条 |
| T6 | 本机删 1 条，云端还在 | 同步后云端也删（墓碑传播），不复活 |
| T7 | 上传中断（模拟 50% 失败） | 云端仍是旧包，本地不变 |
| T8 | 下载包损坏 | 报 `E_CORRUPT`，本地库 hash 不变 |
| T9 | 口令错误 | 报 `E_AUTH`，本地不变 |
| T10 | 同一条双方都改 | 保留两份，UI 显示冲突标记 |

### 7.10 自动同步

`WorkManager`：唯一任务名 `sync_auto`，约束 `NetworkType.CONNECTED`，周期 **24h**，退避指数 30s 起；开关在设置项 `sync.auto.enabled`（默认关）。通知文案中性：「备份完成」。

---

## 8. 安全 / 设置项 / 数据

### 8.1 应用锁

- 冷启动 + 后台超过 `lock.timeout.minutes`（默认 5）→ 弹 `BiometricPrompt`
- 认证组合：`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`（**必须带设备凭据兜底**，否则无指纹机型进不去）
- 失败 5 次 → 退到设备 PIN/图案（系统处理），App 不做自有密码
- `FLAG_SECURE` 开关（默认开），开时禁止截屏/最近任务预览

### 8.2 数据导出 / 清空

- 导出：`zip`（db + photos + manifest）→ `MediaStore.Downloads/IntimateTracker/`，文件名 `IntimateTracker-backup-yyyyMMdd-HHmm.zip`
- CSV 导出为可选（类型/地点/记录三张表），列名英文
- 清空：二次确认 + 输入「DELETE」→ 删除 DB + `filesDir/images` + 所有设置（保留 `device_id`）

### 8.3 settings 键表

| key | 默认 | 说明 |
|---|---|---|
| `lock.enabled` | true | 应用锁 |
| `lock.timeout.minutes` | 5 | 后台阈值 |
| `lock.secure_flag` | true | FLAG_SECURE |
| `form.field.<key>.visible` | true | 简化输入 14 项，key 见 §5.8 |
| `form.default.icon` | null | 新记录默认图标 |
| `webdav.url/user/password` | null | 密码进 `EncryptedSharedPreferences`，**不进 settings 表** |
| `sync.auto.enabled` | false | 自动同步 |
| `sync.encrypt.enabled` | false | 备份加密 |
| `locale` | follow_system | zh-CN / en / zh-TW（可选）/ follow_system |

---

## 9. 国际化（D-4：简体为基准）

- **基准语言 = 简体中文（zh-CN）**：所有文案先写中文，放在 `res/values/strings.xml`。
- 目录约定：

| 目录 | 内容 | 是否必出 |
|---|---|---|
| `res/values/strings.xml` | **简体中文**（默认回退语言） | 必出 |
| `res/values-en/strings.xml` | English | 必出 |
| `res/values-zh-rTW/strings.xml` | 繁体中文 | 可选，MVP 不打包 |

- 运行时语言：默认跟随系统；`settings.locale` 可选 `zh-CN` / `en` / `zh-TW` / `follow_system`；
  用 `AppCompatDelegate.setApplicationLocales` 生效，**不重建 Activity 时用 `recreate()` 兜底**。
- key 命名：`screen_calendar_title`、`stats_tab_overview`、`form_field_duration`、`empty_no_records`、`error_net`…
- 约 210 条字符串，全部集中在 `strings.xml`；**禁止硬编码**，也禁止在 Compose 里拼中文。
- 标点与用词规范（简体）：UI 正文统一用中文双引号“”，嵌套时用直角引号「」；数字与单位之间不加空格（如 `10分钟`、`5天`、`100%`）。
- 视觉走查以布局为准、文案以简体为准；需要繁体文案时临时切 `values-zh-rTW`。

---

## 10. 里程碑与验收

| Sprint | 交付 | 验收（可自动化） |
|---|---|---|
| S1 | 工程骨架 + 主题 + 5 tab 导航 + 数据层（13 表 + 种子 + DAO）+ 字典/伴侣 CRUD | 单测：种子 11/23/6、级联删除、软删过滤；截图：5 个页面可点通 |
| S2 | 日历 + 节奏卡 + 新增/编辑记录（含照片/模板/简化输入）+ 历史 | 单测：节奏卡算法；手动：加一条记录后日历出 emoji |
| S3 | 统计 9 tab + 双筛选 + 口径常量 + 图表 | 单测：§6.5 黄金用例全绿 |
| S4 | 同步 v2 + 应用锁 + 导出/清空 + i18n | 单测：§7.9 十条场景；手动：双模拟器对拍 |

**每个 Sprint 的 DoD**：编译通过 + 单测绿 + 目标页面截图 ≥3 张 + 无 `TODO()` 残留。

---

## 11. 剩余待确认（不阻塞 S1，但 S2 前要定）

| # | 问题 | 我的默认值（你不同意再改） |
|---|---|---|
| Q1 | 备份是否默认加密 | **默认关**，设置里可开 |
| Q2 | 冲突策略 | **逐行 LWW + 同条保留双份**（安全优先） |
| Q3 | 语言 | 已定：**简体中文为基准**，另出英文；繁中可选不打包 |
| Q4 | 相册大图是否允许左右滑 | 允许（ViewPager） |
| Q5 | 应用锁超时默认值 | 5 分钟 |

---

## 附：与 v0 的差异索引

| v0 章节 | v1.1 处理 |
|---|---|
| §2 数据库设计 | 重写为 13 表 + 外键/索引/软删（§3） |
| §2.3 卡路里估算 | **删除** |
| §3.3 记录表单 | 补齐 14 字段交互与简化输入联动（§5.2） |
| §4 WebDAV | 整章替换为 v2（§7） |
| 无 | 新增种子数据（§4）、统计口径（§6）、设置键表（§8.3）、i18n（§9） |
| 阶段七 CSV | 并入 §8.2，且不含卡路里列 |
