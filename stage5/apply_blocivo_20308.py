#!/usr/bin/env python3
"""Apply Blocivo 20308 brand and 24h time picker patch on the Phase5+20307 source."""
from pathlib import Path
import sys

root=Path(sys.argv[1])
pkg=root/"uhabits-android/src/main/java/org/isoron/uhabits/activities/tasks"
src=pkg/"TasksActivity.kt"
s=src.read_text()

def replace(old,new,amount=1):
    global s
    found=s.count(old)
    if found!=amount: raise RuntimeError(f"Unexpected Activities patch anchor {found} != {amount}: {old[:100]!r}")
    s=s.replace(old,new)

replace('import com.google.android.material.timepicker.MaterialTimePicker\n','')
replace('import com.google.android.material.timepicker.TimeFormat\n','')
replace('addView(label("Vazifalar", 20f, color = t.WHITE)',
        'addView(label("Blocivo", 20f, color = t.WHITE)')
replace('secondaryBar("Vazifalar sozlamalari"',
        'secondaryBar("Blocivo sozlamalari"')
replace('val picker=DatePickerDialog(this,{ _,year,month,day ->',
        'val picker=DatePickerDialog(this,R.style.BlocivoDatePickerTheme,{ _,year,month,day ->')
replace('DatePickerDialog(this, { _,year,month,day->',
        'DatePickerDialog(this, R.style.BlocivoDatePickerTheme, { _,year,month,day->')
replace('''            val picker=MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(chosenHour).setMinute(chosenMinute).setTitleText("Eslatma vaqti").build()
            picker.addOnPositiveButtonClickListener {
                chosenHour=picker.hour;chosenMinute=picker.minute
                timeLabel.text="%02d:%02d".format(chosenHour,chosenMinute)
            }
            picker.show(supportFragmentManager,"task_reminder_picker")
''','''            TasksPickers.clock(this@TasksActivity, "Eslatma vaqti",
                chosenHour, chosenMinute, "task_reminder_picker") { hour, minute ->
                chosenHour=hour;chosenMinute=minute
                timeLabel.text="%02d:%02d".format(chosenHour,chosenMinute)
            }
''')
replace('''        val picker = MaterialTimePicker.Builder()
            .setTitleText(tr(title))
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(if (value == 1440) 0 else value / 60)
            .setMinute(if (value == 1440) 0 else value % 60)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .build()
        picker.addOnPositiveButtonClickListener {
            onPick(if (allowEndOfDay && picker.hour == 0 && picker.minute == 0) 1440
                   else picker.hour * 60 + picker.minute)
        }
        picker.show(supportFragmentManager, "tasks-block-clock")
''','''        TasksPickers.clock(this, tr(title),
            if (value == 1440) 0 else value / 60,
            if (value == 1440) 0 else value % 60,
            "tasks-block-clock") { hour, minute ->
            onPick(if (allowEndOfDay && hour == 0 && minute == 0) 1440
                   else hour * 60 + minute)
        }
''')
src.write_text(s)

# The home icon/launcher label and launcher alias must agree in each locale.
for rel in (
    "uhabits-android/src/main/res/values/tasks_phase2.xml",
    "uhabits-android/src/main/res/values-en/tasks_phase2.xml",
    "uhabits-android/src/main/res/values-uz/strings.xml",
):
    p=root/rel
    text=p.read_text()
    import re
    updated,n=re.subn(
        r'(<string name="tasks_preview_app_name">)[^<]*(</string>)',
        r'\g<1>Blocivo\g<2>',text
    )
    assert n==1,rel
    p.write_text(updated)

gradle=root/"uhabits-android/build.gradle.kts"
g=gradle.read_text()
assert g.count("versionCode = 20307")==1
g=g.replace("versionCode = 20307","versionCode = 20308")
assert g.count('versionNameSuffix = "-tasks-phase5"')==1
assert g.count('versionNameSuffix = "-tasks-phase5-preview"')==1
g=g.replace('versionNameSuffix = "-tasks-phase5"','versionNameSuffix = "-blocivo-20308"')
g=g.replace('versionNameSuffix = "-tasks-phase5-preview"','versionNameSuffix = "-blocivo-20308-debug"')
gradle.write_text(g)

assert 'KonfettiView' not in src.read_text()
assert 'celebration()' not in src.read_text()
assert 'tasks_preview_app_name">Blocivo<' in (root/"uhabits-android/src/main/res/values/tasks_phase2.xml").read_text()
assert 'SQLiteOpenHelper(context, "odatlar_tasks.db", null, 6)' in (pkg/"TaskEngine.kt").read_text()
assert 'TasksPickers.clock(this' in src.read_text()
assert (pkg/"TasksPickers.kt").exists()
assert (root/"uhabits-android/src/main/res/values/tasks_picker_styles.xml").exists()
assert (root/"uhabits-android/src/main/res/color/tasks_clock_number_text.xml").exists()
print("PASS: Blocivo 20308 brand and styled shared time pickers; package/database preserved")
