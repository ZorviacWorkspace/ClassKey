package com.classkey.modernattendance.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classkey.modernattendance.data.AttStatus
import com.classkey.modernattendance.data.DayReport
import com.classkey.modernattendance.data.User

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Int = 18,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CK.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) { Column(Modifier.padding(padding.dp), content = content) }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = CK.Muted, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f)
        )
        if (action != null && onAction != null) {
            Text(
                action, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CK.Primary,
                modifier = Modifier.clickable { onAction() }
            )
        }
    }
}

@Composable
fun StatusChip(status: AttStatus) {
    val color = statusColor(status)
    Row(
        Modifier.clip(RoundedCornerShape(99.dp)).background(statusSoft(status)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(status.label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PillChip(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Avatar(user: User?, size: Int = 42, light: Boolean = false) {
    val photoPath = user?.photoPath
    val bitmap = remember(photoPath) {
        photoPath?.let {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(it, opts)
            } catch (_: Exception) { null }
        }
    }
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(if (light) Color.White.copy(alpha = .25f) else CK.Primary),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        } else {
            Text(
                user?.initials ?: "CK", color = Color.White,
                fontWeight = FontWeight.Bold, fontSize = (size / 2.6).sp
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CK.Primary, disabledContainerColor = CK.Primary.copy(alpha = .35f))
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(15.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(17.dp), tint = CK.Primary)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, color = CK.Primary)
    }
}

@Composable
fun StatCard(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CK.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(value.toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CK.Muted)
        }
    }
}

@Composable
fun QuickAction(icon: ImageVector, label: String, sub: String? = null, tint: Color, soft: Color, modifier: Modifier = Modifier, badge: Int = 0, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CK.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(soft), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = tint)
                if (badge > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(3.dp).size(15.dp).clip(CircleShape).background(CK.Error),
                        contentAlignment = Alignment.Center
                    ) { Text(badge.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                if (sub != null) Text(sub, fontSize = 11.sp, color = CK.Muted)
            }
        }
    }
}

@Composable
fun EmptyCard(title: String, body: String) {
    AppCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CK.Text)
            Spacer(Modifier.height(4.dp))
            Text(body, fontSize = 13.sp, color = CK.Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text)
        if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = CK.Muted)
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = CK.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 14.sp, color = CK.Muted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

/** Animated concentric-pulse fingerprint button from the Figma mark-attendance screen. */
@Composable
fun PulsingCircleButton(
    icon: ImageVector,
    enabled: Boolean,
    scanning: Boolean,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "pulseScale"
    )
    val alpha by transition.animateFloat(
        initialValue = .45f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "pulseAlpha"
    )
    Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
        if (enabled && !scanning) {
            Box(
                Modifier.size(96.dp).scale(pulse).clip(CircleShape)
                    .background(CK.Primary.copy(alpha = alpha))
            )
        }
        Box(
            Modifier.size(92.dp).clip(CircleShape)
                .background(if (enabled) CK.Primary else CK.Muted.copy(alpha = .3f))
                .clickable(enabled = enabled && !scanning) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(44.dp), tint = Color.White)
        }
    }
}

@Composable
fun ProgressRing(progress: Float, label: String, size: Int = 68, color: Color = CK.Teal) {
    val animated by animateFloatAsState(progress, animationSpec = tween(700), label = "ring")
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(CK.SoftBlue, style = Stroke(width = 8.dp.toPx()))
            drawArc(color, -90f, 360f * animated, false, style = Stroke(width = 8.dp.toPx()))
        }
        Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = CK.Text)
    }
}

/** Grouped weekly bar chart (present / late / absent) rendered with Canvas — no chart library. */
@Composable
fun WeeklyBarChart(data: List<DayReport>) {
    val maxVal = (data.maxOfOrNull { maxOf(it.present, it.late, it.absent) } ?: 1).coerceAtLeast(1)
    Column {
        Row(
            Modifier.fillMaxWidth().height(130.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { day ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(
                            day.present to CK.Success,
                            day.late to CK.Warning,
                            day.absent to CK.Error
                        ).forEach { (value, color) ->
                            val h = (100f * value / maxVal).coerceAtLeast(4f)
                            Box(
                                Modifier.width(9.dp).height(h.dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(color)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(day.label, fontSize = 11.sp, color = CK.Muted)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf("Present" to CK.Success, "Late" to CK.Warning, "Absent" to CK.Error).forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(5.dp))
                    Text(label, fontSize = 11.sp, color = CK.Muted)
                }
            }
        }
    }
}

@Composable
fun MessageBar(message: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(14.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CK.Text)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                "OK", color = Color(0xFF9EC2F5), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clickable { onClose() }.padding(6.dp)
            )
        }
    }
}
