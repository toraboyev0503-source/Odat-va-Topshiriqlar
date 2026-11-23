/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.isoron.uhabits.activities.tasks

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.isoron.uhabits.R

/** Single branded 24-hour clock for day start/end, blocks and task reminders. */
internal object TasksPickers {
    fun clock(
        host: AppCompatActivity, title: String, hour: Int, minute: Int,
        key: String, onSelected: (Int, Int) -> Unit
    ) {
        if (host.isFinishing || host.isDestroyed || host.supportFragmentManager.isStateSaved) return
        val tag = "blocivo_clock_$key"
        if (host.supportFragmentManager.findFragmentByTag(tag) != null) return
        val dialog = MaterialTimePicker.Builder()
            .setTheme(R.style.ThemeOverlay_Blocivo_TimePicker)
            .setTitleText(title)
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .setHour(hour.coerceIn(0,23))
            .setMinute(minute.coerceIn(0,59))
            .setPositiveButtonText(TaskLanguage.text(host,"Saqlash"))
            .setNegativeButtonText(TaskLanguage.text(host,"Bekor qilish"))
            .build()
        dialog.addOnPositiveButtonClickListener { onSelected(dialog.hour, dialog.minute) }
        dialog.show(host.supportFragmentManager,tag)
    }
}
