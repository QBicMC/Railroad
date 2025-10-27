package dev.railroadide.railroad.project.onboarding.impl;

import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.FormComponentBuilder;
import dev.railroadide.core.form.ValidationResult;
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
import dev.railroadide.railroad.project.LicenseRegistry;
import dev.railroadide.railroad.project.MappingChannelRegistry;
import dev.railroadide.railroad.project.ProjectTypeRegistry;
import dev.railroadide.railroad.project.ProjectValidators;
import dev.railroadide.railroad.project.creation.ui.ProjectCreationPane;
import dev.railroadide.railroad.project.data.FabricProjectKeys;
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
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.apache.commons.collections.ListUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

public class FabricProjectOnboarding extends Onboarding {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public void start(Scene scene) {
        var flow = OnboardingFlow.builder()
            .addStep("project_details", this::createProjectDetailsStep)
            .addStep("maven_coordinates", this::createMavenCoordinatesStep)
            .addStep("minecraft_version", this::createMinecraftVersionStep)
            .addStep("mapping_channel", this::createMappingChannelStep)
            .addStep("mapping_version", this::createMappingVersionStep)
            .addStep("fabric_loader", this::createFabricLoaderStep)
            .addStep("fabric_api", this::createFabricApiStep)
            .addStep("mod_details", this::createModDetailsStep)
            .addStep("license", this::createLicenseStep)
            .addStep("git", this::createGitStep)
            .addStep("access_widener", this::createAccessWidenerStep)
            .addStep("split_sources", this::createSplitSourcesStep)
            .addStep("optional_details", this::createOptionalDetailsStep)
            .firstStep("project_details")
            .build();

        var process = OnboardingProcess.createBasic(
            flow,
            new OnboardingContext(executor),
            ctx -> onFinish(ctx, scene)
        );

        process.run(scene);
    }

    protected void onFinish(OnboardingContext ctx, Scene scene) {
        this.executor.shutdown();

        var data = new ProjectData();
        data.set(ProjectData.DefaultKeys.TYPE, ProjectTypeRegistry.FABRIC);
        data.set(ProjectData.DefaultKeys.NAME, ctx.get(ProjectData.DefaultKeys.NAME));
        data.set(ProjectData.DefaultKeys.PATH, ctx.get(ProjectData.DefaultKeys.PATH));
        data.set(ProjectData.DefaultKeys.INIT_GIT, ctx.get(ProjectData.DefaultKeys.INIT_GIT));

        data.set(ProjectData.DefaultKeys.LICENSE, ctx.get(ProjectData.DefaultKeys.LICENSE));
        // TODO: Get rid of this and move into CustomLicense
        if (ctx.contains(ProjectData.DefaultKeys.LICENSE_CUSTOM))
            data.set(ProjectData.DefaultKeys.LICENSE_CUSTOM, ctx.get(ProjectData.DefaultKeys.LICENSE_CUSTOM));

        data.set(MinecraftProjectKeys.MINECRAFT_VERSION, ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION));
        data.set(FabricProjectKeys.FABRIC_LOADER_VERSION, ctx.get(FabricProjectKeys.FABRIC_LOADER_VERSION));

        if (ctx.contains(FabricProjectKeys.FABRIC_API_VERSION))
            data.set(FabricProjectKeys.FABRIC_API_VERSION, ctx.get(FabricProjectKeys.FABRIC_API_VERSION));

