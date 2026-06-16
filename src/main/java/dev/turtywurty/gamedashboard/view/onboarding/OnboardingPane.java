package dev.turtywurty.gamedashboard.view.onboarding;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

public class OnboardingPane extends BorderPane {
    private final List<Node> pages;
    private final ProgressBar progressBar = new ProgressBar();
    private final Label stepLabel = new Label();
    private final Button backButton = new Button("Back");
    private final Button continueButton = new Button("Continue");

    private int currentPageIndex = 0;

    public OnboardingPane(Runnable onComplete) {
        getStyleClass().add("onboarding-pane");
        this.pages = List.of(
                new WelcomePage(),
                new AppearancePage(),
                new PlatformsPage()/*,
                new ReviewPage()*/
        );

        progressBar.getStyleClass().add("onboarding-progress");
        progressBar.setMaxWidth(Double.MAX_VALUE);

        stepLabel.getStyleClass().add("onboarding-step-label");

        var progressHeader = new VBox(stepLabel, progressBar);
        progressHeader.getStyleClass().add("onboarding-progress-header");

        backButton.getStyleClass().addAll("secondary-button", "onboarding-button");
        backButton.setMinWidth(92);
        backButton.setOnAction(event -> showPage(currentPageIndex - 1));

        continueButton.getStyleClass().add("primary-button");
        continueButton.setMinWidth(112);
        continueButton.setDefaultButton(true);
        continueButton.setOnAction(event -> {
            if (currentPageIndex < pages.size() - 1) {
                showPage(currentPageIndex + 1);
            } else {
                onComplete.run();
            }
        });

        var actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        var actions = new HBox(10, backButton, actionSpacer, continueButton);
        actions.getStyleClass().add("onboarding-actions");

        var card = new BorderPane();
        card.getStyleClass().add("onboarding-card");
        card.setMaxWidth(920);
        card.setMaxHeight(680);
        card.setTop(progressHeader);
        card.setBottom(actions);
        setCenter(card);
        showPage(0);
    }

    private void showPage(int index) {
        if (index < 0 || index >= pages.size())
            return;

        currentPageIndex = index;
        progressBar.setProgress((double) (currentPageIndex + 1) / pages.size());
        stepLabel.setText("STEP " + (currentPageIndex + 1) + " OF " + pages.size());
        backButton.setDisable(currentPageIndex == 0);
        backButton.setVisible(currentPageIndex != 0);
        continueButton.setText(currentPageIndex == pages.size() - 1 ? "Finish Setup" : "Continue");
        ((BorderPane) getCenter()).setCenter(pages.get(currentPageIndex));
    }
}
