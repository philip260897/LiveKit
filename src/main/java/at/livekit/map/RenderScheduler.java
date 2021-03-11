package at.livekit.map;

import java.util.ArrayList;
import java.util.List;

import at.livekit.modules.LiveMapModule;

public class RenderScheduler 
{
    private static int cpuTime = 20;
    private static int totalWorkers = 0;
    //private static int workCount = 0;
    
    private static List<LiveMapModule> workers = new ArrayList<LiveMapModule>();

    public static int getCPUTime() {
        return cpuTime;
    }

    public static void setCPUTime(int cpu) {
        cpuTime = cpu;
        if(cpuTime > 50) cpuTime = 50;
        if(cpuTime < 5) cpuTime = 5;
    }

    public static void setTotalWorkers(int total) {
        totalWorkers = total;
        if(totalWorkers < 0) totalWorkers = 0;
    }

    public static int getTotalWorkers() {
        return totalWorkers;
    }

    public static void registerWork(LiveMapModule module) {
        if(!workers.contains(module)) workers.add(module);
    }

    public static void unregisterWork(LiveMapModule module) {
        if(workers.contains(module)) workers.remove(module);
    }

    public static int getTimeAllocation(LiveMapModule module) {
        if(workers.contains(module)) {
            return (cpuTime / workers.size());
        }
        return (workers.size() == 0 ? cpuTime / totalWorkers : 1);
    }
}
