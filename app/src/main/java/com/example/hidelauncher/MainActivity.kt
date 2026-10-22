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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        )
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
    var longPressOffset by remember { mutableStateOf(Offset.Zero) }

    var draggedPackage by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val dockApps = remember(allApps.size) { allApps.take(4) }
    val dockPackages = dockApps.map { it.packageName }

    val visibleDesktopApps = allApps.filter { 
        it.packageName !in dockPackages && (!allHidden || it.packageName in locked) 
    }
    val visibleDockApps = dockApps.filter { 
        !allHidden || it.packageName in locked 
    }
    
    val pageSize = 24
    val pageCount = if (visibleDesktopApps.isEmpty()) 1 else (visibleDesktopApps.size + pageSize - 1) / pageSize
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
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var isDoubleTap = false
                    try {
                        withTimeout(300) {
                            awaitFirstDown()
                            isDoubleTap = true
                        }
                    } catch (_: Exception) {}
                    
                    if (isDoubleTap) {
                        if (allHidden) {
                            allHidden = false
                            persistHidden(emptySet())
                        } else {
                            allHidden = true
                        }
                    }
                }
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                if (longPressedApp != null) longPressedApp = null
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
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
                        delay(1000)
                    }
                }
                Text(text = timeText, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light)
                Text(text = dateText, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val startIndex = page * pageSize
                val pageApps = visibleDesktopApps.drop(startIndex).take(pageSize)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    userScrollEnabled = false 
                ) {
                    itemsIndexed(pageApps, key = { _, app -> app.packageName }) { _, app ->
                        val isDragging = app.packageName == draggedPackage
                        
                        Box(
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .offset { 
                                    if (isDragging) IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) 
                                    else IntOffset.Zero 
                                }
                                .pointerInput(app.packageName) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        
                                        var isLongPress = false
                                        var isDrag = false
                                        var currentDragOffset = Offset.Zero
                                        longPressOffset = down.position

                                        val longPressJob = launch {
                                            delay(viewConfiguration.longPressTimeoutMillis)
                                            isLongPress = true
                                            longPressedApp = app
                                            draggedPackage = app.packageName 
                                        }
                                        
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.first()
                                            
                                            if (change.changedToUp()) {
                                                if (!isLongPress && !isDrag) {
                                                    launchApp(context, app.packageName)
                                                }
                                                break
                                            } else if (change.positionChange() != Offset.Zero) {
                                                if (isLongPress) {
                                                    isDrag = true
                                                    val dragAmount = change.positionChange()
                                                    currentDragOffset += dragAmount
                                                    dragOffset = currentDragOffset
                                                    change.consume()
                                                    
                                                    val thresholdX = 40.dp.toPx()
                                                    val thresholdY = 50.dp.toPx()
                                                    
                                                    val currentGlobalIdx = allApps.indexOfFirst { it.packageName == app.packageName }
                                                    if (currentGlobalIdx == -1) return@awaitEachGesture

                                                    val desktopIdx = currentGlobalIdx - 4
                                                    
                                                    if (dragOffset.x > thresholdX) {
                                                        if (currentGlobalIdx + 1 < allApps.size && (desktopIdx + 1) % 4 != 0) {
                                                            java.util.Collections.swap(allApps, currentGlobalIdx, currentGlobalIdx + 1)
                                                            dragOffset = dragOffset.copy(x = dragOffset.x - thresholdX * 2)
                                                            saveOrder()
                                                        }
                                                    } else if (dragOffset.x < -thresholdX) {
                                                        if (currentGlobalIdx - 1 >= 4 && desktopIdx % 4 != 0) {
                                                            java.util.Collections.swap(allApps, currentGlobalIdx, currentGlobalIdx - 1)
                                                            dragOffset = dragOffset.copy(x = dragOffset.x + thresholdX * 2)
                                                            saveOrder()
                                                        }
                                                    }
                                                    
                                                    if (dragOffset.y > thresholdY) {
                                                        if (currentGlobalIdx + 4 < allApps.size) {
                                                            java.util.Collections.swap(allApps, currentGlobalIdx, currentGlobalIdx + 4)
                                                            dragOffset = dragOffset.copy(y = dragOffset.y - thresholdY * 2)
                                                            saveOrder()
                                                        }
                                                    } else if (dragOffset.y < -thresholdY) {
                                                        if (currentGlobalIdx - 4 >= 4) {
                                                            java.util.Collections.swap(allApps, currentGlobalIdx, currentGlobalIdx - 4)
                                                            dragOffset = dragOffset.copy(y = dragOffset.y + thresholdY * 2)
                                                            saveOrder()
                                                        }
                                                    }
                                                } else {
                                                    longPressJob.cancel()
                                                }
                                            }
                                        }
                                        longPressJob.cancel()
                                        
                                        if (isDrag) {
                                            draggedPackage = null
                                            dragOffset = Offset.Zero
                                        }
                                    }
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 30.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleDockApps.forEach { app ->
                    Box(
                        modifier = Modifier
                            .pointerInput(app.packageName) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val longPressJob = launch {
                                        delay(viewConfiguration.longPressTimeoutMillis)
                                        longPressedApp = app
                                        longPressOffset = down.position
                                    }
                                    
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.first()
                                        if (change.changedToUp()) {
                                            if (longPressJob.isActive) {
                                                launchApp(context, app.packageName)
                                            }
                                            break
                                        } else if (change.positionChange() != Offset.Zero) {
                                            longPressJob.cancel()
                                            break
                                        }
                                    }
                                    longPressJob.cancel()
                                }
                            }
                    ) {
                        AppItemContent(app = app, isLocked = app.packageName in locked)
                    }
                }
            }
        }

        // 删除了底部的“已全部隐藏”文字提示

        longPressedApp?.let { app ->
            val isLocked = app.packageName in locked
            val isHidden = app.packageName in hidden

            Popup(
                alignment = Alignment.TopStart,
                offset = androidx.compose.ui.unit.IntOffset(
                    x = longPressOffset.x.roundToInt(),
                    y = (longPressOffset.y + 80).roundToInt()
                ),
                onDismissRequest = { longPressedApp = null },
                properties = PopupProperties(focusable = true)
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(30.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isLocked) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    if (isHidden) persistHidden(hidden - app.packageName) 
                                    else persistHidden(hidden + app.packageName)
                                    longPressedApp = null
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isHidden) "取消隐藏" else "隐藏",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (!isLocked && !isHidden) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.3f)))
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                if (isLocked) {
                                    persistLocked(locked - app.packageName)
                                } else {
                                    persistLocked(locked + app.packageName)
                                    if (isHidden) persistHidden(hidden - app.packageName)
                                }
                                longPressedApp = null
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isLocked) "解除锁定" else "锁定",
                            color = if (isLocked) Color(0xFFFFC107) else Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppItemContent(app: AppInfo, isLocked: Boolean) {
    val bitmap = remember(app.packageName) {
        app.icon.toBitmap(144, 144).asImageBitmap()
    }

    Box(
        modifier = Modifier.padding(8.dp),
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
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 2.dp, end = 2.dp)
            )
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
