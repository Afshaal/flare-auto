# Editor controls update — 2026-11-11

- Replaced the editor toolbar with circular side controls. Left: Back, Layouts, Main page, Reset and move-left. Right: Add widget, Appearance, Save and move-right. Icon controls have accessible labels.
- Removed Undo/Help and the unused Undo snapshots. Back discards the draft and returns to Settings when opened there.
- Enlarged the complete Home preview from approximately 672x378 to 1034x582 at 1280x720, retaining its aspect ratio and dock. Reserved the card-action row so selection does not resize the preview.
- Card actions have rounded backgrounds and active-theme primary text. Blank side-rail and preview-margin taps clear selection and its outline/actions without rebuilding the preview.
- Dock long press lifts the real shortcut with a constant vertical offset. Horizontal movement is constrained to the dock. Crossing slots animates neighboring shortcuts out of the way. Drop updates only the draft order, followed by a short settling animation; no native free-floating drag shadow or duplicate icon.
- Home appearance, preference keys, version, permissions, dependencies and signing setup unchanged.

Validation:
- Release/debug builds and all 10 existing JVM tests passed.
- Android 9 BlueStacks: larger aspect-preserving preview, circular control bounds, removal of Undo/Help, left/right blank-area deselection, rounded card actions visually inspected.
- Captured live dock drag with pointer moved upward outside dock: held view retained constant Y translation and enlarged scale; neighbor X translation changed before release; only one held shortcut view existed.
- Reorder persisted in the correct insertion order after Save. Reverse-direction drag remained a draft until saving. Page-delete confirmation, horizontal paging, main-page selection and page reordering still worked.
- Back discarded unsaved layout changes; opening from Settings and tapping Back returned to Settings.
- Original Home and driver preferences restored and compared after tests. Signed release installed in BlueStacks and editor opened over Settings.
- Previous APK remains byte-identical (SHA-256 C222E478BDFD60C30B2872C6DF3F9A4B22BA56D89031DE2E7E36457DE11E1ED4).
- No car install, physical head-unit gesture/performance test or GitHub change.

APK: ../FlareAuto-editor-controls-update.apk
Test evidence: ../flare-editor-controls-check/
