package dev.turtywurty.gamedashboard.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ANSIConstants;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

public final class DarkTerminalLevelConverter extends ForegroundCompositeConverterBase<ILoggingEvent> {
    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        return switch (event.getLevel().toInt()) {
            case Level.ERROR_INT -> ANSIConstants.BOLD + ANSIConstants.RED_FG;
            case Level.WARN_INT -> ANSIConstants.BOLD + ANSIConstants.YELLOW_FG;
            case Level.INFO_INT -> ANSIConstants.BOLD + ANSIConstants.GREEN_FG;
            case Level.DEBUG_INT -> ANSIConstants.BOLD + ANSIConstants.CYAN_FG;
            default -> ANSIConstants.BOLD + ANSIConstants.WHITE_FG;
        };
    }
}
