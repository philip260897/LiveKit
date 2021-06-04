package at.livekit.modules;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Listener;
import org.json.JSONObject;

import at.livekit.api.economy.TransactionResult;
import at.livekit.livekit.Economy;
import at.livekit.livekit.Identity;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;

public class EconomyModule extends BaseModule implements Listener {


    public EconomyModule(ModuleListener listener) {
        super(1, "Economy", "livekit.module.economy", UpdateRate.NEVER, listener);
    }

    @Override
    public void onEnable(Map<String,ActionMethod> signature) {
        if(!Economy.getInstance().isAvailable()) return;

        super.onEnable(signature);
    }

    @Override
    public void onDisable(Map<String,ActionMethod> signature) {
        super.onDisable(signature);
    }

    @Action(name = "GetBalance")
    protected IPacket actionGetBalance(Identity identity, ActionPacket packet) {
        try{
            Economy economy = Economy.getInstance();
            if(economy.isAvailable()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(identity.getUuid()));

                JSONObject json = new JSONObject();
                json.put("balance", economy.getBalance(player));
                return new StatusPacket(1, json);
            }
        }catch(Exception ex){ex.printStackTrace();}

        return new StatusPacket(0);
    }

    @Action(name = "Transfer")
    protected IPacket actionTransfer(Identity identity, ActionPacket packet) {
        String targetUUID = packet.getData().getString("target");
        double amount = packet.getData().getDouble("amount");

        try{
            Economy economy = Economy.getInstance();
            if(economy.isAvailable()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(identity.getUuid()));
                OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(targetUUID));

                TransactionResult result = economy.transfer(player, target, amount);
                if(result == TransactionResult.SUCCESS) return new StatusPacket(1);

                return new StatusPacket(0, "Transaction failed: "+result.name());
            }
        }catch(Exception ex){ex.printStackTrace();}

        return new StatusPacket(0, "An error occured");
    }
}
