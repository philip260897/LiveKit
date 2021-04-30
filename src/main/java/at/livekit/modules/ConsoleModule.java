package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.core.LogEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.packets.IPacket;

public class ConsoleModule extends BaseModule 
{
    public static int BACKLOG_COUNT = 1000;

    private List<LogEvent> _history = new ArrayList<LogEvent>();
    private List<LogEvent> _logs = new ArrayList<LogEvent>();

    public ConsoleModule(ModuleListener listener) {
        super(1, "Console", "livekit.module.console", UpdateRate.NEVER, listener);
    }

    public void addEntry(LogEvent entry) {
        synchronized(_logs) {
            _logs.add(entry);
        }

        notifyChange();
    }

    public List<LogEvent> getUnsent() {
        List<LogEvent> unsent = new ArrayList<LogEvent>();
        synchronized(_logs) {
            unsent.addAll(_logs);
            _logs.clear();
        }
        return unsent;
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject data = new JSONObject();
        JSONArray w = new JSONArray();
        data.put("log", w);

        synchronized(_history) {
            for(LogEvent s : _history){
                JSONObject entry = new JSONObject();
                entry.put("level", s.getLevel().name());
                entry.put("timestamp", s.getTimeMillis());
                entry.put("sender", s.getLoggerName());
                entry.put("message", s.getMessage().getFormattedMessage());
                w.put(entry);
            }
        }

        return new ModuleUpdatePacket(this, data, true);
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity, IPacket> response = new HashMap<Identity, IPacket>();
        
        JSONObject data = new JSONObject();
        JSONArray w = new JSONArray();
        data.put("log", w);

        synchronized(_logs) {
            for(LogEvent s : _logs) {
                JSONObject entry = new JSONObject();
                entry.put("level", s.getLevel().name());
                entry.put("timestamp", s.getTimeMillis());
                entry.put("sender", s.getLoggerName());
                entry.put("message", s.getMessage().getFormattedMessage());
                w.put(entry);
            }
            
            synchronized(_history) {
                _history.addAll(_logs);
                while(_history.size() > BACKLOG_COUNT) {
                    _history.remove(0);
                }
            }

            _logs.clear();
        }
        
        for(Identity identity : identities) {
            response.put(identity, new ModuleUpdatePacket(this, data, false));
        }
        
        return response;
    }
    
}
