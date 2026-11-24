#!/usr/bin/env python3
"""Blocivo 20310 patch: preserve horizontally scrolling block strip across re-render.

Apply after Blocivo 20309 overlay/compatibility patch. Does not change data schema.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1])
activity=root/'uhabits-android/src/main/java/org/isoron/uhabits/activities/tasks/TasksActivity.kt'
gradle=root/'uhabits-android/build.gradle.kts'
s=activity.read_text(encoding='utf-8')

def replace(old: str, new: str, count: int=1):
    global s
    actual=s.count(old)
    if actual!=count:raise RuntimeError(f'Unexpected anchor match: {actual} instead of {count}: {old[:85]!r}')
    s=s.replace(old,new)

replace('    private var blockStripScrollX = 0\n',
'''    // The strip is saved BEFORE a full screen re-render; a detached view must never
    // overwrite the last real user scroll position with zero.
    private var blockStripScrollX = 0
    private var blockStripView: HorizontalScrollView? = null
''')
replace('''        root.removeAllViews(); countdown=null; countdownCaption=null; countdownProgress=null
''',
'''        // Capture the current position before detaching the old strip. Android may
        // dispatch a final scroll-to-zero while detaching views; disable its listener.
        blockStripView?.let { oldStrip ->
            if (oldStrip.isAttachedToWindow && oldStrip.width > 0) {
                blockStripScrollX = oldStrip.scrollX
            }
            oldStrip.setOnScrollChangeListener(null)
        }
        blockStripView = null
        root.removeAllViews(); countdown=null; countdownCaption=null; countdownProgress=null
''')
replace('''        val desiredScrollX = blockStripScrollX
        isHorizontalScrollBarEnabled=false
        setOnScrollChangeListener { _, x, _, _, _ -> blockStripScrollX = x }
''',
'''        val desiredScrollX = blockStripScrollX.coerceAtLeast(0)
        isHorizontalScrollBarEnabled=false
        // Do not track this newly created view before its previous X is restored.
''')
replace('''        addView(items)
        post { scrollTo(desiredScrollX.coerceAtLeast(0), 0) }
''',
'''        addView(items)
        blockStripView = this
        // Unlike post { scrollTo(...) }, OnPreDraw restores the scroll position
        // before the first visible frame, eliminating the flash at x=0.
        val observer = viewTreeObserver
        observer.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (observer.isAlive) observer.removeOnPreDrawListener(this)
                val maxScrollX = (items.measuredWidth - measuredWidth).coerceAtLeast(0)
                scrollTo(desiredScrollX.coerceIn(0, maxScrollX), 0)
                // Only active strip scrolls update state, not old/detached views.
                setOnScrollChangeListener { _, x, _, _, _ ->
                    if (blockStripView === this@strip) blockStripScrollX = x
                }
                return true
            }
        })
''')
# Prevent 1Hz remove/add layout churn from animating or re-measuring the entire row.
replace('''    private fun progressFill(track: LinearLayout?, ratio: Float) {
        if (track == null) return
        track.removeAllViews()
        track.addView(View(this).apply { background = t.background(t.BLUE, 7f, context = this@TasksActivity) }, LinearLayout.LayoutParams(0,-1,ratio.coerceIn(0.001f,1f)))
        track.addView(View(this), LinearLayout.LayoutParams(0,-1,(1f-ratio).coerceAtLeast(.001f)))
    }
''',
'''    private fun progressFill(track: LinearLayout?, ratio: Float) {
        if (track == null) return
        val leftWeight = ratio.coerceIn(0.001f, 1f)
        val rightWeight = (1f-ratio).coerceAtLeast(.001f)
        if (track.childCount == 2) {
            val left = track.getChildAt(0)
            val right = track.getChildAt(1)
            val leftParams = left.layoutParams as? LinearLayout.LayoutParams
            val rightParams = right.layoutParams as? LinearLayout.LayoutParams
            if (leftParams != null && rightParams != null) {
                // Subpixel changes do not justify a full layout every second.
                if (kotlin.math.abs(leftParams.weight-leftWeight) < .001f &&
                    kotlin.math.abs(rightParams.weight-rightWeight) < .001f) return
                leftParams.weight = leftWeight
                rightParams.weight = rightWeight
                left.layoutParams = leftParams
                right.layoutParams = rightParams
                return
            }
        }
        track.removeAllViews()
        track.addView(View(this).apply { background = t.background(t.BLUE, 7f, context = this@TasksActivity) }, LinearLayout.LayoutParams(0,-1,leftWeight))
        track.addView(View(this), LinearLayout.LayoutParams(0,-1,rightWeight))
    }
''')
activity.write_text(s,encoding='utf-8')
g=gradle.read_text(encoding='utf-8')
if g.count('versionCode = 20309')!=1:raise RuntimeError('Expected code 20309')
g=g.replace('versionCode = 20309','versionCode = 20310')
# Prevent stale version names from shipping in this new APK.
g=g.replace('versionNameSuffix = "-tasks-20309"','versionNameSuffix = "-blocivo-20310"')
g=g.replace('versionNameSuffix = "-tasks-20309-dev"','versionNameSuffix = "-blocivo-20310-dev"')
gradle.write_text(g,encoding='utf-8')
assert 'post { scrollTo(desiredScrollX' not in s
assert 'observer.addOnPreDrawListener' in s
assert 'blockStripView = null' in s
assert 'SQLiteOpenHelper(context, "odatlar_tasks.db", null, 6)' in (root/'uhabits-android/src/main/java/org/isoron/uhabits/activities/tasks/TaskEngine.kt').read_text()
print('PASS: Blocivo 20310 stable horizontal strip, cheap 1Hz progress; existing DB remains v6')