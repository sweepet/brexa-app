package com.brexa.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// PALETA OFICIAL BREXA - DARK OBSIDIAN & NEON TURQUOISE (Teoría del Color Pro)
// Fondo gris-azul muy oscuro mate (#0E1116) idéntico al squircle del logo de Brexa
// Contraste AAA con Turquesa Neón (#00E5FF), Verde Financiero y Rojo Financiero
// ============================================================================

// 1. Color de Marca Identitario Oficial Brexa (#00E5FF)
val AccentTurquoise = Color(0xFF00E5FF)       // Turquesa Neón Oficial Brexa (13.5:1 contraste en fondo oscuro)
val AccentTurquoiseDark = Color(0xFF00B4D8)   // Turquesa profundo balanceado
val AccentTurquoiseGlow = Color(0xFF80F4FF)   // Resplandor cian suave

// Mapeos retrocompatibles
val AccentGold = AccentTurquoise
val AccentLime = AccentTurquoise
val AccentAmber = AccentTurquoise

// 2. Fondos y Superficies Dark Obsidian Mate (#0E1116 - Squircle Oficial)
val BackgroundBase = Color(0xFF0E1116)        // Gris-azul oscuro mate profundo (#0E1116)
val BackgroundElevated = Color(0xFF13171F)    // Superficie elevada suave
val SurfaceCard = Color(0xFF161B22)           // Tarjeta estándar carbón oscuro aterciopelado
val SurfaceCardElevated = Color(0xFF1E2530)   // Tarjeta destacada y modales
val SurfaceCardInput = Color(0xFF11151D)      // Campos de texto y teclado

// 3. Delimitadores y Micro-bordes Sutiles (1dp)
val BorderSolid = Color(0xFF263040)           // Borde ultra sutil y refinado
val BorderBright = AccentTurquoise            // Borde activo en Turquesa Neón
val BorderSubtle = Color(0xFF1C232E)          // Borde casi imperceptible

// 4. Semántica Financiera Exclusiva (Únicos con Rojo y Verde según regla visual)
val IncomeGreen = Color(0xFF0ECB81)           // Verde Financiero (#0ECB81) exclusivo para ingresos y ganancias
val AccentGreen = IncomeGreen
val ExpenseRed = Color(0xFFF6465D)            // Rojo Financiero (#F6465D) exclusivo para gastos, alertas y vencidos
val ExchangeBlue = AccentTurquoise            // Transferencias alineadas con la identidad de marca
val AccentCyan = AccentTurquoise
val AccentPurple = Color(0xFF8F9CAE)          // Mapeado a gris pizarra para eliminar morados discordantes

// 5. Tipografía y Máxima Legibilidad Descansada
val TextPrimary = Color(0xFFF0F4F8)           // Blanco marfil nítido de alto contraste (15:1)
val TextSecondary = Color(0xFF8F9CAE)         // Gris pizarra intermedio descansado para la vista
val TextMuted = Color(0xFF5A687A)             // Gris apagado para metadatos y subtítulos
val TextOnAccent = Color(0xFF0E1116)          // Texto oscuro de alto contraste sobre Turquesa Neón
val TextOnLime = Color(0xFF0E1116)
