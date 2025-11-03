package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.service.ZipService;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.project.creation.ProjectContextKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;
import dev.railroadide.railroad.switchboard.SwitchboardRepositories;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public record ExtractNeoforgeMdkStep(FilesService files, ZipService zip) implements CreationStep {
    private static final String GRADLE_VERSION = "8.14.3";

    @Override
    public String id() {
        return "railroad:extract_neoforge_mdk";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.extracting_neoforge_mdk";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        Path projectDir = ctx.projectDir();
        Path archive = projectDir.resolve("neoforge-mdk.zip");
        if (!files.exists(archive))
            throw new IllegalStateException("NeoForge MDK archive not found: " + archive);

        reporter.info("Extracting NeoForge MDK archive...");
        zip.unzip(archive, projectDir);

        try (var stream = Files.list(projectDir)) {
            List<Path> entries = stream
                .filter(path -> !path.getFileName().toString().equals("neoforge-mdk.zip"))
                .toList();

            if (entries.size() == 1 && Files.isDirectory(entries.getFirst())) {
                Path innerDir = entries.getFirst();
                files.extractDirectoryContents(innerDir, projectDir);
                files.deleteDirectory(innerDir);
            }
        }

        reporter.info("Deleting NeoForge MDK archive...");
        files.delete(archive);

        reporter.info("Updating Gradle wrapper to version " + GRADLE_VERSION + "...");
        updateGradleWrapperVersion(projectDir);

        MinecraftVersion requested = ctx.data().get(MinecraftProjectKeys.MINECRAFT_VERSION, MinecraftVersion.class);
        if (requested == null)
            throw new IllegalStateException("Minecraft version is not specified.");

        reporter.info("Resolving NeoForge MDK version for " + requested.id() + "...");
        MinecraftVersion resolved = resolveMdkVersion(requested);
        ctx.put(ProjectContextKeys.MDK_VERSION, resolved);
        ctx.put(ProjectContextKeys.EXAMPLE_MOD_BRANCH, resolved.id());
    }

    private void updateGradleWrapperVersion(Path projectDir) throws Exception {
        Path wrapperPropertiesPath = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties");

        if (!files.exists(wrapperPropertiesPath)) {
            throw new IllegalStateException("gradle-wrapper.properties not found at: " + wrapperPropertiesPath);
        }

        String content = files.readString(wrapperPropertiesPath);
        String newDistributionUrl = "https\\://services.gradle.org/distributions/gradle-" + GRADLE_VERSION + "-bin.zip";

        String updatedContent = content.replaceAll(
            "distributionUrl=https\\\\://services\\.gradle\\.org/distributions/gradle-[^\\s]+",
            "distributionUrl=" + newDistributionUrl
        );

        files.writeString(wrapperPropertiesPath, updatedContent);
    }

    private MinecraftVersion resolveMdkVersion(MinecraftVersion version) {
        MinecraftVersion.Type type = version.getType();

        if (type != MinecraftVersion.Type.SNAPSHOT) {
            if (version.id().matches("\\d+\\.\\d+"))
                return version;

            String releaseId = version.id().replaceAll("\\.\\d+$", "");
            Optional<MinecraftVersion> release = fetchVersion(releaseId);
            return release.orElse(version);
        }

        String id = version.id();
        int dashIndex = id.indexOf('-');
        if (dashIndex > 0) {
            String releaseId = id.substring(0, dashIndex);
            Optional<MinecraftVersion> release = fetchVersion(releaseId);
            if (release.isPresent())
                return release.get();
        }

        return findClosestRelease(version);
    }

    private @NotNull MinecraftVersion findClosestRelease(MinecraftVersion version) {
        List<MinecraftVersion> versions = fetchAllVersions();
        long releaseTime = version.releaseTime().toEpochSecond(ZoneOffset.UTC);
        MinecraftVersion closest = null;
        for (MinecraftVersion candidate : versions) {
            if (candidate.getType() != MinecraftVersion.Type.RELEASE)
                continue;

            if (closest == null) {
                closest = candidate;
                continue;
            }

            long epochSecond = candidate.releaseTime().toEpochSecond(ZoneOffset.UTC);
            long candidateDiff = Math.abs(epochSecond - releaseTime);
            long closestDiff = Math.abs(closest.releaseTime().toEpochSecond(ZoneOffset.UTC) - releaseTime);
            if (candidateDiff < closestDiff)
                closest = candidate;
        }

        if (closest != null)
            return closest;

        throw new IllegalStateException("NeoForge does not support this Minecraft version");
    }

    private Optional<MinecraftVersion> fetchVersion(String id) {
        try {
            return SwitchboardRepositories.MINECRAFT.getVersionSync(id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while resolving MDK version", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to resolve MDK version", e);
        }
    }

    private List<MinecraftVersion> fetchAllVersions() {
        try {
            return SwitchboardRepositories.MINECRAFT.getAllVersionsSync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while resolving MDK version", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to resolve MDK version", e);
        }
    }
}
