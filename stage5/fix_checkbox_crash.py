#!/usr/bin/env python3
"""Phase5 20307 crash hotfix. Apply to already overlaid Phase5 source tree."""
from pathlib import Path
import sys

root=Path(sys.argv[1] if len(sys.argv)>1 else '/mnt/data/crash_fix/source')
pkg=root/'uhabits-android/src/main/java/org/isoron/uhabits/activities/tasks'
activity=pkg/'TasksActivity.kt'
s=activity.read_text(encoding='utf-8')
# Remove Konfetti XML view *and* its asynchronous frame callbacks from the critical task completion route.
for line in [
'import nl.dionsegijn.konfetti.core.Party\n',
'import nl.dionsegijn.konfetti.core.Position\n',
'import nl.dionsegijn.konfetti.core.emitter.Emitter\n',
'import nl.dionsegijn.konfetti.xml.KonfettiView\n',
'import java.util.concurrent.TimeUnit\n',
'    private lateinit var particles: KonfettiView\n',
'        particles = KonfettiView(this).apply { isClickable = false; isFocusable = false }\n        overlay.addView(particles, FrameLayout.LayoutParams(-1, -1))\n',
]:
    assert s.count(line)==1,(line,s.count(line))
    s=s.replace(line,'')
old='''        if (status == "DONE") {
            try {
                celebration()
            } catch (e: Exception) {
                TaskDiagnostics.record(this, "status-celebration", e)
            }
        }
'''
assert s.count(old)==1
s=s.replace(old,'''        // Completion feedback must not dispatch a third-party animation: an
        // asynchronous Konfetti render crash is not caught by the button listener.
        // The saved task state and notice remain the source of truth.
''')
old='''    private fun celebration(){
        particles.start(Party(speed=0f,maxSpeed=16f,damping=.9f,spread=360,angle=0,
            colors=listOf(t.BLUE,t.TEAL,t.GREEN,Color.rgb(244,180,83)),
            position=Position.Absolute(particles.width/2f,particles.height/2f),
            emitter=Emitter(duration=26,TimeUnit.MILLISECONDS).max(28),timeToLive=0))
    }

'''
assert s.count(old)==1
s=s.replace(old,'')
# Install self-contained option to copy diagnostic info in case of repeat crashes.
anchor='''        panel.addView(t.action(this@TasksActivity,"Saqlash","check",true){saveConfiguration()},t.lp(this@TasksActivity,-1,dp(48f),0f,19f))'''
new='''        panel.addView(t.action(this@TasksActivity,"Xatolik jurnalini nusxalash","copy",false) {
            val info = listOf("tasks_fatal_crash.log", "tasks_diagnostics.log").joinToString("\\n") { filename ->
                val file = getFileStreamPath(filename)
                filename + ":\\n" + runCatching {
                    if (file.exists()) file.readText().takeLast(12000) else "Yozuv yo‘q"
                }.getOrDefault("Jurnalni o‘qib bo‘lmadi")
            }
            runCatching {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Vazifalar diagnostikasi", info))
                notice("Xatolik jurnali nusxalandi")
            }.onFailure { notice("Xatolik jurnalini nusxalab bo‘lmadi") }
        },t.lp(this@TasksActivity,-1,dp(45f),0f,14f))
        panel.addView(t.action(this@TasksActivity,"Saqlash","check",true){saveConfiguration()},t.lp(this@TasksActivity,-1,dp(48f),0f,19f))'''
assert s.count(anchor)==1
s=s.replace(anchor,new)
activity.write_text(s,encoding='utf-8')
app=pkg/'TasksOnlyApplication.kt'
s=app.read_text(encoding='utf-8')
old='''        super.onCreate()
        TaskLanguage.install(this)'''
new='''        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Store only error class and stack locations, never task titles or notes.
            runCatching {
                val trace = throwable.stackTrace.take(32).joinToString("\\n") { frame ->
                    "${frame.className}.${frame.methodName}:${frame.lineNumber}"
                }
                val line = "${System.currentTimeMillis()} | ${thread.name} | ${throwable.javaClass.name}\\n$trace\\n\\n"
                openFileOutput("tasks_fatal_crash.log", MODE_APPEND).use { it.write(line.toByteArray()) }
                val file = getFileStreamPath("tasks_fatal_crash.log")
                if (file.length() > 120_000L) {
                    openFileOutput("tasks_fatal_crash.log", MODE_PRIVATE).use {
                        it.write(file.readText().takeLast(60_000).toByteArray())
                    }
                }
            }
            if (previousHandler != null) previousHandler.uncaughtException(thread, throwable)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
        TaskLanguage.install(this)'''
assert s.count(old)==1
app.write_text(s.replace(old,new),encoding='utf-8')
gradle=root/'uhabits-android/build.gradle.kts'
g=gradle.read_text()
assert g.count('versionCode = 20306')==1
gradle.write_text(g.replace('versionCode = 20306','versionCode = 20307'),encoding='utf-8')
# Guard against accidentally re-introducing animation in future builds.
assert 'KonfettiView' not in activity.read_text()
assert 'celebration()' not in activity.read_text()
assert 'timeToLive=0' not in activity.read_text()
assert 'fun setStatus' in (pkg/'TaskEngine.kt').read_text()
assert 'SQLiteOpenHelper(context, "odatlar_tasks.db", null, 6)' in (pkg/'TaskEngine.kt').read_text()
print('PASS: checkbox crash hotfix applied; old tasks DB v6 preserved, version 20307')