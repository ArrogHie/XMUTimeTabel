package com.lingion.sleepy.ui.screen.mine

import androidx.compose.runtime.Composable

/**
 * release 版「手动触发赞赏弹窗」占位 — 空实现。
 *
 * 真按钮在 `src/debug` 的同名文件里(源集覆盖: debug 编译真按钮 / release 编译此空实现),
 * 因此正式包里不含按钮代码与文案。main 源集里 MineScreen 统一调用 [DebugDonateTrigger]。
 */
@Composable
fun DebugDonateTrigger() {
    // no-op: 正式版无调试入口
}
