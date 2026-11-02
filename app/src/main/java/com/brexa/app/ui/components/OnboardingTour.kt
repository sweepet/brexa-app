package com.brexa.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.ui.theme.*

data class SpotlightStep(
    val title: String,
    val message: String
)

@Composable
fun OnboardingTour(
    currentStepIndex: Int,
    targetRect: Rect?,
    onStepChange: (Int) -> Unit,
    onFinishTour: () -> Unit
) {
    val steps = remember {
        listOf(
            SpotlightStep(
                title = "PATRIMONIO TOTAL",
                message = "Tu saldo total en Dólares y su valor equivalente en vivo en Bolívares (BCV) o Pesos (TRM)."
            ),
            SpotlightStep(
                title = "MOVIMIENTOS RÁPIDOS",
                message = "Anota compras del día a día (− GASTAR), ingresos (+ INGRESAR) o transferencias y cambio de divisas (⇄)."
            ),
            SpotlightStep(
                title = "CUENTAS Y MONEDAS",
                message = "Toca en GESTIONAR para crear nuevas cuentas o actualizar las tasas oficiales del día con 1 toque."
            )
        )
    }

    val currentStep = steps[currentStepIndex.coerceIn(0, steps.lastIndex)]
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. RECORTE SPOTLIGHT (HOLE-PUNCH CANVAS)
        // El fondo oscuro deja ver el elemento real en 100% de brillo con un marco Neón Lima
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Bloquea clics a la pantalla de fondo */ }
                )
        ) {
            val fullScreenPath = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
            }

            if (targetRect != null && targetRect.width > 0f && targetRect.height > 0f) {
                val pad = 4.dp.toPx()
                val paddedRect = Rect(
                    left = (targetRect.left - pad).coerceAtLeast(0f),
                    top = (targetRect.top - pad).coerceAtLeast(0f),
                    right = (targetRect.right + pad).coerceAtMost(size.width),
                    bottom = (targetRect.bottom + pad).coerceAtMost(size.height)
                )

                val holePath = Path().apply {
                    addRoundRect(RoundRect(paddedRect, CornerRadius(6.dp.toPx(), 6.dp.toPx())))
                }

                val scrimPath = Path.combine(PathOperation.Difference, fullScreenPath, holePath)
                drawPath(scrimPath, color = Color(0xD9000000)) // Fondo oscuro 85%

                // Marco Neobrutalista Neón Lima resaltando la zona real
                drawRoundRect(
                    color = AccentLime,
                    topLeft = Offset(paddedRect.left, paddedRect.top),
                    size = Size(paddedRect.width, paddedRect.height),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            } else {
                drawPath(fullScreenPath, color = Color(0xD9000000))
            }
        }

        // 2. BURBUJA MICRO-TOOLTIP (SOLO 1 FRASE CORTA Y ACCIONES RÁPIDAS)
        val estimatedBubbleHeightPx = with(density) { 150.dp.toPx() }
        val isBelow = targetRect == null || (targetRect.bottom + estimatedBubbleHeightPx + with(density) { 24.dp.toPx() } <= screenHeightPx)

        val yOffsetPx = if (targetRect == null) {
            (screenHeightPx - estimatedBubbleHeightPx) / 2f
        } else if (isBelow) {
            targetRect.bottom + with(density) { 12.dp.toPx() }
        } else {
            (targetRect.top - estimatedBubbleHeightPx - with(density) { 12.dp.toPx() }).coerceAtLeast(with(density) { 24.dp.toPx() })
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, yOffsetPx.toInt()) }
                .padding(horizontal = 16.dp)
        ) {
            BrutalistCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = SurfaceCard,
                borderColor = AccentLime,
                borderWidth = 2.dp,
                shadowColor = Color.Black,
                shadowOffset = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Encabezado compacto: Paso + Título + Omitir
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrutalistBadge(
                                text = "${currentStepIndex + 1}/3",
                                backgroundColor = BackgroundBase,
                                contentColor = AccentLime,
                                borderColor = AccentLime,
                                fontSize = 9.sp,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentStep.title,
                                style = TextStyle(
                                    fontFamily = SpaceMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = AccentLime
                                )
                            )
                        }

                        Text(
                            text = "OMITIR ✕",
                            style = TextStyle(
                                fontFamily = SpaceMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = TextSecondary
                            ),
                            modifier = Modifier
                                .clickable { onFinishTour() }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 1 Sola Frase Clara y Directa (Micro-texto)
                    Text(
                        text = currentStep.message,
                        style = BrexaTypography.bodyMedium.copy(
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Botones de acción ultra-compactos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStepIndex > 0) {
                            Box(modifier = Modifier.weight(1f)) {
                                BrutalistButton(
                                    text = "← ATRÁS",
                                    onClick = { onStepChange(currentStepIndex - 1) },
                                    containerColor = SurfaceCardElevated,
                                    contentColor = TextPrimary,
                                    borderColor = BorderSolid,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(if (currentStepIndex > 0) 1.3f else 1f)) {
                            BrutalistButton(
                                text = if (currentStepIndex == steps.lastIndex) "¡LISTO! ⚡" else "SIGUIENTE ➔",
                                onClick = {
                                    if (currentStepIndex == steps.lastIndex) {
                                        onFinishTour()
                                    } else {
                                        onStepChange(currentStepIndex + 1)
                                    }
                                },
                                containerColor = AccentLime,
                                contentColor = Color.Black,
                                borderColor = BorderBright,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
