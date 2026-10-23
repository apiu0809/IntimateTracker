package com.mysecret.intimatetracker.ui.component

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 全屏查看用的一张照片。 */
data class ViewerPhoto(
    val id: String,
    val relPath: String,
    val recordId: String,
    val timestamp: Long,
)

private val viewerDate = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE)

/**
 * 全屏照片浏览：沉浸显示、双击缩放、左右滑动、左下角日期、右上角分享；
 * [showRecordButton] 为 true 时额外显示「打开记录」（日历图标）。
 */
@Composable
fun PhotoViewer(
    photos: List<ViewerPhoto>,
    startIndex: Int,
    showRecordButton: Boolean,
    onOpenRecord: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, photos.lastIndex),
        pageCount = { photos.size },
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val photo = photos[page]
                var scale by remember(page) { mutableFloatStateOf(1f) }
                var offset by remember(page) { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            // 双指缩放 + 平移；放大后拖到边界继续拖则翻页
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var event = awaitPointerEvent()
                                var overshoot = 0f
                                var direction = 0
                                while (event.changes.any { it.pressed }) {
                                    val pointers = event.changes.count { it.pressed }
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    if (pointers > 1 || scale > 1f) {
                                        scale = (scale * zoom).coerceIn(1f, 6f)
                                        val maxX = (size.width * (scale - 1f)) / 2f
                                        val maxY = (size.height * (scale - 1f)) / 2f
                                        val raw = offset + pan
                                        val clampedX = raw.x.coerceIn(-maxX, maxX)
                                        val clampedY = raw.y.coerceIn(-maxY, maxY)
                                        if (scale > 1.02f && kotlin.math.abs(raw.x) > maxX + 1f) {
                                            val dir = if (raw.x > 0) -1 else 1
                                            if (dir != direction) {
                                                direction = dir
                                                overshoot = 0f
                                            }
                                            overshoot += kotlin.math.abs(raw.x - clampedX)
                                            if (overshoot > 220f && pager.currentPage + dir in 0 until photos.size) {
                                                event.changes.forEach { it.consume() }
                                                scope.launch { pager.animateScrollToPage(pager.currentPage + dir) }
                                                break
                                            }
                                        } else {
                                            direction = 0
                                            overshoot = 0f
                                        }
                                        offset = Offset(clampedX, clampedY)
                                        event.changes.forEach { it.consume() }
                                    }
                                    event = awaitPointerEvent()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = File(context.filesDir, photo.relPath),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "${pager.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                if (showRecordButton) {
                    IconButton(onClick = { onOpenRecord(photos[pager.currentPage].recordId) }) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = "打开记录",
                            tint = Color.White,
                        )
                    }
                }
                IconButton(onClick = {
                    val photo = photos[pager.currentPage]
                    runCatching {
                        val file = File(context.filesDir, photo.relPath)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享照片"))
                    }
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "分享", tint = Color.White)
                }
            }

            val date = Instant.ofEpochMilli(photos[pager.currentPage].timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            Text(
                text = viewerDate.format(date),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 36.dp),
            )
        }
    }
}
