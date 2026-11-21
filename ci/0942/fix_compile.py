from pathlib import Path
import os

root = Path(os.environ["PROJECT_DIR"])
opt_in = "@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)\n"
items = {
    "app/src/main/java/com/memora/app/ui/components/FullCanvasNoteChrome.kt":
        "@Composable\nfun FloatingNoteChrome(",
    "app/src/main/java/com/memora/app/ui/components/NoteStatusBarEffect.kt":
        "@Composable\nfun stableNoteStatusInset()",
}
for rel, marker in items.items():
    path = root / rel
    source = path.read_text(encoding="utf-8")
    if opt_in + marker in source:
        continue
    if source.count(marker) != 1:
        raise RuntimeError(f"Expected exactly one function to annotate: {rel}")
    path.write_text(source.replace(marker, opt_in + marker), encoding="utf-8")
    print("Applied ExperimentalLayoutApi opt-in:", rel)
