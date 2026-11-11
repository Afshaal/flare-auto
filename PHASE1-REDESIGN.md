# Home and screensaver redesign — Phase 1

Local implementation build. Compilation only in this phase; interaction tests, regression testing and visual polish are pending Phases 2 and 3. Existing version, package, SDK/build configuration and signing setup retained. No GitHub or car installation changes.

## Implemented
- One complete scaled Home preview with top bar, grid and dock. Cancel/Undo/Save, appearance and Help share one toolbar. Clock/corner/dock/application controls update the draft preview; old Appearance & Dock activity is no longer used by this editor.
- A memory-backed preference draft uses existing keys; Save writes edited keys only. Draft settings/pages survive recreation. Custom wallpaper imports are staged until Save and participate in Undo; Cancel keeps the original image.
- Selected-only outlines and edge-midpoint resize handles; hold to move; swipes page; tap corners or dock to edit. Dock shortcuts reorder by drag/drop. Apps shortcut remains required.
- Visual categorized widget gallery with sample media/weather, clock variants, portrait/wide/compact players, independent projection controls, apps and weather/solar cards. Size options reflect current free space; placement highlights and preview precede explicit Place. A new-page option handles full pages.
- Balanced, Media, Minimal and Three-card presets preview before explicit use. Finite swipe-to-add page panels remain; page indicator opens default/delete/reorder/preset actions.
- Person/steering-wheel/custom avatar profile tiles with Add New name/avatar creation. Existing family/default settings preserved. Default Ask mode prompts once per cold app process or Android reboot, rather than on every Home resume. Last/Family choices remain available.
- Independent screensaver left/right slots: off/weather/solar/both, including existing offline weather fallback. Optional music banner follows media-session metadata changes without polling; no album artwork. It slides in/out at top right, stays five seconds, hides the slot beneath it, and restores it after leaving. General app banners use the same slide and slot-occupancy handling. Animation toggle retained.

## Phase 2 focus
- Preview versus actual Home proportions, theme/wallpaper changes, all gallery sizes and minimum bounds, replacement/placement/collision handling, edge resize versus swipes, dock reorder and required Apps behavior.
- Draft cancellation/Undo across image imports and activity recreation; imported-image decoding and profile switching; selector cold process versus deep sleep/full boot.
- Media variants and source behavior; music notification deduplication, listener access, app-notification queues, corner restoration during rapid changes/exit, animation toggle and offline weather.
- Current cached/live data is used where available; incoming media/cache content and optional weather permissions depend on Android data availability. No Civic behavior is claimed verified.
