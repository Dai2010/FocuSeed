package io.github.dai2010.focuseed.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.dai2010.focuseed.core.UpdateChecker;

final class AndroidDiagnostics {
    private static final String CHANNEL = "Canary";
    private static final String LOG_FILE_NAME = "focuseed-canary.log";
    private static final String LAST_CRASH_FILE_NAME = "last-crash.txt";
    private static Thread.UncaughtExceptionHandler previousHandler;
    private static boolean installed;

    private AndroidDiagnostics() {
    }

    static synchronized void install(Context context) {
        if (installed) {
            return;
        }
        installed = true;
        Context appContext = context.getApplicationContext();
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            recordCrash(appContext, thread, error);
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, error);
            }
        });
        logEvent(appContext, "diagnostics installed");
    }

    static void logStartup(Context context) {
        logEvent(context, "startup " + appSummary(context));
    }

    static void logEvent(Context context, String message) {
        append(logFile(context), timestamp() + " " + message + System.lineSeparator());
    }

    static void recordRecoverable(Context context, String source, Throwable error) {
        append(logFile(context), timestamp() + " recoverable " + source + System.lineSeparator() + stackTrace(error));
    }

    static void recordCrash(Context context, Thread thread, Throwable error) {
        String report = timestamp() + " fatal crash" + System.lineSeparator()
            + appSummary(context) + System.lineSeparator()
            + "thread=" + (thread == null ? "unknown" : thread.getName()) + System.lineSeparator()
            + stackTrace(error);
        append(logFile(context), report);
        write(lastCrashFile(context), report);
    }

    static String buildReport(Context context) {
        return "FocuSeed " + appVersionName(context) + " " + CHANNEL + System.lineSeparator()
            + appSummary(context) + System.lineSeparator()
            + "logFile=" + logFile(context).getAbsolutePath() + System.lineSeparator()
            + "lastCrashFile=" + lastCrashFile(context).getAbsolutePath() + System.lineSeparator()
            + System.lineSeparator()
            + readLastCrash(context);
    }

    static String statusText(Context context) {
        if (lastCrashFile(context).isFile()) {
            return "Canary 诊断：已记录上次崩溃，点按钮复制报告";
        }
        return "Canary 诊断：暂未记录到崩溃，日志在应用私有目录";
    }

    static boolean copyReportToClipboard(Context context) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return false;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("FocuSeed Canary diagnostics", buildReport(context)));
            return true;
        } catch (RuntimeException error) {
            recordRecoverable(context, "copyReportToClipboard", error);
            return false;
        }
    }

    private static String readLastCrash(Context context) {
        File file = lastCrashFile(context);
        if (!file.isFile()) {
            return "lastCrash=none" + System.lineSeparator();
        }
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException error) {
            return "lastCrash=unreadable " + error.getClass().getName() + System.lineSeparator();
        }
    }

    private static void append(File file, String value) {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (FileOutputStream stream = new FileOutputStream(file, true)) {
                stream.write(value.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
        }
    }

    private static void write(File file, String value) {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (FileOutputStream stream = new FileOutputStream(file, false)) {
                stream.write(value.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
        }
    }

    private static String stackTrace(Throwable error) {
        if (error == null) {
            return "no throwable" + System.lineSeparator();
        }
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static File logFile(Context context) {
        return new File(context.getFilesDir(), LOG_FILE_NAME);
    }

    private static File lastCrashFile(Context context) {
        return new File(context.getFilesDir(), LAST_CRASH_FILE_NAME);
    }

    private static String appSummary(Context context) {
        return "version=" + appVersionName(context)
            + " channel=" + CHANNEL
            + " sdk=" + Build.VERSION.SDK_INT
            + " manufacturer=" + Build.MANUFACTURER
            + " brand=" + Build.BRAND
            + " model=" + Build.MODEL
            + " device=" + Build.DEVICE
            + " product=" + Build.PRODUCT;
    }

    private static String appVersionName(Context context) {
        try {
            String versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            if (versionName != null && !versionName.trim().isEmpty()) {
                return versionName;
            }
        } catch (Exception ignored) {
        }
        return UpdateChecker.CURRENT_VERSION;
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date());
    }
}
