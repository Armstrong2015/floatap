package app.floatap.android.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.floatap.android.Prefs
import app.floatap.android.R
import app.floatap.android.canDrawOverlay
import app.floatap.android.engine.FloatRunService
import app.floatap.android.engine.RunBus
import app.floatap.android.isAccessibilityEnabled
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("使用边界") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.boundary), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = {
                Prefs(context).boundaryAccepted = true
                onContinue()
            }) { Text("继续") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onCreate: () -> Unit, onEdit: (Long) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel()
    val jobs by vm.jobs.collectAsState()
    val notice = vm.message ?: RunBus.message.collectAsState().value
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("浮序") },
                actions = { TextButton(onClick = onSettings) { Text("设置") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PermissionReminder(onSettings)
            if (Build.VERSION.SDK_INT >= 33) {
                TextButton(onClick = { notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("允许通知，以便运行时停止")
                }
            }
            notice?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = {
                    vm.clearMessage()
                    RunBus.message.value = null
                }) { Text("知道了") }
            }
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("新建作业") }
            if (jobs.isEmpty()) {
                Text("先打开悬浮窗和辅助功能，再新建一个单点作业。")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(jobs, key = { it.id }) { job ->
                        Card(onClick = { onEdit(job.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(job.name, style = MaterialTheme.typography.titleMedium)
                                Text("${job.stepCount} 个步骤 · ${formatTime(job.updatedAt)}")
                                TextButton(onClick = { vm.delete(job.id) }) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        if (context.canDrawOverlay()) FloatRunService.showBall(context)
        onDispose { }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(jobId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: EditViewModel = viewModel(key = "edit-$jobId", factory = EditViewModel.factory(jobId))
    val ui = vm.state
    val collapse by RunBus.collapseHost.collectAsState()
    LaunchedEffect(collapse) {
        if (!collapse) return@LaunchedEffect
        RunBus.collapseHost.value = false
        (context as? Activity)?.moveTaskToBack(true)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.consumePick()
                vm.refreshPoint()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (jobId == 0L) "新建作业" else "编辑作业") },
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
        )
    }) { padding ->
        if (!ui.ready) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = ui.name,
                onValueChange = { value -> vm.update { it.copy(name = value) } },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (ui.x < 0) "还没有选点" else "坐标 ${ui.x}, ${ui.y} · ${ui.displayWidth}×${ui.displayHeight}",
            )
            FilledTonalButton(onClick = {
                if (!context.canDrawOverlay()) {
                    vm.update { it.copy(error = "先打开悬浮窗权限") }
                    return@FilledTonalButton
                }
                vm.save(allowMissingPoint = true) {
                    vm.update {
                        it.copy(
                            error = null,
                            warning = "已切换到这份作业。打开要点的应用，点圆点，再点「选点」。",
                        )
                    }
                    FloatRunService.showBall(context, collapse = true)
                }
            }) { Text("准备选点") }
            OutlinedTextField(
                value = ui.intervalText,
                onValueChange = { value -> vm.update { it.copy(intervalText = value.filter(Char::isDigit)) } },
                label = { Text(if (ui.randomInterval) "最小间隔（毫秒）" else "间隔（毫秒）") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("随机间隔")
                Switch(checked = ui.randomInterval, onCheckedChange = { checked -> vm.update { it.copy(randomInterval = checked) } })
            }
            if (ui.randomInterval) {
                OutlinedTextField(
                    value = ui.intervalMaxText,
                    onValueChange = { value -> vm.update { it.copy(intervalMaxText = value.filter(Char::isDigit)) } },
                    label = { Text("最大间隔（毫秒）") },
                    supportingText = { Text("每次等待在这两个时间之间随机取值") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = ui.durationText,
                onValueChange = { value -> vm.update { it.copy(durationText = value.filter(Char::isDigit)) } },
                label = { Text("持续时长（秒）") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.clickLimitText,
                onValueChange = { value -> vm.update { it.copy(clickLimitText = value.filter(Char::isDigit)) } },
                label = { Text("点击次数，可留空") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("开始倒计时 ${ui.countdown} 秒")
            Slider(
                value = ui.countdown.toFloat(),
                onValueChange = { value -> vm.update { it.copy(countdown = value.toInt()) } },
                valueRange = 0f..10f,
                steps = 9,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("触摸屏幕时暂停")
                Switch(checked = ui.pauseOnTouch, onCheckedChange = { checked -> vm.update { it.copy(pauseOnTouch = checked) } })
            }
            PermissionReminder(onSettings = null)
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ui.warning?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(onClick = { vm.save { } }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
            Button(
                onClick = {
                    val blocked = permissionBlock(context.canDrawOverlay(), context.isAccessibilityEnabled())
                    if (blocked != null) {
                        vm.update { it.copy(error = blocked) }
                        return@Button
                    }
                    vm.save { id ->
                        FloatRunService.startJob(context, id)
                        vm.update {
                            it.copy(warning = "已开始「${ui.name.trim()}」。请从最近任务切到要点的应用，倒计时结束后才会点击。")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (ui.name.isBlank()) "开始这一份" else "开始「${ui.name.trim()}」") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("设置") },
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (context.canDrawOverlay()) "悬浮窗已开启" else "悬浮窗未开启")
            if (!context.canDrawOverlay()) {
                Button(onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                    context.startActivity(intent)
                }) { Text("打开悬浮窗设置") }
            }
            Text(if (context.isAccessibilityEnabled()) "辅助功能已开启" else "辅助功能未开启")
            if (!context.isAccessibilityEnabled()) {
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("打开辅助功能设置") }
            }
            Text(stringResource(R.string.boundary))
        }
    }
}

@Composable
private fun PermissionReminder(onSettings: (() -> Unit)?) {
    val context = LocalContext.current
    var overlay by remember { mutableStateOf(context.canDrawOverlay()) }
    var accessibility by remember { mutableStateOf(context.isAccessibilityEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlay = context.canDrawOverlay()
                accessibility = context.isAccessibilityEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val message = permissionBlock(overlay, accessibility) ?: return
    Text(message, color = MaterialTheme.colorScheme.error)
    if (onSettings != null) {
        TextButton(onClick = onSettings) { Text("去设置") }
    }
}

private fun permissionBlock(overlay: Boolean, accessibility: Boolean): String? {
    return when {
        !accessibility -> "辅助功能还没打开，浮序无法点击。请到设置里打开「浮序」。"
        !overlay -> "悬浮窗还没打开。请到设置里允许显示在其他应用上层。"
        else -> null
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))
}
