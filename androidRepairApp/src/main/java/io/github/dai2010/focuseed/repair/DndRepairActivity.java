package io.github.dai2010.focuseed.repair;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class DndRepairActivity extends Activity {
    private static final int BG = Color.rgb(255, 247, 242);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(62, 53, 56);
    private static final int MUTED = Color.rgb(128, 110, 116);
    private static final int ROSE = Color.rgb(242, 124, 141);
    private static final int GREEN = Color.rgb(56, 139, 91);
    private static final int RED = Color.rgb(184, 63, 63);

    private TextView statusText;
    private TextView detailText;
    private boolean pendingRepairAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createLayout());
        updateStatus("等待修复", "请点击一键修复。若系统要求授权，请允许本修复包访问勿扰设置后返回。", MUTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingRepairAfterPermission) {
            pendingRepairAfterPermission = false;
            if (hasPolicyAccess()) {
                repairNow();
            } else {
                updateStatus("尚未授权", "没有勿扰访问权限，修复包无法恢复系统勿扰策略。请再次点击一键修复并授权。", RED);
            }
        }
    }

    private ScrollView createLayout() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(26));
        scrollView.addView(root, new ScrollView.LayoutParams(match(), wrap()));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackgroundColor(CARD);
        root.addView(card, new LinearLayout.LayoutParams(match(), wrap()));

        TextView title = text("FocuSeed 勿扰修复包", 24, TEXT, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(title, new LinearLayout.LayoutParams(match(), wrap()));

        TextView subtitle = text("单独提供的一键修复工具，不会启动番茄钟。", 14, MUTED, false);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(match(), wrap());
        subtitleParams.setMargins(0, dp(8), 0, dp(18));
        card.addView(subtitle, subtitleParams);

        statusText = text("等待修复", 18, MUTED, true);
        card.addView(statusText, new LinearLayout.LayoutParams(match(), wrap()));

        detailText = text("", 14, MUTED, false);
        detailText.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(match(), wrap());
        detailParams.setMargins(0, dp(8), 0, dp(16));
        card.addView(detailText, detailParams);

        Button repairButton = button("一键修复媒体音量");
        repairButton.setOnClickListener(view -> repairNow());
        card.addView(repairButton, new LinearLayout.LayoutParams(match(), dp(48)));

        Button settingsButton = button("打开系统勿扰访问设置");
        settingsButton.setOnClickListener(view -> openPolicyAccessSettings());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(match(), dp(48));
        settingsParams.setMargins(0, dp(10), 0, 0);
        card.addView(settingsButton, settingsParams);

        Button closeButton = button("关闭修复包");
        closeButton.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(match(), dp(48));
        closeParams.setMargins(0, dp(10), 0, 0);
        card.addView(closeButton, closeParams);

        TextView note = text("修复完成后可以卸载本修复包。若你仍需要系统勿扰模式，请在确认媒体音量恢复后手动重新开启。", 13, MUTED, false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(match(), wrap());
        noteParams.setMargins(0, dp(16), 0, 0);
        card.addView(note, noteParams);

        return scrollView;
    }

    private void repairNow() {
        NotificationManager manager = notificationManager();
        if (manager == null) {
            updateStatus("修复失败", "系统没有返回 NotificationManager，无法修改勿扰策略。", RED);
            return;
        }
        if (!hasPolicyAccess(manager)) {
            pendingRepairAfterPermission = true;
            updateStatus("需要授权", "系统将打开勿扰访问设置。请允许“FocuSeed 勿扰修复包”，返回后会自动继续修复。", ROSE);
            openPolicyAccessSettings();
            return;
        }

        try {
            NotificationManager.Policy currentPolicy = null;
            try {
                currentPolicy = manager.getNotificationPolicy();
            } catch (RuntimeException ignored) {
            }

            int categories = currentPolicy == null ? 0 : currentPolicy.priorityCategories;
            categories |= NotificationManager.Policy.PRIORITY_CATEGORY_CALLS;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                categories |= NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA;
                categories |= NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS;
            }

            int callSenders = currentPolicy == null
                ? NotificationManager.Policy.PRIORITY_SENDERS_ANY
                : currentPolicy.priorityCallSenders;
            int messageSenders = currentPolicy == null
                ? NotificationManager.Policy.PRIORITY_SENDERS_ANY
                : currentPolicy.priorityMessageSenders;
            int suppressedVisualEffects = currentPolicy == null ? 0 : currentPolicy.suppressedVisualEffects;

            NotificationManager.Policy repairedPolicy = new NotificationManager.Policy(
                categories,
                callSenders,
                messageSenders,
                suppressedVisualEffects
            );
            manager.setNotificationPolicy(repairedPolicy);
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);

            updateStatus("修复完成", "已关闭一次勿扰状态，并把媒体、闹钟、电话加入勿扰允许项。请现在测试媒体音量。", GREEN);
            Toast.makeText(this, "FocuSeed 勿扰修复已完成", Toast.LENGTH_LONG).show();
        } catch (SecurityException error) {
            pendingRepairAfterPermission = true;
            updateStatus("需要授权", "系统拒绝修复请求。请允许本修复包访问勿扰设置后返回。", RED);
            openPolicyAccessSettings();
        } catch (RuntimeException error) {
            updateStatus("修复失败", "系统返回错误：" + error.getClass().getSimpleName() + "。请手动关闭勿扰模式并允许媒体声音。", RED);
        }
    }

    private void openPolicyAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } catch (RuntimeException error) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (RuntimeException ignored) {
                updateStatus("无法打开设置", "请手动进入系统设置，搜索“勿扰访问”或“免打扰访问”，允许本修复包。", RED);
            }
        }
    }

    private boolean hasPolicyAccess() {
        return hasPolicyAccess(notificationManager());
    }

    private boolean hasPolicyAccess(NotificationManager manager) {
        try {
            return manager != null && manager.isNotificationPolicyAccessGranted();
        } catch (RuntimeException error) {
            return false;
        }
    }

    private NotificationManager notificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void updateStatus(String title, String detail, int color) {
        statusText.setText(title);
        statusText.setTextColor(color);
        detailText.setText(detail + "\n\n当前勿扰状态：" + currentFilterText());
    }

    private String currentFilterText() {
        NotificationManager manager = notificationManager();
        if (manager == null) {
            return "未知";
        }
        try {
            int filter = manager.getCurrentInterruptionFilter();
            if (filter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                return "已关闭";
            }
            if (filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                return "仅优先事项";
            }
            if (filter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                return "完全勿扰";
            }
            if (filter == NotificationManager.INTERRUPTION_FILTER_ALARMS) {
                return "仅闹钟";
            }
            return "状态值 " + filter;
        } catch (RuntimeException error) {
            return "无法读取";
        }
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return textView;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackgroundColor(ROSE);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int match() {
        return ViewGroup.LayoutParams.MATCH_PARENT;
    }

    private int wrap() {
        return ViewGroup.LayoutParams.WRAP_CONTENT;
    }
}
