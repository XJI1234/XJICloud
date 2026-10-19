package com.xjicloud.native3dgs

/** Process engine slot; a reservation belongs to exactly one view. */
internal class EngineAdmission {
    internal enum class Stage { Reserved, Active, Closing, InitializationUnknown, CloseFailed }
    private var owner: Reservation? = null
    @Synchronized fun reserve(): Reservation? = if (owner != null) null
        else Reservation().also { owner = it }
    @Synchronized fun blockedMessage(): String = when (owner?.stage) {
        Stage.InitializationUnknown -> "上一引擎初始化状态无法确认，请重启应用。"
        Stage.CloseFailed -> "上一引擎关闭失败，请重启应用。"
        else -> "上一引擎仍在使用或关闭中，请等待。"
    }
    inner class Reservation internal constructor() {
        internal var stage = Stage.Reserved
        fun constructed() = synchronized(this@EngineAdmission) {
            check(owner === this && stage == Stage.Reserved)
            stage = Stage.Active
        }
        fun initializationFailed(knownNoEngine: Boolean) = synchronized(this@EngineAdmission) {
            if (owner === this && stage == Stage.Reserved) {
                if (knownNoEngine) owner = null else stage = Stage.InitializationUnknown
            }
        }
        fun beginClosing() = synchronized(this@EngineAdmission) {
            check(owner === this && stage == Stage.Active)
            stage = Stage.Closing
        }
        fun closeCompleted(success: Boolean) = synchronized(this@EngineAdmission) {
            if (owner === this && stage == Stage.Closing) {
                if (success) owner = null else stage = Stage.CloseFailed
            }
        }
    }
}
