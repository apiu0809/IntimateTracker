# TASK_STATE — Intimate Tracker

> 快照时间：2026-10-24 22:55（Asia/Shanghai）
> 用途：上下文告急时的交接文件；恢复工作请先读本文件，再读 `prd-v1.1.md`。

## 1. 任务目标

按 `prd-v1.1.md` 实现 Android App「亲密记录 / Intimate Tracker」（Kotlin + Compose + Room + Hilt），
分 4 个 Sprint 交付：**S1 骨架+数据层+字典/伴侣（已完成）→ S2 记录表单+日历+历史 → S3 统计 9 页签 → S4 同步 v2+应用锁+导出**。

已确认的四项产品决策：
1. 范围裁剪：不做账号体系与增值模块，仅 WebDAV 文件同步
2. 删除卡路里（不存、不算、不显示；总览卡 12 → 11 张）
3. 同步走 v2（OkHttp 自写 WebDAV + 整库 zip + ETag + 原子替换 + 逐行 LWW + 墓碑 + 可选加密）
4. 默认语言 = 简体中文（`values/` 基准，另出 `values-en`；繁中可选不打包）

## 2. 已完成事项

- [x] 评审 `prd.md`（v0），产出环境实测/依赖核查/PRD 缺陷/里程碑结论
- [x] 实测工具链：JDK 21（Android Studio JBR）、Gradle 9.4.1、AGP 9.2.1、Kotlin 2.2.10、compileSdk 37
- [x] 产出 `prd-v1.1.md`：16 表 DDL、种子数据、统计口径表、WebDAV v2 全流程、错误码、测试用例
- [x] git 仓库初始化 + 远端 `https://github.com/apiu0809/intimate-tracker`（main 分支）
- [x] **S1 全部交付**（commit `c0fcd1e`，已 push）：
  - Gradle 骨架（`gradle/libs.versions.toml`、`gradlew`）、`gradle.properties`（含 `android.disallowKotlinSourceSets=false`、`org.gradle.java.home`）
  - 深色主题 + 5 tab 底部导航（简体）
  - 16 张表 Entity/DAO/AppDatabase（外键 CASCADE、软删、索引、schema 导出到 `app/schemas/`）
  - 种子数据 11 类型 / 23 地点 / 6 姿势（幂等）
  - 字典管理（内置可隐藏、自建可删、姿势带强度）
  - 伴侣列表 / 新增 / 编辑 / 归档 / 软删
  - 测试：JVM 8 项 + 仪器 7 项，全部通过
  - 真机截图 8 张（`evidence/s1/`）+ README 更新
- [x] 模拟器验证：安装、启动、5 个 tab、字典新增、伴侣新增均可操作（截图留档）

## 2.1 S2 进度（2026-10-22 更新）

- [x] 记录数据层：RecordRepository（事务写记录 + 5 张交叉表、替换式更新、hythm() 节奏统计）
- [x] DAO 查询：observeListBetween（GROUP_CONCAT 聚合类型/地点/姿势名）、observeCalendar（emoji 回退）、llTimestamps
- [x] 新增记录 UI：日期选择、记录图标（63 emoji 五分类）、伴侣多选、主动方、时长（预设+步进）、满意度、类型/地点/姿势多选、防护措施、双向高潮、备注
- [x] 日历 FAB 入口 + 路由 ecord/new
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
- [x] 同步测试修复：根因是 unBlocking 尾表达式返回 Boolean/Long 导致 JUnit 报 should be void，加 : Unit + ssertEquals(5_000L, …) 后通过；SyncFlowTest 3 项覆盖 首次上传 / ETag 短路 / 远端较新合并
- [x] WebDAV 设置页：URL/账号/密码/测试连接/立即同步/状态与上次同步时间（密码暂存 settings 明文，待加密）
- [x] SyncFlowTest 4 项全绿（2026-10-24 真机复跑确认；此前 initializationError 已随类重写消失），同步内核已有自动测试
- [x] 应用锁：AppLockGate + BiometricPrompt(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)，More 页开关，默认关闭
- [x] 导出：zip（db+photos）写入 MediaStore 下载/IntimateTracker
- [x] 清空数据：clearAllTables + 删除 images/sync 缓存（二次确认弹窗）
- [x] 安全加固：启用锁时加 FLAG_SECURE；后台超 5 分钟 onStart 时 recreate 重新上锁
- [x] 英文 values-en：143 条字符串全部翻译（aapt2 校验 en 配置已进 APK）
## 2.5 S4 完成情况（2026-10-22）

