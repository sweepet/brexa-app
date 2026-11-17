package com.brexa.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// PALETA OFICIAL BREXA - ESTILO FINTECH BINANCE (Clean Light Pro)
// Identidad de marca: Turquesa Neón (#00E5FF) sustituyendo al amarillo de Binance
// ============================================================================

// 1. Color de Marca Identitario Brexa (#00E5FF)
val AccentTurquoise = Color(0xFF00E5FF)       // Turquesa Neón Oficial Brexa
val AccentTurquoiseDark = Color(0xFF0097A7)   // Turquesa profundo para textos sobre fondo blanco
val AccentTurquoiseGlow = Color(0xFF80F4FF)   // Tinte suave de resplandor

// Mapeos retrocompatibles para heredar el nuevo color de marca de inmediato
val AccentGold = AccentTurquoise              // Sustituye el antiguo dorado por Turquesa Neón
val AccentLime = AccentTurquoise              // Retrocompatibilidad
val AccentAmber = AccentTurquoise             // Retrocompatibilidad

// 2. Fondos y Superficies Estilo Binance (Fondo limpio blanco y tarjetas redondeadas suaves)
val BackgroundBase = Color(0xFFFFFFFF)        // Fondo blanco puro de Binance
val BackgroundElevated = Color(0xFFF8F9FA)    // Superficie elevada suave
val SurfaceCard = Color(0xFFF5F6F8)           // Tarjeta estándar gris suave Binance (#F5F6F8)
val SurfaceCardElevated = Color(0xFFFFFFFF)   // Tarjeta destacada con elevación
val SurfaceCardInput = Color(0xFFF0F2F5)      // Campo de entrada y búsqueda

// 3. Delimitadores y Bordes Sutiles
val BorderSolid = Color(0xFFEAECEF)           // Delimitador ultra sutil de Binance (#EAECEF)
val BorderBright = AccentTurquoise            // Borde activo en Turquesa Neón
val BorderSubtle = Color(0xFFF1F3F5)          // Borde casi imperceptible

// 4. Semántica Financiera Oficial Binance
val IncomeGreen = Color(0xFF0ECB81)           // Verde Binance (#0ECB81) para ingresos y variaciones positivas
val AccentGreen = IncomeGreen                 // Alias
val ExpenseRed = Color(0xFFF6465D)            // Rojo Binance (#F6465D) para gastos y variaciones negativas
val ExchangeBlue = Color(0xFF1E88E5)          // Azul Binance para transferencias
val AccentCyan = AccentTurquoise              // Alias
val AccentPurple = Color(0xFF8B5CF6)          // Violeta complementario

// 5. Tipografía y Contraste Descansado Binance
val TextPrimary = Color(0xFF181A20)           // Negro grafito de Binance (#181A20)
val TextSecondary = Color(0xFF707A8A)         // Gris intermedio de Binance (#707A8A)
val TextMuted = Color(0xFF848E9C)             // Gris claro de Binance (#848E9C)
val TextOnLime = Color(0xFF0E1116)            // Texto oscuro de alto contraste sobre Turquesa Neón
val TextOnAccent = Color(0xFF0E1116)          // Texto oscuro sobre Turquesa Neón
