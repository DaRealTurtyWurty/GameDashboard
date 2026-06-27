package dev.turtywurty.gamedashboard.data.game;

import java.io.IOException;

public sealed interface LaunchTarget permits ExecutableLaunchTarget, UriLaunchTarget, WindowsAppLaunchTarget {
    Process launch() throws IOException;
}
