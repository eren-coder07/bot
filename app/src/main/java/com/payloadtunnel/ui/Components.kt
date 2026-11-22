package com.payloadtunnel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

val CardShape = RoundedCornerShape(18.dp)

/** Olive-black background with a soft green glow at the top, as in the design. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, Color(0xFF10150B), BgBottom)))
            .background(
                Brush.radialGradient(
                    listOf(Lime.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(540f, 700f), radius = 900f,
                ),
            ),
        content = content,
    )
}

@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Screen header: round button, centred title, round button. */
@Composable
fun TopBar(
    title: String,
    left: (@Composable () -> Unit)? = null,
    right: (@Composable RowScope.() -> Unit)? = null,
) {
    Box(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = Poppins)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (left != null) left() else Spacer(Modifier.size(44.dp))
            Spacer(Modifier.weight(1f))
            if (right != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = right)
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(
                Brush.verticalGradient(
                    if (highlighted) listOf(Color(0xFF243019), Color(0xFF171E11))
                    else listOf(CardHigh, Card),
                ),
            )
            .border(1.dp, if (highlighted) Lime.copy(alpha = 0.22f) else CardBorder, CardShape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Lime,
    border: Color = Color.White.copy(alpha = 0.16f),
    background: Color = Color.White.copy(alpha = 0.04f),
    textColor: Color = TextPrimary,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, color = textColor, fontSize = 12.sp, maxLines = 1, fontFamily = Poppins)
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = CircleShape, color = background, border = BorderStroke(1.dp, border), modifier = modifier) { content() }
    } else {
        Surface(shape = CircleShape, color = background, border = BorderStroke(1.dp, border), modifier = modifier) { content() }
    }
}

private val avatarPalettes = listOf(
    listOf(Color(0xFFD4F53C), Color(0xFF6E9A1E)),
    listOf(Color(0xFF5EC8F2), Color(0xFF1F5E9E)),
    listOf(Color(0xFFFF8A65), Color(0xFFB3401F)),
    listOf(Color(0xFFB388FF), Color(0xFF5E35B1)),
    listOf(Color(0xFFFFD54F), Color(0xFFB28704)),
    listOf(Color(0xFF4DD0B0), Color(0xFF1B7A63)),
)

/** Round "flag" avatar for a profile: gradient circle with initials. */
@Composable
fun ProfileAvatar(name: String, size: Dp = 36.dp) {
    val palette = avatarPalettes[abs(name.hashCode()) % avatarPalettes.size]
    val initials = name.split(' ', '-', '_').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(palette))
            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials, color = Color(0xFF0E1209), fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp, textAlign = TextAlign.Center,
        )
    }
}

/** Three-bar signal indicator. */
@Composable
fun SignalBars(level: Int, modifier: Modifier = Modifier) {
    Row(modifier.height(14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..3) {
            Box(
                Modifier.width(3.dp).height((4 + i * 3).dp).clip(RoundedCornerShape(1.dp))
                    .background(if (i <= level) Signal else Color.White.copy(alpha = 0.18f)),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = Poppins, modifier = modifier)
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val alpha = if (enabled && !loading) 1f else 0.45f
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Lime, LimeDim)))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .let { if (enabled && !loading) it.clickable(onClick = onClick) else it }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                color = OnLime, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp),
            )
        } else {
            androidx.compose.material3.Text(
                text, color = OnLime.copy(alpha = alpha), fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontFamily = Poppins,
            )
        }
    }
}
