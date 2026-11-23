from pathlib import Path
import sys
p=Path(sys.argv[1])/"uhabits-android/src/main/java/org/isoron/uhabits/activities/tasks/TasksActivity.kt"
s=p.read_text()
changes=[
("""                store.reconcile(now)
                render()
                TasksAlarms.schedule(this)
                TasksAlarms.scheduleNextDay(this)
                TasksReminders.schedule(this)""",
"""                store.reconcile(now)
                render()
                TasksAlarms.schedule(this@TasksActivity)
                TasksAlarms.scheduleNextDay(this@TasksActivity)
                TasksReminders.schedule(this@TasksActivity)"""),
("ScrollView(this).apply { fillViewport = true }","ScrollView(this).apply { setFillViewport(true) }"),
("GridLayout.LayoutParams().apply { width=width; height=dp(49f) }","GridLayout.LayoutParams().apply { this.width=width; height=dp(49f) }"),
("                width=width; height=dp(49f); setMargins(","                this.width=width; height=dp(49f); setMargins("),
]
for before,after in changes:
    count=s.count(before)
    if count!=1: raise RuntimeError(f'Expected 1 source match, got {count}: {before[:80]}')
    s=s.replace(before,after,1)
p.write_text(s)
print('Applied Kotlin compile fixes for TasksActivity.')
