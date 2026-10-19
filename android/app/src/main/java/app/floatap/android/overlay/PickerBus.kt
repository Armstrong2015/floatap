package app.floatap.android.overlay

data class PickResult(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val stepIndex: Int = 0,
)

object PickerBus {
    @Volatile
    var pending: PickResult? = null
}
