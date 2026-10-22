package com.example.hidelauncher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 关键：让窗口透出壁纸
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        )
        
        setContent {
            MaterialTheme {
                LauncherScreen()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val apps = remember { loadApps(context) }
    val prefs = remember { context.getSharedPreferences("hide_launcher", Context.MODE_PRIVATE) }

    var hidden by remember {
        mutableStateOf(prefs.getStringSet("hidden_packages", emptySet())?.toSet() ?: emptySet())
    }
    var locked by remember {
        mutableStateOf(prefs.getStringSet("locked_packages", emptySet())?.toSet() ?: emptySet())
    }
    var allHidden by rememberSaveable { mutableStateOf(false) }
    var longPressedApp by remember { mutableStateOf<AppInfo?>(null) }

    // 划分 Dock 栏应用 (取前4个)
    val dockApps = remember(apps) { apps.take(4) }
    val dockPackageNames = dockApps.map { it.packageName }.toSet()

    // 桌面上其余的应用
    val desktopApps = remember(apps) { apps.filter { it.packageName !in dockPackageNames } }

    fun persistHidden(newSet: Set<String>) {
        hidden = newSet
        prefs.edit().putStringSet("hidden_packages", newSet).apply()
    }

    fun persistLocked(newSet: Set<String>) {
        locked = newSet
        prefs.edit().putStringSet("locked_packages", newSet).apply()
    }

    // 根视图：透明背景，处理双击
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (allHidden) {
                            allHidden = false
                            persistHidden(emptySet())
                        } else {
                            allHidden = true
                        }
                    }
                )
            }
    ) {
        if (!allHidden) {
            // ======= 正常显示状态 =======
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 中间应用网格 (支持上下滑动)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 60.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    val visibleApps = desktopApps.filter { app ->
                        if (app.packageName in locked) true
                        else app.packageName !in hidden
                    }
                    items(visibleApps, key = { it.packageName }) { app ->
                        AppItem(
                            app = app,
                            isLocked = app.packageName in locked,
                            onClick = { launchApp(context, app.packageName) },
                            onLongClick = { longPressedApp = app }
                        )
                    }
                }

                // 底部 Dock 栏
                DockBar(
                    apps = dockApps,
                    hidden = hidden,
                    locked = locked,
                    onAppClick = { launchApp(context, it.packageName) },
                    onAppLongClick = { longPressedApp = it }
                )
            }
        } else {
            // ======= 全部隐藏状态 =======
            Text(
                text = "已全部隐藏 · 双击空白处恢复",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }
    }

    // 长按应用弹窗
    longPressedApp?.let { app ->
        val isLocked = app.packageName in locked
        val isHidden = app.packageName in hidden

        AlertDialog(
            onDismissRequest = { longPressedApp = null },
            title = { Text(app.label) },
            text = {
                Column {
                    Text(
                        text = when {
                            isLocked -> "已锁定：双击全隐藏时仍会显示"
                            isHidden -> "已单独隐藏：双击空白处恢复"
                            else -> "选择操作"
                        },
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            if (isLocked) {
                                persistLocked(locked - app.packageName)
                            } else {
                                persistLocked(locked + app.packageName)
                                if (isHidden) persistHidden(hidden - app.packageName)
                            }
                            longPressedApp = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isLocked) "解除锁定" else "锁定应用（防隐藏）")
                    }

                    if (!isLocked) {
                        TextButton(
                            onClick = {
                                persistHidden(hidden + app.packageName)
                                longPressedApp = null
                            },
                            enabled = !isHidden,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isHidden) "已单独隐藏" else "单独隐藏此应用")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { longPressedApp = null }) { Text("关闭") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    app: AppInfo,
    isLocked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bitmap = remember(app.packageName) {
        app.icon.toBitmap(144, 144).asImageBitmap()
    }

    Box(
        modifier = Modifier
            .padding(8.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = app.label,
            modifier = Modifier.size(52.dp)
        )
        if (isLocked) {
            Text(
                text = "🔒",
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 4.dp, end = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockBar(
    apps: List<AppInfo>,
    hidden: Set<String>,
    locked: Set<String>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit
) {
    // 底部固定栏
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.forEach { app ->
            // 根据隐藏状态决定是否显示
            if (app.packageName in locked || app.packageName !in hidden) {
                AppItem(
                    app = app,
                    isLocked = app.packageName in locked,
                    onClick = { onAppClick(app) },
                    onLongClick = { onAppLongClick(app) }
                )
            }
        }
    }
}

private fun launchApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .mapNotNull { it.activityInfo?.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map {
            AppInfo(
                label = it.loadLabel(pm).toString(),
                packageName = it.packageName,
                icon = it.loadIcon(pm)
            )
        }
        .sortedBy { it.label.lowercase() }
}
