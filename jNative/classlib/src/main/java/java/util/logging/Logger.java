package java.util.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Logger {
    private static final Map<String, Logger> LOGGERS = new HashMap<String, Logger>();
    private final String name;

    private Logger(String name) {
        this.name = name;
    }

    public static synchronized Logger getLogger(String name) {
        Objects.requireNonNull(name);
        Logger logger = LOGGERS.get(name);
        if(logger == null) {
            logger = new Logger(name);
            LOGGERS.put(name, logger);
        }
        return logger;
    }

    public void log(Level level, String message, Throwable error) {
        if(level.intValue() < Level.INFO.intValue()) return;
        System.err.println("[" + level + "] " + name + ": " + message);
        if(error != null) error.printStackTrace(System.err);
    }

    public void log(Level level, String message) {
        log(level, message, null);
    }
}
