/*
 * Copyright (C) 2026 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy.ui.dialog

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenModeConfig
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.android.settingslib.R as SettingsLibR
import com.android.settingslib.notification.modes.EnableDndDialogMetricsLogger
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

/**
 * What the "Turn on Do Not Disturb" dialog of quick settings asks: the interruption level and how
 * long DND stays on (until turned off, for a number of minutes, or until the next alarm). The same
 * choices and behavior as SettingsLib's EnableDndDialogFactory, for the dialog drawn like the other
 * quick settings dialogs.
 */
class EnableDndDialogState(
    private val context: Context,
    private val metricsLogger: EnableDndDialogMetricsLogger,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)!!
    private val alarmManager = context.getSystemService(AlarmManager::class.java)!!
    private val user = ActivityManager.getCurrentUser()

    var zenMode by mutableIntStateOf(Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS)
        private set

    var selected by mutableIntStateOf(FOREVER)
        private set

    private var bucketIndex by mutableIntStateOf(DEFAULT_BUCKET_INDEX)

    /** Until the next alarm, when there is one in the coming week. */
    val nextAlarmCondition: Condition? = untilNextAlarm()

    val countdownCondition: Condition
        get() = ZenModeConfig.toTimeCondition(context, MINUTE_BUCKETS[bucketIndex], user)

    val canShorten: Boolean
        get() = bucketIndex > 0

    val canExtend: Boolean
        get() = bucketIndex < MINUTE_BUCKETS.size - 1

    fun selectZenMode(mode: Int) {
        zenMode = mode
    }

    fun select(option: Int) {
        if (selected != option) {
            selected = option
            metricsLogger.logOnConditionSelected()
        }
    }

    fun step(up: Boolean) {
        metricsLogger.logOnClickTimeButton(up)
        bucketIndex = (bucketIndex + if (up) 1 else -1).coerceIn(0, MINUTE_BUCKETS.size - 1)
        selected = COUNTDOWN
    }

    /** Why the next alarm won't be heard with this choice, or null. */
    fun alarmWarning(): String? {
        val allowAlarms =
            (notificationManager.notificationPolicy.priorityCategories and
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) != 0
        if (
            zenMode == Settings.Global.ZEN_MODE_ALARMS ||
                (allowAlarms && zenMode == Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS)
        ) {
            return null
        }
        val now = System.currentTimeMillis()
        val nextAlarm = nextAlarmTime()
        if (nextAlarm < now) return null
        val warning =
            when (selected) {
                FOREVER -> SettingsLibR.string.zen_alarm_warning_indef
                COUNTDOWN -> {
                    val until = ZenModeConfig.tryParseCountdownConditionId(countdownCondition.id)
                    if (until > now && nextAlarm < until) SettingsLibR.string.zen_alarm_warning
                    else return null
                }
                else -> return null
            }
        return context.getString(warning, alarmTime(nextAlarm, now))
    }

    /** Turns DND on with the chosen interruption level and condition. */
    fun turnOn() {
        val conditionId: Uri? =
            when (selected) {
                FOREVER -> {
                    metricsLogger.logOnEnableZenModeForever()
                    null
                }
                COUNTDOWN -> {
                    metricsLogger.logOnEnableZenModeUntilCountdown()
                    countdownCondition.id
                }
                else -> {
                    metricsLogger.logOnEnableZenModeUntilAlarm()
                    nextAlarmCondition?.id
                }
            }
        notificationManager.setZenMode(zenMode, conditionId, TAG, /* fromUser= */ true)
    }

    private fun nextAlarmTime(): Long = alarmManager.getNextAlarmClock(user)?.triggerTime ?: 0

    private fun untilNextAlarm(): Condition? {
        val nextAlarm = nextAlarmTime()
        if (nextAlarm <= 0) return null
        val weekRange = GregorianCalendar().apply { toMidnight() }.apply { add(Calendar.DATE, 6) }
        val alarmDay = GregorianCalendar().apply { timeInMillis = nextAlarm }.apply { toMidnight() }
        return if (weekRange >= alarmDay) {
            ZenModeConfig.toNextAlarmCondition(context, nextAlarm, user)
        } else {
            null
        }
    }

    private fun alarmTime(nextAlarm: Long, now: Long): String {
        val soon = nextAlarm - now < 24 * 60 * 60 * 1000
        val is24 = DateFormat.is24HourFormat(context, user)
        val skeleton = if (soon) (if (is24) "Hm" else "hma") else (if (is24) "EEEHm" else "EEEhma")
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
        return context.getString(
            if (soon) SettingsLibR.string.alarm_template
            else SettingsLibR.string.alarm_template_far,
            DateFormat.format(pattern, nextAlarm),
        )
    }

    private fun Calendar.toMidnight() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    companion object {
        private const val TAG = "EnableDndDialog"
        const val FOREVER = 0
        const val COUNTDOWN = 1
        const val UNTIL_ALARM = 2
        private val MINUTE_BUCKETS = ZenModeConfig.MINUTE_BUCKETS
        private val DEFAULT_BUCKET_INDEX = MINUTE_BUCKETS.indexOf(60).coerceAtLeast(0)
    }
}
