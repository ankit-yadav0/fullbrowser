package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun QuickControlFab(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCollapse: () -> Unit,
    onExitClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    adTrackerBlockingEnabled: Boolean = true,
    privacyNetworkReady: Boolean = false,
    vpnActive: Boolean = false,
    blockedRequestCount: Int = 0,
    onToggleAdTrackerBlocking: () -> Unit = {},
    isBackgroundAudioEnabled: Boolean = true,
    onToggleBackgroundAudio: () -> Unit = {},
    onDiagnosticsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val viewConfig = LocalViewConfiguration.current
        val touchSlop = viewConfig.touchSlop

        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        val buttonSizeDp = 52.dp
        val buttonSizePx = with(density) { buttonSizeDp.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }

        val minX = marginPx
        val maxX = (screenWidthPx - buttonSizePx - marginPx).coerceAtLeast(minX)
        val minY = marginPx
        val maxY = (screenHeightPx - buttonSizePx - marginPx).coerceAtLeast(minY)

        // Floating button position coordinates
        val defaultX = maxX
        val defaultY = (maxY - with(density) { 24.dp.toPx() }).coerceAtLeast(minY)

        var offsetX by rememberSaveable { mutableFloatStateOf(-1f) }
        var offsetY by rememberSaveable { mutableFloatStateOf(-1f) }

        val currentX = (if (offsetX < 0f) defaultX else offsetX).coerceIn(minX, maxX)
        val currentY = (if (offsetY < 0f) defaultY else offsetY).coerceIn(minY, maxY)

        val isOnBottomHalf = currentY > (screenHeightPx / 2f)
        val isOnRightHalf = currentX > (screenWidthPx / 2f)

        // Semi-transparent scrim when quick control menu is expanded
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCollapse() }
            )
        }

        // Expanded Action Buttons Container (Bookmarks, Background Audio, Diagnostics & Exit)
        val actionsContainerWidthDp = 210.dp
        val actionsContainerWidthPx = with(density) { actionsContainerWidthDp.toPx() }
        val actionsHeightPx = with(density) { 270.dp.toPx() }
        val gapPx = with(density) { 12.dp.toPx() }

        val actionsX = if (isOnRightHalf) {
            (currentX + buttonSizePx - actionsContainerWidthPx).coerceAtLeast(minX)
        } else {
            currentX.coerceAtMost(maxX + buttonSizePx - actionsContainerWidthPx)
        }

        val actionsMaxY = (screenHeightPx - actionsHeightPx - marginPx).coerceAtLeast(minY)
        val actionsY = if (isOnBottomHalf) {
            (currentY - actionsHeightPx - gapPx).coerceIn(minY, actionsMaxY)
        } else {
            (currentY + buttonSizePx + gapPx).coerceIn(minY, actionsMaxY)
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .offset { IntOffset(actionsX.roundToInt(), actionsY.roundToInt()) }
                .width(actionsContainerWidthDp)
        ) {
            Column(
                modifier = Modifier.width(actionsContainerWidthDp),
                horizontalAlignment = if (isOnRightHalf) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = if (privacyNetworkReady) "VPN: ACTIVE" else "VPN: REQUIRED",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        fontSize = 11.sp,
                        color = if (privacyNetworkReady && vpnActive) Color(0xFF10B981) else Color(0xFFF59E0B)
                    )
                }
                if (isOnBottomHalf) {
                    // When floating near the bottom: Exit is topmost, then Diagnostics, Bg Audio, Bookmarks
                    FabActionItem(
                        icon = Icons.Default.Close,
                        label = "Exit",
                        testTag = "quick_control_exit",
                        tint = Color(0xFFEF4444),
                        isOnRightHalf = isOnRightHalf,
                        onClick = {
                            onCollapse()
                            onExitClick()
                        }
                    )
                    FabActionItem(
                        icon = Icons.Default.Shield,
                        label = if (adTrackerBlockingEnabled) {
                            if (blockedRequestCount > 0) "Shield: ON ($blockedRequestCount)" else "Shield: ON"
                        } else "Shield: OFF",
                        testTag = "quick_control_ad_tracker",
                        tint = if (adTrackerBlockingEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                        isOnRightHalf = isOnRightHalf,
                        onClick = { onToggleAdTrackerBlocking() }
                    )
                    if (onDiagnosticsClick != null) {
                        FabActionItem(
                            icon = Icons.Default.BugReport,
                            label = "Diagnostics",
                            testTag = "quick_control_diagnostics",
                            tint = Color(0xFFA855F7),
                            isOnRightHalf = isOnRightHalf,
                            onClick = {
                                onCollapse()
                                onDiagnosticsClick()
                            }
                        )
                    }
                    FabActionItem(
                        icon = Icons.Default.Headphones,
                        label = if (isBackgroundAudioEnabled) "Bg Audio: ON" else "Bg Audio: OFF",
                        testTag = "quick_control_bg_audio",
                        tint = if (isBackgroundAudioEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                        isOnRightHalf = isOnRightHalf,
                        onClick = {
                            onToggleBackgroundAudio()
                        }
                    )
                    FabActionItem(
                        icon = Icons.Default.Bookmark,
                        label = "Bookmarks",
                        testTag = "quick_control_bookmarks",
                        tint = Color(0xFF38BDF8),
                        isOnRightHalf = isOnRightHalf,
                        onClick = {
                            onCollapse()
                            onBookmarksClick()
                        }
                    )
                } else {
                    // When floating near the top: Bookmarks is topmost, then Bg Audio, Diagnostics, Exit
                    FabActionItem(
                        icon = Icons.Default.Bookmark,
                        label = "Bookmarks",
                        testTag = "quick_control_bookmarks",
                        tint = Color(0xFF38BDF8),
                        isOnRightHalf = isOnRightHalf,
                        onClick = {
                            onCollapse()
                            onBookmarksClick()
                        }
                    )
                    FabActionItem(
                        icon = Icons.Default.Headphones,
                        label = if (isBackgroundAudioEnabled) "Bg Audio: ON" else "Bg Audio: OFF",
                        testTag = "quick_control_bg_audio",
                        tint = if (isBackgroundAudioEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                        isOnRightHalf = isOnRightHalf,
                        onClick = {
                            onToggleBackgroundAudio()
                        }
                    )
                    FabActionItem(
                        icon = Icons.Default.Shield,
                        label = if (adTrackerBlockingEnabled) {
                            if (blockedRequestCount > 0) "Shield: ON ($blockedRequestCount)" else "Shield: ON"
                        } else "Shield: OFF",
                        testTag = "quick_control_ad_tracker",
                        tint = if (adTrackerBlockingEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                        isOnRightHalf = isOnRightHalf,
                        onClick = { onToggleAdTrackerBlocking() }
                    )
                    if (onDiagnosticsClick != null) {
                        FabActionItem(
                            icon = Icons.Default.BugReport,
                            label = "Diagnostics",
                            testTag = "quick_control_diagnostics",
                            tint = Color(0xFFA855F7),
                            isOnRightHalf = isOnRightHalf,
                            onClick = {
                                onCollapse()
                                onDiagnosticsClick()
                            }
                        )
                    }
                    FabActionItem(
                        icon = Icons.Default.Close,
                        label = "Exit",
                        testTag = "quick_control_exit",
                        tint = Color(0xFFEF4444),
                        isOnRightHalf = isOnRightHalf,
                        onClick = {
                            onCollapse()
                            onExitClick()
                        }
                    )
                }
            }
        }

        // Main Floating Trigger Button (Draggable & Clickable)
        val rotation by animateFloatAsState(
            targetValue = if (isExpanded) 180f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "fab_rotation"
        )

        Surface(
            modifier = Modifier
                .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
                .size(buttonSizeDp)
                .shadow(elevation = 12.dp, shape = CircleShape)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = Color.White.copy(alpha = 0.45f),
                    shape = CircleShape
                )
                .testTag("quick_control_toggle")
                .pointerInput(minX, maxX, minY, maxY) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDrag = Offset.Zero
                        var hasDragged = false
                        val pointerId = down.id

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                            if (change.changedToUp()) {
                                if (!hasDragged) {
                                    // User tapped without dragging -> toggle quick controls
                                    change.consume()
                                    onToggleExpand()
                                }
                                break
                            }

                            val drag = change.positionChange()
                            totalDrag += drag

                            if (!hasDragged && totalDrag.getDistance() > touchSlop) {
                                hasDragged = true
                                if (isExpanded) {
                                    onCollapse()
                                }
                            }

                            if (hasDragged) {
                                change.consume()
                                val baseStartX = if (offsetX < 0f) currentX else offsetX
                                val baseStartY = if (offsetY < 0f) currentY else offsetY
                                offsetX = (baseStartX + drag.x).coerceIn(minX, maxX)
                                offsetY = (baseStartY + drag.y).coerceIn(minY, maxY)
                            }
                        }
                    }
                },
            shape = CircleShape,
            color = Color(0xEB0A0E17),
            contentColor = Color.White
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(buttonSizeDp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Menu,
                    contentDescription = if (isExpanded) "Close quick controls" else "Open quick controls",
                    tint = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

@Composable
private fun FabActionItem(
    icon: ImageVector,
    label: String,
    testTag: String,
    tint: Color,
    isOnRightHalf: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isOnRightHalf) {
            // Label on left, circular action button on right
            ActionLabelBadge(label = label, onClick = onClick)
            ActionButtonCircle(icon = icon, label = label, testTag = testTag, tint = tint, onClick = onClick)
        } else {
            // Circular action button on left, label on right
            ActionButtonCircle(icon = icon, label = label, testTag = testTag, tint = tint, onClick = onClick)
            ActionLabelBadge(label = label, onClick = onClick)
        }
    }
}

@Composable
private fun ActionLabelBadge(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(Color(0xF00A0E17), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ActionButtonCircle(
    icon: ImageVector,
    label: String,
    testTag: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .shadow(elevation = 8.dp, shape = CircleShape)
            .clip(CircleShape)
            .border(1.5.dp, tint.copy(alpha = 0.8f), CircleShape)
            .testTag(testTag),
        shape = CircleShape,
        color = Color(0xF00A0E17),
        contentColor = tint
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = tint),
                onClick = onClick
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
