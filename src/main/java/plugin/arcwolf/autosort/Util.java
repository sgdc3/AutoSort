package plugin.arcwolf.autosort;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import plugin.arcwolf.autosort.Network.SortChest;

public class Util {

    private static AutoSort plugin;

    public Util(AutoSort plugin) {
        Util.plugin = plugin;
    }

    public static String getName(UUID pId) {
        OfflinePlayer op = plugin.getServer().getOfflinePlayer(pId);
        if (op != null) return op.getName();
        return "unknown";
    }

    /**
     * Will find the exact player and is case sensitive
     * 
     * @param name
     *            - The players name
     * @return Player object or null if not found
     */
    @SuppressWarnings("deprecation")
    public static Player getPlayer(String name) {
        for(Player test : Bukkit.matchPlayer(name)) {
            if (test.getName().equals(name)) return test;
        }
        return null;
    }

    public boolean isValidInventoryBlock(Block block) {
        return isValidInventoryBlock(null, block, false);
    }

    public boolean isValidInventoryBlock(Player player, Block block, Boolean isEventCheck) {
        int blockId = block.getTypeId();
        int blockData = block.getData();
        if (plugin.sortBlocks.containsKey(new InventoryBlock(blockId, blockData))) {
            return true;
        }
        else if (plugin.sortBlocks.containsKey(new InventoryBlock(blockId))) {
            return true;
        }
        else {
            if (isEventCheck) player.sendMessage(ChatColor.RED + "That's not a recognized inventory block!");
            return false;
        }
    }

    public boolean isValidDepositBlock(Block block) {
        return isValidDepositBlock(null, block, false);
    }

    public boolean isValidDepositBlock(Player player, Block block, Boolean isEventCheck) {
        int blockId = block.getTypeId();
        int blockData = block.getData();
        if (plugin.depositBlocks.containsKey(new InventoryBlock(blockId, blockData))) {
            return true;
        }
        else if (plugin.depositBlocks.containsKey(new InventoryBlock(blockId))) {
            return true;
        }
        else {
            if (isEventCheck) player.sendMessage(ChatColor.RED + "That's not a recognized inventory block!");
            return false;
        }
    }

    public boolean isValidWithdrawBlock(Block block) {
        return isValidWithdrawBlock(null, block, false);
    }

    public boolean isValidWithdrawBlock(Player player, Block block, Boolean isEventCheck) {
        int blockId = block.getTypeId();
        int blockData = block.getData();
        if (plugin.withdrawBlocks.containsKey(new InventoryBlock(blockId, blockData))) {
            return true;
        }
        else if (plugin.withdrawBlocks.containsKey(new InventoryBlock(blockId))) {
            return true;
        }
        else {
            if (isEventCheck) player.sendMessage(ChatColor.RED + "That's not a recognized inventory block!");
            return false;
        }
    }

    public static ItemStack parseMaterialID(String str) {
        if (str != null) {
            if (str.contains(":")) {
                String[] parts = str.split(":");
                String sid = parts[0];
                String sdam = parts[1];
                if (isNumeric(sid) && isNumeric(sdam)) {
                    int id = Integer.parseInt(sid);
                    short dam = Short.parseShort(sdam);
                    Material mat = Material.getMaterial(id);
                    if (mat != null) {
                        if (dam == 0) {
                            return new ItemStack(mat, 1);
                        }
                        else {
                            return new ItemStack(mat, 1, dam);
                        }
                    }
                }
            }
            else if (isNumeric(str)) {
                Material mat = Material.getMaterial(Integer.parseInt(str));
                if (mat != null) { return new ItemStack(mat, 1); }
            }
            else if (str.equalsIgnoreCase("MISC")) {
                return new ItemStack(Material.AIR);
            }
            else {
                Material mat = Material.getMaterial(str.toUpperCase());
                if (mat != null) { return new ItemStack(mat, 1); }
            }
        }
        return null;
    }

    public static boolean isNumeric(String str) {
        if (str.equalsIgnoreCase("")) { return false; }
        if (str.contains(",")) { return false; }
        NumberFormat formatter = NumberFormat.getInstance();
        ParsePosition pos = new ParsePosition(0);
        formatter.parse(str, pos);
        return str.length() == pos.getIndex();
    }

    public static Inventory getInventory(Block block) {
        if (!block.getChunk().isLoaded()) block.getChunk().load();
        if (!block.getChunk().isLoaded()) return null;
        BlockState state = block.getState();
        if (state instanceof InventoryHolder)
            return ((InventoryHolder) state).getInventory();
        else
            //return tryNMSInventory(block);
            return null;
    }

