# Home editor update — 2026-11-11

- Whole Home page is shown in a floating, aspect-preserving preview, including its header, cards, page dots and dock. Header and dock now share rendering with Home.
- Removed Page options. Visible controls set the main page, open layout presets, reset the current page (with confirmation), and move the current page earlier/later.
- Drag downward to reveal the trash icon from above. It turns red at the deletion threshold. Releasing past that threshold asks for confirmation; cancellation returns the preview. At least one page is retained.
- Quick page swipes remain separate from long-press widget movement and edge resizing. The selected widget retains its four edge handles.
- Tapping a dock shortcut opens app/action choices. Holding and dragging inserts the item in its new position. Required Apps shortcut is retained.
- All changes remain drafts until Save, with existing Undo/Cancel behavior. Reset affects only the current page, restoring full clock and media cards.

Validation:
- Release/debug builds and existing 10 JVM tests passed.
- BlueStacks Android 9: deletion prompt/cancel, below-threshold swipe, deletion/Undo/Save, both page-order directions, main-page persistence, reset confirmation, dock app/action menu, dock insertion reorder, selected-edge resizing without page deletion, and last-page protection passed.
- Original Home/driver preferences restored and compared semantically after isolated-profile tests.
- Signed release installed in BlueStacks and editor launch verified.
- No Civic installation, GitHub changes or physical head-unit gesture verification.

APK: ../FlareAuto-home-editor-update.apk
Previous APKs and signing key remain intact. Version remains 1.1.0 / code 3.
