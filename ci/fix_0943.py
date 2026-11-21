from pathlib import Path
import sys

root = Path(sys.argv[1])

def replace(path, before, after):
    p = root / path
    text = p.read_text(encoding="utf-8")
    if before not in text and "fun stableNoteStatusInset()" in before:
        before = before.replace("@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)\n", "")
    if before not in text:
        raise SystemExit(f"Expected expression missing: {path}: {before[:90]}")
    p.write_text(text.replace(before, after), encoding="utf-8")

components = "app/src/main/java/com/memora/app/ui/components/"
replace(
    components + "FullCanvasNoteChrome.kt",
    ".windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)",
    ".padding(top = stableNoteStatusInset())"
)
replace(
    "app/src/main/java/com/memora/app/ui/screens/ReaderScreen.kt",
    ".windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)",
    ".padding(top = stableNoteStatusInset())"
)
replace(
    components + "NoteStatusBarEffect.kt",
    "import androidx.core.view.WindowCompat",
    "import androidx.core.view.ViewCompat\nimport androidx.compose.ui.unit.Dp\nimport androidx.core.view.WindowCompat"
)
replace(
    components + "NoteStatusBarEffect.kt",
    """@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun stableNoteStatusInset() = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()""",
    """@Composable
fun stableNoteStatusInset(): Dp {
    val view = LocalView.current
    val insets = ViewCompat.getRootWindowInsets(view)
    val statusBarHeightPx =
        insets?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())?.top
            ?: run {
                val resourceId = view.resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resourceId != 0) view.resources.getDimensionPixelSize(resourceId) else 0
            }
    return with(LocalDensity.current) { statusBarHeightPx.toDp() }
}"""
)
print("0.9.43 compile fix: stable AndroidX status bar insets applied")
