package com.tbtechs.focusflow.data.repository

internal object ActiveBlockGuardPolicy {
    fun isActive(
        focusActive: Boolean,
        focusEndMs: Long,
        standaloneActive: Boolean,
        standaloneUntilMs: Long,
        nowMs: Long,
    ): Boolean =
        isSessionActive(focusActive, focusEndMs, nowMs) ||
            isSessionActive(standaloneActive, standaloneUntilMs, nowMs)

    private fun isSessionActive(active: Boolean, endsAtMs: Long, nowMs: Long): Boolean =
        active && (endsAtMs <= 0L || nowMs < endsAtMs)
}
