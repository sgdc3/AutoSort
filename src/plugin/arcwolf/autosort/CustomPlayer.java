package plugin.arcwolf.autosort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class CustomPlayer {

    public static Map<String, CustomPlayer> playerSettings = new HashMap<String, CustomPlayer>();
    public int startItemIdx = 0; // Current Start index for chest withdraw
    public int currentItemIdx = 0; // Current item to start a chest withdraw on.
    public int wantedAmount = 1; // Current amount for a withdraw
    public String netName = ""; // This networks name
    public String owner = ""; // This networks owner
    public String playerName = ""; // The players name
    public Block block = null; // The withdraw chest block
    public List<InventoryItem> inventory = new ArrayList<InventoryItem>(400); // Full inventory of this network

    public static CustomPlayer getSettings(Player player) {
        
        CustomPlayer settings = (CustomPlayer) playerSettings.get(player.getName());
        if (settings == null) {
            playerSettings.put(player.getName(), new CustomPlayer());
            settings = (CustomPlayer) playerSettings.get(player.getName());
        }
        return settings;
    }

    public void clearPlayer() {
        currentItemIdx = 0;
        startItemIdx = 0;
        wantedAmount = 1;
        netName = "";
        owner = "";
        playerName = "";
        block = null;
        inventory.clear();
    }

    public int findItem(int itemId, int itemData) {
        for(int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i).itemId == itemId && inventory.get(i).itemData == itemData) return i;
        }
        return -1;
    }
}
