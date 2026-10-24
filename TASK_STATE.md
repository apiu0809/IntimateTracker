# TASK_STATE — Intimate Tracker

> 快照时间：2026-10-25 03:20（Asia/Shanghai）
> 用途：上下文告急时的交接文件；恢复工作请先读本文件，再读 `prd-v1.1.md`。

## 1. 任务目标

按 `prd-v1.1.md` 实现 Android App「亲密记录 / Intimate Tracker」（Kotlin + Compose + Room + Hilt）。
4 个 Sprint 均已于 2026-10-22 交付：**S1 骨架+数据层+字典/伴侣 → S2 记录表单+日历+历史 → S3 统计 9 页签 → S4 同步 v2+应用锁+导出**；
当前版本 **v1.7.0**（versionCode 53，数据库 v4），2026-10-25 已发布（tag `v1.7.0`）：真机 24129PN74C（Android 17）与模拟器各 82 项仪器测试、57 项 JVM 单测全绿（见 §2.9）。上一版 v1.6.0（versionCode 52）同日发布。

已确认的四项产品决策：
1. 范围裁剪：不做账号体系与增值模块，仅 WebDAV 文件同步
2. 删除卡路里（不存、不算、不显示；总览卡 12 → 11 张）
3. 同步走 v2（OkHttp 自写 WebDAV + 整库 zip + ETag + 原子替换 + 逐行 LWW + 墓碑 + 可选加密）
4. 只做简体中文，不做 i18n（`values/` 即全部文案；v1.6.0 已删除 `values-en`）

## 2. 已完成事项

- [x] 评审 `prd.md`（v0），产出环境实测/依赖核查/PRD 缺陷/里程碑结论
- [x] 实测工具链：JDK 21（Android Studio JBR）、Gradle 9.4.1、AGP 9.2.1、Kotlin 2.2.10、compileSdk 37
- [x] 产出 `prd-v1.1.md`：16 表 DDL、种子数据、统计口径表、WebDAV v2 全流程、错误码、测试用例
- [x] git 仓库初始化 + 远端 `https://github.com/apiu0809/intimate-tracker`（main 分支）
- [x] **S1 全部交付**（commit `c0fcd1e`，已 push）：
  - Gradle 骨架（`gradle/libs.versions.toml`、`gradlew`）、`gradle.properties`（含 `android.disallowKotlinSourceSets=false`、`org.gradle.java.home`）
  - 深色主题 + 5 tab 底部导航（简体）
  - 16 张表 Entity/DAO/AppDatabase（5 张关联表 v4 起只对 record 级联 + label 快照、软删、索引、schema 导出到 `app/schemas/`）
  - 种子数据 10 类型 / 23 地点 / 0 内置姿势（幂等）
  - 字典管理（内置可隐藏、自建可删、姿势带强度）
  - 伴侣列表 / 新增 / 编辑 / 归档 / 软删
  - 测试：JVM 8 项 + 仪器 7 项，全部通过
  - 真机截图 8 张（`evidence/s1/`）+ README 更新
- [x] 模拟器验证：安装、启动、5 个 tab、字典新增、伴侣新增均可操作（截图留档）

## 2.1 S2 进度（2026-10-22 更新）

- [x] 记录数据层：RecordRepository（事务写记录 + 5 张交叉表、替换式更新、
hythm() 节奏统计）
- [x] DAO 查询：observeListBetween（GROUP_CONCAT 聚合类型/地点/姿势名）、observeCalendar（emoji 回退）、llTimestamps
- [x] 新增记录 UI：日期选择、记录图标（63 emoji 五分类）、伴侣多选、主动方、时长（预设+步进）、满意度、类型/地点/姿势多选、防护措施、双向高潮、备注
- [x] 日历 FAB 入口 + 路由 
ecord/new
- [x] 测试：RecordRepositoryTest 5 项（交叉表写入、重复保存替换、emoji 回退、节奏、空库）
- [x] 记录编辑：卡片点击进入编辑页，预填全部字段与关联（commit 见 git log）
- [x] 照片：多选 → 压缩(最长边 2048/JPEG85) → filesDir/images/yyyy/MM → photo 表；表单横向预览；相册网格
- [x] 简化输入：14 开关 + 设置页 + 表单按开关隐藏（至少保留 2 项）
- [x] 模板：保存为模板（JSON payload）+ 顶栏模板列表套用
- [x] 日历：自绘月历 + 真实节奏卡 + 当日记录列表（commit 980eb1f）
- [x] 历史页：活动列表（月 → 日两级分组 + 卡片）、相册空态（待照片功能）
## 2.2 S2 完成情况（2026-10-22）

