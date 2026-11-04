package com.frescuratotal.erp.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Central file logger. Log files are written outside source control. */
public final class AppLogger {

    private static final Logger LOGGER = createLogger();

    private AppLogger() {
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void error(String message, Exception exception) {
        LOGGER.log(Level.SEVERE, message, exception);
    }

    private static Logger createLogger() {
        Logger logger = Logger.getLogger("com.frescuratotal.erp");
        logger.setUseParentHandlers(false);
        try {
            Path logDirectory = Path.of("logs");
            Files.createDirectories(logDirectory);
            FileHandler handler = new FileHandler(logDirectory.resolve("frescura-total-%g.log").toString(),
                    1_000_000, 3, true);
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
        } catch (IOException | SecurityException exception) {
            System.err.println("Could not initialize file logging: " + exception.getMessage());
            logger.setUseParentHandlers(true);
        }
        return logger;
    }
}
