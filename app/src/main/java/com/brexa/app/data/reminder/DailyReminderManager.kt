package com.brexa.app.data.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.brexa.app.MainActivity
import com.brexa.app.R
import com.brexa.app.data.local.BrexaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DailyReminderManager {

    const val CHANNEL_ID = "brexa_daily_reminder"
    const val NOTIFICATION_ID = 1001
    const val REQUEST_CODE = 2001
    const val ACTION_DAILY_REMINDER = "com.brexa.app.ACTION_DAILY_REMINDER"

    // Canal y configuración para avisos matutinos de compromisos/pagos vencidos hoy
    const val DUE_PAYMENTS_CHANNEL_ID = "brexa_due_payments"
    const val DUE_PAYMENTS_NOTIFICATION_ID = 1002
    const val DUE_PAYMENTS_REQUEST_CODE = 2002
    const val ACTION_DUE_PAYMENTS_REMINDER = "com.brexa.app.ACTION_DUE_PAYMENTS_REMINDER"
    const val KEY_LAST_NOTIFIED_DUE_PAYMENT_DATE = "last_due_payment_notification_date"

    private const val PREFS_NAME = "brexa_reminder_prefs"
    private const val KEY_ENABLED = "reminder_enabled"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_MINUTE = "reminder_minute"

    private val REMINDER_MESSAGES = listOf(
        Pair("BREXA // CIERRE DEL DÍA ⚡", "¿Registraste tus gastos de hoy? Mantén tus cuentas claras en 30 segundos."),
        Pair("BREXA // ¿QUÉ MOVIMIENTOS HICISTE HOY?", "No dejes compras ni pagos por fuera. Registra tus movimientos del día."),
        Pair("BREXA // BALANCE DIARIO 📊", "Revisa cómo se movió tu dinero hoy y mantén tu patrimonio exacto al centavo."),
        Pair("BREXA // CONTROL DE PATRIMONIO", "Un minuto diario para tus finanzas. Toca aquí para anotar tus compras de hoy.")
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            scheduleDailyReminder(context)
        } else {
            cancelReminder(context)
        }
    }

    fun getHour(context: Context): Int {
        return getPrefs(context).getInt(KEY_HOUR, 20) // 20:30 (8:30 PM) por defecto
    }

    fun getMinute(context: Context): Int {
        return getPrefs(context).getInt(KEY_MINUTE, 30)
    }

    fun setTime(context: Context, hour: Int, minute: Int) {
        getPrefs(context).edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
        if (isEnabled(context)) {
            scheduleDailyReminder(context)
        }
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val dailyChannel = NotificationChannel(
                CHANNEL_ID,
                "Recordatorio Diario de Gastos",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Aviso diario para registrar gastos y mantener tus cuentas actualizadas"
                enableVibration(true)
            }
            manager.createNotificationChannel(dailyChannel)

            val duePaymentsChannel = NotificationChannel(
                DUE_PAYMENTS_CHANNEL_ID,
                "Avisos de Pagos Pendientes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Aviso matutino único el día exacto que vence un pago fijo o variable"
                enableVibration(true)
            }
            manager.createNotificationChannel(duePaymentsChannel)
        }
    }

    fun scheduleDailyReminder(context: Context) {
        createNotificationChannel(context)

        if (!isEnabled(context)) {
            cancelReminder(context)
            return
        }

        val hour = getHour(context)
        val minute = getMinute(context)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        alarmManager.cancel(pendingIntent)
    }

    fun showNotification(context: Context) {
        createNotificationChannel(context)

        val (title, text) = REMINDER_MESSAGES.random()

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.logo_brexa)
        } catch (_: Exception) {
            null
        }

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brexa_notification)
            .setColor(0xFFD4FF00.toInt()) // Brexa Neon Lime (#D4FF00)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)

        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon)
        }

        val notification = notificationBuilder.build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) { }
    }

    /**
     * Programa la verificación matutina (8:00 AM) para revisar si hay gastos que vencen hoy.
     */
    fun scheduleMorningDuePaymentsCheck(context: Context) {
        createNotificationChannel(context)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = ACTION_DUE_PAYMENTS_REMINDER
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, DUE_PAYMENTS_REQUEST_CODE, intent, flags)

        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    /**
     * Revisa en segundo plano si hay gastos (fijos o variables) que vencen HOY.
     * Se asegura de notificar MÁXIMO 1 VEZ AL DÍA para evitar spam o molestias al usuario.
     */
    fun checkAndNotifyDuePayments(context: Context, isManualTest: Boolean = false) {
        createNotificationChannel(context)
        val prefs = getPrefs(context)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (!isManualTest) {
            val lastDate = prefs.getString(KEY_LAST_NOTIFIED_DUE_PAYMENT_DATE, null)
            if (lastDate == todayStr) {
                // Ya se envió el recordatorio de hoy. No enviar spam.
                return
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = BrexaDatabase.getDatabase(context)
                val allPending = db.pendingExpenseDao().getAllPendingExpensesSync()
                val unpaid = allPending.filter { !it.isPaid }

                if (unpaid.isEmpty() && !isManualTest) return@launch

                val calNow = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val calTomorrow = Calendar.getInstance().apply {
                    timeInMillis = calNow.timeInMillis
                    add(Calendar.DAY_OF_YEAR, 1)
                }

                // Filtrar compromisos que vencen exactamente hoy
                val dueToday = unpaid.filter { item ->
                    item.dueDateTimestamp >= calNow.timeInMillis && item.dueDateTimestamp < calTomorrow.timeInMillis
                }

                // Filtrar compromisos atrasados pendientes
                val overdue = unpaid.filter { item ->
                    item.dueDateTimestamp < calNow.timeInMillis
                }

                if (dueToday.isEmpty() && overdue.isEmpty() && !isManualTest) {
                    // No hay compromisos para hoy ni atrasados: Cero molestias
                    return@launch
                }

                val decimalFormat = DecimalFormat("#,##0.00")
                val title: String
                val text: String

                if (dueToday.isNotEmpty()) {
                    if (dueToday.size == 1) {
                        val item = dueToday[0]
                        title = "BREXA // PAGO PENDIENTE HOY 🔔"
                        val formattedAmount = decimalFormat.format(item.amount)
                        val recurrenceLabel = if (item.expenseType == "FIXED") " (Gasto Fijo)" else ""
                        text = "Hoy vence tu compromiso \"${item.title}\" por $formattedAmount ${item.currencyCode}$recurrenceLabel. Toca para registrarlo."
                    } else {
                        title = "BREXA // ${dueToday.size} PAGOS VENCEN HOY 🔔"
                        val summary = dueToday.take(2).joinToString(", ") {
                            "${it.title} (${decimalFormat.format(it.amount)} ${it.currencyCode})"
                        }
                        val extra = if (dueToday.size > 2) " y ${dueToday.size - 2} más" else ""
                        text = "Hoy debes pagar: $summary$extra. Toca para revisar y confirmar tus pagos."
                    }
                } else if (overdue.isNotEmpty()) {
                    val first = overdue[0]
                    title = "BREXA // COMPROMISO ATRASADO ⚠️"
                    text = "Tienes ${overdue.size} pago(s) vencido(s) pendiente(s): \"${first.title}\" (${decimalFormat.format(first.amount)} ${first.currencyCode})."
                } else {
                    title = "BREXA // SIN PAGOS PENDIENTES HOY ✓"
                    text = "Tus compromisos están al día. No tienes gastos fijos ni variables programados para hoy."
                }

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("EXTRA_SCREEN", "PENDING_EXPENSES")
                }
                val contentPendingIntent = PendingIntent.getActivity(
                    context,
                    DUE_PAYMENTS_REQUEST_CODE,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val largeIcon = try {
                    BitmapFactory.decodeResource(context.resources, R.drawable.logo_brexa)
                } catch (_: Exception) {
                    null
                }

                val builder = NotificationCompat.Builder(context, DUE_PAYMENTS_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_brexa_notification)
                    .setColor(0xFFD4FF00.toInt()) // Brexa Neon Lime (#D4FF00)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(contentPendingIntent)
                    .setAutoCancel(true)

                if (largeIcon != null) {
                    builder.setLargeIcon(largeIcon)
                }

                NotificationManagerCompat.from(context).notify(DUE_PAYMENTS_NOTIFICATION_ID, builder.build())

                if (!isManualTest) {
                    prefs.edit().putString(KEY_LAST_NOTIFIED_DUE_PAYMENT_DATE, todayStr).apply()
                }
            } catch (_: Exception) { }
        }
    }
}
