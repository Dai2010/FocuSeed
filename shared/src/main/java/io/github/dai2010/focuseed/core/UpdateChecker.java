package io.github.dai2010.focuseed.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {
    public static final String CURRENT_VERSION = "0.1.2";
    public static final String PLATFORM_ANDROID = "android";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/Dai2010/FocuSeed/releases/latest";
    private static final String ACCELERATOR_PREFIX = "https://ghfast.top/";
    private static final Pattern TAG_PATTERN = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern HTML_URL_PATTERN = Pattern.compile("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ASSET_PATTERN = Pattern.compile(
        "\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*?\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
        Pattern.DOTALL
    );

    private UpdateChecker() {
    }

    public static UpdateInfo checkLatest(String assetKey) throws IOException {
        return checkLatest(assetKey, CURRENT_VERSION);
    }

    public static UpdateInfo checkLatest(String assetKey, String currentVersion) throws IOException {
        String safeCurrentVersion = normalizeVersion(currentVersion == null || currentVersion.trim().isEmpty() ? CURRENT_VERSION : currentVersion);
        String json = readUrl(LATEST_RELEASE_API, safeCurrentVersion);
        String latestVersion = normalizeVersion(firstMatch(TAG_PATTERN, json, safeCurrentVersion));
        String releasePageUrl = firstMatch(HTML_URL_PATTERN, json, "https://github.com/Dai2010/FocuSeed/releases/latest");
        Asset asset = selectAsset(parseAssets(json), assetKey);
        boolean available = compareVersions(latestVersion, safeCurrentVersion) > 0 && asset != null;
        return new UpdateInfo(
            safeCurrentVersion,
            latestVersion,
            available,
            asset == null ? "" : asset.name,
            asset == null ? "" : asset.downloadUrl,
            releasePageUrl
        );
    }

    public static String detectDesktopAssetKey() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows-msi";
        }
        if (os.contains("linux")) {
            String osRelease = readOsRelease();
            if (osRelease.contains("id=arch") || osRelease.contains("id_like=arch")) {
                return "linux-arch";
            }
            if (osRelease.contains("fedora")
                || osRelease.contains("rhel")
                || osRelease.contains("centos")
                || osRelease.contains("suse")
                || osRelease.contains("rpm")) {
                return "linux-rpm";
            }
            return "linux-deb";
        }
        return "desktop";
    }

    public static String acceleratedUrl(String originalUrl) {
        if (originalUrl.startsWith(ACCELERATOR_PREFIX)) {
            return originalUrl;
        }
        return ACCELERATOR_PREFIX + originalUrl;
    }

    public static String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private static String readUrl(String address, String currentVersion) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(8_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "FocuSeed/" + currentVersion);
        try (InputStream stream = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static String firstMatch(Pattern pattern, String value, String fallback) {
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return fallback;
    }

    private static List<Asset> parseAssets(String json) {
        List<Asset> assets = new ArrayList<>();
        String assetsJson = assetsSection(json);
        Matcher matcher = ASSET_PATTERN.matcher(assetsJson);
        while (matcher.find()) {
            assets.add(new Asset(unescapeJson(matcher.group(1)), unescapeJson(matcher.group(2))));
        }
        return assets;
    }

    private static String assetsSection(String json) {
        int assetsIndex = json.indexOf("\"assets\"");
        if (assetsIndex < 0) {
            return "";
        }
        int arrayStart = json.indexOf('[', assetsIndex);
        if (arrayStart < 0) {
            return "";
        }
        int depth = 0;
        for (int index = arrayStart; index < json.length(); index++) {
            char character = json.charAt(index);
            if (character == '[') {
                depth++;
            } else if (character == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(arrayStart, index + 1);
                }
            }
        }
        return json.substring(arrayStart);
    }

    private static Asset selectAsset(List<Asset> assets, String assetKey) {
        Asset fallback = null;
        for (Asset asset : assets) {
            String name = asset.name.toLowerCase(Locale.ROOT);
            if (matchesAssetKey(name, assetKey)) {
                return asset;
            }
            if (fallback == null && name.endsWith(".deb")) {
                fallback = asset;
            }
        }
        return fallback;
    }

    private static boolean matchesAssetKey(String name, String assetKey) {
        if (PLATFORM_ANDROID.equals(assetKey)) {
            return name.contains("android") && name.endsWith(".apk");
        }
        if ("windows-msi".equals(assetKey)) {
            return name.contains("windows") && name.endsWith(".msi");
        }
        if ("windows-exe".equals(assetKey)) {
            return name.contains("windows") && name.endsWith(".exe");
        }
        if ("linux-arch".equals(assetKey)) {
            return name.endsWith(".pkg.tar.zst");
        }
        if ("linux-rpm".equals(assetKey)) {
            return name.endsWith(".rpm");
        }
        if ("linux-deb".equals(assetKey)) {
            return name.endsWith(".deb");
        }
        return name.endsWith(".msi") || name.endsWith(".deb") || name.endsWith(".rpm") || name.endsWith(".pkg.tar.zst");
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("[.-]");
        String[] rightParts = normalizeVersion(right).split("[.-]");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < count; index++) {
            int leftValue = index < leftParts.length ? parseLeadingInt(leftParts[index]) : 0;
            int rightValue = index < rightParts.length ? parseLeadingInt(rightParts[index]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static int parseLeadingInt(String value) {
        int result = 0;
        boolean foundDigit = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isDigit(character)) {
                foundDigit = true;
                result = result * 10 + Character.digit(character, 10);
            } else if (foundDigit) {
                break;
            }
        }
        return result;
    }

    private static String readOsRelease() {
        File file = new File("/etc/os-release");
        if (!file.isFile()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line.toLowerCase(Locale.ROOT)).append('\n');
            }
        } catch (IOException ignored) {
            return "";
        }
        return builder.toString();
    }

    private static String unescapeJson(String value) {
        return value.replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    private static final class Asset {
        private final String name;
        private final String downloadUrl;

        private Asset(String name, String downloadUrl) {
            this.name = name;
            this.downloadUrl = downloadUrl;
        }
    }
}
