package at.livekit.utils;


import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;

import at.livekit.livekit.LiveKit;
import at.livekit.modules.ConsoleModule;

@Plugin(name = "LiveKitConsole", category = "Core", elementType = "appender", printObject = true)
public class LogServerAppender extends AbstractAppender {

    public List<String> cache = new ArrayList<String>();
    private ConsoleModule module;

    public LogServerAppender() {
        super("LiveKitConsole", null, PatternLayout.newBuilder().withPattern("[%d{HH:mm:ss} %level]: %msg").build());
    }

    @Override
    public boolean isStarted() {
        return true;
    }

    public void invalidateConsole() {
        module = null;
    }

    public void setCache(List<String> entries) {
        this.cache = entries;
    }

    public void updateConsole() {
        module = (ConsoleModule) LiveKit.getInstance().getModuleManager().getModule("ConsoleModule");
        for(String event : cache) {
            //module.addEntry(event);
        }
        cache.clear();
    }

    @Override
    public void append(LogEvent e) {
        if(module == null && cache.size() < 1000) {
            cache.add(e.getMessage().getFormattedMessage());
        }

        if(module != null) {
            //module.addEntry(e.getMessage().getFormattedMessage());
        }
    }

    public static List<String> extractEvents(Object o) throws Exception{
        if(o.getClass().getSimpleName().equals(LogServerAppender.class.getSimpleName())) {
            List<String> events = (List<String>) o.getClass().getField("cache").get(o);
            return events;
        }
        return null;
    }
}
