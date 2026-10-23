package app.floatap.android.domain

import org.json.JSONArray
import org.json.JSONObject

class JobJsonException(message: String) : Exception(message)

fun jobToJson(job: Job): String {
    val root = JSONObject()
    root.put("format", 1)
    root.put("name", job.name)
    root.put("countdownSec", job.countdownSec)
    root.put("pauseOnUserTouch", job.pauseOnUserTouch)
    root.put("durationMs", job.durationMs ?: JSONObject.NULL)
    root.put("clickLimit", job.clickLimit ?: JSONObject.NULL)
    root.put("loopLimit", job.loopLimit ?: JSONObject.NULL)
    root.put("scheduleStart", job.scheduleStartMinutes ?: JSONObject.NULL)
    root.put("scheduleEnd", job.scheduleEndMinutes ?: JSONObject.NULL)
    val steps = JSONArray()
    for (step in job.steps) {
        val item = JSONObject()
        item.put("name", step.name)
        item.put("x", step.x)
        item.put("y", step.y)
        item.put("displayWidth", step.displayWidth)
        item.put("displayHeight", step.displayHeight)
        item.put("rotation", step.rotation)
        val wait = JSONObject()
        wait.put("mode", if (step.waitMode == WaitMode.RANGE) "range" else "fixed")
        wait.put("minMs", step.waitMinMs)
        wait.put("maxMs", step.waitMaxMs)
        item.put("wait", wait)
        val color = step.color
        if (color != null) {
            val c = JSONObject()
            c.put("x", color.x)
            c.put("y", color.y)
            c.put("argb", color.sampleArgb)
            c.put("tolerance", color.tolerance)
            c.put("onMismatch", if (color.onMismatch == ColorMismatchAction.SKIP) "skip" else "stop")
            item.put("color", c)
        }
        steps.put(item)
    }
    root.put("steps", steps)
    return root.toString(2)
}

fun jobFromJson(text: String): Job {
    val root = try {
        JSONObject(text)
    } catch (_: Exception) {
        throw JobJsonException("不是有效的 JSON")
    }
    val format = root.optInt("format", -1)
    if (format != 1) throw JobJsonException("不支持的 format，只要 1")
    val name = root.optString("name").trim()
    if (name.isEmpty()) throw JobJsonException("作业名称不能为空")
    val stepsJson = root.optJSONArray("steps") ?: throw JobJsonException("缺少 steps")
    if (stepsJson.length() == 0) throw JobJsonException("至少要有一步")
    val steps = mutableListOf<Step>()
    for (i in 0 until stepsJson.length()) {
        val item = stepsJson.getJSONObject(i)
        val wait = item.optJSONObject("wait") ?: JSONObject()
        val mode = wait.optString("mode", "fixed")
        val minMs = wait.optLong("minMs", 500)
        val maxMs = wait.optLong("maxMs", minMs)
        val colorObj = item.optJSONObject("color")
        val color = if (colorObj == null) {
            null
        } else {
            ColorCondition(
                x = colorObj.getInt("x"),
                y = colorObj.getInt("y"),
                sampleArgb = colorObj.getInt("argb"),
                tolerance = colorObj.optInt("tolerance", 32),
                onMismatch = if (colorObj.optString("onMismatch") == "skip") {
                    ColorMismatchAction.SKIP
                } else {
                    ColorMismatchAction.STOP
                },
            )
        }
        steps += Step(
            order = i,
            name = item.optString("name", "点击").ifBlank { "点击" },
            x = item.getInt("x"),
            y = item.getInt("y"),
            displayWidth = item.getInt("displayWidth"),
            displayHeight = item.getInt("displayHeight"),
            rotation = item.optInt("rotation", 0),
            waitMode = if (mode == "range") WaitMode.RANGE else WaitMode.FIXED,
            waitMinMs = minMs,
            waitMaxMs = maxMs,
            color = color,
        )
    }
    return Job(
        name = name,
        steps = steps,
        durationMs = root.nullableLong("durationMs"),
        clickLimit = root.nullableInt("clickLimit"),
        loopLimit = root.nullableInt("loopLimit"),
        scheduleStartMinutes = root.nullableInt("scheduleStart"),
        scheduleEndMinutes = root.nullableInt("scheduleEnd"),
        countdownSec = root.optInt("countdownSec", 3),
        pauseOnUserTouch = root.optBoolean("pauseOnUserTouch", true),
    )
}

private fun JSONObject.nullableLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return getLong(key)
}

private fun JSONObject.nullableInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return getInt(key)
}
