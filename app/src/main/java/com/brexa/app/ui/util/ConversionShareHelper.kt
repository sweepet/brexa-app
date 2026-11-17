package com.brexa.app.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ShareConversionRow(
    val title: String,
    val amountFormatted: String,
    val rateFormatted: String
)

object ConversionShareHelper {

    fun shareConversion(
        context: Context,
        baseAmountFormatted: String,
        conversions: List<ShareConversionRow>,
        dateHeader: String = ""
    ) {
        val currentDateStr = if (dateHeader.isNotBlank()) {
            dateHeader
        } else {
            val sdf = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
            sdf.format(Date()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "ES")) else it.toString() }
        }

        // Construcción del texto formateado para portapapeles y caption de WhatsApp
        val textSummary = buildString {
            append("⚡ Brexa // Conversión de Tasas\n")
            append("📅 $currentDateStr\n\n")
            append("💵 MONTO: $baseAmountFormatted\n")
            append("─────────────────────────\n")
            conversions.forEach { row ->
                append("• ${row.title}: ${row.amountFormatted}\n")
                if (row.rateFormatted.isNotBlank()) {
                    append("  (Tasa: ${row.rateFormatted})\n")
                }
            }
            append("─────────────────────────\n")
            append("📱 Calculado con Brexa App")
        }

        try {
            val bitmap = createConversionBitmap(
                baseAmount = baseAmountFormatted,
                conversions = conversions,
                dateStr = currentDateStr
            )

            val shareDir = File(context.cacheDir, "shares").apply { if (!exists()) mkdirs() }
            val file = File(shareDir, "brexa_conversion.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, textSummary)
                putExtra(Intent.EXTRA_SUBJECT, "Conversión de Tasas • Brexa")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Compartir conversión")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Fallback seguro a compartir solo texto en caso de error de memoria o archivo
            val textIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, textSummary)
                putExtra(Intent.EXTRA_SUBJECT, "Conversión de Tasas • Brexa")
            }
            val chooser = Intent.createChooser(textIntent, "Compartir conversión")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    private fun createConversionBitmap(
        baseAmount: String,
        conversions: List<ShareConversionRow>,
        dateStr: String
    ): Bitmap {
        val width = 1080
        val outerMargin = 48f
        val cardPadding = 56f
        val cardWidth = width - (outerMargin * 2)

        val headerHeight = 170f
        val baseBoxHeight = 160f
        val rowHeight = 135f
        val rowsTotalHeight = conversions.size * rowHeight
        val footerHeight = 100f
        val cardHeight = headerHeight + baseBoxHeight + rowsTotalHeight + footerHeight

        val totalHeight = (cardHeight + (outerMargin * 2)).toInt()

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Fondo exterior (Dark Obsidian #0E1116)
        canvas.drawColor(Color.parseColor("#0E1116"))

        // 2. Tarjeta principal (#161B22) con esquinas redondeadas y borde sutil
        val cardLeft = outerMargin
        val cardTop = outerMargin
        val cardRight = cardLeft + cardWidth
        val cardBottom = cardTop + cardHeight
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#161B22")
            style = Paint.Style.FILL
        }
        val cornerRadius = 32f
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardPaint)

        // Borde exterior de la tarjeta (#30363D)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#30363D")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

        // Barra decorativa superior Turquesa Neón (#00E5FF)
        val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.FILL
        }
        val barRect = RectF(cardLeft + 24f, cardTop, cardRight - 24f, cardTop + 6f)
        canvas.drawRoundRect(barRect, 3f, 3f, accentBarPaint)

        // Pinceles de texto
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8B949E")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8B949E")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#21262D")
            strokeWidth = 2f
        }

        var currentY = cardTop + cardPadding + 30f

        // HEADER: Logo y Subtítulo
        canvas.drawText("⚡ BREXA", cardLeft + cardPadding, currentY, brandPaint)
        canvas.drawText("CONVERSIÓN DE TASAS", cardLeft + cardPadding, currentY + 36f, titlePaint)

        val dateWidth = datePaint.measureText(dateStr)
        canvas.drawText(dateStr, cardRight - cardPadding - dateWidth, currentY + 10f, datePaint)

        currentY += 80f
        canvas.drawLine(cardLeft + cardPadding, currentY, cardRight - cardPadding, currentY, dividerPaint)
        currentY += 36f

        // BLOQUE MONEDA BASE (Valor ingresado)
        val baseBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0E1116")
            style = Paint.Style.FILL
        }
        val baseBoxBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val baseBoxRect = RectF(
            cardLeft + cardPadding,
            currentY,
            cardRight - cardPadding,
            currentY + baseBoxHeight - 40f
        )
        canvas.drawRoundRect(baseBoxRect, 18f, 18f, baseBoxPaint)
        canvas.drawRoundRect(baseBoxRect, 18f, 18f, baseBoxBorder)

        val baseLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8B949E")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.05f
        }
        canvas.drawText("MONTO A CONVERTIR", baseBoxRect.left + 24f, baseBoxRect.top + 40f, baseLabelPaint)

        val baseAmountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFFF")
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(baseAmount, baseBoxRect.left + 24f, baseBoxRect.top + 92f, baseAmountPaint)

        currentY += baseBoxHeight + 10f

        // FILAS DE CONVERSIONES
        val convTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0F6FC")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val convRatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8B949E")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val convAmountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        conversions.forEachIndexed { index, row ->
            val rowStartY = currentY
            canvas.drawText(row.title, cardLeft + cardPadding, rowStartY + 40f, convTitlePaint)
            if (row.rateFormatted.isNotBlank()) {
                canvas.drawText(row.rateFormatted, cardLeft + cardPadding, rowStartY + 76f, convRatePaint)
            }

            val amountWidth = convAmountPaint.measureText(row.amountFormatted)
            canvas.drawText(row.amountFormatted, cardRight - cardPadding - amountWidth, rowStartY + 54f, convAmountPaint)

            currentY += rowHeight
            if (index < conversions.size - 1) {
                canvas.drawLine(cardLeft + cardPadding, currentY - 20f, cardRight - cardPadding, currentY - 20f, dividerPaint)
            }
        }

        // FOOTER
        canvas.drawLine(cardLeft + cardPadding, currentY, cardRight - cardPadding, currentY, dividerPaint)
        currentY += 46f

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8B949E")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val footerText = "brexa.app • Finanzas Personales Multidivisa"
        val footerWidth = footerPaint.measureText(footerText)
        canvas.drawText(footerText, (width - footerWidth) / 2f, currentY, footerPaint)

        return bitmap
    }
}
