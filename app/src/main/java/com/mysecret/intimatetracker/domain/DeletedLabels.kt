package com.mysecret.intimatetracker.domain

/**
 * 已删除内容的统一标记。
 *
 * 统计、记录列表、记录详情都按「历史真相」展示已删内容，并在名字后加这个后缀。
 * 注意：`RecordDao` 的两条记录查询在 SQL 里硬编码了同一串（无法跨语言共享常量），
 * 改这里时必须同步改 SQL。
 */
const val DELETED_SUFFIX = "（已删除）"

fun String.withDeletedMarker(deleted: Boolean): String = if (deleted) this + DELETED_SUFFIX else this

fun String.withoutDeletedMarker(): String = removeSuffix(DELETED_SUFFIX)