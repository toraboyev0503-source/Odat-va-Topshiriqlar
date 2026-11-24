#!/usr/bin/env python3
"""Blocivo 20311: retain horizontal block strip View instance on block selection.

Run: python Blocivo_20311_stable_strip_fix.py <root-of-20310-source>
Requires: 20310 TasksActivity.kt with predraw scroll-fix already applied.
"""
from pathlib import Path
import sys
base=Path(sys.argv[1]); activity=base/'uhabits-android/src/main/java/org/isoron/uhabits/activities/tasks/TasksActivity.kt'; gradle=base/'uhabits-android/build.gradle.kts'
s=activity.read_text(encoding='utf-8')
def replace(old,new,expected=1):
 global s
 n=s.count(old)
 if n!=expected:raise ValueError(f'Anchor mismatch {n} != {expected}: {old[:120]}')
 s=s.replace(old,new)
replace('''    private var blockStripView: HorizontalScrollView? = null
''','''    private var blockStripView: HorizontalScrollView? = null
    // Preserve the actual HorizontalScrollView and its gesture/scroll position on a
    // block-card tap. Rebuilding it and then restoring scrollX causes visible jumps.
    private var reuseBlockStripOnNextRender = false
    private var detachedReusableStrip: HorizontalScrollView? = null
''')
replace('''        blockStripView?.let { oldStrip ->
            if (oldStrip.isAttachedToWindow && oldStrip.width > 0) {
                blockStripScrollX = oldStrip.scrollX
            }
            oldStrip.setOnScrollChangeListener(null)
        }
        blockStripView = null
        root.removeAllViews(); countdown=null; countdownCaption=null; countdownProgress=null
''','''        // A block selection changes only the large card, not the little block strip.
        // Keep the SAME View rather than recreating all mini cards and scrolling back.
        val shouldReuse = reuseBlockStripOnNextRender && screen == Screen.MAIN
        reuseBlockStripOnNextRender = false
        detachedReusableStrip = null
        blockStripView?.let { oldStrip ->
            if (oldStrip.width > 0) blockStripScrollX = oldStrip.scrollX
            if (shouldReuse && oldStrip.parent is android.view.ViewGroup) {
                (oldStrip.parent as android.view.ViewGroup).removeView(oldStrip)
                detachedReusableStrip = oldStrip
            } else {
                oldStrip.setOnScrollChangeListener(null)
            }
        }
        blockStripView = null
        root.removeAllViews(); countdown=null; countdownCaption=null; countdownProgress=null
''')
replace('''    private fun blocksStrip(blocks:List<TaskBlock>, focus:Int): View = HorizontalScrollView(this).apply strip@ {
''','''    private fun blocksStrip(blocks:List<TaskBlock>, focus:Int): View {
        // Reattach the unmodified strip after rebuilding the large selected-block card.
        // This retains touch-scrolling, layout, and scrollX with no reset or flash.
        detachedReusableStrip?.let { sameStrip ->
            detachedReusableStrip = null
            blockStripView = sameStrip
            return sameStrip
        }
        return HorizontalScrollView(this).apply strip@ {
''')
replace('''                setOnClickListener {
                    blockStripScrollX = this@strip.scrollX
                    selectedBlock=index
                    render()
                }
''','''                setOnClickListener {
                    // Keep this exact HorizontalScrollView instance across selection.
                    if (selectedBlock != index) {
                        blockStripScrollX = this@strip.scrollX
                        reuseBlockStripOnNextRender = true
                        selectedBlock = index
                        render()
                    }
                }
''')
replace('''        })
    }

    private fun openCalendar''','''        })
        }
    }

    private fun openCalendar''')
# When the main screen is rebuilt because data actually changed (e.g. tasks),
# the strip should still be regenerated with up-to-date item counts.
# Preserve reattach only for direct small-card click.
activity.write_text(s,encoding='utf-8')
g=gradle.read_text(encoding='utf-8')
assert g.count('versionCode = 20310')==1
n=g.replace('versionCode = 20310','versionCode = 20311')
n=n.replace('-blocivo-20310','-blocivo-20311')
gradle.write_text(n,encoding='utf-8')
assert 'reuseBlockStripOnNextRender = true' in s
assert 'detachedReusableStrip = sameStrip' not in s
assert s.count('detachedReusableStrip = null')==2
assert 'SQLiteOpenHelper(context, "odatlar_tasks.db", null, 6)' in (base/'uhabits-android/src/main/java/org/isoron/uhabits/activities/tasks/TaskEngine.kt').read_text(encoding='utf-8')
print('PASS: 20311 fix retains exact mini-card strip on selection; task DB v6 preserved')