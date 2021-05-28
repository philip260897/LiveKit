package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Plugin;

public class InventoryModule extends BaseModule implements Listener
{
    private HashMap<Identity, UUID> _inventorySubs = new HashMap<Identity, UUID>();
    private HashMap<UUID, JSONObject> _inventoryUpdates = new HashMap<>();

    private List<Player> _queue = new ArrayList<Player>();

    public InventoryModule(ModuleListener listener) {
        super(1, "Inventories", "livekit.module.inventory", UpdateRate.HIGH, listener);
    } 

    @Override
    public void update() {
        synchronized(_queue) {
            if(_queue.size() != 0) {
                for(int i = 0; i < _queue.size(); i++) {
                    updateInventory(_queue.get(i));
                }
                _queue.clear();
            }
        }
        
        super.update();
    }

    @Override
    public void onEnable(Map<String,ActionMethod> signature) {
        super.onEnable(signature);

        Bukkit.getServer().getPluginManager().registerEvents(this, Plugin.getInstance());
    }

    @Override
    public void onDisable(Map<String,ActionMethod> signature) {
        synchronized(_inventorySubs) {
             _inventorySubs.clear();
        }
        synchronized(_inventoryUpdates) {
            _inventoryUpdates.clear();
        }
        super.onDisable(signature);
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        return super.onJoinAsync(identity);
    }

    @Override
    public void onDisconnectAsync(Identity identity) {
        super.onDisconnectAsync(identity); 
        synchronized(_inventorySubs) {
            if(_inventorySubs.containsKey(identity)) {
                _inventorySubs.remove(identity);
            }
        }
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity, IPacket> responses = new HashMap<Identity, IPacket>();

        synchronized(_inventorySubs) {
            synchronized(_inventoryUpdates) {
                for(Identity identity : identities) {
                    if(_inventorySubs.containsKey(identity)) {
                        UUID target = _inventorySubs.get(identity);
                        if(_inventoryUpdates.containsKey(target)) {
                            responses.put(identity, new ModuleUpdatePacket(this, _inventoryUpdates.get(target), false));
                        }
                    }
                }

                _inventoryUpdates.clear();
            }
        }

        return responses;
    }

    public void updateInventory(Player player) {
        if(!player.isOnline()) return;

        JSONObject inventory = new JSONObject();
        JSONArray storage = new JSONArray();
        //JSONArray armor = new JSONArray();
        //JSONArray extra = new JSONArray();
        for(int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if(stack != null && stack.getAmount() != 0) {
                JSONObject entry = new JSONObject();
                entry.put("i", stack.getType().name());
                entry.put("a", stack.getAmount());
                if(stack.getItemMeta() instanceof Damageable) {
                    entry.put("d", ((Damageable)stack.getItemMeta()).getDamage());
                }
                entry.put("s", i);
                storage.put(entry);
            }
            
            //player.getInventory().setChest
        }
        inventory.put("activeSlot", player.getInventory().getHeldItemSlot());
        inventory.put("storage", storage);
        inventory.put("uuid", player.getUniqueId().toString());
        //inventory.put("extra", extra);
        //inventory.put("armor", armor);

        synchronized(_inventoryUpdates) {
            _inventoryUpdates.put(player.getUniqueId(), inventory);
        }
        notifyChange();
    }

    @Action(name="OpenInventory")
    protected IPacket actionOpenInventory(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        
        if(!uuid.equals(identity.getUuid()) || !identity.hasPermission("livekit.module.admin")) return new StatusPacket(0, "Permission denied!");
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        if(target == null || !target.isOnline()) return new StatusPacket(0, "Player is offline");

        synchronized(_inventorySubs) {
            _inventorySubs.put(identity, target.getUniqueId());
        }
        updateInventory(target.getPlayer());
        
        return new StatusPacket(1);
    }

    @Action(name="CloseInventory")
    protected IPacket actionCloseInventory(Identity identity, ActionPacket packet) {
        synchronized(_inventorySubs) {
            if(_inventorySubs.containsKey(identity)) {
                _inventorySubs.remove(identity);
            }
        }
        return new StatusPacket(1);
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClickEvent(InventoryClickEvent event) {
        if(event.getInventory().getHolder() instanceof Player) {
            Player player = (Player) event.getInventory().getHolder();
            queuePlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDragEvent(InventoryDragEvent event) {
        if(event.getInventory().getHolder() instanceof Player) {
            Player player = (Player) event.getInventory().getHolder();
            queuePlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityPickupItemEvent(EntityPickupItemEvent event) {
        if(event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            queuePlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDropItemEvent(PlayerDropItemEvent event) {
        queuePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawnEvent(PlayerRespawnEvent event) {
        queuePlayer((Player)event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeldEvent(PlayerItemHeldEvent event) {
        queuePlayer(event.getPlayer());
    }

    private void queuePlayer(Player player) {
        synchronized(_inventorySubs) {
            if(_inventorySubs.containsValue(player.getUniqueId())) {
                synchronized(_queue) {
                    _queue.add(player);
                }
            }
        }
    }


}
