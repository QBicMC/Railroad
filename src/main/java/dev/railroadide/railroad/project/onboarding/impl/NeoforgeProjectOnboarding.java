package dev.railroadide.railroad.project.onboarding.impl;

import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.FormComponentBuilder;
import dev.railroadide.core.form.ui.InformativeLabeledHBox;
import dev.railroadide.core.project.License;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.project.creation.ProjectCreationService;
import dev.railroadide.core.project.creation.ProjectServiceRegistry;
import dev.railroadide.core.project.creation.service.GradleService;
import dev.railroadide.core.project.minecraft.MappingChannel;
import dev.railroadide.core.switchboard.pojo.FabricLoaderVersion;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.project.*;
import dev.railroadide.railroad.project.creation.ui.ProjectCreationPane;
import dev.railroadide.railroad.project.data.FabricProjectKeys;
import dev.railroadide.railroad.project.data.ForgeProjectKeys;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;
import dev.railroadide.railroad.project.onboarding.OnboardingContext;
import dev.railroadide.railroad.project.onboarding.OnboardingProcess;
import dev.railroadide.railroad.project.onboarding.flow.OnboardingFlow;
import dev.railroadide.railroad.project.onboarding.step.OnboardingFormStep;
import dev.railroadide.railroad.project.onboarding.step.OnboardingStep;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import dev.railroadide.railroad.switchboard.SwitchboardRepositories;
import dev.railroadide.railroad.switchboard.repositories.FabricApiVersionRepository;
import dev.railroadide.railroad.switchboard.repositories.NeoforgeVersionRepository;
import dev.railroadide.railroad.utility.ExpiringCache;
import dev.railroadide.railroad.welcome.project.ui.widget.StarableListCell;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.apache.commons.collections.ListUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

// TODO: Make it so the display test and client side only options are only shown for versions that support it
// TODO: Make it so the display test and client side only options are in their own steps
// TODO: Fix the comboboxes not being immediately populated and instead having the data fetched completely async
public class NeoforgeProjectOnboarding extends Onboarding {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public void start(Scene scene) {
        var flow = OnboardingFlow.builder()
            .addStep("project_details", this::createProjectDetailsStep) // name, loc
            .addStep("maven_coordinates", this::createMavenCoordinatesStep) // groupid, artifact id, version
            .addStep("minecraft_version", this::createMinecraftVersionStep)
            .addStep("mapping_channel", this::createMappingChannelStep) // parchment or mojmaps
            .addStep("mapping_version", this::createMappingVersionStep)
            .addStep("neo", this::createNeoStep) // neoforge version
            .addStep("mod_details", this::createModDetailsStep) // id, name, class
            .addStep("license", this::createLicenseStep)
            .addStep("git", this::createGitStep) // make git repo
            .addStep("optional_details", this::createOptionalDetailsStep) // author, desc, website, sources
            .firstStep("project_details")
            .build();

        var process = OnboardingProcess.createBasic(
            flow,
            new OnboardingContext(executor),
            ctx -> onFinish(ctx, scene)
        );

        process.run(scene);
    }

    @Override
    protected void onFinish(OnboardingContext ctx, Scene scene) {
        this.executor.shutdown();

        var data = new ProjectData();
        data.set(ProjectData.DefaultKeys.TYPE, ProjectTypeRegistry.NEOFORGE);
        data.set(ProjectData.DefaultKeys.NAME, ctx.get(ProjectData.DefaultKeys.NAME));
        data.set(ProjectData.DefaultKeys.PATH, ctx.get(ProjectData.DefaultKeys.PATH));
        data.set(ProjectData.DefaultKeys.INIT_GIT, ctx.get(ProjectData.DefaultKeys.INIT_GIT));

        data.set(ProjectData.DefaultKeys.LICENSE, ctx.get(ProjectData.DefaultKeys.LICENSE));
        // TODO: Get rid of this and move into CustomLicense
        if (ctx.contains(ProjectData.DefaultKeys.LICENSE_CUSTOM))
            data.set(ProjectData.DefaultKeys.LICENSE_CUSTOM, ctx.get(ProjectData.DefaultKeys.LICENSE_CUSTOM));

        data.set(MinecraftProjectKeys.MINECRAFT_VERSION, ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION));

        if (ctx.contains(ForgeProjectKeys.FORGE_VERSION))
            data.set(ForgeProjectKeys.FORGE_VERSION, ctx.get(ForgeProjectKeys.FORGE_VERSION));

