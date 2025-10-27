package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.ChecksumService;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.service.HttpService;
import dev.railroadide.core.project.creation.service.ZipService;
import dev.railroadide.railroad.project.data.ForgeProjectKeys;

import java.net.URI;
import java.nio.file.Path;

public record DownloadNeoforgeMdkStep(HttpService http, FilesService files, ZipService zip,
                                      ChecksumService checksum) implements CreationStep {
    @Override
    public String id() {
        return "railroad:download_neoforge_installer";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.download_neoforge_installer";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        reporter.info("Downloading NeoForge installer...");

        String neoForgeVersion = ctx.data().getAsString(ForgeProjectKeys.FORGE_VERSION);
        if (neoForgeVersion == null)
            throw new IllegalStateException("NeoForge version is not set");

        Path projectDir = ctx.projectDir();

        String baseUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/";
        String installerUrl = baseUrl + neoForgeVersion + "/neoforge-" + neoForgeVersion + "-installer.zip";
        String sha256Url = installerUrl + ".sha256";

        Path installerPath = projectDir.resolve("neoforge-installer.zip");

        http.download(new URI(installerUrl), installerPath);

        reporter.info("Verifying NeoForge installer checksum...");
        Path sha256Path = projectDir.resolve("neoforge-installer.zip.sha256");
        http.download(new URI(sha256Url), sha256Path);

        String expectedChecksum = files.readString(sha256Path).trim();
        if (!checksum.verify(installerPath, "SHA-256", expectedChecksum)) {
            reporter.info("Cleaning up invalid NeoForge installer files...");
            files.delete(installerPath);
            files.delete(sha256Path);
            throw new IllegalStateException("Downloaded NeoForge installer checksum does not match expected value");
        }

        reporter.info("Cleanup temporary files...");
        files.delete(sha256Path);
    }
}
