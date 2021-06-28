package at.livekit.utils;

import org.bukkit.OfflinePlayer;

import at.livekit.api.economy.IEconomyAdapter;
import at.livekit.api.economy.TransactionResult;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

public class VaultEconomyAdapter implements IEconomyAdapter {

    private Economy economy;
    public VaultEconomyAdapter(Economy economy) {
        this.economy = economy;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    @Override
    public String getBalanceFormatted(OfflinePlayer player) {
        return economy.format(getBalance(player));
    }

    @Override
    public String getCurrencyString() {
        return economy.currencyNameSingular();
    }

    @Override
    public TransactionResult giveAmount(OfflinePlayer from, double amount) {
        EconomyResponse response = economy.depositPlayer(from, amount);
        if(!response.transactionSuccess()) return TransactionResult.ERROR;
        return TransactionResult.SUCCESS;
    }

    @Override
    public TransactionResult withdrawAmount(OfflinePlayer from, double amount) {
        if(hasEnough(from, amount)) {
            EconomyResponse response = economy.withdrawPlayer(from, amount);
            if(response.transactionSuccess()) return TransactionResult.SUCCESS;
        } else {
            return TransactionResult.NOT_ENOUGH_FUNDS;
        }
        return TransactionResult.ERROR;
    }

    @Override
    public boolean hasEnough(OfflinePlayer player, double amount) {
        return economy.getBalance(player) >= amount;
    }

    @Override
    public TransactionResult transfer(OfflinePlayer from, OfflinePlayer to, double amount) {
        TransactionResult result = null;
        if((result = withdrawAmount(from, amount)) != TransactionResult.SUCCESS) return result;

        if(giveAmount(to, amount) != TransactionResult.SUCCESS) {
            giveAmount(from, amount);
            return TransactionResult.ERROR;
        }

        return TransactionResult.SUCCESS;
    }
    
}
