package io.github.dai2010.focuseed.android;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import io.github.dai2010.focuseed.core.FocusPhase;
import io.github.dai2010.focuseed.core.FocusSession;
import io.github.dai2010.focuseed.core.FocusSettings;
import io.github.dai2010.focuseed.core.FocusSnapshot;

public final class FocusGuardService extends Service {
    public static final String REASON_BREAK = "break";
    public static final String REASON_PHONE = "phone";
    public static final String REASON_SETTINGS = "settings";
    public static final String REASON_TEMPORARY = "temporary";
    public static final String REASON_UPDATE = "update";

    private static final String ACTION_FORCE_FOCUS = "io.github.dai2010.focuseed.FORCE_FOCUS";
    private static final String ACTION_REFRESH_GUARD = "io.github.dai2010.focuseed.REFRESH_GUARD";
    private static final String PREFS = "focuseed_session";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_STARTED_AT = "started_at";
    private static final String KEY_WORK = "work_minutes";
    private static final String KEY_BREAK = "break_minutes";
    private static final String KEY_ROUNDS = "rounds";
    private static final String KEY_GUARD_ACTIVITY_VISIBLE = "guard_activity_visible";
    private static final String KEY_GUARD_BACKGROUND_ALLOWED_UNTIL = "guard_background_allowed_until";
    private static final String KEY_GUARD_BACKGROUND_REASON = "guard_background_reason";
    private static final String KEY_GUARD_LAST_RETURN_PROMPT = "guard_last_return_prompt";
    private static final int FOREGROUND_NOTIFICATION_ID = 301;
    private static final int RETURN_NOTIFICATION_ID = 302;
    private static final int FOCUS_REQUEST_CODE = 1301;
    private static final int ALARM_REQUEST_CODE = 1302;
    private static final long GUARD_TICK_MILLIS = 1_500L;
    private static final long RETURN_PROMPT_THROTTLE_MILLIS = 4_000L;
    private static final long PHONE_FALLBACK_ALLOW_MILLIS = 30 * 60_000L;
    private static final long EXTERNAL_ALLOW_MILLIS = 10 * 60_000L;
    private static final String CHANNEL_ID = "focus_guard";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FocusSession session = new FocusSession(new FocusSettings(25, 5, 4));
    private boolean phoneListenerRegistered;

    private final Runnable guardTick = new Runnable() {
        @Override
        public void run() {
            evaluateAndSchedule();
        }
    };

