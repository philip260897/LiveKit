package at.livekit.livekit;

import org.bukkit.OfflinePlayer;

import at.livekit.api.economy.IEconomyAdapter;
import at.livekit.api.economy.TransactionResult;

public class Economy {
    
    private IEconomyAdapter adapter;
    
    private static Economy instance;
    public static Economy getInstance() {
        if(instance == null) {
            instance = new Economy();
        }
        return instance;
    }

    protected void setEconomyAdapter(IEconomyAdapter adapter) {
        this.adapter = adapter;
    }

    public boolean isAvailable() {
        return this.adapter != null;
    }

    public boolean hasEnough(OfflinePlayer player, double amount) throws EconomyNotAvailableException {
        if(!isAvailable()) throw new EconomyNotAvailableException();
        return this.adapter.hasEnough(player, amount);
    }

    public double getBalance(OfflinePlayer player) throws EconomyNotAvailableException {
        if(!isAvailable()) throw new EconomyNotAvailableException();
        return this.adapter.getBalance(player);
    }

    public String getBalanceFormatted(OfflinePlayer player) throws EconomyNotAvailableException {
        if(!isAvailable()) throw new EconomyNotAvailableException();
        return this.adapter.getBalanceFormatted(player);
    }

    public TransactionResult transfer(OfflinePlayer from, OfflinePlayer to, double amount) throws EconomyNotAvailableException {
        if(!isAvailable()) throw new EconomyNotAvailableException();
        return this.adapter.transfer(from, to, amount);
    }

    public TransactionResult giveAmount(OfflinePlayer to, double amount) throws EconomyNotAvailableException {
        if(!isAvailable()) throw new EconomyNotAvailableException();
        return this.adapter.giveAmount(to, amount);
    }

    public TransactionResult withdrawAmount(OfflinePlayer from, double amount) throws EconomyNotAvailableException {
        if(!isAvailable()) throw new EconomyNotAvailableException();
        return this.adapter.withdrawAmount(from, amount);
    }

    public String getCurrencyString() throws EconomyNotAvailableException {
        if(!isAvailable()) throw new EconomyNotAvailableException();
        return this.adapter.getCurrencyString();
    }

    public static class EconomyNotAvailableException extends Exception {
        public EconomyNotAvailableException() {
            super("No Economy Plugin has been initialized");
        }
    }
}
