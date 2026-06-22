package dev.turtywurty.gamedashboard.platform;

import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ManualEntryForm {
    private final VBox content;
    private final Label errorLabel = new Label();
    private final String stylePrefix;
    private boolean built;

    public ManualEntryForm(
            String descriptionText,
            String stylePrefix,
            boolean useDescriptionHeader,
            double spacing
    ) {
        this.stylePrefix = stylePrefix;
        var description = new Label(descriptionText);
        description.getStyleClass().add("dialog-description");
        description.setWrapText(true);

        this.content = new VBox();
        if (hasStylePrefix())
            this.content.getStyleClass().add(stylePrefix + "-configuration-content");
        if (spacing > 0)
            this.content.setSpacing(spacing);

        if (useDescriptionHeader) {
            var header = new VBox(description);
            header.getStyleClass().add("dialog-header");
            this.content.getChildren().add(header);
        } else {
            this.content.getChildren().add(description);
        }

        this.errorLabel.getStyleClass().add("error-label");
        this.errorLabel.setWrapText(true);
        hideError();
    }

    public TextField addDirectoryField(
            String label,
            String initialValue,
            String promptText,
            String chooserTitle,
            boolean fallBackToHomeDirectory
    ) {
        var field = createField(initialValue, promptText);
        addPathCard(label, field, chooserTitle, () -> {
            var chooser = new DirectoryChooser();
            chooser.setTitle(chooserTitle);

            File current = field.getText().isBlank() ? null : new File(field.getText());
            File initialDirectory = current != null && current.isDirectory()
                    ? current
                    : fallBackToHomeDirectory ? new File(System.getProperty("user.home")) : null;
            if (initialDirectory != null && initialDirectory.isDirectory())
                chooser.setInitialDirectory(initialDirectory);

            File selected = chooser.showDialog(field.getScene().getWindow());
            if (selected != null) {
                field.setText(selected.getAbsolutePath());
                hideError();
            }
        });
        return field;
    }

    public TextField addFileField(
            String label,
            String initialValue,
            String promptText,
            String chooserTitle,
            FileChooser.ExtensionFilter filter
    ) {
        var field = createField(initialValue, promptText);
        addPathCard(label, field, chooserTitle, () -> {
            var chooser = new FileChooser();
            chooser.setTitle(chooserTitle);
            if (filter != null)
                chooser.getExtensionFilters().add(filter);

            File current = field.getText().isBlank() ? null : new File(field.getText());
            File initialDirectory = current != null && current.getParentFile() != null
                    ? current.getParentFile()
                    : new File(System.getProperty("user.home"));
            if (initialDirectory.isDirectory())
                chooser.setInitialDirectory(initialDirectory);

            File selected = chooser.showOpenDialog(field.getScene().getWindow());
            if (selected != null) {
                field.setText(selected.getAbsolutePath());
                hideError();
            }
        });
        return field;
    }

    public void showError(String message) {
        this.errorLabel.setText(message);
        this.errorLabel.setManaged(true);
        this.errorLabel.setVisible(true);
    }

    public void hideError() {
        this.errorLabel.setManaged(false);
        this.errorLabel.setVisible(false);
    }

    public boolean validate(TextField field, Predicate<String> validator, String errorMessage) {
        if (validator.test(field.getText()))
            return true;
        showError(errorMessage);
        return false;
    }

    public Optional<Path> readPath(TextField field, String errorMessage) {
        try {
            return Optional.of(Path.of(field.getText()));
        } catch (InvalidPathException exception) {
            showError(errorMessage);
            return Optional.empty();
        }
    }

    public Platform.ManualEntryView build(Consumer<ProgressMonitor> saveAction) {
        if (this.built)
            throw new IllegalStateException("Manual entry form has already been built");
        this.built = true;
        this.content.getChildren().add(this.errorLabel);
        return new Platform.ManualEntryView(this.content, saveAction);
    }

    public static void runAsync(ProgressMonitor progressMonitor, Runnable action) {
        CompletableFuture.runAsync(action)
                .exceptionally(throwable -> {
                    progressMonitor.done();
                    return null;
                });
    }

    private TextField createField(String initialValue, String promptText) {
        var field = new TextField(initialValue == null ? "" : initialValue);
        if (promptText != null)
            field.setPromptText(promptText);
        HBox.setHgrow(field, Priority.ALWAYS);
        return field;
    }

    private void addPathCard(
            String labelText,
            TextField field,
            String chooserTitle,
            Runnable browseAction
    ) {
        if (this.built)
            throw new IllegalStateException("Cannot add fields after the form has been built");

        var label = new Label(labelText);
        label.getStyleClass().add("field-label");

        var browseButton = new Button("Browse");
        browseButton.getStyleClass().add("browse-button");
        browseButton.setAccessibleText(chooserTitle);
        browseButton.setOnAction(event -> browseAction.run());

        var row = new HBox(field, browseButton);
        row.setAlignment(Pos.CENTER_LEFT);
        if (hasStylePrefix()) {
            row.getStyleClass().add(this.stylePrefix + "-location-row");
        } else {
            row.setSpacing(8);
        }

        var card = new VBox(label, row);
        if (hasStylePrefix())
            card.getStyleClass().add(this.stylePrefix + "-location-card");
        if (this.content.getChildren().size() == 1)
            VBox.setMargin(card, new Insets(8, 0, 0, 0));
        this.content.getChildren().add(card);
    }

    private boolean hasStylePrefix() {
        return this.stylePrefix != null && !this.stylePrefix.isBlank();
    }
}