S2 已全部交付：记录表单（14 字段 + 照片 + 模板 + 简化输入联动）、编辑、日历与节奏卡、历史（活动/相册）、照片导入与相册网格。
仪器测试 21 项全绿；commit 见 git log（9ef956d 之后）。

## 2.3 S3 进度（2026-10-22）

- [x] StatsDao：18 个聚合查询（总数/伴侣数/时长/满意度/高潮/防护/类型/地点/姿势/玩具/主动方/星期/月度/伴侣排行），统一支持时间范围 + 伴侣过滤
- [x] StatsRepository：口径常量落地（分母 = 该字段填写数），输出 StatsSnapshot
- [x] 黄金用例 StatsGoldenTest 6 项：17 条固定 fixture（17/4/10/4.3/15/215/100%/20/10/1.2/1.4；类型 59/41/35；地点 67/47；姿势 83/50；主动方 86/14）
- [x] 统计 UI：9 页签、11 张 StatCard、排行条、柱状图、星期分布、期间/伴侣筛选（commit 见 git log）
- [x] 月度图（活动/高潮/时长）与星期分布渲染
- 说明：图表为 Compose 自绘（未引入 Vico），与 PRD 的库选型不同，功能等价
## 2.4 S4 进度（2026-10-22）

- [x] OkHttp WebDAV 客户端（PROPFIND/MKCOL/PUT/GET/MOVE/DELETE，Basic 认证，错误码映射）
- [x] 备份包 BackupArchive：manifest.json + tracker.db + photos/ 打包/解包
- [x] SyncManager v2：ETag 比对 → 拉取→逐行 LWW 合并（伴侣/4 字典/记录+5 类关联）→ 推整包 → 存 ETag
- [x] WebDavClientFactory 可注入（便于假实现测试）
- [x] 同步测试修复：根因是 
unBlocking 尾表达式返回 Boolean/Long 导致 JUnit 报 should be void，加 : Unit + ssertEquals(5_000L, …) 后通过；SyncFlowTest 3 项覆盖 首次上传 / ETag 短路 / 远端较新合并
- [x] WebDAV 设置页：URL/账号/密码/测试连接/立即同步/状态与上次同步时间（密码暂存 settings 明文，待加密）
- [x] SyncFlowTest 4 项全绿（2026-10-24 真机复跑确认；此前 initializationError 已随类重写消失），同步内核已有自动测试
- [x] 应用锁：AppLockGate + BiometricPrompt(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)，More 页开关，默认关闭
- [x] 导出：zip（db+photos）写入 MediaStore 下载/IntimateTracker
- [x] 清空数据：clearAllTables + 删除 images/sync 缓存（二次确认弹窗）
- [x] 安全加固：启用锁时加 FLAG_SECURE；后台超 5 分钟 onStart 时 recreate 重新上锁
- [x] ~~英文 values-en~~：v1.6.0 起不做 i18n，`values-en` 已删除
## 2.5 S4 完成情况（2026-10-22）

S4 已交付：WebDAV v2 同步（客户端/合并/设置页/测试）、应用锁（生物识别+设备凭据）、导出/清空、FLAG_SECURE、5 分钟重新上锁、密码加密存储（当时的英文资源已于 v1.6.0 移除）。
仪器测试 30 项全绿；构建通过（APK 约 20MB）。

## 2.6 真机复跑（2026-10-24 22:45，v1.5.9）

- 设备：**24129PN74C**（Xiaomi，Android 17，adb 序列 `f2c6e979`），真实机型（非模拟器）
- `.\gradlew.bat connectedDebugAndroidTest` → **38 项全绿**，`BUILD SUCCESSFUL in 12s`（用例执行 2.35s）
  - DatabaseTest 7 / PhotoRepositoryTest 3 / RecordRepositoryTest 6 / RhythmSettingsPersistenceTest 3 /
    SettingsAndTemplateTest 5 / StatsGoldenTest 6 / SyncFlowTest 4 / WebDavClientTest 4
  - 报告：`app/build/reports/androidTests/connected/debug/index.html`
