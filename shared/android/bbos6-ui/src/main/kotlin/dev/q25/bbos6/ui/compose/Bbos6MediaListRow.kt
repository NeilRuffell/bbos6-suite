package dev.q25.bbos6.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val mediaListLight = listOf(
    Color(0xFF088EEF), Color(0xFF088AEF), Color(0xFF0886EF),
    Color(0xFF0882EF), Color(0xFF087DE7), Color(0xFF0879E7),
    Color(0xFF1075E7), Color(0xFF1071E7), Color(0xFF106DDE),
    Color(0xFF1069DE), Color(0xFF1065DE)
)
private val mediaListDark = listOf(
    Color(0xFF459FD9), Color(0xFF3285C5), Color(0xFF2368AA)
)
private val removeFace = Brush.verticalGradient(
    listOf(Color(0xFF4B4E53), Color(0xFF292C31))
)

/** The existing BBOS6 Music list-selection face, now canonical shared Compose UI. */
@Composable
fun Modifier.bbos6MediaListSelectionFace(highlighted: Boolean): Modifier =
    if (!highlighted) this.background(Color.Black)
    else this.background(Brush.verticalGradient(
        if (isSystemInDarkTheme()) mediaListDark else mediaListLight
    ))

/**
 * App-neutral BBOS6 media/list row (68dp by default).
 *
 * The host owns recycling, current selection, artwork requests and caches,
 * data, focus, row activation, and long-press detection. This composable
 * supplies only the visible row and optional right-side touch action.
 * Use a smaller rowHeightDp and artworkSlotSizeDp for compact icon lists;
 * default dimensions remain unchanged for existing Music consumers.
 *
 * Passing null for subtitle/duration/trackNumber preserves their absent
 * state; menu artwork gets 8dp padding to make a 52dp visible artwork box.
 */
@Composable
fun Bbos6MediaListRow(
    title: String,
    subtitle: String?,
    duration: String?,
    trackNumber: String?,
    showArtwork: Boolean,
    artwork: Painter?,
    artworkContentDescription: String,
    menuArtwork: Boolean,
    highlighted: Boolean,
    showRemoveAction: Boolean,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    rowHeightDp: Int = 68,
    artworkSlotSizeDp: Int = 68,
    textHorizontalPaddingDp: Int = 16
) {
    require(rowHeightDp > 0) { "rowHeightDp must be positive" }
    require(artworkSlotSizeDp > 0) { "artworkSlotSizeDp must be positive" }
    require(textHorizontalPaddingDp >= 0) { "textHorizontalPaddingDp must not be negative" }
    Row(
        modifier = modifier.fillMaxWidth().height(rowHeightDp.dp)
            .bbos6MediaListSelectionFace(highlighted),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (trackNumber != null) {
            Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                BasicText(
                    text = trackNumber,
                    style = TextStyle(color = if (highlighted) Color.White
                        else Color(0xFFC9C9C9), fontSize = 15.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = true))
                )
            }
        }
        if (showArtwork) {
            Box(Modifier.size(artworkSlotSizeDp.dp), contentAlignment = Alignment.Center) {
                if (artwork != null) {
                    Image(
                        painter = artwork,
                        contentDescription = artworkContentDescription,
                        modifier = Modifier.size(artworkSlotSizeDp.dp)
                            .then(if (menuArtwork) Modifier.padding(8.dp) else Modifier),
                        contentScale = if (menuArtwork) ContentScale.Fit else ContentScale.Crop
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = title,
                    modifier = Modifier.weight(1f).padding(horizontal = textHorizontalPaddingDp.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Color.White, fontSize = 15.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = true))
                )
                if (duration != null) {
                    BasicText(
                        text = duration,
                        modifier = Modifier.padding(end = 16.dp),
                        maxLines = 1,
                        style = TextStyle(color = Color.White, fontSize = 15.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = true))
                    )
                }
            }
            if (subtitle != null) {
                BasicText(
                    text = subtitle,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = textHorizontalPaddingDp.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = if (highlighted) Color(0xFFEDF4FF)
                            else Color(0xFFB3C1CE),
                        fontSize = 12.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = true)
                    )
                )
            }
        }
        if (showRemoveAction) {
            Box(
                modifier = Modifier
                    .padding(
                        start = Bbos6MediaListTouchGeometry.ACTION_START_GAP_DP.dp,
                        end = Bbos6MediaListTouchGeometry.ACTION_END_GAP_DP.dp
                    )
                    .size(
                        width = Bbos6MediaListTouchGeometry.ACTION_WIDTH_DP.dp,
                        height = Bbos6MediaListTouchGeometry.ACTION_HEIGHT_DP.dp
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .background(removeFace)
                    .border(1.dp, Color(0xFF5A5D63), RoundedCornerShape(4.dp))
                    .semantics {
                        contentDescription = "Remove $title"
                        if (onRemove != null) onClick {
                            onRemove()
                            true
                        }
                    }
                    .pointerInput(onRemove) {
                        if (onRemove != null) detectTapGestures(onTap = { onRemove() })
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "Remove",
                    style = TextStyle(color = Color.White, fontSize = 13.sp,
                        textAlign = TextAlign.Center)
                )
            }
        }
    }
}
