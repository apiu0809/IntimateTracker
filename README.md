# Intimate Tracker · 亲密记录

个人私密行为记录 App（Android / Kotlin / Jetpack Compose）。本地优先，数据只存本机；可选 WebDAV 加密备份。

> 本项目为个人作品；仓库内不含任何真实照片、伴侣昵称、账号、设备标识或签名密钥。

## 当前状态

| 里程碑 | 状态 |
|---|---|
| 需求分析（PRD v0 评审） | ✅ [`prd.md`](prd.md) |
| 开发规格 v1.1（DDL / 口径 / 同步算法） | ✅ [`prd-v1.1.md`](prd-v1.1.md) |
| S1 工程骨架 + 数据层 + 字典/伴侣 | ✅ |
| S2 记录表单 + 日历 + 历史 | ✅ |
| S3 统计 9 页签 | ✅ |
| S4 WebDAV v2 + 应用锁 + 导出 | ✅ |
| **当前版本** | **v1.7.0** |

## 已实现功能

- **日历**：月历滑动翻月、筛选伴侣/自慰、按伴侣着色的日期高亮（筛选大环 + 今天小环）、每日记录列表、图标规则（自定义 → 星级 → 类型 → 玩具 → 防护，每天最多 2 个）
- **节奏卡**：距上次天数 / 上次记录 / 通常间隔 / 最长间隔；点击查看星期分布；可自订配色、计数单位、统计范围、是否只算与伴侣等 8 项
- **记录**：14 字段表单、图标、照片、简化输入（底部面板）、日期选择；只读详情页 + 修改 / 删除
- **历史**：按月/日分组 + 相册网格；左右滑动切换「活动 / 相册」
- **统计**：9 页签、左右滑动切换、期间/伴侣筛选、11 张指标卡、排行、月度柱图、星期分布
- **伴侣**：关系类型（随性关系 / 配偶 / 女朋友 / 情人 / 朋友 / 前任 / 同事 / 陌生人 / 其他）、归档、软删；「彻底删除」只删伴侣本身，历史记录里的关联与名字按「历史真相」保留（标「（已删除）」）
- **字典**：类型 / 地点 / 姿势 / 情趣用品；平铺卡片列表 + 行尾手柄拖拽排序；四类都可新增、编辑与删除（姿势带备注 / 强度，其余带备注），不再区分内置与自建；误删的条目在列表底部「已删除」区一键恢复，「彻底删除」后不会被同步或种子重新带回，但已有记录的关联与名字快照保留（标「（已删除）」）
- **同步**：WebDAV `current.zip` + 每日历史（最多 14 份）、逐行 LWW（含照片）、清空后可恢复；支持自动同步（打开 App / 15 分钟–每天定时 / 修改后防抖），可选仅 Wi-Fi、仅充电
- **安全**：生物识别 / 设备凭据应用锁、`FLAG_SECURE`、后台 5 分钟重新上锁、WebDAV 密码加密存储
- **外观**：应用图标（黑灰底 + 白色日历与心形）、由一个「种子色」推导整套 Material 3 配色（8 个预设 + 色轮自选 + Android 12+ 跟随壁纸）、深色 / 浅色 / 跟随系统、纯黑（OLED）开关
- **数据**：导出 zip 到下载目录、详情页删除单张照片；清空全部数据（入口在关于页）
- **关于**：数据与隐私说明、按版本折叠的更新日志、开源许可、崩溃日志查看 / 复制 / 清空
- **语言**：仅简体中文，不做多语言（无 `values-en`、无语言切换）

## 关键决策

1. **范围裁剪**：只做本地记录与 WebDAV 备份，不引入账号体系、云服务与任何增值模块
2. **不存卡路里**：不存、不算、不显示
3. **同步走 WebDAV v2**：OkHttp 自写 + 整库 zip + ETag + 逐行 LWW（照片行参与合并），远端一份 `current.zip` + 每日历史，自动同步由 WorkManager 调度
4. **只做简体中文**，不做 i18n（v1.6.0 起移除英文资源）
5. **历史真相**：删除词条（伴侣 / 字典项）不删除历史关联——关联行自带名字快照，彻底删除父行也不 CASCADE，展示时标「（已删除）」

## 技术栈

| 项 | 版本 |
|---|---|
| AGP | 9.2.1 |
| Gradle | 9.4.1 |
| Kotlin | 2.2.10（AGP 9 内建，勿再加 `kotlin-android` 插件） |
| Compose BOM | 2025.12.00 |
| Room | 2.8.5（KSP 2.2.10-2.0.2） |
| Hilt | 2.60.1 |
| Coil / OkHttp / Biometric | 3.3.0 / 4.12.0 / 1.1.0 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |

