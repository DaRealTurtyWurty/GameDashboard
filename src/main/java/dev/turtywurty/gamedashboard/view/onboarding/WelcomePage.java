package dev.turtywurty.gamedashboard.view.onboarding;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

public class WelcomePage extends VBox {
    public WelcomePage() {
        getStyleClass().add("welcome-page");

        var logo = new ImageView(new Image(Objects.requireNonNull(
                getClass().getResource("/images/logo_transparent_bg.png")
        ).toExternalForm()));
        logo.getStyleClass().add("welcome-logo");
        logo.setFitWidth(88);
        logo.setFitHeight(88);
        logo.setPreserveRatio(true);

        var eyebrow = new Label("GAME DASHBOARD");
        eyebrow.getStyleClass().add("onboarding-eyebrow");

        var title = new Label("Your game library, all in one place");
        title.getStyleClass().add("welcome-title");
        title.setWrapText(true);

        var description = new Label(
                "A few quick steps will personalize your dashboard and connect the platforms you use."
        );
        description.getStyleClass().add("welcome-description");
        description.setWrapText(true);

        var libraryFeature = createFeature(
                "Unified library",
                "Browse installed games from supported platforms in one dashboard."
        );
        var setupFeature = createFeature(
                "Quick setup",
                "Automatically discover your platform configuration or enter it manually."
        );
        var features = new FlowPane(libraryFeature, setupFeature);
        features.getStyleClass().add("welcome-features");

        getChildren().addAll(logo, eyebrow, title, description, features);
    }

    private VBox createFeature(String titleText, String descriptionText) {
        var marker = new Label("\u2713");
        marker.getStyleClass().add("welcome-feature-marker");

        var title = new Label(titleText);
        title.getStyleClass().add("welcome-feature-title");

        var description = new Label(descriptionText);
        description.getStyleClass().add("welcome-feature-description");
        description.setWrapText(true);

        var feature = new VBox(marker, title, description);
        feature.getStyleClass().add("welcome-feature");
        return feature;
    }
}