- 修正 2 处**过期断言**（实现已演进、测试未跟，非产品缺陷）：
  - `DatabaseTest`：内置类型 **9 → 10**（与 JVM `SeedDataTest` 对齐，新增「乳交」），方法名改为 `seed_writes_10_types_23_locations_0_positions`
  - `SyncFlowTest.first_sync_uploads_archive`：改断言同步 v2 布局 `IntimateTracker/current.zip` + `IntimateTracker/history/backup-<yyyyMMdd>.zip`，原断言在找旧根目录 `backup-*.zip`
- 结论：`SyncFlowTest` 的 initializationError 已消失（4/4 通过），§2.4 遗留条目已勾掉
- 真机环境坑（复跑前必看）：
  1. **MIUI 每次 ADB 安装弹「USB 安装提示」**，10 秒内不点「继续安装」自动拒绝 → UTP 报 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`；跑测试时要盯手机点确认，或用脚本轮询 `mCurrentFocus` 命中 `AdbInstallActivity` 后 `input tap`
  2. 设备残留**旧签名包** → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，先 `adb uninstall com.mysecret.intimatetracker`（`.test` 同理）
  3. `adb` 不在 PATH，统一用 `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`

## 2.7 字典管理重做（2026-10-24，v1.6.0）

- [x] 数据层：`sex_type` / `location` 增加 `notes`；DB v2 → v3（`Migrations.MIGRATION_2_3`），schema 导出 `3.json`
- [x] DAO：四类字典 `observeAll` 改为 `ORDER BY sort_order, name`（平铺排序，不再强制内置在前）
- [x] 仓库：`DictionaryItem` 带 `note` / `intensity`（不再暴露 `isBuiltin`，四类同等对待）；新增 `update()`（名称 / 备注 / 强度，保留排序与隐藏标记）；`add()` 默认强度 `VERY_LOW`
- [x] UI：`DictionaryScreen` 重写为平铺卡片列表 + 行尾手柄拖动排序（无需长按，拖动中其余行实时让位）；点整行进入编辑（v1.7 起移除「显示 / 隐藏」眼睛图标）
- [x] 弹窗：四类共用一套下划线输入框（名称 / 备注 / 强度下拉）+ 胶囊「保存」；弹窗不再有「取消」，点弹窗外关闭；编辑态左下是红色「删除」（二次确认后软删），内置项与自建项完全同等
- [x] 种子：`Seeder` 改为「表为空（含软删）才写入」，用户删掉的内置项不会被再种回来
- [x] 测试：新增 `MigrationTest`（1→3、2→3）+ `DictionaryRepositoryTest` 15 项；真机 `am instrument` 共 67 项全绿（38 + 3 + 15 + 5 + 6）
- [x] 软删口径（历史真相，全端统一）：`domain/DeletedLabels.kt` 提供「（已删除）」后缀工具；`StatsRepository` 与 `RecordDetailViewModel` 用含软删的 `all()` 取名并加标记；`RecordDao` 两处查询的 `GROUP_CONCAT` 对已删项加同一后缀、玩具数含已删；`RecordIcons` / 日历与历史的自慰筛选按去后缀后的名字匹配
- [x] 编辑记录同样按历史展示：`DictionaryRepository.observeIncludingDeleted` / `PartnerRepository.observeAllHistory` 提供含软删的选项，表单只额外保留「已被本记录选中」的已删项并标「（已删除）」，保存照常写回关联
- [x] 伴侣统计口径：`partnerCount` 与伴侣排行都排除已删除与已归档（`pt.is_archived = 0`），记录里归档伴侣照常显示
- [x] 筛选自动清理：统计页选中伴侣被删后回到「全部伴侣」；日历 / 历史在伴侣被删或归档后剔除该筛选项
- [x] 测试：新增 `DeletedItemsConsistencyTest` 4 项（伴侣删除、类型删除、地点删除、玩具删除）+ `FilterPruneTest` 2 项（日历删除 / 历史归档）
- [x] 重名与恢复：新增 / 改名做重名检查（**含已删除项**，排除自己），字典命中未删项可「去编辑它」、命中已删项可「恢复它」，伴侣同理；字典页与伴侣列表新增「已删除（N）」区 + 恢复（清 `deleted_at`，id 与关联不变）
- [x] 彻底删除：`tombstone` 表 + 物理删；同步合并墓碑优先、关联回填过滤悬空 id、`dataVersion` 纳入墓碑时间；`TombstoneDao` 已注册进 `AppDatabase`
- [x] 去掉 i18n：删除 `res/values-en/`，PRD §9 改为「只做简体中文」，文档里「英文资源」相关描述同步
- [x] 依赖：`kotlinx-serialization` 统一强制 1.8.1（`room-testing` 读 schema 需要；否则 `MigrationTestHelper` 抛 `AbstractMethodError`）
- 截图：`evidence/v160-*.png`（姿势列表 / 编辑弹窗 / 新增弹窗 / 隐藏 / 拖动排序 / 情趣用品 / 地点 / 性爱类型 / 记录表单）

## 2.8 伴侣 / 字典 / UI 收口（2026-10-25，随 v1.6.0 发布）

- [x] 种子尊重墓碑：`Seeder.seed` 读取 `tombstone` 并跳过其中的 id，彻底删除的内置类型 / 地点不会被下次启动重新种回（此前会让「重启复活 + 墓碑让同步永久忽略」多端永久分叉）
- [x] 字典空态：在用项全删光时不再整页空态，列表 + 底部「已删除」区照常展示，并给出提示文案 `dict_empty_active`
- [x] 恢复语义统一：`restore` 清 `deleted_at` 并把 `sort_order` 追加到末尾（落点可预期）；伴侣恢复同时取消归档；重名「恢复它」改走 `restoreAndUpdate`，把表单刚填的名称 / 备注 / 强度（伴侣还有性别 / 关系 / 头像）写回，不再丢输入
- [x] 记录保存兜底：`RecordRepository.save` 先把 partner / 4 字典 id 过滤为「本地仍存在的行」，避免选中项被彻底删除后写交叉表撞外键；`AddRecordViewModel.save` 加 try/catch + 失败弹窗
- [x] 移除「显示 / 隐藏」：`DictionaryItem.isHidden`、`DictionaryRepository.setHidden`、`DictionaryViewModel.toggleHidden`、4 个 DAO 的 `setHidden`、`dict_show` / `dict_hide` 文案、行尾眼睛图标与删除线样式全部删除；实体 `is_hidden` 列保留只为免迁移（PRD §5.6 已注明）
- [x] **修复级联删除（真机复现后修的）**：6 个「有子表的外键父表」`upsert` 从 `@Insert(onConflict = REPLACE)` 改为 Room `@Upsert`。`INSERT OR REPLACE` 会先物理删旧行，触发 `ON DELETE CASCADE`：编辑一次记录照片全没、改名一次伴侣 / 字典项的历史关联全没（真机断言 `expected:<1> but was:<0>` 已确认）
- [x] 测试：新增 9 项（seeder 墓碑 2 / 恢复写回与排序 1 / 字典编辑+恢复保关联 1 / 伴侣恢复取消归档与写回 2 / 伴侣改名+恢复保关联 1 / 记录悬空 id 1 / 编辑记录保照片 1），删除原隐藏用例 1 项
- [x] 涟漪圆角修复（2026-10-25）：伴侣/记录卡片、节奏卡改用可点击 `Card(onClick=...)` 重载（涟漪随 Card shape 裁剪）；其余「圆角背景 + `clickable`」控件补 `clip(shape)`（日历筛选胶囊、统计筛选框、月份选择格、添加照片框、未使用的 Segmented/PresetChip）
- [x] 动效优化（2026-10-25，8 个方向）：①列表 `animateItem`（伴侣/历史/照片）；②导航 tab fade-through + 二级页右侧滑入滑出、编辑页保上滑、底栏进出 + zIndex 防止盖住新页；③空态/列表、日历日内容 Crossfade；④字典「已删除」展开收起 + 箭头旋转；⑤月份标题方向滑动、选中圆缩放/颜色过渡；⑥统计数字翻转、排行条与柱状图生长；⑦predictive back + `rememberAnimationsEnabled`；⑧卡片/FAB 按压缩放 + 伴侣头像 `SharedTransitionLayout` 共享元素
- [x] 日历样式（2026-10-25）：点击日期时的按压高亮（涟漪 / state layer）用 `clip(RoundedCornerShape(16.dp))` 裁成圆角，选中态保持原 28dp 圆形；有记录的日期数字 `FontWeight.Bold`（`MonthGrid` 传 `hasRecords = style != null`）
- [x] UI 统一第二批（2026-10-25，对应审查项 1/2/3/4/7）：记录表单的输入 / 选择 / 备注改用 `FormTextField` / `FormSelectField`（删除旧 `SelectionField`）；记录新增 / 详情页改用 `ScreenScaffold` 紧凑标题栏（新增记录带可点日期副标题 + 关闭 ×，移除 M3 `TopAppBar`）；删除 / 清空确认按钮统一 `colorScheme.error`；字典 FAB 改用共享 `AppFab`（日历 / 伴侣同步复用）；新增 `AppBottomSheet` 统一日历筛选 / 历史筛选 / 简化输入 / 节奏详情的标题、底色与右上操作位
- [x] UI 统一（2026-10-25）：全应用删除按钮图标统一为编辑伴侣页的 `Icons.Outlined.Delete` + `colorScheme.error` 红色 tint（字典彻底删除、记录详情 / 编辑记录删除、关于页「清空所有数据」；伴侣「已删除」区的彻底删除同步改为红色，原先两处彻底删除都是灰色 `onSurfaceVariant`）；记录照片上的删除图标保持白色以保证压在照片上的对比度；隐私政策卡片的 `DeleteSweep` 属说明性图标，未改
- [x] UI 统一（2026-10-25）：四类字典新增 / 编辑弹窗的输入框、下拉选项框改为与「新增伴侣」同一套外框组件（`ui/component/FormControls.kt` 的 `FormTextField` / `FormSelectField`：8dp 圆角、18sp 正文、浮动标签），伴侣页同步改用同一组件；省略旧的下划线输入框 / 强度下划线下拉
- 验证：`testDebugUnitTest` 57 项全绿；真机 24129PN74C `connectedDebugAndroidTest` 75 项全绿（DictionaryRepositoryTest 18 / FilterPruneTest 9 / RecordRepositoryTest 8）

## 2.9 历史真相强化：彻底删除也不移除关联（2026-10-25，v1.7.0，DB v4）

- [x] 数据层：5 张关联表新增 `label` 名字快照，并**去掉指向 `partner` / 4 张字典表的外键**（只保留 `record` 级联）；`AppDatabase.version = 4`，`Migrations.MIGRATION_3_4` 重建表并用父表当前名字回填，schema 导出 `4.json`
- [x] 写入口径：`RecordRepository.save` 对每个关联 id 先取父行当前名字（含软删行），父行已被物理删除则沿用该记录原有快照；改名（`PartnerRepository.save` / `DictionaryRepository.update` / `restoreAndUpdate`）同事务刷新所有历史关联的 `label`
- [x] 展示口径：`RecordDao` 列表 / 日历改 `LEFT JOIN` + `COALESCE(父行名, label)`，父行不存在或软删都标「（已删除）」；详情页改用 5 条 `*LinkNames` 查询；统计用 `*HistoryLabels` 给已彻底删除项补名，排行与分母不缩水
- [x] 编辑记录：`RecordDao.observeLinkRows`（UNION ALL）+ `AddRecordViewModel.combine`，父行已彻底删除的选中项以「（已删除）」幽灵选项出现，可取消，保存不丢关联
- [x] 同步：`SyncManager.copyLinks` 整行搬运关联（含 label），删除 `LinkIds` 悬空过滤；墓碑只删父行，不再连带关联
- [x] 文案：`purge_confirm_dict` / `purge_confirm_partner` 改为「历史关联按历史真相保留」
- [x] 测试：新增 `HistoryTruthPurgeTest` 6 项（伴侣 / 类型 / 地点+姿势+玩具 / 编辑回写 / 改名刷新 / 幽灵选项）；`MigrationTest` 增 3→4 回填与去外键用例、1→4 全链路；`DatabaseTest` 硬删伴侣断言由「级联清空」改为「保留 + 标记」
- 验证：JVM 57 项全绿；`connectedDebugAndroidTest` 在**真机 24129PN74C（Android 17）**与模拟器 Pixel_10_Pro 上各 **82 项全绿**（0 failure / 0 error，用时 3.7s / 5.1s）

## 3. 剩余事项（2026-10-24 按代码核对后重写）

> 旧版本节停留在 S1 阶段。核对后确认已落地、可从待办移除的：S2/S3/S4 全部功能、伴侣头像（导入/中心裁切 512px/更换/移除）、
> 简化输入（已改为记录表单内 `SimplifiedInputSheet`）、偏好设置、应用锁、WebDAV、导出/清空、Room v2 迁移、应用图标。
>
> 2026-10-24 已修文档：`prd-v1.1.md` 表数 13 → **16**、种子 11/23/6 → **10/23/0**、§4.1 类型清单（去「主动/被动」、补「乳交」）、
> §5.8 简化输入形态（表单内弹层）；`README.md`「已知限制」按实际重写（删 Room v1 / 头像 / 删照片三条过期项）。
>
> 下表只保留**尚未处理**的项。

| 事项 | 现状 / 依据 |
|---|---|
| ~~应用内语言切换~~ | **不做多语言**（v1.6.0 决定），从待办移除 |
| 产物 / 包体配置 | 仅 `arm64-v8a`（`splits.abi`，可 `-PwithX86` 额外出 x86_64）；未开 R8/minify |
| 生物识别成功路径 | 仍缺真机验证记录（instrumentation 不覆盖 BiometricPrompt） |
| 未引用文案 | `more_simplified` / `simplified_hint` / `simplified_min` 仅定义未被引用（grep 确认）→ 可选清理 |

## 4. 关键文件路径

```
<repo-root>\
├── prd.md                      # 原始需求 v0（保留追溯，不再作为开发依据）
├── prd-v1.1.md                 # ★ 当前开发规格（唯一事实来源）
├── 可行性分析.md                 # 环境/依赖/口径/风险分析
├── README.md                   # 项目状态、技术栈、构建方式、隐私声明
├── TASK_STATE.md               # 本文件
├── settings.gradle.kts / build.gradle.kts / gradle.properties / local.properties
├── gradle/libs.versions.toml   # 版本锁定
├── app/build.gradle.kts        # compileSdk 37 / minSdk 26 / targetSdk 36
├── app/schemas/.../1.json      # Room schema（迁移基线，勿删）
├── app/src/main/java/com/mysecret/intimatetracker/
│   ├── MainActivity.kt / TrackerApp.kt
│   ├── domain/model/Enums.kt
│   ├── data/db/{Entities,Daos,AppDatabase,Seed}.kt
│   ├── data/repo/{PartnerRepository,DictionaryRepository}.kt
│   ├── di/DatabaseModule.kt
│   └── ui/{theme,navigation,component,calendar,history,stats,partner,dictionary,more}/
├── app/src/test/.../SeedDataTest.kt
├── app/src/androidTest/.../DatabaseTest.kt
└── evidence/{probe-run-api37.png, s1/*.png}
```

## 5. 已修改 / 新增文件（S1 范围）

- 新增：`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、`gradlew(.bat)`、`gradle/wrapper/*`
- 新增：`app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`、`app/src/main/res/values/{strings,colors,themes}.xml`
- 新增：Kotlin 源码 18 个（见上目录树）、`app/schemas/…/1.json`
- 新增：`app/src/test/.../SeedDataTest.kt`、`app/src/androidTest/.../DatabaseTest.kt`
- 新增：`evidence/s1/*.png`（8 张）、`README.md`、`TASK_STATE.md`
- 修改：`.gitignore`（排除 `build/`、`local.properties`、`*.apk`）、`.gitattributes`
- 注意：`local.properties` 含本机 SDK 路径，**不入库**

## 6. 验证命令（Windows / PowerShell）

```powershell
# 环境（每次新会话先设）
$env:JAVA_HOME="E:\Program Files\Android\Android Studio\jbr"

# 构建
.\gradlew.bat assembleDebug --console=plain
# 产物：app/build/outputs/apk/debug/app-debug.apk（约 18.9MB）

# JVM 单测（57 项）
.\gradlew.bat testDebugUnitTest --console=plain

# 仪器测试（82 项：DatabaseTest 7 + PhotoRepositoryTest 3 + RecordRepositoryTest 8 + RhythmSettingsPersistenceTest 3 + SettingsAndTemplateTest 5 + StatsGoldenTest 6 + SyncFlowTest 5 + WebDavClientTest 4 + DictionaryRepositoryTest 18 + DeletedItemsConsistencyTest 5 + FilterPruneTest 9 + MigrationTest 3 + HistoryTruthPurgeTest 6）
# 真机：先 adb devices 确认在线；MIUI 会弹「USB 安装提示」，须在 10 秒内点「继续安装」，否则安装阶段失败
# 模拟器：
$sdk="$env:LOCALAPPDATA\Android\Sdk"
Start-Process "$sdk\emulator\emulator.exe" -ArgumentList "-avd","Pixel_10_Pro","-no-window","-no-audio","-no-snapshot","-no-boot-anim","-gpu","swiftshader_indirect"
& "$sdk\platform-tools\adb.exe" wait-for-device
.\gradlew.bat connectedDebugAndroidTest --console=plain

# 装机 + 截图
& "$sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
& "$sdk\platform-tools\adb.exe" shell am start -n com.mysecret.intimatetracker/.MainActivity
& "$sdk\platform-tools\adb.exe" exec-out screencap -p > evidence\shot.png

# UI 元素定位（拿准确坐标用）
& "$sdk\platform-tools\adb.exe" shell uiautomator dump /sdcard/ui.xml
& "$sdk\platform-tools\adb.exe" pull /sdcard/ui.xml ui.xml

# 推送
git add -A; git commit -m "..."; git push
```

## 7. 当前错误 / 风险

| 类别 | 说明 |
|---|---|
| 环境 | `C:\Program Files\Java\jdk-17.0.5` 是空壳（无 bin），必须用 `E:\...\Android Studio\jbr`；`gradle.properties` 已固定 |
| 环境 | 本机默认沙箱会拦截 git 的 schannel 和 Gradle 写盘；当前会话已是 danger-full-access，新会话若回退需重新申请 |
| 编译告警 | `hiltViewModel` 提示迁移到 `androidx.hilt.lifecycle.viewmodel.compose`（未处理，不影响构建）；`TabRow` 已换 `PrimaryTabRow` |
| 数据 | 关联表只对 `record` 级联；伴侣 / 字典行被物理删除后**关联与名字快照保留**（v4 历史真相，`HistoryTruthPurgeTest` 锁定）；软删同样保留 |
| 迁移 | DB version=4，迁移集中在 `Migrations.kt`（`Migrations.ALL`），schema 已导出 `1.json` ~ `4.json`；再加列必须补 Migration 并导出 schema |
| 测试 | 2026-10-25 真机 24129PN74C（Android 17）+ 模拟器 Pixel_10_Pro：`connectedDebugAndroidTest` 各 82 项全绿（0 failure）；JVM 57 项全绿 |
| 测试（真机） | MIUI 每次 ADB 安装弹「USB 安装提示」，10 秒内不点「继续安装」即 `INSTALL_FAILED_USER_RESTRICTED`；跑 `connectedDebugAndroidTest` 需盯手机确认 |
| 数据 | pm clear 后旧记录仍可能出现（WAL/文件残留），验证前用 **卸载重装** 才是干净状态 |
| 隐私 | 真实记录、昵称与照片**禁止入库** |

## 8. 下一步建议

1. **回归口径**：改动后跑 `.\gradlew.bat testDebugUnitTest`（57 项）+ 仪器测试（82 项；真机注意 MIUI 安装弹框，模拟器可用 `am instrument`）；数据层改动务必跑 `MigrationTest`
2. **保持文档同步**：功能或数据模型再变时同步 `prd-v1.1.md` / `README.md` / 本文件（2026-10-25 已按 DB v4 同步一轮）
3. **发版**：`powershell -ExecutionPolicy Bypass -File tools\publish_release.ps1 -Version vX.Y.Z`，同步更新 `CHANGELOG.md` / `更新日志.md` 并打 `vX.Y.Z` tag
4. **可选增强**：开 R8 + 体积优化、补 x86_64 包、语言切换接系统 API、生物识别真机验证、清理未引用文案
