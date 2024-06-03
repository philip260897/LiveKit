package at.livekit.timings;

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

public class TimedRegisteredListener extends RegisteredListener {

    public TimedRegisteredListener(Listener listener, EventExecutor executor, EventPriority priority, Plugin plugin, boolean ignoreCancelled) {
        super(listener, executor, priority, plugin, ignoreCancelled);
    }

    @Override
    public void callEvent(org.bukkit.event.Event event) throws EventException {
        long start = System.nanoTime();
        super.callEvent(event);
        long end = System.nanoTime();

        double seconds = (end - start) / 1000000000.0;

        System.out.println(event.getEventName() + " took " + (end - start) + "ns (" + seconds + "s) to process.");
    }
    

    public static TimedRegisteredListener fromRegisteredListener(RegisteredListener listener) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException{
        Field executorField = RegisteredListener.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        EventExecutor executor = (EventExecutor) executorField.get(listener);
        return new TimedRegisteredListener(listener.getListener(), executor, listener.getPriority(), listener.getPlugin(), listener.isIgnoringCancelled());
    }

    public static void registerListeners() {
		try {
			Field allListField = HandlerList.class.getDeclaredField("allLists");
			allListField.setAccessible(true);
			ArrayList<HandlerList> allLists = (ArrayList<HandlerList>) allListField.get(Bukkit.getScheduler());
			for (HandlerList list : allLists) {
				RegisteredListener[] listeners = list.getRegisteredListeners();
				for(int i = 0; i < listeners.length; i++) {
					listeners[i] = TimedRegisteredListener.fromRegisteredListener(listeners[i]);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
}