    /*
     * Deprecated! 
     * 
    private static Inventory tryNMSInventory(Block block) {
        Plugin p = plugin.getServer().getPluginManager().getPlugin("BKCommonLib");
        if (p != null) {
            if (AutoSort.bkError) {
                AutoSort.LOGGER.info(plugin.getName() + ": BKCommonLib detected, attempting custom inventory access.");
            }
            AutoSort.bkError = false;
            Inventory i = null;
            try {
                if (block == null) return null;
                Object tileEntity = TileEntityRef.getFromWorld(block);
                i = Conversion.toInventory.convert(tileEntity);
            } catch (Exception e) {
                if (AutoSort.getDebug() == 2) {
                    AutoSort.LOGGER.warning(plugin.getName() + ": unknown inventory type");
                    e.printStackTrace();
                    AutoSort.LOGGER.warning("----------------------------");
                }
            }
            return i;
        }
        else {
            if (!AutoSort.bkError) {
                AutoSort.bkError = true;
                AutoSort.LOGGER.warning(plugin.getName() + ": Can't access custom inventories without BKCommonLib Loaded.");
                AutoSort.LOGGER.warning(plugin.getName() + ": BKCommonLib can be found at:");
                AutoSort.LOGGER.warning(plugin.getName() + ": http://dev.bukkit.org/bukkit-plugins/bkcommonlib/");
            }
        }
        return null;
    }
    */

    /* For Future Reference
    public static Inventory tryNMSInventory(Block block) {
        Inventory i = null;
        try {
            if (block == null) return null;
            CraftWorld world = (CraftWorld) block.getWorld();
            World mWorld = world.getHandle();
            TileEntity te = mWorld.getTileEntity(block.getX(), block.getY(), block.getZ());
            IInventory ii = (IInventory) te;
            CraftInventory ci = new CraftInventory(ii);
            i = ci;
        } catch (Exception e) {
            if (AutoSort.getDebug() == 1) {
                System.out.println("unknown inventory type");
                e.printStackTrace();
                System.out.println("----------------------------");
            }
        }
        return i;
    }
    */

