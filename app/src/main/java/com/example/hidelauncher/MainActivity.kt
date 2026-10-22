package com.example.hidelauncher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
        setContent {
            MaterialTheme {
                LauncherScreen()
            }
        }
    }
}

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

    fun persistHidden(newSet: Set<String>) {
        hidden = newSet
        prefs.edit().putStringSet("hidden_packages", newSet).apply()
    }

    fun persistLocked(newSet: Set<String>) {
        locked = newSet
        prefs.edit().putStringSet("locked_packages", newSet).apply()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1116))
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
        val visibleApps = apps.filter { app ->
            when {
                app.packageName in locked -> true
                allHidden -> false
                else -> app.packageName !in hidden
            }
        }

        if (visibleApps.isEmpty()) {
            Text(
                text = "双击空白处恢复图标",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(28.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(76.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(visibleApps, key = { it.packageName }) { app ->
                    AppItem(
                        app = app,
                        isLocked = app.packageName in locked,
                        onClick = { launchApp(context, app.packageName) },
                        onLongClick = { longPressedApp = app }
                    )
                }
            }
        }

        if (allHidden) {
            Text(
                text = "已全部隐藏 · 双击空白处恢复",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(28.dp)
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
                            isLocked -> "已锁定：双击全部隐藏时仍会显示"
                            isHidden -> "已单独隐藏：双击空白处可恢复"
                            else -> "选择要执行的操作"
                        },
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))

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
                        Text(if (isLocked) "解除锁定" else "锁定（全隐藏时仍显示）")
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
        app.icon.toBitmap(120, 120).asImageBitmap()
    }

    Column(
        modifier = Modifier
            .padding(6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = app.label,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isLocked) {
            Text(
                text = "🔒",
                fontSize = 9.sp,
                color = Color(0xFFFFC107)
            )
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
