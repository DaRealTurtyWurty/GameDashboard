package dev.turtywurty.gamedashboard.platform;

import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import javafx.scene.Node;
import javafx.scene.image.Image;

import java.util.function.Consumer;

public interface Platform {
    Image getIcon();

    String getName();

    String getWebsite();

    String getDescription();

    Consumer<ProgressMonitor> performDiscovery();

    ManualEntryView createManualEntryView();

    record ManualEntryView(Node content, Consumer<ProgressMonitor> saveAction) {
    }
}