        data.set(MinecraftProjectKeys.MOD_ID, ctx.get(MinecraftProjectKeys.MOD_ID));
        data.set(MinecraftProjectKeys.MOD_NAME, ctx.get(MinecraftProjectKeys.MOD_NAME));
        data.set(MinecraftProjectKeys.MAIN_CLASS, ctx.get(MinecraftProjectKeys.MAIN_CLASS));
        data.set(FabricProjectKeys.USE_ACCESS_WIDENER, ctx.get(FabricProjectKeys.USE_ACCESS_WIDENER));
        data.set(FabricProjectKeys.SPLIT_SOURCES, ctx.get(FabricProjectKeys.SPLIT_SOURCES));
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
            ProjectTypeRegistry.FABRIC,
            serviceRegistry
        ), creationPane.getContext()));

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
                        .defaultValue(() -> MappingChannelRegistry.YARN)
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

    private OnboardingStep createFabricLoaderStep() {
        ObservableList<FabricLoaderVersion> availableVersions = FXCollections.observableArrayList();
        ObjectProperty<FabricLoaderVersion> latestVersionProperty = new SimpleObjectProperty<>();
        return OnboardingFormStep.builder()
            .id("fabric_loader")
            .title("railroad.project.creation.fabric_loader.title")
            .description("railroad.project.creation.fabric_loader.description")
            .appendSection("railroad.project.creation.section.fabric_loader",
                described(
                    FormComponent.comboBox(FabricProjectKeys.FABRIC_LOADER_VERSION, "railroad.project.creation.fabric_loader", FabricLoaderVersion.class)
                        .required()
                        .items(() -> availableVersions)
                        .keyFunction(FabricLoaderVersion::version)
                        .defaultValue(() -> {
                            if (latestVersionProperty.get() != null)
                                return latestVersionProperty.get();

                            if (!availableVersions.isEmpty())
                                return availableVersions.getFirst();

                            return null;
                        })
                        .translate(false),
                    "railroad.project.creation.fabric_loader.info"))
            .onEnter(ctx -> {
                MinecraftVersion mcVersion = ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION);
                if (mcVersion != null) {
                    try {
                        CompletableFuture<List<FabricLoaderVersion>> versionsFuture = SwitchboardRepositories.FABRIC_LOADER.getVersionsFor(mcVersion.id());
                        CompletableFuture<FabricLoaderVersion> latestFuture = SwitchboardRepositories.FABRIC_LOADER.getLatestVersionFor(mcVersion.id());

                        List<FabricLoaderVersion> versions = versionsFuture.get();
                        FabricLoaderVersion latest = latestFuture.get();
                        availableVersions.clear();
                        availableVersions.addAll(versions);
                        latestVersionProperty.set(latest);
                        ctx.markForRefresh(FabricProjectKeys.FABRIC_LOADER_VERSION);
                    } catch (ExecutionException | InterruptedException exception) {
                        Railroad.LOGGER.error("Failed to fetch Fabric Loader versions for Minecraft {}", mcVersion.id(), exception);
                    }
                }
            })
            .build();
    }

    private OnboardingStep createFabricApiStep() {
        ObservableList<String> availableVersions = FXCollections.observableArrayList();
        return OnboardingFormStep.builder()
            .id("fabric_api")
            .title("railroad.project.creation.fabric_api.title")
            .description("railroad.project.creation.fabric_api.description")
            .appendSection("railroad.project.creation.section.fabric_api",
                described(
                    FormComponent.comboBox(FabricProjectKeys.FABRIC_API_VERSION, "railroad.project.creation.fabric_api", String.class)
                        .items(() -> availableVersions)
                        .defaultValue(() -> {
                            if (!availableVersions.isEmpty())
                                return availableVersions.getFirst();

                            return null;
                        })
                        .translate(false),
                    "railroad.project.creation.fabric_api.info"))
            .onEnter(ctx -> {
                MinecraftVersion mcVersion = ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION);
                if (mcVersion != null) {
                    try {
                        CompletableFuture<List<String>> versionsFuture = SwitchboardRepositories.FABRIC_API.getVersionsFor(mcVersion.id());
                        List<String> versions = versionsFuture.get();
                        availableVersions.clear();
                        availableVersions.addAll(versions);
                        ctx.markForRefresh(FabricProjectKeys.FABRIC_API_VERSION);
                    } catch (ExecutionException | InterruptedException exception) {
                        Railroad.LOGGER.error("Failed to fetch Fabric API versions for Minecraft {}", mcVersion.id(), exception);
                    }
                }
            })
            .build();
    }

    private OnboardingStep createAccessWidenerStep() {
        ObjectProperty<CheckBox> useAccessWidenerCheckBox = new SimpleObjectProperty<>();
        BooleanProperty accessWidenerEnabled = new SimpleBooleanProperty(true);
        ChangeListener<Boolean> useAccessWidenerListener = (observable, oldValue, newValue) ->
            accessWidenerEnabled.set(Boolean.TRUE.equals(newValue));

        useAccessWidenerCheckBox.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.selectedProperty().removeListener(useAccessWidenerListener);
            }

            if (newValue != null) {
                accessWidenerEnabled.set(newValue.isSelected());
                newValue.selectedProperty().addListener(useAccessWidenerListener);
            } else {
                accessWidenerEnabled.set(false);
            }
        });

        BooleanBinding accessWidenerPathVisible = Bindings.createBooleanBinding(
            accessWidenerEnabled::get,
            accessWidenerEnabled
        );

        return OnboardingFormStep.builder()
            .id("access_widener")
            .title("railroad.project.creation.access_widener.title")
            .description("railroad.project.creation.access_widener.description")
            .appendSection("railroad.project.creation.section.access_widener",
                described(
                    FormComponent.checkBox(FabricProjectKeys.USE_ACCESS_WIDENER, "railroad.project.creation.use_access_widener")
                        .selected(true)
                        .bindCheckBoxTo(useAccessWidenerCheckBox),
                    "railroad.project.creation.use_access_widener.info"),
                described(
                    FormComponent.textField(FabricProjectKeys.ACCESS_WIDENER_PATH, "railroad.project.creation.access_widener.path")
                        .text("${modid}.accesswidener")
                        .promptText("railroad.project.creation.access_widener.path.prompt")
                        .visible(accessWidenerPathVisible)
                        .validator(field -> {
                            if (!accessWidenerEnabled.get())
                                return ValidationResult.ok();

                            String text = field.getText();
                            if (text == null || text.isBlank())
                                return ValidationResult.error("railroad.project.creation.access_widener.path.error.required");

                            return ValidationResult.ok();
                        }),
                    "railroad.project.creation.access_widener.path.info"))
            .onExit(ctx -> {
                if (!accessWidenerEnabled.get()) {
                    ctx.data().remove(FabricProjectKeys.ACCESS_WIDENER_PATH);
                }
            })
            .build();
    }

    private OnboardingStep createSplitSourcesStep() {
        return OnboardingFormStep.builder()
            .id("split_sources")
            .title("railroad.project.creation.split_sources.title")
            .description("railroad.project.creation.split_sources.description")
            .appendSection("railroad.project.creation.section.split_sources",
                described(
                    FormComponent.checkBox(FabricProjectKeys.SPLIT_SOURCES, "railroad.project.creation.split_sources")
                        .selected(true),
                    "railroad.project.creation.split_sources.info"))
            .build();
    }

    @Override
    protected @NotNull List<MinecraftVersion> getMinecraftVersions() {
        try {
            return SwitchboardRepositories.FABRIC_API.getAllVersionsSync().stream()
                .map(FabricApiVersionRepository::fapiToMinecraftVersion)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .map(FabricProjectOnboarding::getMinecraftVersion)
                .filter(Objects::nonNull)
                .sorted(Comparator.reverseOrder())
                .toList();
        } catch (ExecutionException | InterruptedException exception) {
            Railroad.LOGGER.error("Failed to fetch Minecraft versions", exception);
            return Collections.emptyList();
        }
    }
}
