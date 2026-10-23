package com.mysecret.intimatetracker.data.sync

/**
 * 本地数据变更后的同步触发点。
 *
 * 仓库层只关心「这次改动需要同步」这一件事，不需要知道调度器怎么排任务、
 * 依赖哪些组件。把它收窄成一个函数式接口后：
 * - 生产代码由 Hilt 绑定到 [SyncScheduler]；
 * - 测试可以传空实现或记录型实现，不必构造整条 WorkManager 依赖链。
 */
fun interface SyncTrigger {
    fun onLocalChange()
}
