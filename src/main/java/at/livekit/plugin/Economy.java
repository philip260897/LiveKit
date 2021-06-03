package at.livekit.plugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import at.livekit.api.economy.IEconomyAdapter;

public class Economy {
    
    private static IEconomyAdapter adapter;
    private static boolean available = false;

    protected void initializeDefault() {
        if(Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }

        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        net.milkbowl.vault.economy.Economy econ = rsp.getProvider();
        
        if(econ == null) {
            return;
        }

        Plugin.log("Found Vault! Using economy ["+econ.getName()+"]");

        
    }

    public void setEconomyAdapter(IEconomyAdapter adapter) {
        Economy.adapter = adapter;
    }

    public boolean isAvailable() {
        return available;
    }

}
