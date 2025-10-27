package dev.railroadide.railroad.project.onboarding.impl;

import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.FormComponentBuilder;
import dev.railroadide.core.form.ui.InformativeLabeledHBox;
import dev.railroadide.core.project.License;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.LicenseRegistry;
import dev.railroadide.railroad.project.ProjectValidators;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;
import dev.railroadide.railroad.project.onboarding.OnboardingContext;
import dev.railroadide.railroad.project.onboarding.step.OnboardingFormStep;
import dev.railroadide.railroad.project.onboarding.step.OnboardingStep;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import dev.railroadide.railroad.switchboard.SwitchboardRepositories;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class Onboarding {
    public abstract void start(Scene scene);

    protected abstract void onFinish(OnboardingContext ctx, Scene scene);

    protected abstract List<MinecraftVersion> getMinecraftVersions();

    protected OnboardingStep createProjectDetailsStep() {
        return OnboardingFormStep.builder()
            .id("project_details")
            .title("railroad.project.creation.project_details.title")
            .description("railroad.project.creation.project_details.description")
            .appendSection("railroad.project.creation.section.project",
                described(
                    FormComponent.textField(ProjectData.DefaultKeys.NAME, "railroad.project.creation.name")
                        .required()
                        .promptText("railroad.project.creation.name.prompt")
                        .validator(ProjectValidators::validateProjectName),
                    "railroad.project.creation.name.info"),
                described(
                    FormComponent.directoryChooser(ProjectData.DefaultKeys.PATH, "railroad.project.creation.location")
                        .required()
                        .defaultPath(System.getProperty("user.home"))
                        .validator(ProjectValidators::validatePath),
                    value -> {
                        if (value == null)
                            return null;

                        String text = value.toString();
                        return text.isBlank() ? null : Path.of(text);
                    },
                    value -> {
                        if (value == null)
                            return null;

                        return value instanceof Path path ? path.toAbsolutePath().toString() : value.toString();
                    },
                    "railroad.project.creation.location.info"))
            .build();
    }

    protected OnboardingStep createMavenCoordinatesStep() {
        StringProperty artifactId = new SimpleStringProperty();
        String configuredGroupId = SettingsHandler.getValue(Settings.DEFAULT_PROJECT_GROUP_ID);
        String configuredVersion = SettingsHandler.getValue(Settings.DEFAULT_PROJECT_VERSION);
        String defaultGroupId = isNullOrBlank(configuredGroupId) ? "" : configuredGroupId;
        String defaultVersion = isNullOrBlank(configuredVersion) ? "1.0.0" : configuredVersion;

        return OnboardingFormStep.builder()
            .id("maven_coordinates")
            .title("railroad.project.creation.maven_coordinates.title")
            .description("railroad.project.creation.maven_coordinates.description")
            .appendSection("railroad.project.creation.section.maven_coordinates",
                described(
                    FormComponent.textField(MavenProjectKeys.GROUP_ID, "railroad.project.creation.group_id")
                        .required()
                        .promptText("railroad.project.creation.group_id.prompt")
                        .text(() -> defaultGroupId)
                        .validator(ProjectValidators::validateGroupId),
                    "railroad.project.creation.group_id.info"),
                described(
                    FormComponent.textField(MavenProjectKeys.ARTIFACT_ID, "railroad.project.creation.artifact_id")
                        .required()
                        .promptText("railroad.project.creation.artifact_id.prompt")
                        .text(artifactId::get)
                        .validator(ProjectValidators::validateArtifactId),
                    "railroad.project.creation.artifact_id.info"),
                described(
                    FormComponent.textField(MavenProjectKeys.VERSION, "railroad.project.creation.version")
                        .required()
                        .promptText("railroad.project.creation.version.prompt")
                        .text(() -> defaultVersion)
                        .validator(ProjectValidators::validateVersion),
                    "railroad.project.creation.version.info"))
            .onEnter(ctx -> {
                String projectName = ctx.get(ProjectData.DefaultKeys.NAME);
                if (projectName != null) {
                    String defaultArtifactId = ProjectValidators.projectNameToArtifactId(projectName);
                    if (isNullOrBlank(artifactId.get())) {
                        artifactId.set(defaultArtifactId);
                    }
                }
            })
            .build();
    }

    protected OnboardingStep createMinecraftVersionStep() {
        ObservableList<MinecraftVersion> availableVersions = FXCollections.observableArrayList();
        var nextInvalidationTime = new AtomicLong(0L);

        return OnboardingFormStep.builder()
            .id("minecraft_version")
            .title("railroad.project.creation.minecraft_version.title")
            .description("railroad.project.creation.minecraft_version.description")
            .appendSection("railroad.project.creation.section.minecraft_version",
                described(
                    FormComponent.comboBox(MinecraftProjectKeys.MINECRAFT_VERSION, "railroad.project.creation.minecraft_version", MinecraftVersion.class)
                        .items(() -> availableVersions)
                        .defaultValue(() -> MinecraftVersion.determineDefaultMinecraftVersion(availableVersions))
                        .keyFunction(MinecraftVersion::id)
                        .valueOfFunction(Onboarding::getMinecraftVersion)
                        .required()
                        .translate(false),
                    "railroad.project.creation.minecraft_version.info"))
            .onEnter(ctx -> {
                if (availableVersions.isEmpty() || System.currentTimeMillis() > nextInvalidationTime.get()) {
                    availableVersions.clear();
                    availableVersions.addAll(getMinecraftVersions());
                    nextInvalidationTime.set(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5));
                    ctx.markForRefresh(MinecraftProjectKeys.MINECRAFT_VERSION);
                }
            })
            .build();
    }

    protected OnboardingStep createModDetailsStep() {
        StringProperty modIdProperty = new SimpleStringProperty();
        StringProperty modNameProperty = new SimpleStringProperty();
        StringProperty mainClassProperty = new SimpleStringProperty();

        ObjectProperty<TextField> modIdField = new SimpleObjectProperty<>();
        ObjectProperty<TextField> modNameField = new SimpleObjectProperty<>();
        ObjectProperty<TextField> mainClassField = new SimpleObjectProperty<>();

        bindTextField(modIdProperty, modIdField);
        bindTextField(modNameProperty, modNameField);
        bindTextField(mainClassProperty, mainClassField);

        return OnboardingFormStep.builder()
            .id("mod_details")
            .title("railroad.project.creation.mod_details.title")
            .description("railroad.project.creation.mod_details.description")
            .appendSection("railroad.project.creation.section.mod_details",
                described(
                    FormComponent.textField(MinecraftProjectKeys.MOD_ID, "railroad.project.creation.mod_id")
                        .required()
                        .promptText("railroad.project.creation.mod_id.prompt")
                        .text(modIdProperty::get)
                        .bindTextFieldTo(modIdField)
                        .validator(ProjectValidators::validateModId),
                    "railroad.project.creation.mod_id.info"),
                described(
                    FormComponent.textField(MinecraftProjectKeys.MOD_NAME, "railroad.project.creation.mod_name")
                        .required()
                        .promptText("railroad.project.creation.mod_name.prompt")
                        .text(modNameProperty::get)
                        .bindTextFieldTo(modNameField)
                        .validator(ProjectValidators::validateModName),
                    "railroad.project.creation.mod_name.info"),
                described(
                    FormComponent.textField(MinecraftProjectKeys.MAIN_CLASS, "railroad.project.creation.main_class")
                        .required()
                        .promptText("railroad.project.creation.main_class.prompt")
                        .text(mainClassProperty::get)
                        .bindTextFieldTo(mainClassField)
                        .validator(ProjectValidators::validateMainClass),
                    "railroad.project.creation.main_class.info"))
            .onEnter(ctx -> {
                String projectName = ctx.get(ProjectData.DefaultKeys.NAME);

                if (!isNullOrBlank(projectName)) {
                    modIdProperty.set(ProjectValidators.projectNameToModId(projectName));
                }

                if (!isNullOrBlank(projectName)) {
                    modNameProperty.set(projectName);
                }

                if (!isNullOrBlank(projectName)) {
                    String mainClassName = ProjectValidators.projectNameToMainClass(projectName);
                    mainClassProperty.set(isNullOrBlank(mainClassName) ? "" : mainClassName);
                }
            })
            .build();
    }

    protected OnboardingStep createLicenseStep() {
        ObservableList<License> availableLicenses = FXCollections.observableArrayList();
        ObjectProperty<ComboBox<License>> licenseComboBox = new SimpleObjectProperty<>();
        BooleanProperty showCustomLicense = new SimpleBooleanProperty(false);
        ChangeListener<License> licenseSelectionListener = (observable, oldValue, newValue) ->
            showCustomLicense.set(newValue == LicenseRegistry.CUSTOM);

        licenseComboBox.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.valueProperty().removeListener(licenseSelectionListener);
            }

            if (newValue != null) {
                showCustomLicense.set(newValue.getValue() == LicenseRegistry.CUSTOM);
                newValue.valueProperty().addListener(licenseSelectionListener);
            } else {
                showCustomLicense.set(false);
            }
        });

        BooleanBinding customLicenseVisible = Bindings.createBooleanBinding(showCustomLicense::get, showCustomLicense);

        return OnboardingFormStep.builder()
            .id("license")
            .title("railroad.project.creation.license.title")
            .description("railroad.project.creation.license.description")
            .appendSection("railroad.project.creation.section.license",
                described(
                    FormComponent.comboBox(ProjectData.DefaultKeys.LICENSE, "railroad.project.creation.license", License.class)
                        .required()
                        .bindComboBoxTo(licenseComboBox)
                        .keyFunction(License::getSpdxId)
                        .valueOfFunction(License::fromSpdxId)
                        .defaultDisplayNameFunction(License::getName)
                        .translate(false)
                        .items(() -> availableLicenses)
                        .defaultValue(() -> {
                            if (availableLicenses.contains(LicenseRegistry.LGPL))
                                return LicenseRegistry.LGPL;

                            if (!availableLicenses.isEmpty())
                                return availableLicenses.getFirst();

                            return null;
                        }),
                    "railroad.project.creation.license.info"),
                described(
                    FormComponent.textField(ProjectData.DefaultKeys.LICENSE_CUSTOM, "railroad.project.creation.license.custom")
                        .visible(customLicenseVisible)
                        .promptText("railroad.project.creation.license.custom.prompt")
                        .validator(ProjectValidators::validateCustomLicense),
                    "railroad.project.creation.license.custom.info"))
            .onEnter(ctx -> {
                List<License> newValues = License.REGISTRY.values()
                    .stream()
                    .sorted(Comparator.comparing(License::getName))
                    .toList();

                if (availableLicenses.size() != newValues.size() || !ListUtils.isEqualList(availableLicenses, newValues)) {
                    availableLicenses.clear();
                    availableLicenses.addAll(newValues);
                    ctx.markForRefresh(ProjectData.DefaultKeys.LICENSE);
                }
            })
            .build();
    }

    protected OnboardingStep createGitStep() {
        // TODO: Provide options for GitHub, GitLab, Bitbucket initialization (with protected/public options)
        return OnboardingFormStep.builder()
            .id("git")
            .title("railroad.project.creation.git.title")
            .description("railroad.project.creation.git.description")
            .appendSection("railroad.project.creation.section.git",
                described(
                    FormComponent.checkBox(ProjectData.DefaultKeys.INIT_GIT, "railroad.project.creation.init_git")
                        .selected(true),
                    "railroad.project.creation.init_git.info"))
            .build();
    }

    protected OnboardingStep createOptionalDetailsStep() {
        String configuredAuthor = SettingsHandler.getValue(Settings.DEFAULT_PROJECT_AUTHOR);
        String defaultAuthor = !isNullOrBlank(configuredAuthor)
            ? configuredAuthor
            : Optional.ofNullable(System.getProperty("user.name"))
            .filter(name -> !isNullOrBlank(name))
            .orElse("");

        return OnboardingFormStep.builder()
            .id("optional_details")
            .title("railroad.project.creation.optional_details.title")
            .description("railroad.project.creation.optional_details.description")
            .appendSection("railroad.project.creation.section.optional_details",
                described(
                    FormComponent.textField(ProjectData.DefaultKeys.AUTHOR, "railroad.project.creation.author")
                        .text(() -> defaultAuthor)
                        .promptText("railroad.project.creation.author.prompt")
                        .validator(ProjectValidators::validateAuthor),
                    "railroad.project.creation.author.info"),
                described(
                    FormComponent.textArea(ProjectData.DefaultKeys.DESCRIPTION, "railroad.project.creation.description")
                        .promptText("railroad.project.creation.description.prompt")
                        .validator(ProjectValidators::validateDescription),
                    "railroad.project.creation.description.info"),
                described(
                    FormComponent.textField(ProjectData.DefaultKeys.ISSUES_URL, "railroad.project.creation.issues_url")
                        .promptText("railroad.project.creation.issues_url.prompt")
                        .validator(ProjectValidators::validateIssues),
                    "railroad.project.creation.issues_url.info"),
                described(
                    FormComponent.textField(ProjectData.DefaultKeys.HOMEPAGE_URL, "railroad.project.creation.homepage_url")
                        .promptText("railroad.project.creation.homepage_url.prompt")
                        .validator(textField -> ProjectValidators.validateGenericUrl(textField, "homepage")),
                    "railroad.project.creation.homepage_url.info"),
                described(
                    FormComponent.textField(ProjectData.DefaultKeys.SOURCES_URL, "railroad.project.creation.sources_url")
                        .promptText("railroad.project.creation.sources_url.prompt")
                        .validator(textField -> ProjectValidators.validateGenericUrl(textField, "sources")),
                    "railroad.project.creation.sources_url.info"))
            .build();
    }

    protected static OnboardingFormStep.ComponentSpec described(FormComponentBuilder<?, ?, ?, ?> builder, String descriptionKey) {
        return OnboardingFormStep.component(builder, createDescriptionCustomizer(descriptionKey));
    }

    protected static OnboardingFormStep.ComponentSpec described(FormComponentBuilder<?, ?, ?, ?> builder, Function<Object, Object> transformer, Function<Object, Object> reverseTransformer, String descriptionKey) {
        return OnboardingFormStep.component(builder, builder != null ? builder.dataKey() : null, transformer, reverseTransformer, createDescriptionCustomizer(descriptionKey));
    }

    protected static Consumer<FormComponent<?, ?, ?, ?>> createDescriptionCustomizer(String descriptionKey) {
        if (isNullOrBlank(descriptionKey))
            return null;

        return component -> attachDescription(component, descriptionKey);
    }

    protected static void attachDescription(FormComponent<?, ?, ?, ?> component, String descriptionKey) {
        if (component == null || isNullOrBlank(descriptionKey))
            return;

        Consumer<Node> applyToNode = node -> {
            if (node instanceof InformativeLabeledHBox<?> informative) {
                boolean exists = informative.getInformationLabels().stream()
                    .anyMatch(label -> descriptionKey.equals(label.getKey()));
                if (!exists) {
                    informative.addInformationLabel(descriptionKey);
                }
            }
        };

        Node currentNode = component.componentProperty().get();
        if (currentNode != null) {
            applyToNode.accept(currentNode);
        }

        component.componentProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                applyToNode.accept(newValue);
            }
        });
    }

    protected static MinecraftVersion getMinecraftVersion(String string) {
        try {
            return SwitchboardRepositories.MINECRAFT.getVersionSync(string).orElse(null);
        } catch (ExecutionException | InterruptedException exception) {
            Railroad.LOGGER.error("Failed to fetch Minecraft version {}", string, exception);
            return null;
        }
    }

    protected static boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }

    protected static void bindTextField(StringProperty valueProperty, ObjectProperty<TextField> fieldProperty) {
        Objects.requireNonNull(valueProperty, "valueProperty");
        Objects.requireNonNull(fieldProperty, "fieldProperty");

        valueProperty.addListener((obs, oldValue, newValue) -> {
            TextField field = fieldProperty.get();
            if (field != null && !Objects.equals(field.getText(), newValue)) {
                field.setText(newValue);
            }
        });

        fieldProperty.addListener((obs, oldField, newField) -> {
            if (newField == null)
                return;

            if (!Objects.equals(newField.getText(), valueProperty.get())) {
                newField.setText(valueProperty.get());
            }

            newField.textProperty().addListener((textObs, oldText, newText) -> {
                if (!Objects.equals(valueProperty.get(), newText)) {
                    valueProperty.set(newText);
                }
            });
        });
    }
}
