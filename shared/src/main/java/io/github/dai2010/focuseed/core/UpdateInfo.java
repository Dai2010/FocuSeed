package io.github.dai2010.focuseed.core;

public final class UpdateInfo {
    private final String currentVersion;
    private final String latestVersion;
    private final boolean updateAvailable;
    private final String assetName;
    private final String downloadUrl;
    private final String releasePageUrl;

    public UpdateInfo(
        String currentVersion,
        String latestVersion,
        boolean updateAvailable,
        String assetName,
        String downloadUrl,
        String releasePageUrl
    ) {
        this.currentVersion = currentVersion;
        this.latestVersion = latestVersion;
        this.updateAvailable = updateAvailable;
        this.assetName = assetName;
        this.downloadUrl = downloadUrl;
        this.releasePageUrl = releasePageUrl;
    }

    public String currentVersion() {
        return currentVersion;
    }

    public String latestVersion() {
        return latestVersion;
    }

    public boolean updateAvailable() {
        return updateAvailable;
    }

    public String assetName() {
        return assetName;
    }

    public String downloadUrl() {
        return downloadUrl;
    }

    public String releasePageUrl() {
        return releasePageUrl;
    }
}
