package com.example.hidelauncher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

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
        // 尝试清除默认背景
        window.setBackgroundDrawable(null)
        
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
    val prefs = remember { context.getSharedPreferences("hide_launcher", Context.MODE_PRIVATE) }

    val allApps = remember { mutableStateListOf<AppInfo>().apply { addAll(loadApps(context, prefs)) } }
    
    var hidden by remember {
        mutableStateOf(prefs.getStringSet("hidden_packages", emptySet())?.toSet() ?: emptySet())
    }
    var locked by remember {
        mutableStateOf(prefs.getStringSet("locked_packages", emptySet())?.toSet() ?: emptySet())
    }
    var allHidden by rememberSaveable { mutableStateOf(false) }
    var longPressedApp by remember { mutableStateOf<AppInfo?>(null) }

    var draggedPackage by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val dockApps = remember(allApps.size) { allApps.take(4) }
    val dockPackages = dockApps.map { it.packageName }

    val desktopApps = allApps.filter { it.packageName !in dockPackages }
    
    val pageSize = 24
    val pageCount = if (desktopApps.isEmpty()) 1 else (desktopApps.size + pageSize - 1) / pageSize
    val pagerState = rememberPagerState(pageCount = { pageCount })

    fun persistHidden(newSet: Set<String>) {
        hidden = newSet
        prefs.edit().putStringSet("hidden_packages", newSet).apply()
    }

    fun persistLocked(newSet: Set<String>) {
        locked = newSet
        prefs.edit().putStringSet("locked_packages", newSet).apply()
    }

    fun saveOrder() {
        val order = allApps.joinToString(",") { it.packageName }
        prefs.edit().putString("app_order", order).apply()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 注意：这里绝对不要加 background，保持完全透明
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
            Column(modifier = Modifier.fillMaxSize()) {
                
                // 顶部时钟
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    var timeText by remember { mutableStateOf("") }
                    var dateText by remember { mutableStateOf("") }
                    LaunchedEffect(Unit) {
                        while (true) {
                            val now = Date()
                            timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                            dateText = SimpleDateFormat("M月d日 EEEE", Locale.getDefault()).format(now)
                            kotlinx.coroutines.delay(1000)
                        }
                    }
                    Text(text = timeText, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light)
                    Text(text = dateText, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                }

                // 中间 6x4 分页网格
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val startIndex = page * pageSize
                    val pageApps = desktopApps.drop(startIndex).take(pageSize)
                    
                    val visibleApps = pageApps.filter { app ->
                        if (app.packageName in locked) true
                        else app.packageName !in hidden
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        userScrollEnabled = false 
                    ) {
                        itemsIndexed(visibleApps, key = { _, app -> app.packageName }) { indexInPage, app ->
                            val isDragging = app.packageName == draggedPackage
                            
                            Box(
                                modifier = Modifier
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .offset { 
                                        if (isDragging) IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) 
                                        else IntOffset.Zero 
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedPackage = app.packageName
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                                
                                                val thresholdX = 30.dp.toPx()
                                                val thresholdY = 35.dp.toPx()
                                                
                                                val currentGlobalIdx = allApps.indexOfFirst { it.packageName == app.packageName }
                                                if (currentGlobalIdx == -1) return@detectDragGesturesAfterLongPress

                                                val desktopIdx = currentGlobalIdx - 4
                                                
                                                if (dragOffset.x > thresholdX) {
                                                    if (currentGlobalIdx + 1 < allApps.size && (desktopIdx + 1) % 6 != 0) {
                                                        java.util.Collections.swap(allApps, currentGlobalIdx, currentGlobalIdx + 1)
                                                        dragOffset = dragOffset.copy(x = dragOffset.x - thresholdX * 2)
                                                        saveOrder()
                                                    }
                                                } else if (dragOffset.x < -thresholdX) {
                                                    if (currentGlobalIdx - 1 >= 4 && desktopIdx % 6 != 0) {
                                                        java.util.Collections.swap(allApps, currentGlobalIdx, currentGlobalIdx - 1)
                                                        dragOffset = dragOffset.copy(x = dragOffset.x + thresholdX * 2)
                                                        saveOrder()
                                                    }
                                                }
                                                
                                                if (dragOffset.y > thresholdY) {
                                                    if (currentGlobalIdx + 6 < allApps.size) {
                                                        java.util.Collections.swap(allApps, currentGlobalIdx, currentGlobalIdx + 6)
                                                        dragOffset = dragOffset.copy(y = dragOffset.y - thresholdY * 2)
                                                        saveOrder()
                                                    }
                                                } else if (dragOffset.y < -thresholdY) {
                                                    if (currentGlobalIdx - 6 >= 4) {
                                                        java.util.Collections.swap(allApps, currentGlobalIdx, currentGlobalIdx - 6)
                                                        dragOffset = dragOffset.copy(y = dragOffset.y + thresholdY * 2)
                                                        saveOrder()
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                if (dragOffset.getDistance() < 15f) {
                                                    longPressedApp = app
                                                }
                                                draggedPackage = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggedPackage = null
                                                dragOffset = Offset.Zero
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { launchApp(context, app.packageName) }
                                        )
                                    }
                            ) {
                                AppItemContent(
                                    app = app,
                                    isLocked = app.packageName in locked
                                )
                            }
                        }
                    }
                }

                DockBar(
                    apps = dockApps,
                    hidden = hidden,
                    locked = locked,
                    onAppClick = { launchApp(context, it.packageName) },
                    onAppLongClick = { longPressedApp = it }
                )
            }
        } else {
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

@Composable
fun AppItemContent(app: AppInfo, isLocked: Boolean) {
    val bitmap = remember(app.packageName) {
        app.icon.toBitmap(144, 144).asImageBitmap()
    }

    Box(
        modifier = Modifier.padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = app.label,
            modifier = Modifier.size(60.dp)
        )
        if (isLocked) {
            Text(
                text = "🔒",
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 2.dp, end = 2.dp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 30.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.forEach { app ->
            if (app.packageName in locked || app.packageName !in hidden) {
                Box(
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onAppClick(app) },
                            onLongPress = { onAppLongClick(app) }
                        )
                    }
                ) {
                    AppItemContent(app = app, isLocked = app.packageName in locked)
                }
            }
        }
    }
}

private fun launchApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun loadApps(context: Context, prefs: SharedPreferences): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val allApps = pm.queryIntentActivities(intent, 0)
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
    
    val savedOrder = prefs.getString("app_order", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    
    if (savedOrder.isEmpty()) {
        return allApps.sortedBy { it.label.lowercase() }
    }
    
    val orderedList = mutableListOf<AppInfo>()
    savedOrder.forEach { pkg ->
        allApps.find { it.packageName == pkg }?.let { orderedList.add(it) }
    }
    allApps.forEach { app ->
        if (orderedList.none { it.packageName == app.packageName }) {
            orderedList.add(app)
        }
    }
    return orderedList
}