    public Block findSign(Block block) {
        BlockFace[] surchest = { BlockFace.SELF, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
        for(BlockFace face : surchest) {
            Block sign = block.getRelative(face);
            if (sign.getType().equals(Material.WALL_SIGN) || sign.getType().equals(Material.SIGN_POST)) { return sign; }
        }
        return null;
    }

    public Block doubleChest(Block block) {
        if (block.getType().equals(Material.CHEST) || block.getType().equals(Material.TRAPPED_CHEST)) {
            BlockFace[] surchest = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
            for(BlockFace face : surchest) {
                Block otherHalf = block.getRelative(face);
                if (otherHalf.getType().equals(block.getType())) { return otherHalf; }
            }
        }
        return block;
    }

    // Roll through the network and pull out the correct amount of resources.
    // If not enough space return a false
    // true is successful
    public boolean makeWithdraw(Player player, CustomPlayer settings) {
        int wantedAmount = settings.wantedAmount;
        ItemStack wantedItem = settings.inventory.get(settings.currentItemIdx).item;
        Map<Integer, ItemStack> couldntFit = null;
        Inventory networkInv;
        for(SortChest chest : settings.sortNetwork.sortChests) {
            //if (chest.signText.contains("LAVAFURNACE")) continue; // TODO Lavafurnace block
            if (!chest.block.getChunk().isLoaded())
                chest.block.getChunk().load();
            networkInv = getInventory(chest.block);
            if (networkInv == null) return false;
            for(int idx = 0; idx < networkInv.getSize(); idx++) {
                ItemStack networkItem = networkInv.getItem(idx);
                if (networkItem != null) {
                    if (networkItem.equals(wantedItem)) {
                        int foundAmount = networkItem.getAmount();
                        Inventory withdrawInv = settings.withdrawInventory;
                        ItemStack stack = networkItem;
                        if (wantedAmount >= foundAmount && foundAmount != 0) { // Found amount and was less then wanted
                            couldntFit = withdrawInv.addItem(stack);
                            if (couldntFit != null && !couldntFit.isEmpty()) { return false; }
                            wantedAmount -= foundAmount;
                            settings.wantedAmount = wantedAmount;
                            networkInv.clear(idx);
                        }
                        else if (wantedAmount != 0 && wantedAmount < foundAmount) { // Found amount and was more then wanted
                            while (wantedAmount > 0) {
                                couldntFit = withdrawInv.addItem(stack);
                                if (couldntFit != null && !couldntFit.isEmpty()) { return false; }
                                wantedAmount -= foundAmount;
                                settings.wantedAmount = wantedAmount;
                                networkInv.clear(idx);
                            }
                            if (couldntFit != null && !couldntFit.isEmpty()) { return false; }
                            wantedAmount -= foundAmount;
                            settings.wantedAmount = wantedAmount;
                        }
                    }
                }
            }
        }
        settings.wantedAmount = wantedAmount;
        return true;
    }

    public void updateChestInventory(Player player, CustomPlayer settings) {
        ItemStack dummyItem = new ItemStack(Material.POTION, 1);
        try {
            if (settings.block != null && !settings.block.getChunk().isLoaded())
                settings.block.getChunk().load();
            if (tooManyItems(player, settings)) player.sendMessage(ChatColor.GOLD + settings.netName + ChatColor.RED + " is too full to replace withdrawchest Items!");
            settings.withdrawInventory.clear();
            settings.withdrawInventory.setItem(0, dummyItem);
            settings.withdrawInventory.setItem(8, dummyItem);

            for(settings.currentItemIdx = settings.startItemIdx; settings.currentItemIdx < settings.inventory.size(); settings.currentItemIdx++) {
                settings.wantedAmount = settings.inventory.get(settings.currentItemIdx).amount;
                makeWithdraw(player, settings);
            }
            if (settings.withdrawInventory.firstEmpty() != -1 && settings.startItemIdx != 0) {
                for(int count = 0; count < settings.startItemIdx; count++) {
                    settings.currentItemIdx = count;
                    settings.wantedAmount = settings.inventory.get(count).amount;
                    makeWithdraw(player, settings);
                }
            }
        } catch (Exception e) {
            ConsoleCommandSender sender = plugin.getServer().getConsoleSender();
            sender.sendMessage(ChatColor.RED + "AutoSort critical Withdraw Chest error!");
            sender.sendMessage(settings.block != null ? "Chest at " + settings.block.getLocation() : "Player at " + player.getLocation());
            sender.sendMessage("Player was " + player.getName());
            sender.sendMessage("Owner was " + settings.owner);
            sender.sendMessage("Network was " + settings.netName);
            sender.sendMessage("Error is as follows: ");
            sender.sendMessage(ChatColor.RED + "---------------------------------------");
            e.printStackTrace();
            sender.sendMessage(ChatColor.RED + "---------------------------------------");
        } finally {
            settings.withdrawInventory.setItem(0, new ItemStack(Material.AIR));
            settings.withdrawInventory.setItem(8, new ItemStack(Material.AIR));
        }
    }

    public boolean tooManyItems(Player player, CustomPlayer settings) {
        boolean tooManyItems = false;
        Inventory inv = settings.withdrawInventory;
        Location dropLoc = player.getLocation();
        for(int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) != null) {
                if (!settings.sortNetwork.quickSortItem(inv.getItem(i))) {
                    dropLoc.getWorld().dropItem(dropLoc, inv.getItem(i));
                    tooManyItems = true;
                }
            }
        }
        return tooManyItems;
    }

    public boolean updateInventoryList(Player player, CustomPlayer settings) {
        for(SortChest chest : settings.sortNetwork.sortChests) {
            //if (chest.signText.contains("LAVAFURNACE")) continue; //TODO lavafurnace block
            Inventory inv = Util.getInventory(chest.block);
            if (inv == null) continue;
            for(ItemStack item : inv) {
                if (item != null) {
                    int index = settings.findItem(item);
                    if (index != -1) {
                        settings.inventory.get(index).amount += item.getAmount();
                    }
                    else {
                        settings.inventory.add(new InventoryItem(item, item.getAmount()));
                    }
                }
            }
        }
        return settings.inventory.size() > 0;
    }

    public void updateChestTask(final Player player, final CustomPlayer settings) {
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

            public void run() {
                if (settings.withdrawInventory != null)
                    plugin.util.updateChestInventory(player, settings);
            }
        }, 3);
    }

    public void restoreWithdrawnInv(CustomPlayer settings, Player player) {
        if (settings.sortNetwork != null) {
            if (plugin.asListener.chestLock.containsKey(player.getName())) {
                plugin.asListener.chestLock.remove(player.getName());
                Inventory inv = settings.withdrawInventory;
                for(int i = 0; i < inv.getSize(); i++) {
                    if (inv.getItem(i) != null) {
                        settings.sortNetwork.sortItem(inv.getItem(i));
                    }
                }
                inv.clear();
                settings.clearPlayer();
            }
        }
    }
}
