# Decisions log

Ambiguous choices made during the optimisation / release-preparation pass, with the conservative option taken.

| ID | Decision | Why |
|---|---|---|
| D-01 | Work in `flare-auto-profiles-local/`, not `flare-auto-kotlin-6677f69/`. | The brief names the older snapshot, but all maps / widgets / profiles / routing work lives in `flare-auto-profiles-local/`. Confirmed by the owner before any change. |
| D-02 | Device measurements use only `install -r`, `am start`, `dumpsys`, `top`, `logcat`. The drawer jank run from the baseline (which used `adb shell input`) is not repeated. | Ground rule 6 lists the allowed adb commands; `input` and `push` are not on it. The exact commands are given in the report for the owner to run. |
| D-03 | Baseline device numbers are the ones recorded by the previous session at 21:31 from `baseline-release.apk`, built from this exact commit (`79e345e`, 21:23) and measured with the same script. | Avoids re-installing an identical build; the commit and script match. |
