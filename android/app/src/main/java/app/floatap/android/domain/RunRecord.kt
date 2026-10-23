package app.floatap.android.domain

data class RunRecord(
    val id: Long = 0,
    val jobId: Long?,
    val jobName: String,
    val startedAt: Long,
    val endedAt: Long,
    val stopReason: StopReason,
    val clicks: Int,
)

fun StopReason.label(): String = when (this) {
    StopReason.USER -> "用户停止"
    StopReason.DURATION -> "到达时长"
    StopReason.CLICK_LIMIT -> "到达点击次数"
    StopReason.LOOP_LIMIT -> "循环完成"
    StopReason.SCHEDULE_END -> "到达结束时刻"
    StopReason.PERMISSION -> "权限或手势失败"
    StopReason.DISPLAY_CHANGED -> "屏幕尺寸变化"
    StopReason.COLOR_MISMATCH -> "颜色条件不匹配"
}
