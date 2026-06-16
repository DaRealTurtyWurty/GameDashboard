package dev.turtywurty.gamedashboard.view.onboarding;

import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class AppearancePage extends VBox {
    public AppearancePage() {
        getStyleClass().add("appearance-page");

        var eyebrow = new Label("APPEARANCE");
        eyebrow.getStyleClass().add("onboarding-eyebrow");

        var title = new Label("Customize Appearance");
        title.getStyleClass().add("appearance-title");
        title.setWrapText(true);

        var description = new Label(
                "Game Dashboard currently uses a dark interface designed to keep your library readable and focused."
        );
        description.getStyleClass().add("appearance-description");
        description.setWrapText(true);

        var themeLabel = new Label("Theme");
        themeLabel.getStyleClass().add("appearance-option-label");

        var darkThemeOption = createThemeOption("Dark", "Current theme", "appearance-preview-dark", true);
        var lightThemeOption = createThemeOption("Light", "Coming soon", "appearance-preview-light", false);
        var systemThemeOption = createThemeOption("System", "Coming soon", "appearance-preview-system", false);

        var themeOptions = new FlowPane(darkThemeOption, lightThemeOption, systemThemeOption);
        themeOptions.getStyleClass().add("appearance-options");

        var note = new Label("More appearance controls can be added later without repeating setup.");
        note.getStyleClass().add("appearance-note");
        note.setWrapText(true);

        getChildren().addAll(eyebrow, title, description, themeLabel, themeOptions, note);
    }

    private VBox createThemeOption(String name, String status, String previewStyleClass, boolean selected) {
        var preview = new VBox();
        preview.getStyleClass().addAll("appearance-preview", previewStyleClass);

        var nameLabel = new Label(name);
        nameLabel.getStyleClass().add("appearance-option-name");

        var statusLabel = new Label(status);
        statusLabel.getStyleClass().add("appearance-option-status");

        var option = new VBox(preview, nameLabel, statusLabel);
        option.getStyleClass().add("appearance-option");
        option.setMaxWidth(Double.MAX_VALUE);
        option.getStyleClass().add(selected ? "selected" : "unavailable");
        return option;
    }
}
