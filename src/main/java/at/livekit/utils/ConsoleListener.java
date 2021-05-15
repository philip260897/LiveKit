package at.livekit.utils;


import java.io.Console;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;

import at.livekit.livekit.LiveKit;
import at.livekit.modules.ConsoleModule;
import at.livekit.plugin.Config;

@Plugin(name = "LiveKitConsole", category = "Core", elementType = "appender", printObject = true)
public class ConsoleListener extends AbstractAppender {
    
    private static String APPENDER_NAME = "LiveKitConsole";
    private static ConsoleListener listener;

    public List<LogEvent> cache = new ArrayList<LogEvent>();
    public ConsoleModule module;

    public ConsoleListener(ConsoleModule module) {
        super(APPENDER_NAME, null, PatternLayout.newBuilder().withPattern("[%d{HH:mm:ss} %level]: %msg").build());
        this.module = module;
    }

    public void invalidateConsole() {
        module = null;
    }

    @Override
    public void append(LogEvent event) {
        if(module == null) {
            if(cache.size() >= ConsoleModule.BACKLOG_COUNT) return;
            cache.add(event.toImmutable());
        } else {
            module.addEntry(event.toImmutable());
        }
    } 

    public static void registerListener() {
        Logger logger = (Logger) LogManager.getRootLogger();
        
        List<LogEvent> startupEntries = new ArrayList<>();

        Appender previousAppender = logger.getAppenders().get(APPENDER_NAME);
        if(previousAppender != null) {
            try {
                List<LogEvent> cached = ConsoleListener.extractLogFromDeadObject(previousAppender);
                if(cached != null) {
                    startupEntries.addAll(cached);
                }
            }catch(Exception ex){ex.printStackTrace();}

            logger.removeAppender(previousAppender);
        }

        if(Config.moduleEnabled("ConsoleModule")) {
            ConsoleModule module = (ConsoleModule) LiveKit.getInstance().getModuleManager().getModule("ConsoleModule:default");
            if(module != null) {
                for(LogEvent entry : startupEntries) {
                    module.addEntry(entry);
                }
                listener = new ConsoleListener(module);
                listener.start();
                logger.addAppender(listener);
            }
        }
    }

    public static void unregisterListener() {
        if(listener != null) {
            listener.cache = listener.module.getUnsent();
            listener.invalidateConsole();
        }
    }

    private static List<LogEvent> extractLogFromDeadObject(Appender appender) throws Exception {
        if(appender.getClass().getSimpleName().equals(ConsoleListener.class.getSimpleName())) {
            List<LogEvent> events = (List<LogEvent>) appender.getClass().getField("cache").get(appender);
            return events;
        }
        return null;
    }

    /*public static class ConsoleEntry {
        private long timestamp;
        private String severity;
        private String message;

        public ConsoleEntry(long timestamp, String severity, String message) {
            this.timestamp = timestamp;
            this.severity = severity;
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public String getSeverity() {
            return severity;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }*/
}
