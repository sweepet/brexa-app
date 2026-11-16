package com.brexa.app

import android.app.Application
import com.brexa.app.data.reminder.DailyReminderManager

class BrexaApplication : Application() {
    companion object {
        lateinit var instance: BrexaApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        DailyReminderManager.createNotificationChannel(this)
        DailyReminderManager.scheduleDailyReminder(this)
        DailyReminderManager.scheduleMorningDuePaymentsCheck(this)
    }
}
