package plugin.arcwolf.autosort.Task;

import java.util.List;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
//import org.bukkit.block.Furnace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import plugin.arcwolf.autosort.AutoSort;
import plugin.arcwolf.autosort.Util;
import plugin.arcwolf.autosort.Network.NetworkItem;
import plugin.arcwolf.autosort.Network.SortChest;
import plugin.arcwolf.autosort.Network.SortNetwork;
//import plugin.arcwolf.lavafurnace.ChestHelper;
//import plugin.arcwolf.lavafurnace.ChestProcessing;
//import plugin.arcwolf.lavafurnace.FurnaceHelper;
//import plugin.arcwolf.lavafurnace.FurnaceObject;
//import plugin.arcwolf.lavafurnace.LavaFurnace;

public class SortTask implements Runnable {

    private AutoSort plugin;
    private long timer = 0;
    private long previousTime = 0;
    boolean waitTime = false;
    private long tick = 0;

    public SortTask(AutoSort autoSort) {
        plugin = autoSort;
    }

    public void run() {
        if (!plugin.UUIDLoaded) return;
        timer = System.currentTimeMillis();
        if (waitTime && timer - previousTime > 5000) {
            waitTime = false;
        }
        try {
            for(Item item : plugin.items) { // Deposit Signs Sort
                if (item.getVelocity().equals(new Vector(0, 0, 0))) {
                    plugin.stillItems.add(item);
                    World world = item.getWorld();
                    Block dropSpot = world.getBlockAt(item.getLocation());
                    BlockFace[] surrounding = { BlockFace.SELF, BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST, BlockFace.NORTH_WEST };
                    Block hopper;
                    for(BlockFace face : surrounding) {
                        hopper = dropSpot.getRelative(BlockFace.DOWN);
                        if (hopper.getType().equals(Material.HOPPER)) {
                            break;
                        }
                        else if (hopper.getRelative(face).getType().equals(Material.HOPPER)) {
                            break;
                        }
                        if (dropSpot.getRelative(face).getType().equals(Material.SIGN_POST)) {
                            Sign sign = (Sign) dropSpot.getRelative(face).getState();
                            sortDropSign(item, sign);
                            break;
                        }
                    }
                }
            }
            for(Item item : plugin.stillItems) {
                plugin.items.remove(item);
            }
            plugin.stillItems.clear();

        } catch (Exception e) {
            AutoSort.LOGGER.warning("[AutoSort] Error in Drop Sign Sort Thread");
            e.printStackTrace();
        }
        try {
            for(List<SortNetwork> networks : plugin.networks.values())
                // Deposit Chest Sort
                for(SortNetwork net : networks)
                    for(Entry<Block, NetworkItem> depChest : net.depositChests.entrySet()) {
                        if (depChest.getKey().getChunk().isLoaded()) {
                            if (net != null && plugin.util.isValidDepositBlock(depChest.getKey())) {
                                Inventory chest = Util.getInventory(depChest.getKey());
                                if (chest == null) continue;
                                Inventory inv = chest;
                                ItemStack[] contents = inv.getContents();
                                int i;
                                ItemStack is;
                                for(i = 0; i < contents.length; i++) {
                                    is = contents[i];
                                    if (is != null) {
                                        if (net.sortItem(is)) {
                                            contents[i] = null;
                                        }
                                    }
                                }
                                inv.setContents(contents);
                            }
                        }
                    }
        } catch (Exception e) {
            AutoSort.LOGGER.warning("[AutoSort] Error in DepositChests Sort Thread");
            e.printStackTrace();
        }

        try {
            if (AutoSort.keepPriority) { // Priority Resort
                for(int i = 4; i > 1; i--) {
                    for(List<SortNetwork> networks : plugin.networks.values()) {
                        for(SortNetwork net : networks) {
                            for(SortChest chest : net.sortChests) {
                                if (chest.block.getChunk().isLoaded()) {
                                    if (chest.priority == i && plugin.util.isValidInventoryBlock(chest.block)) {
                                        if (chest.signText.contains("LAVAFURNACE")) continue; //TODO lavafurnace block
                                        Inventory inv = Util.getInventory(chest.block);
                                        if (inv != null) {
                                            ItemStack[] items = inv.getContents();
                                            ItemStack is;
                                            for(int j = 0; j < items.length; j++) {
                                                is = items[j];
                                                if (is != null) {
                                                    if (net.sortItem(is, i - 1)) {
                                                        items[j] = null;
                                                    }
                                                }
                                            }
                                            inv.setContents(items);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            AutoSort.LOGGER.warning("[AutoSort] Error in Sort Chests Sort Thread");
            e.printStackTrace();
        }
        /* LavaFurnace is not 1.8 compat. Code disabled.
        try {
            if (plugin.getServer().getPluginManager().getPlugin("LavaFurnace") == null) return;
            for(List<SortNetwork> networks : plugin.networks.values()) {
                for(SortNetwork net : networks) {
                    for(SortChest chest : net.sortChests) {
                        if (chest.block.getChunk().isLoaded()) {
                            if (plugin.util.isValidInventoryBlock(chest.block)) {
                                maintainLavaFurnace(net, chest, chest.block);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            AutoSort.LOGGER.warning("[AutoSort] Error in Sort Chests Sort Thread");
            e.printStackTrace();
        }
        */
        if (AutoSort.getDebug() == 10) {
            if (tick != (System.currentTimeMillis() - timer)) {
                tick = (System.currentTimeMillis() - timer);
                System.out.println("Sort Time = " + tick + "ms");
            }
        }
    }

    // TODO Lavafurnace integration
    /*
    private void maintainLavaFurnace(SortNetwork net, SortChest sortChest, Block cblock) {
        try {
            if (!sortChest.block.getChunk().isLoaded())
                sortChest.block.getChunk().load();
            if (!(sortChest.sign.getType().equals(Material.WALL_SIGN))) return;
            if (!sortChest.signText.toLowerCase().contains("lavafurnace") && !sortChest.signText.toLowerCase().contains("lavafurnace")) return;
            LavaFurnace lfp = (LavaFurnace) LavaFurnace.plugin;
            FurnaceHelper fh = lfp.furnaceHelper;
            FurnaceObject fo = lfp.furnaceHelper.findFurnaceFromProductionChest(net.owner, cblock);
            if (fo == null) return;
            ChestHelper ch = new ChestHelper(lfp, fo);
            Inventory furnaceInv = ch.getFurnaceInventory();
            Furnace f = fh.getFurnace(fo);
            int fSA = fh.getAmount(furnaceInv.getItem(0));
            int fFA = fh.getAmount(furnaceInv.getItem(1));
            int fPA = fh.getAmount(furnaceInv.getItem(2));
            int burnTime = f.getBurnTime();
            if (lfp.datawriter.isSourceChestFuel() && fo.power < 1 && fSA > 0 && fFA == 0 && burnTime <= 0 && fPA == 0 && !waitTime) { // Keep Furnaces Fueled
                waitTime = true;
                previousTime = timer;
                Inventory chest = net.findItemStack(new ItemStack(Material.LAVA_BUCKET));
                if (chest != null) {
                    new ChestProcessing((LavaFurnace) LavaFurnace.plugin).fuelFurnaceWithLava(fo, f);

                    int index = chest.first(Material.LAVA_BUCKET);

                    chest.setItem(index, new ItemStack(Material.BUCKET));
                }
            }
        } catch (Exception e) {
            AutoSort.LOGGER.warning("[AutoSort] Error in LavaFurnace Thread");
            e.printStackTrace();
        }
    }
    */

    private void sortDropSign(Item item, Sign sign) {
        if (sign.getLine(0).startsWith("*")) {
            SortNetwork net = plugin.allNetworkBlocks.get(sign.getBlock());
            if (net == null) return;
            if (net.sortItem(item.getItemStack())) {
                item.remove();
            }
        }
    }
}
