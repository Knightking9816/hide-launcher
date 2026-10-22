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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

class DesktopItem(
    val id: String, 
    var name: String, 
    val packageNames: MutableList<String>, 
    var isFolder: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        )
        window.setBackgroundDrawable(null)
        setContent { MaterialTheme { LauncherScreen() } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hide_launcher_v3", Context.MODE_PRIVATE) }
    val density = LocalDensity.current

    val allAppsMap = remember { loadAllApps(context) }
    val desktopItems = remember { mutableStateListOf<DesktopItem>().apply { addAll(loadDesktopItems(context, prefs, allAppsMap)) } }
    // 记录每个 Item 在屏幕上的位置，用于拖拽合并计算
    val itemRects = remember { mutableStateMapOf<String, Rect>() }
    
    var hidden by remember { mutableStateOf(prefs.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()) }
    var locked by remember { mutableStateOf(prefs.getStringSet("locked", emptySet())?.toSet() ?: emptySet()) }
    var allHidden by rememberSaveable { mutableStateOf(false) }
    
    var longPressedItem by remember { mutableStateOf<DesktopItem?>(null) }
    var longPressPosition by remember { mutableStateOf(Offset.Zero) }
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    var openedFolder by remember { mutableStateOf<DesktopItem?>(null) }
    var renameFolder by remember { mutableStateOf<DesktopItem?>(null) }

    fun saveItems() {
        val str = desktopItems.joinToString(";") { "${it.id},${it.name},${it.isFolder},${it.packageNames.joinToString("|")}" }
        prefs.edit().putString("desktop_layout", str).apply()
    }
    fun persistHidden(newSet: Set<String>) { hidden = newSet; prefs.edit().putStringSet("hidden", newSet).apply() }
    fun persistLocked(newSet: Set<String>) { locked = newSet; prefs.edit().putStringSet("locked", newSet).apply() }

    val dockItems = remember(desktopItems.size) { desktopItems.take(4) }
    val dockIds = dockItems.map { it.id }
    val gridItems = desktopItems.filter { it.id !in dockIds }
    
    val pageSize = 24
    val pageCount = if (gridItems.isEmpty()) 1 else (gridItems.size + pageSize - 1) / pageSize
    val pagerState = rememberPagerState(pageCount = { pageCount })

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
                        longPressedItem = null
                    },
                    onTap = { longPressedItem = null }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 顶部时钟
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 60.dp, bottom = 20.dp),
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

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val startIndex = page * pageSize
                val pageItems = gridItems.drop(startIndex).take(pageSize)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    userScrollEnabled = false 
                ) {
                    itemsIndexed(pageItems, key = { _, item -> item.id }) { _, item ->
                        val isDragging = item.id == draggedItemId
                        var itemPosition by remember { mutableStateOf(Offset.Zero) }
                        
                        val isItemLocked = item.packageNames.any { it in locked }
                        val isItemHidden = if (item.isFolder) item.packageNames.all { it in hidden } else item.packageNames[0] in hidden
                        val shouldHide = if (allHidden) !isItemLocked else (isItemHidden && !isItemLocked)

                        Box(
                            modifier = Modifier
                                .offset { if (isDragging) IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) else IntOffset.Zero }
                                .onGloballyPositioned { 
                                    itemPosition = it.positionInRoot()
                                    itemRects[item.id] = it.boundsInRoot()
                                }
                                .pointerInput(item.id) {
                                    detectTapGestures(
                                        onTap = {
                                            // 【修复2】：如果是隐藏状态，拦截点击，绝不打开应用
                                            if (shouldHide) return@detectTapGestures
                                            
                                            if (longPressedItem == null) {
                                                if (item.isFolder) openedFolder = item
                                                else launchApp(context, item.packageNames[0])
                                            }
                                        }
                                    )
                                }
                                .pointerInput(item.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { 
                                            longPressPosition = itemPosition
                                            longPressedItem = item
                                            draggedItemId = item.id
                                            dragOffset = Offset.Zero
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount
                                            // 如果手指发生了移动，就隐藏长按菜单，表示用户正在拖拽
                                            if (dragOffset.getDistance() > 20f) {
                                                longPressedItem = null
                                            }
                                        },
                                        onDragEnd = {
                                            if (dragOffset.getDistance() < 20f) {
                                                // 手指没怎么动，当成普通的长按弹菜单
                                                longPressedItem = item
                                            } else {
                                                // 【修复1】：拖拽结束，计算松手坐标，判断是否重叠
                                                val startRect = itemRects[item.id]
                                                if (startRect != null) {
                                                    val finalCenter = startRect.center + dragOffset
                                                    
                                                    var closestItem: DesktopItem? = null
                                                    var minDist = Float.MAX_VALUE
                                                    
                                                    // 寻找拖拽位置附近最近的 Item
                                                    pageItems.forEach { other ->
                                                        if (other.id != item.id) {
                                                            itemRects[other.id]?.let { otherRect ->
                                                                val dist = (otherRect.center - finalCenter).getDistance()
                                                                if (dist < minDist) {
                                                                    minDist = dist
                                                                    closestItem = other
                                                                }
                                                            }
                                                        }
                                                    }

                                                    if (closestItem != null) {
                                                        if (minDist < 120f) {
                                                            // 距离极近，触发合并逻辑
                                                            val target = closestItem!!
                                                            if (item.isFolder) {
                                                                if (target.isFolder) {
                                                                    target.packageNames.addAll(item.packageNames)
                                                                    desktopItems.remove(item)
                                                                } else {
                                                                    item.packageNames.add(target.packageNames[0])
                                                                    desktopItems.remove(target)
                                                                }
                                                            } else {
                                                                if (target.isFolder) {
                                                                    target.packageNames.add(item.packageNames[0])
                                                                    desktopItems.remove(item)
                                                                } else {
                                                                    // 两个普通软件合并
                                                                    val newFolder = DesktopItem(
                                                                        id = UUID.randomUUID().toString(),
                                                                        name = "新建文件夹",
                                                                        packageNames = mutableListOf(item.packageNames[0], target.packageNames[0]),
                                                                        isFolder = true
                                                                    )
                                                                    desktopItems[desktopItems.indexOf(target)] = newFolder
                                                                    desktopItems.remove(item)
                                                                }
                                                            }
                                                            saveItems()
                                                        } else {
                                                            // 没有合并，只是单纯地移动位置
                                                            val fromIndex = desktopItems.indexOf(item)
                                                            val toIndex = desktopItems.indexOf(closestItem!!)
                                                            if (fromIndex != -1 && toIndex != -1) {
                                                                val moved = desktopItems.removeAt(fromIndex)
                                                                desktopItems.add(toIndex, moved)
                                                                saveItems()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            draggedItemId = null
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = { draggedItemId = null; dragOffset = Offset.Zero }
                                    )
                                }
                        ) {
                            if (!shouldHide) {
                                DesktopItemContent(
                                    item = item,
                                    allAppsMap = allAppsMap,
                                    isLocked = isItemLocked
                                )
                            } else {
                                // 隐形占位，不渲染内容，不可点击
                                Box(modifier = Modifier.size(76.dp))
                            }
                        }
                    }
                }
            }

            // 底部 Dock 栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 30.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dockItems.forEach { item ->
                    var itemPosition by remember { mutableStateOf(Offset.Zero) }
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { itemPosition = it.positionInRoot() }
                            .pointerInput(item.id) {
                                detectTapGestures(
                                    onTap = { 
                                        val isItemHidden = if (item.isFolder) item.packageNames.all { it in hidden } else item.packageNames[0] in hidden
                                        if (allHidden && item.packageNames.none { it in locked }) return@detectTapGestures
                                        if (!allHidden && isItemHidden) return@detectTapGestures
                                        
                                        if (longPressedItem == null) launchApp(context, item.packageNames[0]) 
                                    },
                                    onLongPress = { longPressPosition = itemPosition; longPressedItem = item }
                                )
                            }
                    ) {
                        val isItemLocked = item.packageNames.any { it in locked }
                        val isItemHidden = if (item.isFolder) item.packageNames.all { it in hidden } else item.packageNames[0] in hidden
                        val shouldHide = if (allHidden) !isItemLocked else (isItemHidden && !isItemLocked)
                        if (!shouldHide) {
                            DesktopItemContent(item, allAppsMap, isItemLocked)
                        } else {
                            Box(modifier = Modifier.size(76.dp))
                        }
                    }
                }
            }
        }

        // 悬浮菜单
        longPressedItem?.let { item ->
            val isLocked = item.packageNames.all { it in locked }
            val isHidden = if (item.isFolder) item.packageNames.all { it in hidden } else item.packageNames[0] in hidden
            val yOffsetPx = with(density) { 80.dp.toPx() }

            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(longPressPosition.x.roundToInt(), (longPressPosition.y + yOffsetPx).roundToInt()),
                onDismissRequest = { longPressedItem = null },
                properties = PopupProperties(focusable = true)
            ) {
                Row(
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(30.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isFolder) {
                        Text("重命名", color = Color.White, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { renameFolder = item; longPressedItem = null }.padding(12.dp))
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.3f)))
                        Text("解散", color = Color.White, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                            val idx = desktopItems.indexOf(item)
                            val appItems = item.packageNames.map { DesktopItem(UUID.randomUUID().toString(), allAppsMap[it]?.label ?: it, mutableListOf(it), false) }
                            desktopItems.removeAt(idx)
                            desktopItems.addAll(idx, appItems)
                            saveItems()
                            longPressedItem = null
                        }.padding(12.dp))
                    } else {
                        if (!isLocked) {
                            Text("隐藏", color = Color.White, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { 
                                val pkgs = item.packageNames[0]
                                if (isHidden) persistHidden(hidden - pkgs) else persistHidden(hidden + pkgs)
                                longPressedItem = null
                            }.padding(12.dp))
                            Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.3f)))
                        }
                        
                        Text(if (isLocked) "解锁" else "锁定", color = if (isLocked) Color(0xFFFFC107) else Color.White, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                            val pkgs = item.packageNames[0]
                            if (isLocked) persistLocked(locked - pkgs) else { persistLocked(locked + pkgs); if (isHidden) persistHidden(hidden - pkgs) }
                            longPressedItem = null
                        }.padding(12.dp))
                    }
                }
            }
        }

        // 重命名弹窗
        renameFolder?.let { folder ->
            var inputText by remember { mutableStateOf(folder.name) }
            AlertDialog(
                onDismissRequest = { renameFolder = null },
                title = { Text("重命名文件夹") },
                text = { OutlinedTextField(value = inputText, onValueChange = { inputText = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = { folder.name = inputText; saveItems(); renameFolder = null }) { Text("确定") }
                }
            )
        }

        // 打开文件夹弹窗
        openedFolder?.let { folder ->
            Dialog(onDismissRequest = { openedFolder = null }) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(24.dp)).padding(16.dp)
                ) {
                    Text(folder.name, color = Color.White, fontSize = 18.sp, modifier = Modifier.align(Alignment.TopCenter).padding(bottom = 12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        itemsIndexed(folder.packageNames) { _, pkg ->
                            allAppsMap[pkg]?.let { app ->
                                Box(modifier = Modifier.padding(8.dp).clickable { 
                                    launchApp(context, pkg)
                                    openedFolder = null
                                }) {
                                    Image(bitmap = remember(pkg) { app.icon.toBitmap(144, 144).asImageBitmap() }, contentDescription = null, modifier = Modifier.size(56.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopItemContent(item: DesktopItem, allAppsMap: Map<String, AppInfo>, isLocked: Boolean) {
    Box(
        modifier = Modifier.padding(8.dp).size(60.dp),
        contentAlignment = Alignment.Center
    ) {
        if (item.isFolder) {
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.packageNames.isNotEmpty()) {
                    allAppsMap[item.packageNames[0]]?.let { app ->
                        Image(bitmap = remember(app.packageName) { app.icon.toBitmap(144, 144).asImageBitmap() }, contentDescription = null, modifier = Modifier.size(44.dp).align(Alignment.Center))
                    }
                }
            }
        } else {
            val app = allAppsMap[item.packageNames[0]]
            if (app != null) {
                Image(
                    bitmap = remember(app.packageName) { app.icon.toBitmap(144, 144).asImageBitmap() },
                    contentDescription = app.label,
                    modifier = Modifier.size(60.dp)
                )
            }
        }
        
        if (isLocked) Text("🔒", fontSize = 14.sp, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

private fun launchApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun loadAllApps(context: Context): Map<String, AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .mapNotNull { it.activityInfo?.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .associate {
            it.packageName to AppInfo(it.loadLabel(pm).toString(), it.packageName, it.loadIcon(pm))
        }
}

private fun loadDesktopItems(context: Context, prefs: SharedPreferences, allAppsMap: Map<String, AppInfo>): List<DesktopItem> {
    val saved = prefs.getString("desktop_layout", "") ?: ""
    if (saved.isNotEmpty()) {
        val parsed = saved.split(";").mapNotNull { str ->
            val parts = str.split(",")
            if (parts.size >= 4) {
                DesktopItem(parts[0], parts[1], parts[3].split("|").toMutableList(), parts[2].toBoolean())
            } else null
        }
        if (parsed.isNotEmpty()) return parsed
    }
    return allAppsMap.values.map {
        DesktopItem(it.packageName, it.label, mutableListOf(it.packageName), false)
    }.sortedBy { it.name.lowercase() }
}
