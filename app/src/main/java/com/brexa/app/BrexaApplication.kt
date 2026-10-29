package com.brexa.app

import android.app.Application
import com.brexa.app.data.reminder.DailyReminderManager

class BrexaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DailyReminderManager.createNotificationChannel(this)
        DailyReminderManager.scheduleDailyReminder(this)
    }
}