    private final PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            if (state == TelephonyManager.CALL_STATE_IDLE) {
                clearBackgroundAllowance(FocusGuardService.this);
                handler.post(FocusGuardService.this::evaluateAndSchedule);
            }
        }
    };

    public static void startIfSessionRunning(Context context) {
        if (prefs(context).getBoolean(KEY_RUNNING, false)) {
            start(context);
        }
    }

    public static void start(Context context) {
        if (!prefs(context).getBoolean(KEY_RUNNING, false)) {
            return;
        }
        Intent intent = new Intent(context, FocusGuardService.class);
        intent.setAction(ACTION_REFRESH_GUARD);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException ignored) {
        }
    }

    public static void stop(Context context) {
        cancelFocusAlarm(context);
        cancelNotifications(context);
        clearGuardState(context);
        try {
            context.stopService(new Intent(context, FocusGuardService.class));
        } catch (RuntimeException ignored) {
        }
    }

    public static void markActivityVisible(Context context, boolean visible) {
        prefs(context).edit().putBoolean(KEY_GUARD_ACTIVITY_VISIBLE, visible).apply();
    }

    public static void clearBackgroundAllowance(Context context) {
        prefs(context).edit()
            .remove(KEY_GUARD_BACKGROUND_ALLOWED_UNTIL)
            .remove(KEY_GUARD_BACKGROUND_REASON)
            .apply();
    }

    public static void allowBackgroundUntil(Context context, long allowedUntilMillis, String reason) {
        prefs(context).edit()
            .putBoolean(KEY_GUARD_ACTIVITY_VISIBLE, false)
            .putLong(KEY_GUARD_BACKGROUND_ALLOWED_UNTIL, allowedUntilMillis)
            .putString(KEY_GUARD_BACKGROUND_REASON, reason)
            .apply();
        if (!(context instanceof FocusGuardService)) {
            start(context);
        }
        scheduleFocusAlarm(context, allowedUntilMillis);
    }

    public static void allowExternal(Context context, String reason) {
        allowBackgroundUntil(context, System.currentTimeMillis() + EXTERNAL_ALLOW_MILLIS, reason);
    }

    public static void allowPhone(Context context) {
        allowBackgroundUntil(context, System.currentTimeMillis() + PHONE_FALLBACK_ALLOW_MILLIS, REASON_PHONE);
    }

    public static void requestReturn(Context context, long returnAtMillis) {
        allowBackgroundUntil(context, returnAtMillis, REASON_TEMPORARY);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        registerPhoneListenerIfAllowed();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        evaluateAndSchedule();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        unregisterPhoneListener();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        markActivityVisible(this, false);
        evaluateAndSchedule();
        super.onTaskRemoved(rootIntent);
    }

    private void evaluateAndSchedule() {
        handler.removeCallbacks(guardTick);
        SharedPreferences sharedPreferences = prefs(this);
        FocusSnapshot snapshot = restoreSnapshot(sharedPreferences);
        if (snapshot.phase() == FocusPhase.IDLE || snapshot.phase() == FocusPhase.FINISHED) {
            sharedPreferences.edit().putBoolean(KEY_RUNNING, false).apply();
            stopForegroundCompat();
            stopSelf();
            return;
        }
        updateForegroundNotification(snapshot);
        if (snapshot.phase() == FocusPhase.BREAK) {
            long returnAt = System.currentTimeMillis() + Math.max(1_000L, snapshot.remainingMillis());
            if (prefs(this).getBoolean(KEY_GUARD_ACTIVITY_VISIBLE, false)) {
                scheduleFocusAlarm(this, returnAt);
            } else {
                allowBackgroundUntil(this, returnAt, REASON_BREAK);
            }
            handler.postDelayed(guardTick, Math.min(Math.max(1_000L, snapshot.remainingMillis()), 15_000L));
            return;
        }
        handleWorkingPhase(sharedPreferences, snapshot);
        handler.postDelayed(guardTick, GUARD_TICK_MILLIS);
    }

    private FocusSnapshot restoreSnapshot(SharedPreferences sharedPreferences) {
        if (!sharedPreferences.getBoolean(KEY_RUNNING, false)) {
            session.stop();
            return session.snapshot(System.currentTimeMillis());
        }
        int workMinutes = sharedPreferences.getInt(KEY_WORK, 25);
        int breakMinutes = sharedPreferences.getInt(KEY_BREAK, 5);
        int rounds = sharedPreferences.getInt(KEY_ROUNDS, 4);
        long startedAt = sharedPreferences.getLong(KEY_STARTED_AT, 0L);
        if (startedAt <= 0L) {
            session.stop();
            return session.snapshot(System.currentTimeMillis());
        }
        session.resume(new FocusSettings(workMinutes, breakMinutes, rounds), startedAt);
        return session.snapshot(System.currentTimeMillis());
    }

    private void handleWorkingPhase(SharedPreferences sharedPreferences, FocusSnapshot snapshot) {
        long now = System.currentTimeMillis();
        boolean activityVisible = sharedPreferences.getBoolean(KEY_GUARD_ACTIVITY_VISIBLE, false);
        String reason = sharedPreferences.getString(KEY_GUARD_BACKGROUND_REASON, "");
        long allowedUntil = sharedPreferences.getLong(KEY_GUARD_BACKGROUND_ALLOWED_UNTIL, 0L);
        if (REASON_PHONE.equals(reason) && phoneMayStillBeActive(now, allowedUntil)) {
            scheduleFocusAlarm(this, Math.max(now + 15_000L, allowedUntil));
            return;
        }
        if (!REASON_PHONE.equals(reason) && now < allowedUntil) {
            scheduleFocusAlarm(this, allowedUntil);
            return;
        }
        if (reason != null && !reason.isEmpty()) {
            clearBackgroundAllowance(this);
        }
        if (activityVisible) {
            cancelReturnNotification();
            cancelFocusAlarm(this);
            return;
        }
        promptFocusReturn(snapshot, now);
    }

    private boolean phoneMayStillBeActive(long now, long fallbackAllowedUntil) {
        if (!hasPhonePermission()) {
            return now < fallbackAllowedUntil;
        }
        TelephonyManager manager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (manager == null) {
            return now < fallbackAllowedUntil;
        }
        try {
            return manager.getCallState() != TelephonyManager.CALL_STATE_IDLE;
        } catch (SecurityException error) {
            return now < fallbackAllowedUntil;
        }
    }

    private void promptFocusReturn(FocusSnapshot snapshot, long now) {
        SharedPreferences sharedPreferences = prefs(this);
        long lastPrompt = sharedPreferences.getLong(KEY_GUARD_LAST_RETURN_PROMPT, 0L);
        if (now - lastPrompt < RETURN_PROMPT_THROTTLE_MILLIS) {
            return;
        }
        sharedPreferences.edit().putLong(KEY_GUARD_LAST_RETURN_PROMPT, now).apply();
        scheduleFocusAlarm(this, now + 1_000L);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(RETURN_NOTIFICATION_ID, buildNotification(snapshot, true));
        }
    }

    private void updateForegroundNotification(FocusSnapshot snapshot) {
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification(snapshot, false));
    }

    private Notification buildNotification(FocusSnapshot snapshot, boolean urgent) {
        PendingIntent focusPendingIntent = PendingIntent.getActivity(this, FOCUS_REQUEST_CODE, focusIntent(), pendingIntentFlags());
        String phase = snapshot.phase() == FocusPhase.WORKING ? "工作中" : "休息中";
        String text = urgent
            ? "工作时间仍在计时，请回到全屏专注。"
            : phase + "，剩余 " + snapshot.remainingText() + "，轮次 " + snapshot.roundText();
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_focuseed)
            .setContentTitle(urgent ? "FocuSeed 正在拉回专注" : "FocuSeed 正在守护专注")
            .setContentText(text)
            .setContentIntent(focusPendingIntent)
            .setOngoing(!urgent)
            .setOnlyAlertOnce(!urgent)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(urgent ? Notification.PRIORITY_MAX : Notification.PRIORITY_LOW);
        if (urgent) {
            builder.setFullScreenIntent(focusPendingIntent, true)
                .setAutoCancel(true)
                .setOngoing(false);
        }
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "FocuSeed 专注守护",
            NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("用于工作期全屏拉回与休息期唤醒。");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    private void registerPhoneListenerIfAllowed() {
        if (!hasPhonePermission()) {
            return;
        }
        TelephonyManager manager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            manager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            phoneListenerRegistered = true;
        } catch (SecurityException ignored) {
        }
    }

    private void unregisterPhoneListener() {
        if (!phoneListenerRegistered) {
            return;
        }
        TelephonyManager manager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (manager != null) {
            manager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        phoneListenerRegistered = false;
    }

    private boolean hasPhonePermission() {
        return Build.VERSION.SDK_INT < 23
            || checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    private void cancelReturnNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(RETURN_NOTIFICATION_ID);
        }
    }

    private static void cancelNotifications(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(FOREGROUND_NOTIFICATION_ID);
            manager.cancel(RETURN_NOTIFICATION_ID);
        }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    private static void clearGuardState(Context context) {
        prefs(context).edit()
            .remove(KEY_GUARD_ACTIVITY_VISIBLE)
            .remove(KEY_GUARD_BACKGROUND_ALLOWED_UNTIL)
            .remove(KEY_GUARD_BACKGROUND_REASON)
            .remove(KEY_GUARD_LAST_RETURN_PROMPT)
            .apply();
    }

    private static void scheduleFocusAlarm(Context context, long triggerAtMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            ALARM_REQUEST_CODE,
            focusIntent(context),
            pendingIntentFlags()
        );
        long safeTrigger = Math.max(System.currentTimeMillis() + 1_000L, triggerAtMillis);
        alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(safeTrigger, pendingIntent), pendingIntent);
    }

    private static void cancelFocusAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            ALARM_REQUEST_CODE,
            focusIntent(context),
            pendingIntentFlags()
        );
        alarmManager.cancel(pendingIntent);
    }

    private Intent focusIntent() {
        return focusIntent(this);
    }

    private static Intent focusIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(ACTION_FORCE_FOCUS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
