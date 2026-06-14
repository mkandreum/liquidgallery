package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassBarFill
import com.example.ui.theme.GlassBarBorder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    borderAlpha: Float = 1f,
    backgroundAlpha: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = GlassBarBorder.copy(alpha = borderAlpha),
                shape = shape
            )
            .clip(shape)
            .background(GlassBarFill.copy(alpha = GlassBarFill.alpha * backgroundAlpha))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            propagateMinConstraints = true
        ) {
            content()
        }
    }
}

@Composable
fun LiquidLensCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Derive highlight positions from wavePhase using slower factors (single animation optimization)
    val highlightX = 0.5f + 0.25f * sin(wavePhase * 0.35f)
    val highlightY = 0.45f + 0.15f * cos(wavePhase * 0.28f)

    Box(
        modifier = modifier
            .drawWithContent {
                drawContent()
                drawLiquidLens(
                    size = size,
                    wavePhase = wavePhase,
                    highlightX = highlightX,
                    highlightY = highlightY
                )
            }
    ) {
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLiquidLens(
    size: Size,
    wavePhase: Float,
    highlightX: Float,
    highlightY: Float
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val rx = w * 0.49f
    val ry = h * 0.49f

    // ── 1. Outer chromatic ring (red fringe) ──
    drawOval(
        color = Color(0x44FF3333),
        topLeft = Offset(cx - rx - 1.5f, cy - ry),
        size = Size(rx * 2f + 3f, ry * 2f)
    )

    // ── 2. Inner chromatic ring (blue fringe) ──
    drawOval(
        color = Color(0x443366FF),
        topLeft = Offset(cx - rx + 1.5f, cy - ry),
        size = Size(rx * 2f - 3f, ry * 2f)
    )

    // ── 3. Main glass body ── radial gradient simulating lens curvature ──
    val glassGradient = Brush.radialGradient(
        *arrayOf(
            0f to Color(0x0AFFFFFF),
            0.55f to Color(0x0FFFFFFF),
            0.82f to Color(0x18FFFFFF),
            1f to Color(0x08FFFFFF)
        ),
        center = Offset(cx, cy)
    )
    drawOval(
        brush = glassGradient,
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f)
    )

    // ── 4. Caustic light patterns (simulated SVG feTurbulence waves) ──
    for (i in 0 until 3) {
        val phaseOffset = i * 2.1f
        val path = Path()
        val startY = cy - ry * 0.7f + i * ry * 0.45f
        path.moveTo(cx - rx * 0.65f, startY)

        var x = cx - rx * 0.65f
        while (x < cx + rx * 0.65f) {
            val nx = (x - cx) / rx
            val amplitude = ry * 0.06f * (1f - nx * nx).coerceAtLeast(0f)
            val freq = 3f + i * 1.5f
            val y = startY + amplitude * sin(nx * PI.toFloat() * freq + wavePhase + phaseOffset)
            path.lineTo(x, y)
            x += 10f
        }

        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.07f + i * 0.02f),
            style = Stroke(width = 2.5f - i * 0.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // ── 5. Secondary caustic arcs (curved light bands) ──
    for (i in 0 until 2) {
        val arcPath = Path()
        val arcCy = cy - ry * 0.3f + i * ry * 0.6f
        val arcR = rx * 0.5f + i * rx * 0.15f
        val segments = 20
        for (j in 0..segments) {
            val angle = -PI.toFloat() * 0.6f + j.toFloat() * PI.toFloat() * 1.2f / segments
            val px = cx + arcR * cos(angle)
            val py = arcCy + ry * 0.2f * sin(angle * 2.5f + wavePhase * 0.7f + i)
            if (j == 0) arcPath.moveTo(px, py) else arcPath.lineTo(px, py)
        }
        drawPath(
            path = arcPath,
            color = Color.White.copy(alpha = 0.05f + i * 0.03f),
            style = Stroke(width = 3f - i * 1f, cap = StrokeCap.Round)
        )
    }

    // ── 6. Specular highlight (moving light reflection on curved glass) ──
    val specCx = cx + (highlightX - 0.5f) * rx * 1.2f
    val specCy = cy + (highlightY - 0.45f) * ry * 1.1f
    val specRx = rx * 0.35f
    val specRy = ry * 0.18f

    val highlightBrush = Brush.radialGradient(
        *arrayOf(
            0f to Color.White.copy(alpha = 0.32f),
            0.3f to Color.White.copy(alpha = 0.12f),
            0.7f to Color.White.copy(alpha = 0.02f),
            1f to Color.Transparent
        ),
        center = Offset(specCx, specCy)
    )
    drawOval(
        brush = highlightBrush,
        topLeft = Offset(specCx - specRx * 2.5f, specCy - specRy * 3f),
        size = Size(specRx * 5f, specRy * 6f)
    )

    // ── 7. Small bright spot (point reflection like water surface) ──
    drawCircle(
        color = Color.White.copy(alpha = 0.45f),
        radius = rx * 0.04f,
        center = Offset(specCx - specRx * 0.3f, specCy - specRy * 0.8f)
    )

    // ── 8. Glass rim inner highlight (thin light line inside border) ──
    drawOval(
        color = Color.White.copy(alpha = 0.18f),
        topLeft = Offset(cx - rx + 1f, cy - ry + 1f),
        size = Size(rx * 2f - 2f, ry * 2f - 2f),
        style = Stroke(width = 1.2f)
    )

    // ── 9. Bottom edge shadow (depth cue) ──
    val shadowBrush = Brush.verticalGradient(
        *arrayOf(
            0f to Color.Transparent,
            0.6f to Color.Black.copy(alpha = 0.15f),
            1f to Color.Black.copy(alpha = 0.3f)
        ),
        startY = cy + ry * 0.3f,
        endY = cy + ry
    )
    drawOval(
        brush = shadowBrush,
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f)
    )
}

@Composable
fun AnimatedPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val dotScale by animateFloatAsState(
                targetValue = if (isSelected) 1.3f else 0.7f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "dotScale"
            )
            val dotAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.4f,
                animationSpec = tween(200),
                label = "dotAlpha"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                        alpha = dotAlpha
                    }
                    .background(
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    label: String = ""
) {
    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Text(
                    String.format("%.0f", value),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.7f),
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AnimatedPressIcon(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    icon: @Composable (Modifier) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = 800f),
        label = "pressScale"
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "pressAlpha"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = pressAlpha
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        icon(Modifier)
    }
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp)
) {
    val shimmerTransition = rememberInfiniteTransition(label = "shimmerBox")
    val shimmerAlpha by shimmerTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = shimmerAlpha))
    )
}
