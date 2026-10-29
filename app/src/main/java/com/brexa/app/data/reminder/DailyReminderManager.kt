package com.brexa.app.data.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.brexa.app.MainActivity
import com.brexa.app.R
import java.util.Calendar

object DailyReminderManager {

    const val CHANNEL_ID = "brexa_daily_reminder"
    const val NOTIFICATION_ID = 1001
    const val REQUEST_CODE = 2001
    const val ACTION_DAILY_REMINDER = "com.brexa.app.ACTION_DAILY_REMINDER"

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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recordatorio Diario de Gastos",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Aviso diario para registrar gastos y mantener tus cuentas actualizadas"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brexa_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) { }
    }
}
