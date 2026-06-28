package dev.turtywurty.gamedashboard.view.onboarding;

import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.platform.Platforms;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import dev.turtywurty.gamedashboard.util.Utils;
import dev.turtywurty.gamedashboard.util.WebUtils;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class PlatformsPage extends VBox {
    public PlatformsPage() {
        getStyleClass().add("platforms-page");

        var eyebrow = new Label("PLATFORMS");
        eyebrow.getStyleClass().add("onboarding-eyebrow");

        var title = new Label("Select Your Platforms");
        title.getStyleClass().add("platforms-title");
        title.setWrapText(true);

        var description = new Label(
                "Connect the platforms you want Game Dashboard to manage. You can update these settings later."
        );
        description.getStyleClass().add("platforms-description");
        description.setWrapText(true);

        var platformsGrid = new FlowPane();
        platformsGrid.getStyleClass().add("platforms-grid");
        platformsGrid.setPrefWrapLength(760);

        var scrollPane = new ScrollPane(platformsGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("platforms-scroll-pane");
        scrollPane.setContent(platformsGrid);

        Platforms.getPlatforms().forEach(platform -> {
            var platformTile = new PlatformTile(platform);
            platformTile.prefWidthProperty().bind(platformsGrid.widthProperty().subtract(12));
            platformsGrid.getChildren().add(platformTile);
        });

        getChildren().addAll(eyebrow, title, description, scrollPane);
    }

    public static class PlatformTile extends VBox {
        public PlatformTile(Platform platform) {
            getStyleClass().add("platform-tile");

            var nameLabel = new Label(platform.getName());
            nameLabel.getStyleClass().add("platform-name");

            var descriptionLabel = new Label(platform.getDescription());
            descriptionLabel.getStyleClass().add("platform-description");
            descriptionLabel.setWrapText(true);

            var platformDetails = new VBox(nameLabel, descriptionLabel);
            platformDetails.getStyleClass().add("platform-details");
            HBox.setHgrow(platformDetails, Priority.ALWAYS);

            Image icon = platform.getIcon();
            var platformHeader = new HBox();
            platformHeader.getStyleClass().add("platform-header");
            if (icon != null) {
                var iconView = new ImageView(icon);
                iconView.getStyleClass().add("platform-icon");
                iconView.setFitWidth(64);
                iconView.setFitHeight(64);
                iconView.setPreserveRatio(true);
                platformHeader.getChildren().add(iconView);
            }
            platformHeader.getChildren().add(platformDetails);

            var websiteButton = new Button("Visit Website");
            websiteButton.getStyleClass().addAll("secondary-button", "platform-website-button");
            websiteButton.setOnAction(event -> WebUtils.openWebpage(platform.getWebsite()));

            var startDiscoveryButton = new Button("Start Discovery");
            startDiscoveryButton.getStyleClass().addAll("primary-button", "platform-discovery-button");

            var discoveryTaskLabel = new Label();
            discoveryTaskLabel.getStyleClass().add("platform-discovery-task");
            discoveryTaskLabel.setWrapText(true);

            var discoveryProgressBar = new ProgressBar(0);
            discoveryProgressBar.getStyleClass().add("platform-discovery-progress");
            discoveryProgressBar.setMinHeight(12);
            discoveryProgressBar.setPrefHeight(12);
            discoveryProgressBar.setMaxHeight(12);
            discoveryProgressBar.setMaxWidth(Double.MAX_VALUE);

            var discoveryStatusLabel = new Label();
            discoveryStatusLabel.getStyleClass().add("platform-discovery-status");

            var discoveryProgress = new VBox(
                    discoveryTaskLabel,
                    discoveryProgressBar,
                    discoveryStatusLabel
            );
            discoveryProgress.getStyleClass().add("platform-discovery-monitor");
            discoveryProgress.setManaged(false);
            discoveryProgress.setVisible(false);

            startDiscoveryButton.setOnAction(event -> {
                Consumer<ProgressMonitor> progressMonitorConsumer = platform.performDiscovery();
                if (progressMonitorConsumer != null) {
                    startDiscoveryButton.setDisable(true);
                    discoveryTaskLabel.setText("Starting discovery...");
                    discoveryStatusLabel.setText("");
                    discoveryProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                    discoveryProgress.setManaged(true);
                    discoveryProgress.setVisible(true);

                    var totalWorkCounter = new AtomicInteger();
                    var completedWork = new AtomicInteger();
                    ProgressMonitor progressMonitor = new ProgressMonitor() {
                        @Override
                        public void start(String taskName, int totalWork) {
                            int normalizedTotalWork = Math.max(totalWork, 0);
                            totalWorkCounter.set(normalizedTotalWork);
                            completedWork.set(0);
                            Utils.runOnFxThread(() -> {
                                discoveryTaskLabel.setText(taskName == null || taskName.isBlank()
                                        ? "Discovering " + platform.getName()
                                        : taskName);
                                discoveryProgressBar.setProgress(normalizedTotalWork > 0
                                        ? 0
                                        : ProgressBar.INDETERMINATE_PROGRESS);
                                discoveryStatusLabel.setText(normalizedTotalWork > 0 ? "0%" : "Working...");
                            });
                        }

                        @Override
                        public void worked(int work) {
                            int completed = completedWork.addAndGet(Math.max(work, 0));
                            int total = totalWorkCounter.get();
                            Utils.runOnFxThread(() -> {
                                if (total > 0) {
                                    double progress = Math.min(1, (double) completed / total);
                                    discoveryProgressBar.setProgress(progress);
                                    discoveryStatusLabel.setText(Math.round(progress * 100) + "%");
                                }
                            });
                        }

                        @Override
                        public void done() {
                            Utils.runOnFxThread(() -> {
                                discoveryProgressBar.setProgress(1);
                                discoveryStatusLabel.setText("Discovery complete");
                                startDiscoveryButton.setDisable(false);
                            });
                        }
                    };

                    CompletableFuture.runAsync(() -> progressMonitorConsumer.accept(progressMonitor))
                            .exceptionally(throwable -> {
                                Utils.runOnFxThread(() -> {
                                    discoveryProgressBar.setProgress(0);
                                    discoveryStatusLabel.setText("Discovery failed");
                                    startDiscoveryButton.setDisable(false);
                                });
                                return null;
                            });
                }
            });

            var advancedSetupButton = new Button("Advanced Setup");
            advancedSetupButton.getStyleClass().addAll("secondary-button", "platform-advanced-setup-button");
            advancedSetupButton.setOnAction(event -> {
                ProgressMonitor progressMonitor = createProgressMonitor(
                        platform,
                        discoveryProgress,
                        discoveryTaskLabel,
                        discoveryProgressBar,
                        discoveryStatusLabel,
                        advancedSetupButton,
                        "Setup complete"
                );
                showAdvancedSetupDialog(platform, progressMonitor);
            });

            var actions = new FlowPane(websiteButton, advancedSetupButton, startDiscoveryButton);
            actions.getStyleClass().add("platform-actions");

            getChildren().addAll(platformHeader, discoveryProgress, actions);
        }

        private ProgressMonitor createProgressMonitor(
                Platform platform,
                VBox progressView,
                Label taskLabel,
                ProgressBar progressBar,
                Label statusLabel,
                Button actionButton,
                String completionText
        ) {
            var totalWorkCounter = new AtomicInteger();
            var completedWork = new AtomicInteger();

            return new ProgressMonitor() {
                @Override
                public void start(String taskName, int totalWork) {
                    int normalizedTotalWork = Math.max(totalWork, 0);
                    totalWorkCounter.set(normalizedTotalWork);
                    completedWork.set(0);
                    Utils.runOnFxThread(() -> {
                        actionButton.setDisable(true);
                        progressView.setManaged(true);
                        progressView.setVisible(true);
                        taskLabel.setText(taskName == null || taskName.isBlank()
                                ? "Configuring " + platform.getName()
                                : taskName);
                        progressBar.setProgress(normalizedTotalWork > 0
                                ? 0
                                : ProgressBar.INDETERMINATE_PROGRESS);
                        statusLabel.setText(normalizedTotalWork > 0 ? "0%" : "Working...");
                    });
                }

                @Override
                public void worked(int work) {
                    int completed = completedWork.addAndGet(Math.max(work, 0));
                    int total = totalWorkCounter.get();
                    Utils.runOnFxThread(() -> {
                        if (total > 0) {
                            double progress = Math.min(1, (double) completed / total);
                            progressBar.setProgress(progress);
                            statusLabel.setText(Math.round(progress * 100) + "%");
                        }
                    });
                }

                @Override
                public void done() {
                    Utils.runOnFxThread(() -> {
                        progressBar.setProgress(1);
                        statusLabel.setText(completionText);
                        actionButton.setDisable(false);
                    });
                }
            };
        }

        private void showAdvancedSetupDialog(Platform platform, ProgressMonitor progressMonitor) {
            var dialog = new Dialog<Void>();
            dialog.setTitle(platform.getName() + " Advanced Setup");
            dialog.initOwner(getScene().getWindow());

            Platform.ManualEntryView manualEntryView = platform.createManualEntryView();
            if (manualEntryView == null)
                return;

            var dialogPane = dialog.getDialogPane();
            dialogPane.getStyleClass().add("platform-advanced-dialog");
            dialogPane.setHeaderText(platform.getName() + " Advanced Setup");
            dialogPane.setContent(manualEntryView.content());

            var cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            var saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            dialogPane.getButtonTypes().addAll(cancelButtonType, saveButtonType);

            var saveButton = (Button) dialogPane.lookupButton(saveButtonType);
            saveButton.getStyleClass().add("primary-button");
            saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                event.consume();
                manualEntryView.saveAction().accept(new ProgressMonitor() {
                    @Override
                    public void start(String taskName, int totalWork) {
                        progressMonitor.start(taskName, totalWork);
                        Utils.runOnFxThread(() -> saveButton.setDisable(true));
                    }

                    @Override
                    public void worked(int work) {
                        progressMonitor.worked(work);
                    }

                    @Override
                    public void done() {
                        progressMonitor.done();
                        Utils.runOnFxThread(dialog::close);
                    }
                });
            });
            dialog.showAndWait();
        }
    }

}
