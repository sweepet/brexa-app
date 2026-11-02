package com.brexa.app.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.ui.theme.*

enum class TourPointerDirection {
    UP,
    DOWN,
    NONE
}

data class TourStep(
    val stepIndex: Int,
    val totalSteps: Int,
    val targetIndex: Int,
    val pointerDirection: TourPointerDirection,
    val badgeText: String,
    val title: String,
    val description: String,
    val tip: String,
    val cardAlignment: Alignment
)

@Composable
fun OnboardingTour(
    currentStepIndex: Int,
    onStepChange: (Int) -> Unit,
    onFinishTour: () -> Unit
) {
    val steps = remember {
        listOf(
            TourStep(
                stepIndex = 0,
                totalSteps = 7,
                targetIndex = 0,
                pointerDirection = TourPointerDirection.UP,
                badgeText = "▲ MIRA LA BARRA SUPERIOR (OJITO Y ENGRANAJE)",
                title = "TU TERMINAL Y PRIVACIDAD",
                description = "En la barra superior tienes el control:\n\n• 👁 VISIBLE / OCULTO: Toca el ojito para esconder tus saldos al instante cuando estés en la calle o con personas cerca.\n• 📊 GRÁFICAS: Consulta resúmenes y estadísticas de tus gastos.\n• ⚙ AJUSTES: Conecta tu Google Drive para tener copia de seguridad automática y no perder tus datos.",
                tip = "💡 Tus datos son 100% privados: se guardan en tu teléfono y en tu propio Google Drive.",
                cardAlignment = Alignment.TopCenter
            ),
            TourStep(
                stepIndex = 1,
                totalSteps = 7,
                targetIndex = 0,
                pointerDirection = TourPointerDirection.UP,
                badgeText = "▲ MIRA LA TARJETA VERDE LIMA",
                title = "TU DINERO TOTAL CONSOLIDADO",
                description = "Esta tarjeta suma todo tu dinero de todas tus cuentas convertido a tu moneda principal (por ejemplo, USD $).\n\nAbajo en morado ves el equivalente en tu moneda local (como Bolívares o Euros). Si manejas varias monedas, toca el símbolo ⇄ para alternarlas al momento.",
                tip = "💡 Ya no necesitas calcular a mano con una calculadora: Brexa hace la conversión en vivo con tu tasa del día.",
                cardAlignment = Alignment.BottomCenter
            ),
            TourStep(
                stepIndex = 2,
                totalSteps = 7,
                targetIndex = 2,
                pointerDirection = TourPointerDirection.UP,
                badgeText = "▲ MIRA LOS BOTONES DE MOVIMIENTOS",
                title = "REGISTRA GASTOS, INGRESOS Y CAMBIOS",
                description = "Usa estos 3 botones para tus operaciones rápidas:\n\n• − GASTAR (Rojo): Anota cualquier compra o gasto.\n• + INGRESAR (Verde): Suma sueldos, cobros o ventas.\n• ⇄ TRANSFERIR (Azul): Pasa dinero entre tus cuentas o anota cambios de divisas (por ejemplo: cambiaste 20$ en efectivo a Bolívares en tu banco a la tasa del día).",
                tip = "💡 Cuando transfieres entre diferentes monedas, Brexa calcula sola la tasa real a la que cambiaste.",
                cardAlignment = Alignment.BottomCenter
            ),
            TourStep(
                stepIndex = 3,
                totalSteps = 7,
                targetIndex = 4,
                pointerDirection = TourPointerDirection.DOWN,
                badgeText = "▼ MIRA EL CARRUSEL DE CUENTAS",
                title = "TUS CUENTAS (¡CREA LAS QUE NECESITES!)",
                description = "Aquí ves tus cuentas: Efectivo, Bancos, Billeteras digitales o Cripto.\n\nPuedes tener hasta 11 cuentas activas. Tocando 'GESTIONAR ➔' arriba a la derecha puedes CREAR NUEVAS CUENTAS con el nombre, icono y moneda que tú quieras.",
                tip = "💡 Si algún día el saldo no coincide con tu bolsillo real, entra a la cuenta y usa 'Ajustar saldo' para reconciliarlo en un segundo.",
                cardAlignment = Alignment.TopCenter
            ),
            TourStep(
                stepIndex = 4,
                totalSteps = 7,
                targetIndex = 3,
                pointerDirection = TourPointerDirection.DOWN,
                badgeText = "▼ TOCA 'GESTIONAR ➔' PESTAÑA MONEDAS",
                title = "MONEDAS PREDETERMINADAS Y TASAS",
                description = "Brexa incluye el Dólar como moneda base, además del Bolívar (BCV) y el Peso Colombiano (TRM) con actualización oficial al momento.\n\nEn 'GESTIONAR ➔ MONEDAS' puedes:\n• Actualizar las tasas oficiales con 1 toque o fijarlas a mano.\n• Crear cualquier otra moneda que uses (Euros, USDT, etc.).",
                tip = "💡 Si asignas BCV o COP a una de tus cuentas, su valor se recalculará automáticamente con la tasa oficial.",
                cardAlignment = Alignment.TopCenter
            ),
            TourStep(
                stepIndex = 5,
                totalSteps = 7,
                targetIndex = 1,
                pointerDirection = TourPointerDirection.UP,
                badgeText = "▲ MIRA LA TARJETA DE PAGOS PENDIENTES",
                title = "PAGOS PENDIENTES Y SALDO LIBRE REAL",
                description = "Anota aquí tus deudas, servicios o pagos por hacer con su fecha límite. Brexa te avisa con colores si vencen hoy o en próximos días.\n\nTambién calcula tu 'Saldo Libre Real': el dinero que de verdad te queda libre para gastar después de apartar tus deudas pendientes.",
                tip = "💡 Al pagar una deuda, tócala y pulsa 'Pagar ahora': se descontará sola de la cuenta que elijas.",
                cardAlignment = Alignment.BottomCenter
            ),
            TourStep(
                stepIndex = 6,
                totalSteps = 7,
                targetIndex = 0,
                pointerDirection = TourPointerDirection.NONE,
                badgeText = "⚡ ¡BREXA ESTÁ EN TUS MANOS!",
                title = "¡TODO LISTO PARA EMPEZAR!",
                description = "Ya conoces lo esencial para dominar tus finanzas.\n\nCrea tus cuentas, ajusta tus monedas y registra tus primeros movimientos. ¡Verás lo fácil que es tener el control total!\n\nPuedes volver a ver esta guía cuando quieras desde Ajustes (⚙).",
                tip = "🚀 ¡Vamos a tomar el control de cada centavo!",
                cardAlignment = Alignment.Center
            )
        )
    }

    val currentStep = steps[currentStepIndex.coerceIn(0, steps.lastIndex)]

    // Animación suave de rebote vertical para la flecha indicadora
    val infiniteTransition = rememberInfiniteTransition(label = "pointerBounce")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    // Capa de fondo oscurecida (scrim) que no deja pasar clics accidentales a la pantalla inferior
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* Bloquea clics a la pantalla de fondo */ }
            )
    ) {
        val topPadding = if (currentStep.cardAlignment == Alignment.TopCenter) 76.dp else 16.dp
        val bottomPadding = if (currentStep.cardAlignment == Alignment.BottomCenter) 24.dp else 16.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 14.dp, top = topPadding, bottom = bottomPadding),
            contentAlignment = currentStep.cardAlignment
        ) {
            BrutalistCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 450.dp),
                backgroundColor = SurfaceCard,
                borderColor = AccentLime,
                borderWidth = 2.dp,
                shadowColor = Color.Black,
                shadowOffset = 5.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Fila superior: Indicador de paso + Botón Omitir
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrutalistBadge(
                            text = "PASO ${currentStep.stepIndex + 1} DE ${steps.size}",
                            backgroundColor = BackgroundBase,
                            contentColor = AccentLime,
                            borderColor = AccentLime,
                            fontSize = 9.5.sp
                        )

                        Text(
                            text = "OMITIR GUÍA ✕",
                            style = TextStyle(
                                fontFamily = SpaceMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = TextSecondary
                            ),
                            modifier = Modifier
                                .clickable { onFinishTour() }
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Globo o insignia indicadora con animación de rebote hacia el elemento
                    if (currentStep.pointerDirection != TourPointerDirection.NONE) {
                        val yOffset = if (currentStep.pointerDirection == TourPointerDirection.UP) {
                            (-bounceOffset).dp
                        } else {
                            bounceOffset.dp
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = yOffset),
                            contentAlignment = Alignment.Center
                        ) {
                            BrutalistBadge(
                                text = currentStep.badgeText,
                                backgroundColor = AccentLime,
                                contentColor = Color.Black,
                                borderColor = BorderBright,
                                fontSize = 10.sp,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Contenido textual con scroll vertical protegido para pantallas pequeñas o fuentes grandes
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 270.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = currentStep.title.uppercase(),
                            style = TextStyle(
                                fontFamily = SpaceMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                letterSpacing = 0.5.sp,
                                color = AccentLime
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = currentStep.description,
                            style = BrexaTypography.bodyMedium.copy(
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )
                        )

                        if (currentStep.tip.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BackgroundBase, RectangleShape)
                                    .border(1.dp, AccentPurple, RectangleShape)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = currentStep.tip,
                                    style = BrexaTypography.labelSmall.copy(
                                        color = AccentPurple,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Barra de progreso con puntos (dots)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.indices.forEach { idx ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(
                                        width = if (idx == currentStep.stepIndex) 16.dp else 6.dp,
                                        height = 5.dp
                                    )
                                    .background(
                                        if (idx == currentStep.stepIndex) AccentLime else BorderSolid,
                                        RectangleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Botones de Navegación Neobrutalistas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStep.stepIndex > 0) {
                            Box(modifier = Modifier.weight(1f)) {
                                BrutalistButton(
                                    text = "← ANTERIOR",
                                    onClick = { onStepChange(currentStep.stepIndex - 1) },
                                    containerColor = SurfaceCardElevated,
                                    contentColor = TextPrimary,
                                    borderColor = BorderSolid,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(if (currentStep.stepIndex > 0) 1.4f else 1f)) {
                            BrutalistButton(
                                text = if (currentStep.stepIndex == steps.lastIndex) "¡EMPEZAR AHORA! ⚡" else "SIGUIENTE ➔",
                                onClick = {
                                    if (currentStep.stepIndex == steps.lastIndex) {
                                        onFinishTour()
                                    } else {
                                        onStepChange(currentStep.stepIndex + 1)
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
