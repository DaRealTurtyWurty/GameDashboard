package dev.turtywurty.gamedashboard.platform;

import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import javafx.scene.Node;
import javafx.scene.image.Image;

import java.util.function.Consumer;

public interface Platform {
    public Image getIcon();

    public String getName();

    public String getWebsite();

    public String getDescription();

    public Consumer<ProgressMonitor> performDiscovery();

    public ManualEntryView createManualEntryView();

    public record ManualEntryView(Node content, Consumer<ProgressMonitor> saveAction) {
    }
}
