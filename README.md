# ⚡ Brexa — Finanzas Personales Multimoneda

> **Brexa** es una aplicación móvil nativa para Android diseñada para el control financiero personal en economías de alta inflación y multimoneda (con enfoque prioritario en Venezuela). Su pilar fundamental es la estabilidad del poder adquisitivo: registrar y consolidar toda la actividad en una moneda funcional dura (**USD**), permitiendo la coexistencia dinámica de divisas locales y criptoactivos sin distorsión contable.

---

## 🎨 Características Principales
- **Diseño Neobrutalista Estricto**: Alto contraste (`#111111`, Lima Neón `#D4FF00`, Violeta Eléctrico `#9D4EDD`), bordes sólidos de `2.dp`, esquinas rectas (`0.dp`), sombras duras offset y tipografía `Space Mono`.
- **Formulario Inteligente con Tasa Manual en Tiempo Real**: Entrada numérica precisa de la cotización con cálculo interactivo y reactivo del equivalente en moneda base.
- **Transacciones Bimonetarias Inmutables**: Congelación histórica del poder adquisitivo en USD al momento de cada gasto/ingreso.
- **Operación "Exchange"**: Canje neutral entre cuentas propias sin distorsión de gastos ni ingresos.
- **Patrimonio Consolidado Vivo**: Valuación en tiempo real del saldo disponible según las tasas vigentes configuradas.
- **Respaldo Privado y Gratuito**: Sincronización en la carpeta privada de Google Drive (`appDataFolder`) sin costos de servidor y funcionamiento 100% Offline-First.
- **Auto-Actualización In-App**: Detección y descarga automática de nuevas versiones directamente desde la aplicación.

---

## 🛠️ Stack Tecnológico
- **Lenguaje**: Kotlin 2.0+
- **UI Toolkit**: Jetpack Compose con Material Design 3
- **Base de Datos**: Room (SQLite) con Flow reactivo
- **Arquitectura**: Clean Architecture + MVVM
- **Aritmética Financiera**: `BigDecimal` de alta precisión
