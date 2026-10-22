package io.github.cn_cizhanyue.lyricon.universal.provider.lyricon;

import android.content.Context;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.util.Log;

import io.github.cn_cizhanyue.lyricon.universal.provider.BuildConfig;
import android.os.SystemClock;

import io.github.proify.lyricon.lyric.model.Song;
import io.github.proify.lyricon.provider.ConnectionListener;
import io.github.proify.lyricon.provider.LyriconFactory;
import io.github.proify.lyricon.provider.LyriconProvider;
import io.github.proify.lyricon.provider.RemotePlayer;








final class LyriconPublisher {

    private static final String TAG = "LyriconUniversal";
    private static final String CENTRAL_PACKAGE = "com.android.systemui";
    private static final String PROCESS_NAME = "system_server";
    private static final long WATCHDOG_INTERVAL_MS = 4000L;

    private final Handler worker;
    private final String playerPackage;

    private LyriconProvider provider;
    private boolean connected;
    private boolean destroyed;
    private Song pendingSong;
    private String pendingSongKey;
    private String lastPushedSongKey;
    private PlaybackState pendingState;
    private boolean pendingTranslation;
    private String lastStateKey;
    private long lastStateUpdateTime;
    private PlaybackState lastPushedState;
    private boolean watchdogScheduled;
    private boolean manualPosition;
    private boolean positionScheduled;

