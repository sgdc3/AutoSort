package plugin.arcwolf.autosort;

import java.text.NumberFormat;
import java.text.ParsePosition;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class Util {

    public static boolean isValidInventoryBlock(Player player, Block block, Boolean isEventCheck) {
        if (block.getType().equals(Material.CHEST) || block.getType().equals(Material.TRAPPED_CHEST) ||
                block.getType().equals(Material.DISPENSER) || block.getType().equals(Material.DROPPER) ||
                block.getType().equals(Material.HOPPER)) { return true; }
        if (isEventCheck) player.sendMessage(ChatColor.RED + "That's not recognised inventory block!");
        return false;
    }

    public static boolean isValidDepositWithdrawBlock(Player player, Block block, Boolean isEventCheck) {
        if (block.getType().equals(Material.CHEST) || block.getType().equals(Material.TRAPPED_CHEST) ||
                block.getType().equals(Material.FURNACE) || block.getType().equals(Material.BURNING_FURNACE) ||
                block.getType().equals(Material.HOPPER)) { return true; }
        if (isEventCheck) player.sendMessage(ChatColor.RED + "That's not recognised inventory block!");
        return false;
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
                Material mat = Material.getMaterial(str);
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

    public static InventoryHolder getInventoryHolder(Block block) {
        InventoryHolder invHolder = null;
        if (block.getChunk().load()) {
            BlockState state = block.getState();
            if (state == null) {
                return null;
            }
            else if (state instanceof Dispenser) {
                invHolder = (Dispenser) state;
            }
            else if (state instanceof Chest) {
                invHolder = (Chest) state;
            }
            else if (state instanceof Dropper) {
                invHolder = (Dropper) state;
            }
            else if (state instanceof Hopper) {
                invHolder = (Hopper) state;
            }
        }
        return invHolder;
    }

    public static Block findSign(Block block) {
        BlockFace[] surchest = { BlockFace.SELF, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
        for(BlockFace face : surchest) {
            Block sign = block.getRelative(face);
            if (sign.getType().equals(Material.WALL_SIGN) || sign.getType().equals(Material.SIGN_POST)) { return sign; }
        }
        return null;
    }

    public static Inventory getInventory(Block block) {
        if (block.getState() instanceof Chest)
            return ((Chest) block.getState()).getInventory();
        else if (block.getState() instanceof Dispenser) {
            return ((Dispenser) block.getState()).getInventory();
        }
        else if (block.getState() instanceof Dropper) {
            return ((Dropper) block.getState()).getInventory();
        }
        else if (block.getState() instanceof Hopper) {
            return ((Hopper) block.getState()).getInventory();
        }
        else
            return null;
    }
}
