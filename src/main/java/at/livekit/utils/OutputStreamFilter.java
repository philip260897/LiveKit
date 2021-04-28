package at.livekit.utils;

import java.io.OutputStream;
import java.io.PrintStream;

import at.livekit.livekit.LiveKit;
import at.livekit.modules.ConsoleModule;

public class OutputStreamFilter extends PrintStream 
{   
    private ConsoleModule module;

    int counter = 0;

    private static OutputStreamFilter instance;
    public static OutputStreamFilter getInstance(OutputStream out) {
        if(instance == null) {
            OutputStreamFilter.instance = new OutputStreamFilter(out);
        }
        return instance;
    }

    public OutputStreamFilter(OutputStream out) {
        super(out);
        module = (ConsoleModule) LiveKit.getInstance().getModuleManager().getModule("ConsoleModule");
    }


    @Override
    public void println(String string) {
        super.println( (counter++) + " " + string);

        if(module != null) {
           //module.addEntry(string);
        }
    }
}
