package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.livekit.LiveKit;
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
        super(1, "Inventories", "livekit.inventory", UpdateRate.HIGH, listener);
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
        if(!player.isOnline() || !isEnabled()) return;

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
                if((stack.getItemMeta() instanceof Damageable) && stack.getType().getMaxDurability() != 0) {
                    entry.put("d",(int) ((double)((Damageable)stack.getItemMeta()).getDamage() / (double)stack.getType().getMaxDurability() * 100.0));
                }
                entry.put("s", i);
                JSONArray ench = new JSONArray();
                for(Entry<Enchantment, Integer> enchantment : stack.getEnchantments().entrySet()) {
                    JSONObject obj = new JSONObject();
                    obj.put("k", enchantment.getKey().getKey().getKey());
                    obj.put("n", enchantment.getKey().getKey().getNamespace());
                    obj.put("l", enchantment.getValue());
                    ench.put(obj);
                }
                entry.put("e", ench);
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

    @Action(name="OpenInventory", permission = "livekit.inventory")
    protected IPacket actionOpenInventory(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        
        if(!uuid.equals(identity.getUuid()) && !identity.hasPermission("livekit.inventory.other")) return new StatusPacket(0, "Permission denied!");
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        if(target == null || !target.isOnline()) return new StatusPacket(0, "Player is offline");

        synchronized(_inventorySubs) {
            _inventorySubs.put(identity, target.getUniqueId());
        }
        updateInventory(target.getPlayer());
        
        return new StatusPacket(1);
    }

    @Action(name="CloseInventory", permission = "livekit.inventory")
    protected IPacket actionCloseInventory(Identity identity, ActionPacket packet) {
        synchronized(_inventorySubs) {
            if(_inventorySubs.containsKey(identity)) {
                _inventorySubs.remove(identity);
            }
        }
        return new StatusPacket(1);
    }

    @Action(name="ClearInventory", permission = "livekit.inventory.delete")
    protected IPacket actionClearInventory(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        
        OfflinePlayer offline = Bukkit.getServer().getOfflinePlayer(UUID.fromString(uuid));
        if(offline == null) return new StatusPacket(0, "Player not found!"); 

        offline.getPlayer().getInventory().clear();

        InventoryModule inventoryModule = (InventoryModule)LiveKit.getInstance().getModuleManager().getModule("InventoryModule");
        if(inventoryModule != null && inventoryModule.isEnabled()) {
            inventoryModule.updateInventory(offline.getPlayer());
        }

        return new StatusPacket(1);
    }

    @Action(name="RemoveItem", permission = "livekit.inventory.delete")
    protected IPacket actionRemoveItem(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        String material = packet.getData().getString("material");
        int amount = packet.getData().getInt("amount");
        int slot = packet.getData().getInt("slot");
        
        OfflinePlayer offline = Bukkit.getServer().getOfflinePlayer(UUID.fromString(uuid));
        if(offline == null) return new StatusPacket(0, "Player not found!"); 

        Player player = offline.getPlayer();
        PlayerInventory inventory = player.getInventory();
        
        ItemStack stack = inventory.getItem(slot);
        if(stack == null) return new StatusPacket(0, "Slot was empty");

        if(!stack.getType().name().equals(material) || stack.getAmount() != amount) return new StatusPacket(0, "ItemStack missmatch!");

        inventory.clear(slot);

        updateInventory(player);

        return new StatusPacket(1);
    }

    @Action(name="RemoveEnchantment", permission = "livekit.inventory.delete")
    protected IPacket actionRemoveEnchantment(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        String material = packet.getData().getString("material");
        int amount = packet.getData().getInt("amount");
        int slot = packet.getData().getInt("slot");
        String senchant = packet.getData().getString("enchantment");

        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(senchant.split(":")[1]));
        if(enchantment == null) return new StatusPacket(0, "An error occured!"); 
        
        OfflinePlayer offline = Bukkit.getServer().getOfflinePlayer(UUID.fromString(uuid));
        if(offline == null) return new StatusPacket(0, "Player not found!"); 

        Player player = offline.getPlayer();
        PlayerInventory inventory = player.getInventory();
        
        ItemStack stack = inventory.getItem(slot);
        if(stack == null) return new StatusPacket(0, "Slot was empty");

        if(!stack.getType().name().equals(material) || stack.getAmount() != amount) return new StatusPacket(0, "ItemStack missmatch!");

        stack.removeEnchantment(enchantment);

        updateInventory(player);

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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteractEvent(PlayerInteractEvent event) {
        if(event.getItem() != null) {
            queuePlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDamageEvent(PlayerItemDamageEvent event) {
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