    private final ConnectionListener connectionListener = new ConnectionListener() {
        @Override
        public void onConnected(LyriconProvider connectedProvider) {
            worker.post(() -> handleConnected(connectedProvider));
        }

        @Override
        public void onReconnected(LyriconProvider connectedProvider) {
            worker.post(() -> handleConnected(connectedProvider));
        }

        @Override
        public void onDisconnected(LyriconProvider disconnectedProvider) {
            worker.post(() -> {
                if (disconnectedProvider != provider) {
                    return;
                }
                connected = false;
                Log.w(TAG, "lyricon central disconnected for " + playerPackage);
            });
        }

        @Override
        public void onConnectTimeout(LyriconProvider timedOutProvider) {
            worker.post(() -> {
                if (timedOutProvider != provider) {
                    return;
                }
                connected = false;
                Log.w(TAG, "lyricon central registration timed out for " + playerPackage);
            });
        }
    };

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            watchdogScheduled = false;
            if (provider == null || destroyed) {
                return;
            }
            if (!connected) {
                ensureRegistered();
            }
            scheduleWatchdog();
        }
    };

    LyriconPublisher(Handler worker, String playerPackage) {
        this.worker = worker;
        this.playerPackage = playerPackage;
    }

    void submit(String songKey, Song song, PlaybackState state, boolean translation) {
        worker.post(() -> submitNow(songKey, song, state, translation));
    }

    void updatePlayback(PlaybackState state) {
        worker.post(() -> {
            if (!usableState(state, false)) {
                Log.i(TAG, "ignoring stale state " + playerPackage
                        + " pos=" + state.getPosition()
                        + " t=" + state.getLastPositionUpdateTime()
                        + " st=" + state.getState());
                return;
            }
            pendingState = state;
            lastStateUpdateTime = Math.max(lastStateUpdateTime, state.getLastPositionUpdateTime());
            if (connected) {
                pushState();
            }
        });
    }

    void clear() {
        worker.post(this::destroyNow);
    }

    private void submitNow(String songKey, Song song, PlaybackState state, boolean translation) {
        if (destroyed || song == null) {
            return;
        }
        if (provider == null) {
            provider = createProvider();
            if (provider == null) {
                return;
            }
            scheduleWatchdog();
        }
        boolean songChanged = songKey == null || !songKey.equals(pendingSongKey);
        pendingSong = song;
        pendingSongKey = songKey;
        pendingTranslation = translation;
        if (songChanged) {
            lastPushedState = null;
        }
        if (usableState(state, songChanged)) {
            pendingState = state;
            lastStateUpdateTime = Math.max(lastStateUpdateTime, state.getLastPositionUpdateTime());
        } else if (songChanged && !usableState(pendingState, true)) {
            
            pendingState = null;
        }
        if (connected) {
            pushAll(songChanged);
        } else {
            ensureRegistered();
        }
    }

    private LyriconProvider createProvider() {
        Context context = SystemServerContext.get();
        if (context == null) {
            Log.w(TAG, "system_server context is not ready yet");
            return null;
        }
        try {
            LyriconProvider created = LyriconFactory.INSTANCE.createProvider(
                    context,
                    BuildConfig.APPLICATION_ID,
                    playerPackage,
                    null,
                    null,
                    PROCESS_NAME,
                    null,
                    CENTRAL_PACKAGE);
            created.setAutoSync(false);
            created.getService().addConnectionListener(connectionListener);
            Log.i(TAG, "publishing to lyricon as provider for " + playerPackage);
            created.register();
            return created;
        } catch (Throwable t) {
            Log.e(TAG, "unable to create lyricon provider for " + playerPackage, t);
            return null;
        }
    }

    private void ensureRegistered() {
        if (provider == null || connected || destroyed) {
            return;
        }
        try {
            provider.register();
        } catch (Throwable t) {
            Log.e(TAG, "lyricon provider register failed", t);
        }
    }

    private void handleConnected(LyriconProvider connectedProvider) {
        if (connectedProvider != provider || destroyed) {
            return;
        }
        connected = true;
        lastStateKey = null;
        lastPushedSongKey = null;
        Log.i(TAG, "connected to lyricon central (" + playerPackage + ")");
        pushAll(true);
    }

    private void pushAll(boolean songChanged) {
        if (provider == null) {
            return;
        }
        RemotePlayer player = provider.getPlayer();
        if (player == null) {
            return;
        }
        try {
            if (pendingSong != null && (songChanged || lastPushedSongKey == null)) {
                if (player.setSong(pendingSong)) {
                    Log.i(TAG, "setSong " + playerPackage + " key=" + pendingSongKey
                            + " lyrics=" + (pendingSong.getLyrics() == null ? "null" : pendingSong.getLyrics().size()));
                    lastPushedSongKey = pendingSongKey;
                } else {
                    Log.w(TAG, "lyricon rejected the song payload (" + playerPackage + ")");
                }
            }
            player.setDisplayTranslation(pendingTranslation);
            player.setDisplayRoma(false);
        } catch (Throwable t) {
            Log.e(TAG, "pushing song to lyricon failed", t);
        }
        pushState();
    }

    private void pushState() {
        if (provider == null || pendingState == null) {
            return;
        }
        String key = stateKey(pendingState);
        if (key.equals(lastStateKey)) {
            return;
        }
        if (lastPushedState != null && pendingSongKey != null
                && pendingSongKey.equals(lastPushedSongKey)) {
            float advance = lastPushedState.getState() == PlaybackState.STATE_PLAYING
                    ? lastPushedState.getPlaybackSpeed() : 0f;
            long expected = lastPushedState.getPosition()
                    + (long) ((SystemClock.elapsedRealtime() - lastPushedState.getLastPositionUpdateTime())
                    * Math.max(0f, advance));
            long drift = pendingState.getPosition() - expected;
            boolean sameState = pendingState.getState() == lastPushedState.getState();
            boolean sameSpeed = Math.abs(pendingState.getPlaybackSpeed() - lastPushedState.getPlaybackSpeed()) < 0.01f;
            if (sameState && sameSpeed && Math.abs(drift) < 800L) {
                
                
                Log.i(TAG, "skip clock update drift=" + drift);
                pendingState = lastPushedState;
                return;
            }
        }
        RemotePlayer player = provider.getPlayer();
        if (player == null) {
            return;
        }
        Log.i(TAG, "state " + playerPackage
                + " pos=" + pendingState.getPosition()
                + " t=" + pendingState.getLastPositionUpdateTime()
                + " speed=" + pendingState.getPlaybackSpeed()
                + " st=" + pendingState.getState());
        boolean accepted = false;
        try {
            accepted = player.setPlaybackState(pendingState);
        } catch (Throwable t) {
            Log.e(TAG, "setPlaybackState(PlaybackState) failed", t);
        }
        if (!accepted) {
            
            
            manualPosition = true;
            try {
                player.setPlaybackState(pendingState.getState() == PlaybackState.STATE_PLAYING);
                player.setPosition(Math.max(0L, pendingState.getPosition()));
            } catch (Throwable t) {
                Log.e(TAG, "playback fallback failed", t);
            }
            schedulePositionTick();
        } else {
            manualPosition = false;
        }
        lastStateKey = key;
        lastPushedState = pendingState;
    }

    private final Runnable positionTick = new Runnable() {
        @Override
        public void run() {
            positionScheduled = false;
            if (!manualPosition || destroyed || provider == null || pendingState == null) {
                return;
            }
            RemotePlayer player = provider.getPlayer();
            if (player == null) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            long base = Math.max(0L, pendingState.getPosition());
            long delta = Math.max(0L, now - pendingState.getLastPositionUpdateTime());
            float speed = pendingState.getPlaybackSpeed();
            long position = base + (long) (delta * Math.max(0f, speed));
            try {
                player.setPosition(Math.max(0L, position));
            } catch (Throwable t) {
                return;
            }
            if (pendingState.getState() == PlaybackState.STATE_PLAYING) {
                schedulePositionTick();
            }
        }
    };

    private void schedulePositionTick() {
        if (positionScheduled || destroyed) {
            return;
        }
        positionScheduled = true;
        worker.postDelayed(positionTick, 200L);
    }

    



    private boolean usableState(PlaybackState state, boolean freshRequired) {
        if (state == null) {
            return false;
        }
        long updateTime = state.getLastPositionUpdateTime();
        if (updateTime <= 0L) {
            return false;
        }
        long age = SystemClock.elapsedRealtime() - updateTime;
        if (state.getState() == PlaybackState.STATE_PLAYING) {
            if (age > 5000L || age < -1500L) {
                return false;
            }
        } else if (freshRequired) {
            if (age > 10000L || age < -1500L) {
                return false;
            }
        }
        if (!freshRequired && updateTime < lastStateUpdateTime) {
            return false;
        }
        return true;
    }

    private void destroyNow() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        LyriconProvider old = provider;
        provider = null;
        connected = false;
        pendingSong = null;
        pendingSongKey = null;
        pendingState = null;
        pendingTranslation = false;
        lastStateKey = null;
        lastPushedSongKey = null;
        manualPosition = false;
        if (positionScheduled) {
            worker.removeCallbacks(positionTick);
            positionScheduled = false;
        }
        if (watchdogScheduled) {
            worker.removeCallbacks(watchdog);
            watchdogScheduled = false;
        }
        if (old != null) {
            try {
                old.destroy();
            } catch (Throwable t) {
                Log.e(TAG, "destroying lyricon provider failed", t);
            }
            Log.i(TAG, "lyricon provider released for " + playerPackage);
        }
    }

    private void scheduleWatchdog() {
        if (provider == null || watchdogScheduled || destroyed) {
            return;
        }
        watchdogScheduled = true;
        worker.postDelayed(watchdog, WATCHDOG_INTERVAL_MS);
    }

    private static String stateKey(PlaybackState state) {
        return state.getState() + ":" + state.getPosition() + ":"
                + state.getPlaybackSpeed() + ":" + state.getLastPositionUpdateTime();
    }
}
