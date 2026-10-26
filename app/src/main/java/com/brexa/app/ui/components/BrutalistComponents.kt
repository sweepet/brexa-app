package com.brexa.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.ui.theme.*

/**
 * Tarjeta Neobrutalista: Bordes rectos de 90°, borde sólido y sombra dura rígida sin difuminar.
 */
@Composable
fun BrutalistCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = SurfaceCard,
    borderColor: Color = BorderSolid,
    borderWidth: Dp = 2.dp,
    shadowColor: Color = Color.Transparent,
    shadowOffset: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedOffset by animateDpAsState(
        targetValue = if (isPressed && onClick != null) shadowOffset else 0.dp,
        label = "pressOffset"
    )

    Box(modifier = modifier) {
        // Capa de Sombra Dura (Bloque desplazado)
        if (shadowColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = shadowOffset, y = shadowOffset)
                    .background(shadowColor, RectangleShape)
            )
        }

        // Superficie principal de la tarjeta
        Box(
            modifier = Modifier
                .offset(x = animatedOffset, y = animatedOffset)
                .background(backgroundColor, RectangleShape)
                .border(borderWidth, borderColor, RectangleShape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                    } else Modifier
                ),
            content = content
        )
    }
}

/**
 * Botón Neobrutalista: Pulsación mecánica con colapso de sombra dura en tiempo real.
 */
@Composable
fun BrutalistButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AccentLime,
    contentColor: Color = TextOnLime,
    borderColor: Color = BorderBright,
    shadowColor: Color = Color(0xFF000000),
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shadowSize = 4.dp
    val currentOffset by animateDpAsState(
        targetValue = if (isPressed && enabled) shadowSize else 0.dp,
        label = "buttonPress"
    )

    Box(modifier = modifier) {
        // Sombra dura sólida de fondo
        if (enabled && shadowColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = shadowSize, y = shadowSize)
                    .background(shadowColor, RectangleShape)
            )
        }

        // Superficie del botón
        Row(
            modifier = Modifier
                .offset(x = currentOffset, y = currentOffset)
                .fillMaxWidth()
                .background(if (enabled) containerColor else SurfaceCardElevated, RectangleShape)
                .border(2.dp, if (enabled) borderColor else BorderSolid, RectangleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text.uppercase(),
                style = TextStyle(
                    fontFamily = SpaceMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    color = if (enabled) contentColor else TextMuted,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Campo de Entrada Neobrutalista: Borde sólido, etiqueta fija y tipografía monoespaciada.
 */
@Composable
fun BrutalistTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    prefix: String? = null,
    suffix: String? = null,
    isMonospace: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    borderColor: Color = BorderSolid
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = SpaceMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = TextSecondary
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard, RectangleShape)
                .border(2.dp, borderColor, RectangleShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (prefix != null) {
                Text(
                    text = prefix,
                    style = TextStyle(
                        fontFamily = SpaceMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AccentLime
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontFamily = if (isMonospace) SpaceMonoFontFamily else FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                ),
                cursorBrush = SolidColor(AccentLime),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                fontFamily = if (isMonospace) SpaceMonoFontFamily else FontFamily.SansSerif,
                                fontSize = 15.sp,
                                color = TextMuted
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            )

            if (suffix != null) {
                Text(
                    text = suffix,
                    style = TextStyle(
                        fontFamily = SpaceMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * Badge / Etiqueta Neobrutalista
 */
@Composable
fun BrutalistBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = SurfaceCardElevated,
    contentColor: Color = TextPrimary,
    borderColor: Color = BorderSolid
) {
    Box(
        modifier = modifier
            .background(backgroundColor, RectangleShape)
            .border(1.5.dp, borderColor, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = TextStyle(
                fontFamily = SpaceMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                color = contentColor
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
