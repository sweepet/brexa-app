package com.brexa.app.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.brexa.app.R
import com.brexa.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun BrexamanOverlay(
    modifier: Modifier = Modifier,
    onOpenAssistant: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val prefs = remember { context.getSharedPreferences("brexa_prefs", Context.MODE_PRIVATE) }

    val savedSideIsLeft = remember { prefs.getBoolean("brexaman_is_left", false) }
    val savedRatioY = remember { prefs.getFloat("brexaman_ratio_y", 0.40f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(99f)
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        // Tamaño compacto y proporcionado
        val mascotWidthDp = 64.dp
        val mascotHeightDp = 119.dp
        val mascotWidthPx = with(density) { mascotWidthDp.toPx() }
        val mascotHeightPx = with(density) { mascotHeightDp.toPx() }

        val topMarginPx = with(density) { 85.dp.toPx() }
        val bottomMarginPx = with(density) { 95.dp.toPx() }
        val maxAvailableY = (screenHeightPx - mascotHeightPx - bottomMarginPx).coerceAtLeast(topMarginPx)

        val leftAnchorX = 0f
        val rightAnchorX = (screenWidthPx - mascotWidthPx).coerceAtLeast(0f)

        val initialX = if (savedSideIsLeft) leftAnchorX else rightAnchorX
        val initialY = (screenHeightPx * savedRatioY).coerceIn(topMarginPx, maxAvailableY)

        val animX = remember { Animatable(initialX) }
        val animY = remember { Animatable(initialY) }
        val coroutineScope = rememberCoroutineScope()

        var isDragging by remember { mutableStateOf(false) }
        var isDockedLeft by remember { mutableStateOf(savedSideIsLeft) }
        var showDialogBubble by remember { mutableStateOf(false) }
        var speechBubbleText by remember { mutableStateOf("¡Hola! Soy Brexaman. ¿En qué te ayudo hoy?") }

        // Parpadeo orgánico de ojos
        var isBlinking by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(2800L, 5200L))
                isBlinking = true
                delay(140L)
                isBlinking = false
            }
        }

        // Micro-animación de respiración / flotación idle
        val infiniteTransition = rememberInfiniteTransition(label = "brexaman_breath")
        val breathOffsetY by infiniteTransition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breath_y"
        )

        LaunchedEffect(showDialogBubble) {
            if (showDialogBubble) {
                delay(4500)
                showDialogBubble = false
            }
        }

        val speechGreetings = remember {
            listOf(
                "¡Hola! Cuidando cada centavo de tus cuentas.",
                "¡Brexaman al servicio de tu bolsillo!",
                "Toca aquí para analizar tus finanzas.",
                "¿Sabías que puedes moverme a cualquier borde?",
                "Todo bajo control financiero hoy."
            )
        }

        // 1. EL MUÑECO BREXAMAN
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = animX.value.roundToInt(),
                        y = (animY.value + if (isDragging) 0f else breathOffsetY).roundToInt()
                    )
                }
                .size(width = mascotWidthDp, height = mascotHeightDp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            showDialogBubble = false
                        },
                        onDragEnd = {
                            isDragging = false
                            val targetLeft = (animX.value + mascotWidthPx / 2f) < (screenWidthPx / 2f)
                            isDockedLeft = targetLeft
                            val targetX = if (targetLeft) leftAnchorX else rightAnchorX
                            val targetY = animY.value.coerceIn(topMarginPx, maxAvailableY)

                            prefs.edit()
                                .putBoolean("brexaman_is_left", targetLeft)
                                .putFloat("brexaman_ratio_y", targetY / screenHeightPx)
                                .apply()

                            coroutineScope.launch {
                                animX.animateTo(
                                    targetValue = targetX,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            coroutineScope.launch {
                                animY.animateTo(
                                    targetValue = targetY,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                animX.snapTo(animX.value + dragAmount.x)
                                animY.snapTo((animY.value + dragAmount.y).coerceIn(topMarginPx, maxAvailableY))
                            }
                        }
                    )
                }
        ) {
            Image(
                painter = painterResource(
                    id = if (isBlinking) R.drawable.brexaman_peeking_blink else R.drawable.brexaman_peeking
                ),
                contentDescription = "Brexaman Mascota",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = if (isDockedLeft) -1f else 1f
                    }
                    .clickable {
                        speechBubbleText = speechGreetings.random()
                        showDialogBubble = !showDialogBubble
                    }
            )
        }

        // 2. VENTANA FLOTANTE DE TEXTO (GLOBO DE DIÁLOGO ADAPTATIVO)
        // Se coloca como hermano directo para tener el ancho completo de la pantalla y no ser comprimido
        val bubbleMaxWidthDp = (maxWidth - mascotWidthDp - 28.dp).coerceIn(170.dp, 240.dp)
        val bubbleMaxWidthPx = with(density) { bubbleMaxWidthDp.toPx() }

        val bubbleTargetX = if (isDockedLeft) {
            // A la derecha de Brexaman
            (animX.value + mascotWidthPx + with(density) { 8.dp.toPx() })
        } else {
            // A la izquierda de Brexaman
            (animX.value - bubbleMaxWidthPx - with(density) { 8.dp.toPx() }).coerceAtLeast(with(density) { 10.dp.toPx() })
        }

        AnimatedVisibility(
            visible = showDialogBubble && !isDragging,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = bubbleTargetX.roundToInt(),
                        y = (animY.value + if (isDragging) 0f else breathOffsetY).roundToInt().coerceAtLeast(with(density) { 10.dp.toPx() }.toInt())
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 160.dp, max = bubbleMaxWidthDp)
                    .background(SurfaceCardElevated, RoundedCornerShape(14.dp))
                    .border(1.5.dp, AccentLime, RoundedCornerShape(14.dp))
                    .clickable {
                        showDialogBubble = false
                        onOpenAssistant()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "BREXAMAN // IA",
                        style = BrexaTypography.labelSmall.copy(
                            color = AccentLime,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        ),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = speechBubbleText,
                    style = BrexaTypography.bodySmall.copy(
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    softWrap = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "TOCA PARA CHATEAR ⚡",
                    style = BrexaTypography.labelSmall.copy(
                        color = AccentPurple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
