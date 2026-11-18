package com.bloqueo.mobile;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Narrow-purpose, opt-in accessibility service. During an active focus session,
 * it reads only the foreground application's package name and sends the device
 * to Home when that package was explicitly selected by the user.
 * It NEVER reads view trees, text, messages, keystrokes or screen content.
 */
public final class BlockService extends AccessibilityService {
    private static final Set<String> MESSAGING_APPS = new HashSet<>(Arrays.asList(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
        "org.telegram.messenger.web", "com.discord", "org.thoughtcrime.securesms",
        "com.facebook.orca", "jp.naver.line.android"
    ));
    private long lastHomeAt = 0;
    private String lastBlockedPackage = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !Prefs.active(this)) return;
        final int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        CharSequence cs = event.getPackageName();
        if (cs == null) return;
        String packageName = cs.toString();
        if (packageName.equals(getPackageName()) || packageName.equals("com.android.systemui")) return;
        Set<String> blocked = Prefs.blocked(this);
        if (!blocked.contains(packageName) ||
            (Prefs.chatAllowed(this) && MESSAGING_APPS.contains(packageName))) {
            lastBlockedPackage = "";
            return;
        }
        long now = System.currentTimeMillis();
        if (packageName.equals(lastBlockedPackage) && now - lastHomeAt < 1100) return;
        lastBlockedPackage = packageName;
        lastHomeAt = now;
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    @Override public void onInterrupt() { }
}
