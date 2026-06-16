package dev.turtywurty.gamedashboard.util;

public interface ProgressMonitor {
    void start(String taskName, int totalWork);

    void worked(int work);

    void done();
}
