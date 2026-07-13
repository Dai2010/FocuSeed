package io.github.dai2010.focuseed.android;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import io.github.dai2010.focuseed.core.FocusPhase;
import io.github.dai2010.focuseed.core.FocusSession;
import io.github.dai2010.focuseed.core.FocusSettings;
import io.github.dai2010.focuseed.core.FocusSnapshot;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FocusSession session = new FocusSession(new FocusSettings(25, 5, 4));
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 500L);
        }
    };

    private TextView phaseText;
    private TextView timerText;
    private TextView roundText;
    private TextView dialerText;
    private TextView policyText;
    private EditText workInput;
    private EditText breakInput;
    private EditText roundsInput;

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
        setContentView(createLayout());
        applyFocusWindowMode();
        registerCallStateListenerIfAllowed();
        updateDialerInfo();
        updatePolicyInfo();
        handler.post(tick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyFocusWindowMode();
        updateDialerInfo();
        updatePolicyInfo();
        render();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private View createLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(40, 48, 40, 40);
        root.setBackgroundColor(Color.rgb(18, 22, 28));

        TextView title = text("FocuSeed", 34, Color.rgb(124, 255, 178), true);
        TextView subtitle = text("强制专注番茄钟 · Android 预览版", 16, Color.WHITE, false);
        phaseText = text("准备开始", 28, Color.WHITE, true);
        timerText = text("00:00", 64, Color.rgb(124, 255, 178), true);
        roundText = text("轮次 0 / 4", 18, Color.WHITE, false);
        dialerText = text("正在检测拨号器…", 13, Color.LTGRAY, false);
        policyText = text("勿扰权限未检测", 13, Color.LTGRAY, false);

        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.HORIZONTAL);
        settings.setGravity(Gravity.CENTER);
        workInput = numberInput("25");
        breakInput = numberInput("5");
        roundsInput = numberInput("4");
        settings.addView(labelWithInput("工作", workInput));
        settings.addView(labelWithInput("休息", breakInput));
        settings.addView(labelWithInput("轮数", roundsInput));

        Button start = button("开始并全屏");
        start.setOnClickListener(view -> startSession());
        Button dial = button("打开系统拨号器");
        dial.setOnClickListener(view -> openDialer());
        Button dnd = button("设置勿扰/电话例外");
        dnd.setOnClickListener(view -> configureDoNotDisturb());

        root.addView(title);
        root.addView(subtitle);
        root.addView(spacer(28));
        root.addView(phaseText);
        root.addView(timerText);
        root.addView(roundText);
        root.addView(spacer(24));
        root.addView(settings);
        root.addView(spacer(18));
        root.addView(start);
        root.addView(dial);
        root.addView(dnd);
        root.addView(spacer(18));
        root.addView(dialerText);
        root.addView(policyText);
        return root;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextColor(color);
        textView.setTextSize(sizeSp);
        textView.setGravity(Gravity.CENTER);
        if (bold) {
            textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
        }
        textView.setPadding(0, 8, 0, 8);
        return textView;
    }

    private EditText numberInput(String value) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextColor(Color.WHITE);
        input.setTextSize(18);
        input.setGravity(Gravity.CENTER);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSelectAllOnFocus(true);
        input.setEms(3);
        return input;
    }

    private LinearLayout labelWithInput(String label, EditText input) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(Gravity.CENTER);
        group.setPadding(12, 0, 12, 0);
        TextView title = text(label, 13, Color.LTGRAY, false);
        group.addView(title);
        group.addView(input);
        return group;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private View spacer(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, height));
        return view;
    }

    private void startSession() {
        FocusSettings settings = new FocusSettings(
            parsePositive(workInput, 25),
            parsePositive(breakInput, 5),
            parsePositive(roundsInput, 4)
        );
        session.start(settings, System.currentTimeMillis());
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
            phaseText.setText("准备开始");
            timerText.setText("00:00");
            roundText.setText("轮次 0 / " + parsePositive(roundsInput, 4));
            return;
        }
        if (phase == FocusPhase.FINISHED) {
            phaseText.setText("已完成");
            timerText.setText("00:00");
            roundText.setText("轮次 " + snapshot.roundText());
            return;
        }
        phaseText.setText(phase == FocusPhase.WORKING ? "专注中" : "休息中");
        timerText.setText(snapshot.remainingText());
        roundText.setText("轮次 " + snapshot.roundText());
    }

    private void openDialer() {
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

    private void configureDoNotDisturb() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (!manager.isNotificationPolicyAccessGranted()) {
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
}
