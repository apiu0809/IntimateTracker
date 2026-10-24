# 数据库迁移方案（Room）

> 目标：任何一次 schema 变更都不丢用户数据，且升级失败时用户仍能导出备份。

## 现状

- `AppDatabase` 当前 `version = 4`
- 16 张表，schema JSON 已导出到 `app/schemas/`
- `DatabaseModule.provideDatabase` 已配置 `.addMigrations(*Migrations.ALL)`
- 已完成迁移：
  - **v1 → v2**：`photo` 表新增 `updated_at INTEGER NOT NULL DEFAULT 0`，用 `COALESCE(deleted_at, created_at)` 回填；照片行参与 WebDAV 逐行同步
  - **v2 → v3**：`sex_type` / `location` 新增 `notes TEXT`（字典管理页的「备注」）
  - **v3 → v4**：5 张关联表新增 `label TEXT`（名字快照），并去掉指向 `partner` / 4 张字典表的外键（只保留 `record` 级联）；用父表 `name` 回填 `label`。动机见 PRD §3.3「历史真相」：彻底删除词条不能 CASCADE 掉历史关联
- 迁移已在真机（v1 数据）与模拟器上实际升级验证

## 设计原则

1. **禁止破坏性回退**：release 包绝不调用 `fallbackToDestructiveMigration()`，否则升级即清空数据
2. **每条迁移显式编写**：`ALTER TABLE` / 重建表，写清注释与影响表
3. **迁移可测试**：用 `MigrationTestHelper` 从旧 schema JSON 建库，跑迁移后与旧数据比对
4. **先备份再迁移**（可选增强）：启动时若检测到 `version < 当前`，先把 `tracker.db` 复制为 `tracker.pre-migration.db`，迁移成功后再删

## 落地步骤（下次改 schema 时照做）

1. 改 Entity
2. `AppDatabase.version = 4`（按当前版本递增）
3. 在 `Migrations.kt` 增加：

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE record ADD COLUMN mood TEXT")   // 示例：加可空列
    }
}
val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
```

4. `DatabaseModule` 增加 `.addMigrations(*Migrations.ALL)`
5. 构建生成 `app/schemas/.../5.json`，提交入库
6. 加测试：

```kotlin
@get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

@Test fun migrate1To2_keepsRows() {
    helper.createDatabase("migration-test", 1).use { db ->
        db.execSQL("INSERT INTO record(id, timestamp, created_at, updated_at) VALUES('r1', 1, 1, 1)")
    }
    helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2).use { db ->
        db.query("SELECT COUNT(*) FROM record").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
    }
}
```

7. 真机演练：装旧版 → 造数据 → 覆盖安装新版 → 校验数据与关键页面

## 各类变更的写法

| 变更 | 写法 | 注意 |
|---|---|---|
| 加可空列 | `ALTER TABLE x ADD COLUMN y TEXT` | 最简单 |
| 加非空列 | `ADD COLUMN y INTEGER NOT NULL DEFAULT 0` | 必须给默认值 |
| 删列 | 新建表 → `INSERT SELECT` → 删旧表 → 重命名 | 保留数据 |
| 改类型/约束 | 同上（重建表） | 外键表需先关 `foreign_keys` |
| 加索引 | `CREATE INDEX ...` | 与 Entity 的 `@Index` 保持一致 |
| 加表 | `CREATE TABLE ...` | 字段与 Entity 完全一致（含默认值） |

## 回滚与兜底

- 迁移抛异常时：**不删库**，让 App 进入「数据库不可用」提示页，引导用户从 WebDAV/导出文件恢复
- 备份策略：迁移前复制一份 `tracker.pre-migration.db`，成功启动后删除（保留 1 次）
- 若确认迁移写错且已发布：只能发新版补 `Migration`，**不能**用破坏性回退

## 风险清单

| 风险 | 缓解 |
|---|---|
| 迁移 SQL 与 Entity 不一致 | `MigrationTestHelper` 校验 + schema JSON diff |
| 外键导致重建表失败 | 重建前 `PRAGMA foreign_keys=OFF`，完成后恢复 |
| 大表迁移耗时 | 迁移在 Room 首次打开时同步执行，数据量大时加进度提示 |
| 用户降级安装 | 旧版本无法打开新 schema，会崩；需在发版说明中提示「不要降级」 |
