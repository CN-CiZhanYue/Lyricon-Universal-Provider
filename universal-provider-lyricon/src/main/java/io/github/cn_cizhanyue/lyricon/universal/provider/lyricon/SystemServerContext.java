package io.github.cn_cizhanyue.lyricon.universal.provider.lyricon;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

final class SystemServerContext {

    private static volatile Context cached;

    private SystemServerContext() {
    }

    static Context get() {
        Context value = cached;
        if (value != null) {
            return value;
        }
        synchronized (SystemServerContext.class) {
            if (cached != null) {
                return cached;
            }
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Object activityThread = activityThreadClass
                        .getDeclaredMethod("currentActivityThread")
                        .invoke(null);
                Object systemContext = activityThreadClass
                        .getDeclaredMethod("getSystemContext")
                        .invoke(activityThread);
                if (systemContext instanceof Context) {
                    cached = new SessionContext((Context) systemContext);
                    return cached;
                }
            } catch (Throwable t) {
                Log.e("LyriconUniversal", "unable to resolve the system_server context", t);
            }
        }
        return null;
    }

    private static final class SessionContext extends ContextWrapper {

        SessionContext(Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }
    }
}
