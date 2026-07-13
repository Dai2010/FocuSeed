package io.github.dai2010.focuseed.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import io.github.dai2010.focuseed.core.FocusPhase;
import io.github.dai2010.focuseed.core.FocusSession;
import io.github.dai2010.focuseed.core.FocusSettings;
import io.github.dai2010.focuseed.core.FocusSnapshot;
import io.github.dai2010.focuseed.core.UpdateChecker;
import io.github.dai2010.focuseed.core.UpdateInfo;

public final class MainActivity extends Activity {
    private static final String ACTION_FORCE_FOCUS = "io.github.dai2010.focuseed.FORCE_FOCUS";
    private static final String PREFS = "focuseed_session";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_STARTED_AT = "started_at";
    private static final String KEY_WORK = "work_minutes";
    private static final String KEY_BREAK = "break_minutes";
    private static final String KEY_ROUNDS = "rounds";
    private static final String KEY_EXIT_CHANCES = "exit_chances";
    private static final int EXIT_CHANCES = 3;
    private static final long NO_DOWNLOAD = -1L;
    private static final int TEXT = Color.rgb(82, 65, 72);
    private static final int MUTED = Color.rgb(137, 118, 128);
    private static final int MINT = Color.rgb(112, 213, 176);
    private static final int PEACH = Color.rgb(255, 166, 148);
    private static final int PINK = Color.rgb(255, 214, 224);
    private static final int CARD = Color.argb(232, 255, 255, 255);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FocusSession session = new FocusSession(new FocusSettings(25, 5, 4));
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 500L);
        }
    };

    private Typeface cuteTypeface;
    private TextView phaseText;
    private TextView timerText;
    private TextView roundText;
    private TextView hintText;
    private TextView dialerText;
    private TextView policyText;
    private TextView chanceText;
    private TextView updateText;
    private Button updateDownloadButton;
    private Button updateAccelerateButton;
    private EditText workInput;
    private EditText breakInput;
    private EditText roundsInput;

    private int currentWorkMinutes = 25;
    private int currentBreakMinutes = 5;
    private int currentRounds = 4;
    private int exitChancesLeft = EXIT_CHANCES;
    private long sessionStartedAtMillis;
    private boolean leavingForPhone;
    private boolean leavingForSettings;
    private boolean leavingForUpdateInstall;
    private boolean plannedBackgroundExit;
    private UpdateInfo pendingUpdate;
    private long updateDownloadId = NO_DOWNLOAD;
    private boolean updateInstallPrompted;
    private boolean updateCheckStarted;

    private final Runnable updateProgressTick = new Runnable() {
        @Override
        public void run() {
            pollUpdateDownloadProgress();
        }
    };

    private final PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            if (state == TelephonyManager.CALL_STATE_IDLE) {
                applyFocusWindowMode();
                render();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cuteTypeface = Typeface.createFromAsset(getAssets(), "fonts/ZCOOLKuaiLe-Regular.ttf");
        restoreSession();
        setContentView(createLayout());
        if (ACTION_FORCE_FOCUS.equals(getIntent().getAction())) {
            applyFocusWindowMode();
        }
        registerCallStateListenerIfAllowed();
        updateDialerInfo();
        updatePolicyInfo();
        render();
        checkForUpdatesSilently();
        handler.post(tick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        restoreSession();
        applyFocusWindowMode();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        leavingForPhone = false;
        leavingForSettings = false;
        leavingForUpdateInstall = false;
        plannedBackgroundExit = false;
        restoreSession();
        if (session.snapshot(System.currentTimeMillis()).phase() == FocusPhase.WORKING) {
            cancelWorkAlarm();
            applyFocusWindowMode();
        }
        updateDialerInfo();
        updatePolicyInfo();
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handleUnexpectedBackgroundExit();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        requestExitDuringSession();
    }

    private View createLayout() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackground(gradient(Color.rgb(255, 246, 236), Color.rgb(232, 249, 239), 0));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(24), dp(22), dp(24));
        card.setBackground(rounded(CARD, 28));
        root.addView(card, fullWidth());

        TextView title = text("FocuSeed 软萌专注花园", 31, TEXT, false);
        TextView subtitle = text("种下一颗番茄种子，把注意力轻轻收回来", 15, MUTED, false);
        phaseText = text("准备发芽", 28, TEXT, false);
        timerText = text("00:00", 64, MINT, false);
        roundText = text("轮次 0 / 4", 19, TEXT, false);
        hintText = text("休息时可以退出；工作开始会自动回到全屏。", 14, MUTED, false);
        chanceText = text("临时退出机会：3 次", 14, PEACH, false);
        dialerText = text("正在检测拨号器…", 12, MUTED, false);
        policyText = text("勿扰权限未检测", 12, MUTED, false);
        updateText = text("更新：启动时会静默检查", 12, MUTED, false);

        LinearLayout timerCard = new LinearLayout(this);
        timerCard.setOrientation(LinearLayout.VERTICAL);
        timerCard.setGravity(Gravity.CENTER);
        timerCard.setPadding(dp(16), dp(18), dp(16), dp(18));
        timerCard.setBackground(rounded(Color.argb(214, 255, 248, 251), 24));

        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.HORIZONTAL);
        settings.setGravity(Gravity.CENTER);
        workInput = numberInput(String.valueOf(currentWorkMinutes));
        breakInput = numberInput(String.valueOf(currentBreakMinutes));
        roundsInput = numberInput(String.valueOf(currentRounds));
        settings.addView(labelWithInput("工作", workInput));
        settings.addView(labelWithInput("休息", breakInput));
        settings.addView(labelWithInput("轮数", roundsInput));

        Button start = button("开始种下");
        start.setOnClickListener(view -> startSession());
        Button exit = button("休息/临时退出");
        exit.setOnClickListener(view -> requestExitDuringSession());
        Button dial = button("打开系统拨号器");
        dial.setOnClickListener(view -> openDialer());
        Button dnd = button("设置勿扰/电话例外");
        dnd.setOnClickListener(view -> configureDoNotDisturb());
        updateDownloadButton = button("下载更新");
        updateDownloadButton.setEnabled(false);
        updateDownloadButton.setOnClickListener(view -> startUpdateDownload(false));
        updateAccelerateButton = button("切换加速下载");
        updateAccelerateButton.setEnabled(false);
        updateAccelerateButton.setOnClickListener(view -> startUpdateDownload(true));

        card.addView(title);
        card.addView(subtitle);
        card.addView(spacer(18));
        timerCard.addView(phaseText);
        timerCard.addView(timerText);
        timerCard.addView(roundText);
        card.addView(timerCard, fullWidth());
        card.addView(spacer(16));
        card.addView(settings);
        card.addView(spacer(14));
        card.addView(start, fullWidth());
        card.addView(spacer(8));
        card.addView(exit, fullWidth());
        card.addView(spacer(8));
        card.addView(dial, fullWidth());
        card.addView(spacer(8));
        card.addView(dnd, fullWidth());
        card.addView(spacer(8));
        card.addView(updateDownloadButton, fullWidth());
        card.addView(spacer(8));
        card.addView(updateAccelerateButton, fullWidth());
        card.addView(spacer(14));
        card.addView(hintText);
        card.addView(chanceText);
        card.addView(dialerText);
        card.addView(policyText);
        card.addView(updateText);
        return scrollView;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextColor(color);
        textView.setTextSize(sizeSp);
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(cuteTypeface, bold ? Typeface.BOLD : Typeface.NORMAL);
        textView.setIncludeFontPadding(true);
        textView.setPadding(0, dp(5), 0, dp(5));
        return textView;
    }

    private EditText numberInput(String value) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextColor(TEXT);
        input.setTextSize(18);
        input.setGravity(Gravity.CENTER);
        input.setTypeface(cuteTypeface);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSelectAllOnFocus(true);
        input.setEms(3);
        input.setBackground(rounded(Color.WHITE, 16));
        return input;
    }

    private LinearLayout labelWithInput(String label, EditText input) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(Gravity.CENTER);
        group.setPadding(dp(7), 0, dp(7), 0);
        TextView title = text(label, 13, MUTED, false);
        group.addView(title);
        group.addView(input);
        return group;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setTextSize(16);
        button.setTypeface(cuteTypeface);
        button.setBackground(rounded(PINK, 22));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        return button;
    }

    private View spacer(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private void startSession() {
        currentWorkMinutes = parsePositive(workInput, 25);
        currentBreakMinutes = parsePositive(breakInput, 5);
        currentRounds = parsePositive(roundsInput, 4);
        exitChancesLeft = EXIT_CHANCES;
        sessionStartedAtMillis = System.currentTimeMillis();
        FocusSettings settings = new FocusSettings(currentWorkMinutes, currentBreakMinutes, currentRounds);
        session.start(settings, sessionStartedAtMillis);
        saveSession(true);
        cancelWorkAlarm();
        applyFocusWindowMode();
        configureDoNotDisturbIfAllowed();
        render();
    }

    private int parsePositive(EditText input, int fallback) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            return Math.max(1, value);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private void render() {
        FocusSnapshot snapshot = session.snapshot(System.currentTimeMillis());
        FocusPhase phase = snapshot.phase();
        if (phase == FocusPhase.IDLE) {
            phaseText.setText("准备发芽");
            timerText.setText("00:00");
            roundText.setText("轮次 0 / " + currentRounds);
            hintText.setText("休息时可以退出；工作开始会自动回到全屏。");
            chanceText.setText("临时退出机会：" + exitChancesLeft + " 次");
            return;
        }
        if (phase == FocusPhase.FINISHED) {
            clearSession();
            cancelWorkAlarm();
            phaseText.setText("花开完成啦");
            timerText.setText("00:00");
            roundText.setText("轮次 " + snapshot.roundText());
            hintText.setText("今天也认真照顾了自己的注意力。辛苦啦！");
            chanceText.setText("临时退出机会：已重置");
            return;
        }
        if (phase == FocusPhase.WORKING) {
            applyFocusWindowMode();
            phaseText.setText("专注发芽中");
            hintText.setText("工作期会保持全屏。临时退出会消耗机会，计时不会暂停。");
        } else {
            phaseText.setText("软软休息中");
            hintText.setText("休息期间可以退出应用；下一段工作会自动回到全屏。");
        }
        timerText.setText(snapshot.remainingText());
        roundText.setText("轮次 " + snapshot.roundText());
        chanceText.setText("临时退出机会：" + exitChancesLeft + " 次");
    }

    private void requestExitDuringSession() {
        FocusSnapshot snapshot = session.snapshot(System.currentTimeMillis());
        FocusPhase phase = snapshot.phase();
        if (phase == FocusPhase.IDLE || phase == FocusPhase.FINISHED) {
            finish();
            return;
        }
        if (phase == FocusPhase.BREAK) {
            scheduleFocusAlarm(System.currentTimeMillis() + Math.max(1_000L, snapshot.remainingMillis()));
            Toast.makeText(this, "休息小窝开启，下一段工作会自动回到全屏。", Toast.LENGTH_LONG).show();
            plannedBackgroundExit = true;
            moveTaskToBack(true);
            return;
        }
        if (exitChancesLeft <= 0) {
            applyFocusWindowMode();
            Toast.makeText(this, "三次机会已经用完啦，先陪小种子完成这一轮吧。", Toast.LENGTH_LONG).show();
            return;
        }
        exitChancesLeft--;
        saveSession(true);
        scheduleFocusAlarm(System.currentTimeMillis() + 3_000L);
        Toast.makeText(this, "临时退出机会剩余 " + exitChancesLeft + " 次，马上会回到全屏。", Toast.LENGTH_LONG).show();
        plannedBackgroundExit = true;
        moveTaskToBack(true);
    }

    private void openDialer() {
        leavingForPhone = true;
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"));
        startActivity(intent);
    }

    private void updateDialerInfo() {
        StringBuilder builder = new StringBuilder();
        TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        String defaultDialer = telecomManager == null ? null : telecomManager.getDefaultDialerPackage();
        builder.append("默认拨号器：").append(defaultDialer == null ? "系统未返回" : defaultDialer);

        Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"));
        List<ResolveInfo> candidates = getPackageManager().queryIntentActivities(dialIntent, 0);
        builder.append(" · 可用候选：").append(candidates.size()).append(" 个");
        dialerText.setText(builder.toString());
    }

    private void checkForUpdatesSilently() {
        if (updateCheckStarted) {
            return;
        }
        updateCheckStarted = true;
        new Thread(() -> {
            try {
                UpdateInfo info = UpdateChecker.checkLatest(UpdateChecker.PLATFORM_ANDROID);
                handler.post(() -> onUpdateChecked(info));
            } catch (Exception ignored) {
                handler.post(() -> updateText.setText("更新：静默检查失败，可稍后重启应用再试"));
            }
        }, "FocuSeed-UpdateCheck").start();
    }

    private void onUpdateChecked(UpdateInfo info) {
        if (!info.updateAvailable()) {
            updateText.setText("更新：已是最新版 v" + info.currentVersion());
            return;
        }
        pendingUpdate = info;
        updateDownloadButton.setEnabled(true);
        updateAccelerateButton.setEnabled(true);
        updateText.setText("发现新版本 v" + info.latestVersion() + "：" + info.assetName());
        new AlertDialog.Builder(this)
            .setTitle("发现 FocuSeed 新版本")
            .setMessage("当前 v" + info.currentVersion() + "，最新 v" + info.latestVersion() + "。默认会从 GitHub 原地址下载到系统下载目录；如果卡住，可手动切换 ghfast 加速。")
            .setPositiveButton("下载更新", (dialog, which) -> startUpdateDownload(false))
            .setNegativeButton("稍后", null)
            .setNeutralButton("加速下载", (dialog, which) -> startUpdateDownload(true))
            .show();
    }

    private void startUpdateDownload(boolean accelerated) {
        if (pendingUpdate == null || pendingUpdate.downloadUrl().trim().isEmpty()) {
            Toast.makeText(this, "暂时没有可下载的更新。", Toast.LENGTH_SHORT).show();
            return;
        }
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            Toast.makeText(this, "系统下载管理器不可用。", Toast.LENGTH_LONG).show();
            return;
        }
        if (updateDownloadId != NO_DOWNLOAD) {
            manager.remove(updateDownloadId);
            updateDownloadId = NO_DOWNLOAD;
        }

        String url = accelerated ? UpdateChecker.acceleratedUrl(pendingUpdate.downloadUrl()) : pendingUpdate.downloadUrl();
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("FocuSeed v" + pendingUpdate.latestVersion());
        request.setDescription(accelerated ? "正在通过 ghfast 加速下载更新" : "正在通过 GitHub 原地址下载更新");
        request.setMimeType("application/vnd.android.package-archive");
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, pendingUpdate.assetName());

        updateInstallPrompted = false;
        updateDownloadId = manager.enqueue(request);
        updateDownloadButton.setEnabled(true);
        updateAccelerateButton.setEnabled(true);
        updateText.setText(accelerated ? "更新：已切换 ghfast 加速，准备下载…" : "更新：正在从 GitHub 原地址下载…");
        handler.removeCallbacks(updateProgressTick);
        handler.post(updateProgressTick);
    }

    private void pollUpdateDownloadProgress() {
        if (updateDownloadId == NO_DOWNLOAD) {
            return;
        }
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            return;
        }
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(updateDownloadId);
        try (Cursor cursor = manager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                updateText.setText("更新：下载任务已离开队列");
                updateDownloadId = NO_DOWNLOAD;
                return;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                updateText.setText("更新：下载完成，文件已在 Download/" + pendingUpdate.assetName());
                promptInstallDownloadedUpdate(manager);
                updateDownloadId = NO_DOWNLOAD;
                return;
            }
            if (status == DownloadManager.STATUS_FAILED) {
                updateText.setText("更新：下载失败，可点“切换加速下载”重试");
                updateDownloadId = NO_DOWNLOAD;
                return;
            }
            if (total > 0L) {
                int percent = (int) Math.min(100L, downloaded * 100L / total);
                updateText.setText("更新下载进度：" + percent + "%（" + readableBytes(downloaded) + " / " + readableBytes(total) + "）");
            } else {
                updateText.setText("更新：正在下载 " + readableBytes(downloaded) + "，如卡住可切换加速");
            }
        }
        handler.postDelayed(updateProgressTick, 800L);
    }

    private void promptInstallDownloadedUpdate(DownloadManager manager) {
        if (updateInstallPrompted) {
            return;
        }
        Uri apkUri = manager.getUriForDownloadedFile(updateDownloadId);
        if (apkUri == null) {
            return;
        }
        updateInstallPrompted = true;
        new AlertDialog.Builder(this)
            .setTitle("更新已下载")
            .setMessage("安装包已保存到系统下载目录。FocuSeed 从本版开始使用固定签名，后续同包名可直接覆盖更新。")
            .setPositiveButton("打开安装", (dialog, which) -> openDownloadedUpdate(apkUri))
            .setNegativeButton("稍后", null)
            .show();
    }

    private void openDownloadedUpdate(Uri apkUri) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            leavingForUpdateInstall = true;
            Intent settingsIntent = new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName())
            );
            startActivity(settingsIntent);
            Toast.makeText(this, "请先允许 FocuSeed 安装下载的更新包，然后回到应用再次打开安装。", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            leavingForUpdateInstall = true;
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "无法打开安装器，请到系统下载目录手动安装。", Toast.LENGTH_LONG).show();
        }
    }

    private String readableBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024.0) {
            return String.format(java.util.Locale.ROOT, "%.1f KiB", kib);
        }
        return String.format(java.util.Locale.ROOT, "%.1f MiB", kib / 1024.0);
    }

    private void configureDoNotDisturb() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (!manager.isNotificationPolicyAccessGranted()) {
            leavingForSettings = true;
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            return;
        }
        configureDoNotDisturbIfAllowed();
        updatePolicyInfo();
    }

    private void configureDoNotDisturbIfAllowed() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.isNotificationPolicyAccessGranted()) {
            return;
        }
        NotificationManager.Policy policy = new NotificationManager.Policy(
            NotificationManager.Policy.PRIORITY_CATEGORY_CALLS,
            NotificationManager.Policy.PRIORITY_SENDERS_ANY,
            NotificationManager.Policy.PRIORITY_SENDERS_ANY
        );
        manager.setNotificationPolicy(policy);
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
    }

    private void updatePolicyInfo() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean granted = manager != null && manager.isNotificationPolicyAccessGranted();
        policyText.setText(granted ? "勿扰权限：已授权，可保留电话例外" : "勿扰权限：未授权，需要用户在系统设置中开启");
    }

    private void registerCallStateListenerIfAllowed() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.READ_PHONE_STATE}, 10);
            return;
        }
        TelephonyManager manager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (manager != null) {
            manager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    private void applyFocusWindowMode() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    private void handleUnexpectedBackgroundExit() {
        if (plannedBackgroundExit) {
            plannedBackgroundExit = false;
            return;
        }

        FocusSnapshot snapshot = session.snapshot(System.currentTimeMillis());
        FocusPhase phase = snapshot.phase();
        if (phase == FocusPhase.BREAK) {
            saveSession(true);
            scheduleFocusAlarm(System.currentTimeMillis() + Math.max(1_000L, snapshot.remainingMillis()));
            return;
        }
        if (phase != FocusPhase.WORKING) {
            return;
        }
        if (leavingForPhone || leavingForSettings || leavingForUpdateInstall) {
            saveSession(true);
            return;
        }
        if (exitChancesLeft > 0) {
            exitChancesLeft--;
            saveSession(true);
            scheduleFocusAlarm(System.currentTimeMillis() + 3_000L);
            return;
        }
        saveSession(true);
        scheduleFocusAlarm(System.currentTimeMillis() + 1_000L);
    }

    private void scheduleFocusAlarm(long triggerAtMillis) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pendingIntent = focusPendingIntent();
        long safeTrigger = Math.max(System.currentTimeMillis() + 1_000L, triggerAtMillis);
        alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(safeTrigger, pendingIntent), pendingIntent);
    }

    private void cancelWorkAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(focusPendingIntent());
        }
    }

    private PendingIntent focusPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(ACTION_FORCE_FOCUS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 1001, intent, pendingIntentFlags());
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void restoreSession() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        currentWorkMinutes = prefs.getInt(KEY_WORK, currentWorkMinutes);
        currentBreakMinutes = prefs.getInt(KEY_BREAK, currentBreakMinutes);
        currentRounds = prefs.getInt(KEY_ROUNDS, currentRounds);
        exitChancesLeft = prefs.getInt(KEY_EXIT_CHANCES, exitChancesLeft);
        sessionStartedAtMillis = prefs.getLong(KEY_STARTED_AT, sessionStartedAtMillis);

        if (prefs.getBoolean(KEY_RUNNING, false) && sessionStartedAtMillis > 0L) {
            FocusSettings settings = new FocusSettings(currentWorkMinutes, currentBreakMinutes, currentRounds);
            session.resume(settings, sessionStartedAtMillis);
            if (session.snapshot(System.currentTimeMillis()).phase() == FocusPhase.FINISHED) {
                clearSession();
            }
        }
    }

    private void saveSession(boolean running) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RUNNING, running)
            .putLong(KEY_STARTED_AT, sessionStartedAtMillis)
            .putInt(KEY_WORK, currentWorkMinutes)
            .putInt(KEY_BREAK, currentBreakMinutes)
            .putInt(KEY_ROUNDS, currentRounds)
            .putInt(KEY_EXIT_CHANCES, exitChancesLeft)
            .apply();
    }

    private void clearSession() {
        session.stop();
        exitChancesLeft = EXIT_CHANCES;
        sessionStartedAtMillis = 0L;
        saveSession(false);
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable gradient(int topColor, int bottomColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {topColor, bottomColor}
        );
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