        data.set(MinecraftProjectKeys.MOD_ID, ctx.get(MinecraftProjectKeys.MOD_ID));
        data.set(MinecraftProjectKeys.MOD_NAME, ctx.get(MinecraftProjectKeys.MOD_NAME));
        data.set(MinecraftProjectKeys.MAIN_CLASS, ctx.get(MinecraftProjectKeys.MAIN_CLASS));
        data.set(ForgeProjectKeys.CLIENT_SIDE_ONLY, ctx.get(ForgeProjectKeys.CLIENT_SIDE_ONLY));
        data.set(MinecraftProjectKeys.MAPPING_CHANNEL, ctx.get(MinecraftProjectKeys.MAPPING_CHANNEL));
        data.set(MinecraftProjectKeys.MAPPING_VERSION, ctx.get(MinecraftProjectKeys.MAPPING_VERSION));

        if (ctx.contains(ProjectData.DefaultKeys.AUTHOR))
            data.set(ProjectData.DefaultKeys.AUTHOR, ctx.get(ProjectData.DefaultKeys.AUTHOR));
        if (ctx.contains(ProjectData.DefaultKeys.DESCRIPTION))
            data.set(ProjectData.DefaultKeys.DESCRIPTION, ctx.get(ProjectData.DefaultKeys.DESCRIPTION));
        if (ctx.contains(ProjectData.DefaultKeys.ISSUES_URL))
            data.set(ProjectData.DefaultKeys.ISSUES_URL, ctx.get(ProjectData.DefaultKeys.ISSUES_URL));
        if (ctx.contains(ProjectData.DefaultKeys.HOMEPAGE_URL))
            data.set(ProjectData.DefaultKeys.HOMEPAGE_URL, ctx.get(ProjectData.DefaultKeys.HOMEPAGE_URL));
        if (ctx.contains(ProjectData.DefaultKeys.SOURCES_URL))
            data.set(ProjectData.DefaultKeys.SOURCES_URL, ctx.get(ProjectData.DefaultKeys.SOURCES_URL));

        data.set(MavenProjectKeys.GROUP_ID, ctx.get(MavenProjectKeys.GROUP_ID));
        data.set(MavenProjectKeys.ARTIFACT_ID, ctx.get(MavenProjectKeys.ARTIFACT_ID));
        data.set(MavenProjectKeys.VERSION, ctx.get(MavenProjectKeys.VERSION));

        var creationPane = new ProjectCreationPane(data);

        ProjectServiceRegistry serviceRegistry = Services.PROJECT_SERVICE_REGISTRY;
        serviceRegistry.get(GradleService.class).setOutputStream(creationPane.getTaos());
        creationPane.initService(new ProjectCreationService(Services.PROJECT_CREATION_PIPELINE.createProject(
            ProjectTypeRegistry.NEOFORGE,
            serviceRegistry
        ), creationPane.getContext()));

        data.set(ForgeProjectKeys.USE_MIXINS, true);
        data.set(ForgeProjectKeys.USE_ACCESS_TRANSFORMER, true);
        data.set(ForgeProjectKeys.DISPLAY_TEST, true);
        data.set(ForgeProjectKeys.GEN_RUN_FOLDERS, true);

