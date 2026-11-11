# Only with -Pflare.minifyDebug=true: the debug check harnesses call two private UpdateChecker methods by
# reflection ("verify", "requestInstall"); keep them so the harnesses still run against the shrunk app.
-keepclassmembers class app.flare.auto.UpdateChecker {
    private void verify(java.io.File);
    private void requestInstall();
}