S4 已交付：WebDAV v2 同步（客户端/合并/设置页/测试）、应用锁（生物识别+设备凭据）、导出/清空、FLAG_SECURE、5 分钟重新上锁、密码加密存储、英文资源。
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

## 3. 未完成事项

- [ ] **S2**：新增/编辑记录（14 字段 + 简化输入联动 + emoji 选择器 63 个 + 照片 + 模板）、日历（自绘月历 + 节奏卡 + 筛选）、历史（活动/相册）
- [ ] **S3**：统计 9 页签 + 期间/伴侣双筛 + 口径常量 + 黄金用例（17 条 fixture）+ Vico 3 图表
- [ ] **S4**：WebDAV v2 同步、应用锁（生物识别 + PIN 兜底）、导出/清空数据、i18n 英文
- [ ] 文档修正：`prd-v1.1.md` §10 写"13 表"，实际落地 **16 表**（11 主表 + 5 交叉表），需同步修正
- [ ] 伴侣头像（`avatar_path` 字段已建，UI 未实现上传/裁切）
- [ ] 简化输入设置页（14 开关，当前仅占位弹窗）
- [ ] 偏好设置 / 应用锁 / WebDAV / 导出 / 清空 均为占位项（点击弹"后续版本提供"）
- [ ] Room Migration 策略（当前 version 1，无迁移）
- [ ] 应用图标（暂用系统默认）

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

# JVM 单测（8 项）
.\gradlew.bat testDebugUnitTest --console=plain

# 仪器测试（38 项）
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
| 数据 | 软删伴侣时**保留**交叉表关联（有意为之，历史记录需要）；硬删才 CASCADE —— 已有测试锁定，勿随意改 |
| 迁移 | DB version=1，尚未写 Migration；后续加表必须补 |
| 测试 | 仪器测试 2026-10-24 真机 38 项全绿；模拟器 AEHD 可用但需完整权限 |
| 测试（真机） | MIUI 每次 ADB 安装弹「USB 安装提示」，10 秒内不点「继续安装」即 `INSTALL_FAILED_USER_RESTRICTED`；跑 `connectedDebugAndroidTest` 需盯手机确认 |
| 数据 | pm clear 后旧记录仍可能出现（WAL/文件残留），验证前用 **卸载重装** 才是干净状态 |
| 规格 | `prd-v1.1.md` §10 "13 表"与实现（16 表）不一致，待修正 |
| 隐私 | 真实记录、昵称与照片**禁止入库** |

## 8. 下一步建议

1. **先做 S2 的「新增/编辑记录」**：`RecordRepository`（含 5 张交叉表事务插入）、`AddRecordScreen`（14 字段 + 简化输入开关 + emoji picker 63 个 + PhotoPicker），字段规格见 `prd-v1.1.md §5.2`
2. 再做日历：`MonthCalendar` 自绘 7 列 + `HorizontalPager` 翻月 + 当日 emoji（`record.icon` → 主类型 emoji）+ `RhythmCard`（距上次/最长间隔/通常=中位数±1，样本<3 显示"—"）
3. 最后历史：活动（两级分组卡片，副行"10分钟 · 厕所"）+ 相册（LazyVerticalGrid 按月分组）
4. 每完成一块：`assembleDebug` → 装机截图 → commit；Sprint 结束统一 push
5. 顺手修正 `prd-v1.1.md` 的表数量表述（13 → 16）