        scene.setRoot(creationPane);
    }

    private OnboardingStep createMappingChannelStep() {
        ObservableList<MappingChannel> availableChannels = FXCollections.observableArrayList();
        return OnboardingFormStep.builder()
            .id("mapping_channel")
            .title("railroad.project.creation.mapping_channel.title")
            .description("railroad.project.creation.mapping_channel.description")
            .appendSection("railroad.project.creation.section.mapping_channel",
                described(
                    FormComponent.comboBox(MinecraftProjectKeys.MAPPING_CHANNEL, "railroad.project.creation.mapping_channel", MappingChannel.class)
                        .required()
                        .items(() -> availableChannels)
                        .defaultValue(() -> MappingChannelRegistry.PARCHMENT)
                        .keyFunction(MappingChannel::id)
                        .valueOfFunction(MappingChannel.REGISTRY::get)
                        .defaultDisplayNameFunction(MappingChannel::translationKey)
                        .translate(true),
                    "railroad.project.creation.mapping_channel.info"))
            .onEnter(ctx -> {
                MinecraftVersion mcVersion = ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION);
                if (mcVersion != null) {
                    availableChannels.clear();
                    availableChannels.setAll(MappingChannelRegistry.findValidMappingChannels(mcVersion));
                    ctx.markForRefresh(MinecraftProjectKeys.MAPPING_CHANNEL);
                }
            })
            .build();
    }

    private OnboardingStep createMappingVersionStep() {
        ObservableList<String> availableVersions = FXCollections.observableArrayList();
        return OnboardingFormStep.builder()
            .id("mapping_version")
            .title("railroad.project.creation.mapping_version.title")
            .description("railroad.project.creation.mapping_version.description")
            .appendSection("railroad.project.creation.section.mapping_version",
                described(
                    FormComponent.comboBox(MinecraftProjectKeys.MAPPING_VERSION, "railroad.project.creation.mapping_version", String.class)
                        .required()
                        .items(() -> availableVersions)
                        .defaultValue(() -> {
                            if (!availableVersions.isEmpty())
                                return availableVersions.getFirst();

                            return null;
                        })
                        .translate(false),
                    "railroad.project.creation.mapping_version.info"))
            .onEnter(ctx -> {
                MinecraftVersion mcVersion = ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION);
                MappingChannel channel = ctx.get(MinecraftProjectKeys.MAPPING_CHANNEL);
                if (mcVersion != null && channel != null) {
                    List<String> newVersions = channel.listVersionsFor(mcVersion);
                    availableVersions.clear();
                    availableVersions.setAll(newVersions);
                    ctx.markForRefresh(MinecraftProjectKeys.MAPPING_VERSION);
                }
            })
            .build();
    }

    private OnboardingStep createNeoStep() {
        ObservableList<String> availableVersions = FXCollections.observableArrayList();
        return OnboardingFormStep.builder()
            .id("neo")
            .title("railroad.project.creation.neo.title")
            .description("railroad.project.creation.neo.description")
            .appendSection("railroad.project.creation.section.neo",
                described(
                    FormComponent.comboBox(ForgeProjectKeys.FORGE_VERSION, "railroad.project.creation.neo", String.class)
                        .items(() -> availableVersions)
                        .defaultValue(() -> {
                            if (!availableVersions.isEmpty())
                                return availableVersions.getFirst();

                            return null;
                        })
                        .translate(false),
                    "railroad.project.creation.neo.info"))
            .onEnter(ctx -> {
                MinecraftVersion mcVersion = ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION);
                if (mcVersion != null) {
                    try {
                        CompletableFuture<List<String>> versionsFuture = SwitchboardRepositories.NEOFORGE.getVersionsFor(mcVersion.id());
                        List<String> versions = versionsFuture.get();
                        availableVersions.clear();
                        availableVersions.addAll(versions);
                        ctx.markForRefresh(ForgeProjectKeys.FORGE_VERSION);
                    } catch (ExecutionException | InterruptedException exception) {
                        Railroad.LOGGER.error("Failed to fetch Neoforge versions for Minecraft {}", mcVersion.id(), exception);
                    }
                }
            })
            .build();
    }

    @Override
    protected List<MinecraftVersion> getMinecraftVersions() {

        // TODO: get minecraft versions available for neoforge directly from switchboard rather than version filter
        try {
            return SwitchboardRepositories.MINECRAFT.getAllVersionsSync().stream()
                .filter(v -> {
                    String id = v.id();
                    if (!id.matches("\\d+\\.\\d+(\\.\\d+)?")) return false; // exclude non-standard (pre, w, rc, etc.)
                    String[] p = id.split("\\.");
                    String[] t = {"1", "20", "4"};
                    for (int i = 0; i < Math.max(p.length, t.length); i++) {
                        int a = i < p.length ? Integer.parseInt(p[i]) : 0;
                        int b = i < t.length ? Integer.parseInt(t[i]) : 0;
                        if (a > b) return true;
                        if (a < b) return false;
                    }
                    return true;
                })
                .sorted((a, b) -> {
                    String[] pa = a.id().split("\\.");
                    String[] pb = b.id().split("\\.");
                    for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
                        int va = i < pa.length ? Integer.parseInt(pa[i]) : 0;
                        int vb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
                        if (va != vb) return Integer.compare(vb, va);
                    }
                    return 0;
                })
                .toList();
        } catch (ExecutionException | InterruptedException exception) {
            Railroad.LOGGER.error("Failed to fetch Minecraft versions", exception);
            return Collections.emptyList();
        }
    }
}
