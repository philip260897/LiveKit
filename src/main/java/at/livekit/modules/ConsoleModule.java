package at.livekit.modules;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.core.LogEvent;
import org.bukkit.Bukkit;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.modules.BaseModule.Action;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;

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
        synchronized(_history) {
            unsent.addAll(_history);
            _history.clear();
        }
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
        data.put("canExecute", identity.hasPermission("livekit.console.execute"));

        synchronized(_history) {
            for(LogEvent s : _history){
                w.put(toJson(s));
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
                w.put(toJson(s));
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

    @Action(name = "ExecuteCommand")
    public IPacket actionExecute(Identity identity, ActionPacket packet) {
        String command = packet.getData().getString("command");
        if(!identity.hasPermission("livekit.console.execute")) return new StatusPacket(0, "Permission denied");
        Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
        return new StatusPacket(1);
    }

    private JSONObject toJson(LogEvent s) {
        JSONObject entry = new JSONObject();
        entry.put("level", s.getLevel().name());
        entry.put("timestamp", s.getTimeMillis());
        entry.put("sender", s.getLoggerName());
        entry.put("message", s.getMessage().getFormattedMessage());

        if(s.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            s.getThrown().printStackTrace(pw);
            entry.put("thrown", sw.toString());
        }

        return entry;
    }
    
}
