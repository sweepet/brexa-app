package com.brexa.app.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta Oficial Brexa - Slate & Prestige Gold (Regla 60-30-10)
// 60% Base Dominante (Pizarra Profundo Cálido, descansado para la vista)
val BackgroundBase = Color(0xFF0D1117)
val BackgroundElevated = Color(0xFF131822)

// 30% Superficie Estructural (Tarjetas pulidas, bordes sutiles y contenedores)
val SurfaceCard = Color(0xFF171E2B)
val SurfaceCardElevated = Color(0xFF1F293B)
val SurfaceCardInput = Color(0xFF131924)
val BorderSolid = Color(0xFF263245)
val BorderBright = Color(0xFF384761)
val BorderSubtle = Color(0xFF1F293A)

// 10% Acentos Intencionales y Significativos
val AccentGold = Color(0xFFF59E0B)       // Dorado / Ámbar Cálido (USD, Tasas principales y Valores destacados)
val AccentLime = AccentGold              // Mapeo retrocompatible para heredar el dorado cálido en lugar del verde neón chillón
val AccentAmber = Color(0xFFF59E0B)      // Alias
val IncomeGreen = Color(0xFF10B981)      // Esmeralda Cálido (Ingresos, variaciones positivas y balances a favor)
val AccentGreen = IncomeGreen            // Alias
val ExpenseRed = Color(0xFFEF4444)       // Coral Rojo Cálido (Gastos, advertencias y variaciones negativas)
val ExchangeBlue = Color(0xFF3B82F6)     // Azul Real (Transferencias y cotizaciones de referencia)
val AccentCyan = Color(0xFF06B6D4)       // Cyan suave para herramientas analíticas
val AccentPurple = Color(0xFF8B5CF6)     // Violeta suave para categorías complementarias

// Textos (Contraste equilibrado sin encandilar)
val TextPrimary = Color(0xFFF8FAFC)      // Blanco suave de alta legibilidad
val TextSecondary = Color(0xFF94A3B8)    // Gris pizarra intermedio
val TextMuted = Color(0xFF64748B)        // Gris apagado para metadatos
val TextOnLime = Color(0xFF0F172A)       // Texto oscuro de alto contraste sobre botones dorados/brillantes
val TextOnAccent = Color(0xFF0F172A)