## 本地构建与测试

```powershell
$env:JAVA_HOME = "E:\Program Files\Android\Android Studio\jbr"   # JDK 21
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest          # 57 项单测
.\gradlew.bat connectedDebugAndroidTest  # 82 项仪器测试（需模拟器/真机）
.\gradlew.bat assembleRelease            # 签名包，见下
```

`gradle.properties` 必需项（KSP 与 AGP 9 内建 Kotlin 共存）：

```properties
android.disallowKotlinSourceSets=false
```

release 签名读取根目录 `keystore.properties`（不入库）：

```properties
storeFile=keystore/release.jks
storePassword=******
keyAlias=intimatetracker
keyPassword=******
```

## 发布 Release

```powershell
powershell -ExecutionPolicy Bypass -File tools\publish_release.ps1 -Version v1.7.0
```

脚本会从 `CHANGELOG.md` 提取对应小节生成 notes，创建或更新 Release，并上传 `releases/IntimateTracker-vX.Y.Z-arm64-v8a.apk`。加 `-Draft` 建草稿，加 `-SkipAsset` 只改说明。

每次发版固定为：改 `versionCode` / `versionName` → 更新 `CHANGELOG.md` 与 `更新日志.md` → 单测 → `assembleRelease` → `git commit` + `tag` + `push` → 发布 Release。

## 目录

```
.
├── app/                     # Android 应用（Compose + Room + Hilt）
├── gradle/                  # 版本目录与 wrapper
├── tools/                   # 发布脚本（Release notes / 上传 APK）
├── prd.md                   # 原始需求（v0，保留追溯）
├── prd-v1.1.md              # 开发规格（以此为准）
├── docs/                    # 迁移与同步方案分析
├── CHANGELOG.md             # 完整技术变更记录
├── 更新日志.md                # 面向使用者的简版更新说明
├── icon-design/             # 应用图标候选（7 款，脚本可重跑）
├── releases/                # 本地 release APK（不入库）
└── evidence/                # 各阶段验证截图
```

## 数据

16 张表（Room v4，迁移见 `Migrations.kt`，schema 导出至 `app/schemas/`）：伴侣、类型、姿势、情趣用品、地点、记录、照片、模板、设置、同步元数据、墓碑 + 5 张关联表。照片行含 `updated_at` 并参与逐行同步；业务数据软删除，5 张关联表只对 `record` 级联——关联行带 `label` 名字快照，伴侣 / 字典项被彻底删除后历史关联与名字仍保留（展示标「（已删除）」）。

内置字典：10 个类型、23 个地点；**姿势不内置**。关系类型、性别、主动方、防护、强度均为枚举。

## 版本与提交约定

| 场景 | 做法 |
|---|---|
| 完成一处可运行的改动 | 立即 commit，不攒着 |
| 提交信息 | `type(scope): 中文摘要`，type ∈ feat / fix / docs / build / refactor / test |
| 发版 | 版本号 + `git tag -a vX.Y.Z` + release APK + 更新 CHANGELOG |
| 推送 | `git push`；偶发 `RPC failed` 时用 `git -c http.version=HTTP/1.1 push` 重试 |

### 不入库

`.gitignore` 已排除：`keystore/`、`keystore.properties`、`*.apk`、`local.properties`、`build/`、`.gradle/`。

⚠️ **`keystore/release.jks` + `keystore.properties` 必须单独备份**，丢失将无法发布覆盖升级的版本。

## 已知限制

- 仅提供 `arm64-v8a`（调试可用 `-PwithX86` 额外出 x86_64 包）；未开启 R8 混淆
- 升级迁移只保留正向路径（v1 → v4，见 `Migrations.kt`），不支持降级
- 应用内语言切换未接 `AppCompatDelegate.setApplicationLocales`（跟随系统）
- 生物识别成功路径无自动化覆盖（instrumentation 不触达 `BiometricPrompt`），需真机手测

## 隐私

- 仓库内**不含**任何真实照片、伴侣昵称、账号邮箱
- 所有示例数据均为自造数据，不含真实记录
- 应用数据仅存本机；WebDAV 备份为用户自建服务器，密码经 EncryptedSharedPreferences 加密

## 许可证

本项目以 [GNU AGPL-3.0](LICENSE) 发布，Copyright (C) 2026 apiu0809。

你可以自由使用、修改、再分发；但**分发修改版（含编译出的 APK）或以修改版对外提供网络服务时，必须以同一许可证公开完整源代码**。若需在闭源产品或闭源服务中使用，需另行取得作者授权。
