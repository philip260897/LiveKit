package at.livekit.utils;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class OutputStreamFilter extends PrintStream 
{    
    public OutputStreamFilter(OutputStream out) {
        super(out);
    }


    @Override
    public void println(String string) {
        if(!string.contains("==TEST==")) super.println(string);

        System.out.println("==TEST=="+string);
    }
}
