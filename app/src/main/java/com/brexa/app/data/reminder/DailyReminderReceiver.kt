package com.brexa.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            DailyReminderManager.scheduleDailyReminder(context)
        } else if (action == DailyReminderManager.ACTION_DAILY_REMINDER || action == null) {
            if (DailyReminderManager.isEnabled(context)) {
                DailyReminderManager.showNotification(context)
                DailyReminderManager.scheduleDailyReminder(context)
            }
        }
    }
}
