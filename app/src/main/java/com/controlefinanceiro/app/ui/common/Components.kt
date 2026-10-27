package com.controlefinanceiro.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.ui.theme.LocalMoneyColors

/** Flat card on the container color, with an optional title row and trailing action. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                action?.invoke()
            }
            Spacer(Modifier.height(16.dp))
        }
        content()
    }
}

@Composable
fun MoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    colored: Boolean = false,
    color: Color = Color.Unspecified,
) {
    val money = LocalMoneyColors.current
    val tint = when {
        color != Color.Unspecified -> color
        colored && cents < 0 -> money.negative
        colored && cents > 0 -> money.positive
        else -> Color.Unspecified
    }
    // Shrinks the font until the value fits, so large amounts in narrow tiles are never cut off.
    var scale by remember(cents, style) { mutableStateOf(1f) }
    var ready by remember(cents, style) { mutableStateOf(false) }
    Text(
        Money.format(cents),
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        style = style.copy(fontSize = style.fontSize * scale),
        color = tint,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { result ->
            if (result.didOverflowWidth && scale > MIN_MONEY_SCALE) scale = (scale * 0.92f).coerceAtLeast(MIN_MONEY_SCALE) else ready = true
        },
    )
}

private const val MIN_MONEY_SCALE = 0.6f

/** Green below 90% of the limit, orange up to 100%, red above, like the spreadsheet. */
@Composable
fun usageColor(used: Long, limit: Long): Color {
    val money = LocalMoneyColors.current
    return when {
        limit <= 0 -> MaterialTheme.colorScheme.outline
        used > limit -> money.negative
        used >= limit * 0.9 -> money.warning
        else -> money.positive
    }
}

fun ratioOf(used: Long, limit: Long): Float =
    if (limit <= 0) (if (used > 0) 1f else 0f) else (used.toFloat() / limit)

/** Rounded progress bar that animates and turns red when the value passes the limit. */
@Composable
fun BudgetBar(
    used: Long,
    limit: Long,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 8.dp,
) {
    val over = limit > 0 && used > limit
    val progress by animateFloatAsState(ratioOf(used, limit).coerceIn(0f, 1f), tween(600), label = "bar")
    val fill by animateColorAsState(if (over) LocalMoneyColors.current.negative else color, label = "barColor")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(trackColor)) {
        if (progress > 0f) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(fill))
        }
    }
}

/** Circular gauge for "how much of the income is used". */
@Composable
fun UsageRing(
    ratio: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val sweep by animateFloatAsState(ratio.coerceIn(0f, 1f) * 360f, tween(800), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Icon inside a softly tinted circle. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size * 0.5f), tint = color) }
}

/** Circle with the first letter of a name. */
@Composable
fun Avatar(name: String, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = color)
    }
}

/** Small rounded label, e.g. a status or a percentage. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, container: Color = color.copy(alpha = 0.14f)) {
    Text(
        text,
        modifier.clip(CircleShape).background(container).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val Outer = 20.dp
private val Inner = 6.dp

/** Shape of an item inside a grouped list: round outside corners, tight inner ones. */
fun groupShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(Outer)
    index == 0 -> RoundedCornerShape(topStart = Outer, topEnd = Outer, bottomStart = Inner, bottomEnd = Inner)
    index == count - 1 -> RoundedCornerShape(topStart = Inner, topEnd = Inner, bottomStart = Outer, bottomEnd = Outer)
    else -> RoundedCornerShape(Inner)
}

/** Round "paid" check in the block color. */
@Composable
fun PaidToggle(checked: Boolean, color: Color, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(if (checked) color else Color.Transparent, label = "paid")
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(fill)
                .border(BorderStroke(2.dp, if (checked) color else MaterialTheme.colorScheme.outline), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Default.Check, "Pago", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.surface)
        }
    }
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String? = "Nenhum",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: (noneLabel ?: ""),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = MaterialTheme.shapes.small,
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (noneLabel != null) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onSelect(null); expanded = false })
            }
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
fun AmountField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }) },
        label = { Text(label) },
        prefix = { Text("R$ ") },
        isError = Money.parse(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun LabelValue(
    label: String,
    cents: Long,
    modifier: Modifier = Modifier,
    colored: Boolean = false,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: Color = Color.Unspecified,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
        MoneyText(cents, style = MaterialTheme.typography.titleSmall, colored = colored, color = valueColor)
    }
}

/** Small uppercase caption above a group of rows. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
