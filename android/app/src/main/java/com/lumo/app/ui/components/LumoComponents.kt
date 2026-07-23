package com.lumo.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ── Design tokens ──
private val GradientStart = Color(0xFF58B7B1)
private val GradientEnd = Color(0xFF9688E8)

// ── Chat bubble (adapted from ComposeCraft) ──
@Composable
fun LumoChatBubble(
    message: String,
    isSent: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isSent) 0.8f else 0.85f)
                .clip(
                    if (isSent) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                    else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                )
                .background(
                    if (isSent) Brush.horizontalGradient(listOf(GradientStart, GradientEnd))
                    else Brush.horizontalGradient(listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant,
                    ))
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSent) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ── Typing indicator (adapted from ComposeCraft) ──
@Composable
fun LumoTypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val alphas = (0..2).map { index ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = index * 200),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot$index",
        ).value
    }
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        alphas.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
            )
        }
    }
}

// ── Stats card (adapted from ComposeCraft) ──
@Composable
fun LumoStatsCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ── Empty state (adapted from ComposeCraft) ──
@Composable
fun LumoEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Inbox,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 8.dp).size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onActionClick != null) {
            LumoGradientButton(text = actionLabel, onClick = onActionClick)
        }
    }
}

// ── Gradient button (adapted from ComposeCraft) ──
@Composable
fun LumoGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val gradient = Brush.horizontalGradient(listOf(GradientStart, GradientEnd))
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

// ── Segmented control (adapted from ComposeCraft) ──
@Composable
fun LumoSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Text(
                text = label,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface
                        else Color.Transparent,
                    )
                    .clickable { onSelectionChange(index) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
