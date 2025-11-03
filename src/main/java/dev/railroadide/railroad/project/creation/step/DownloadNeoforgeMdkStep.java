package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.ChecksumService;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.service.HttpService;
import dev.railroadide.core.project.creation.service.ZipService;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.project.data.ForgeProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;

import java.net.URI;
import java.nio.file.Path;

public record DownloadNeoforgeMdkStep(HttpService http, FilesService files, ZipService zip,
                                      ChecksumService checksum) implements CreationStep {
    @Override
    public String id() {
        return "railroad:download_neoforge_mdk";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.download_neoforge_mdk";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        reporter.info("Downloading NeoForge MDK...");

        String neoForgeVersion = ctx.data().getAsString(ForgeProjectKeys.FORGE_VERSION);
        if (neoForgeVersion == null)
            throw new IllegalStateException("NeoForge version is not set");

        MinecraftVersion minecraftVersion = (MinecraftVersion) ctx.data().get(MinecraftProjectKeys.MINECRAFT_VERSION);
        if (minecraftVersion == null)
            throw new IllegalStateException("Minecraft version is not set");

        Path projectDir = ctx.projectDir();
        Path mdkZip = projectDir.resolve("neoforge-mdk.zip");

        String repoBase = "https://github.com/NeoForgeMDKs/MDK-NeoForge-" + minecraftVersion.id();
        String neoGradleRelease = "https://github.com/NeoForgeMDKs/MDK-" + minecraftVersion.id() + "-NeoGradle/releases/download/" + neoForgeVersion + "/";
        String moddevRelease = "https://github.com/NeoForgeMDKs/MDK-" + minecraftVersion.id() + "-ModDevGradle/releases/download/" + neoForgeVersion + "/";
        String fileName = "neoforged-mdk-" + neoForgeVersion + ".zip";

        boolean success = false;

        try {
            http.download(new URI(moddevRelease + fileName), mdkZip);
            success = true;
        } catch (Exception ignored) {}

        if (!success) {
            try {
                http.download(new URI(neoGradleRelease + fileName), mdkZip);
                success = true;
            } catch (Exception ignored) {}
        }

        if (!success) {
            reporter.info("No release found — downloading source archive instead...");
            String fallbackUrlNeoGradle = "https://github.com/NeoForgeMDKs/MDK-" + minecraftVersion.id() + "-NeoGradle/archive/refs/heads/main.zip";
            String fallbackUrlModDev = "https://github.com/NeoForgeMDKs/MDK-" + minecraftVersion.id() + "-ModDevGradle/archive/refs/heads/main.zip";
            try {
                http.download(new URI(fallbackUrlModDev), mdkZip);
            } catch (Exception e) {
                http.download(new URI(fallbackUrlNeoGradle), mdkZip);
            }
        }
    }
}
