package com.hashiry.gallery.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hashiry.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Lightweight click handling shared by shared control call sites. */
fun Modifier.simpleClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = this.combinedClickable(
    enabled = enabled,
    onLongClick = onLongClick,
    onClick = onClick
)

/** Solid card surface retained under the solid surface for cards and sheets. */
@Composable
fun SolidPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 16.dp,
    tint: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val palette = GalleryTheme.palette
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint ?: palette.card)
            .border(
                width = 0.5.dp,
                color = palette.separator,
                shape = shape
            )
    ) {
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** Circular solid button used for toolbar actions. */
@Composable
fun SolidIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    background: Color? = null,
    size: Dp = 38.dp,
    iconSize: Dp = 18.dp
) {
    val palette = GalleryTheme.palette
    SolidPanel(
        modifier = modifier
            .size(size)
            .simpleClickable(pressedScale = 0.9f, onClick = onClick),
        shape = CircleShape,
        elevation = 10.dp,
        tint = background ?: palette.groupedBackground
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint ?: palette.primaryText,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** Solid pill, e.g. the video duration badge on a thumbnail. */
@Composable
fun SolidChip(
    text: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE61A1A1F))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(9.dp)
                )
                if (text != null) Spacer(Modifier.size(3.dp))
            }
            if (text != null) {
                Text(
                    text = text,
                    color = tint,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** Plain background; no ambient gradients or decorative layers. */
@Composable
fun SolidBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(GalleryTheme.palette.background))
}

// --------------------------------------------------------------------- the mark

/**
 * The app's multicoloured flower / aperture mark, drawn with Compose.
 * When [animated] the petals bloom out one after another with a springy stagger.
 */
@Composable
fun FlowerMark(
    size: Dp,
    animated: Boolean = false
) {
    val colors = remember {
        listOf(
            Color(0xFFFF3B30),
            Color(0xFFFF9500),
            Color(0xFFFFCC00),
            Color(0xFF34C759),
            Color(0xFF0A84FF),
            Color(0xFFAF52DE)
        )
    }
    var started by remember { mutableStateOf(!animated) }

    val petalScales = colors.indices.map { index ->
        val scale by animateFloatAsState(
            targetValue = if (started) 1f else 0.2f,
            animationSpec = tween(
                durationMillis = 520,
                delayMillis = index * 65,
                easing = FastOutSlowInEasing
            ),
            label = "petal$index"
        )
        scale
    }
    val petalAlpha = colors.indices.map { index ->
        val alpha by animateFloatAsState(
            targetValue = if (started) 1f else 0f,
            animationSpec = tween(durationMillis = 380, delayMillis = index * 65),
            label = "petalAlpha$index"
        )
        alpha
    }
    val coreScale by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "core"
    )

    LaunchedEffect(Unit) {
        if (animated) {
            delay(60)
            started = true
        }
    }

    Canvas(modifier = Modifier.size(size)) {
        val diameter = this.size.minDimension
        val center = Offset(diameter / 2f, diameter / 2f)
        val petalRadius = diameter * 0.136f
        val distance = diameter * 0.198f
        colors.forEachIndexed { index, color ->
            val angle = Math.toRadians(index * 60.0)
            val cx = center.x + distance * sin(angle).toFloat()
            val cy = center.y - distance * cos(angle).toFloat()
            drawCircle(
                color = color,
                radius = petalRadius * petalScales[index],
                center = Offset(cx, cy),
                alpha = petalAlpha[index]
            )
            // subtle highlight on the upper-left of each petal
            drawCircle(
                color = Color.White,
                radius = petalRadius * 0.34f * petalScales[index],
                center = Offset(
                    cx - petalRadius * 0.30f,
                    cy - petalRadius * 0.34f
                ),
                alpha = 0.32f * petalAlpha[index]
            )
        }
        drawCircle(
            color = Color.White,
            radius = diameter * 0.10f * coreScale,
            center = center,
            alpha = coreScale
        )
    }
}
