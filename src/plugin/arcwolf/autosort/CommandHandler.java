package plugin.arcwolf.autosort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import plugin.arcwolf.autosort.Network.NetworkItem;
import plugin.arcwolf.autosort.Network.SortChest;
import plugin.arcwolf.autosort.Network.SortNetwork;

public class CommandHandler {

    private AutoSort plugin;

    public CommandHandler(AutoSort plugin) {
        this.plugin = plugin;
    }

    public boolean inGame(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        String commandName = cmd.getName();
        Player player = (Player) sender;
        if (commandName.equalsIgnoreCase("autosort") && plugin.playerCanUseCommand(player, "autosort.use")) {
            if (args.length == 1) {
                Inventory inv = player.getInventory();
                SortNetwork net = plugin.findNetwork(player.getName(), args[0]);
                if (net != null) {
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
                    player.sendMessage(ChatColor.GREEN + "Inventory sorted into " + args[0]);
                    return true;
                }
                else {
                    sender.sendMessage(ChatColor.RED + "The network '" + args[0] + "' could not be found.");
                    return true;
                }
            }
        }
        else if (commandName.equalsIgnoreCase("asreload") && plugin.playerCanUseCommand(player, "autosort.reload")) {
            if (args.length == 0) {
                plugin.getPluginLoader().disablePlugin(plugin);
                plugin.getPluginLoader().enablePlugin(plugin);
                sender.sendMessage(ChatColor.GREEN + "AutoSort reloaded.");
                return true;
            }
        }
        else if (commandName.equalsIgnoreCase("addasgroup") && plugin.playerCanUseCommand(player, "autosort.addasgroup")) {
            if (args.length > 1) {
                String groupName = args[0].toUpperCase();
                List<ItemStack> matList = new ArrayList<ItemStack>();
                List<String> ids = new ArrayList<String>();
                for(int i = 1; i < args.length; i++) {
                    String mat = args[i];
                    if (Util.parseMaterialID(mat) != null) {
                        matList.add(Util.parseMaterialID(mat));
                        ids.add(mat);
                    }
                    else {
                        sender.sendMessage(ChatColor.RED + "Invalid Material: " + mat);
                    }
                }
                ConfigurationSection groupsSec = plugin.getConfig().getConfigurationSection("customGroups");
                groupsSec.set(groupName, ids);
                plugin.saveConfig();
                AutoSort.customMatGroups.put(groupName, matList);
                sender.sendMessage(ChatColor.GREEN + "AutoSort group added.");
                return true;
            }
        }
        else if (commandName.equalsIgnoreCase("modasgroup") && plugin.playerCanUseCommand(player, "autosort.modasgroup")) {
            if (args.length > 1) {
                String groupName = args[0].toUpperCase();
                if (AutoSort.customMatGroups.containsKey(groupName)) {
                    List<ItemStack> matList = new ArrayList<ItemStack>();
                    for(int i = 1; i < args.length; i++) {
                        String mat = args[i];
                        if (Util.parseMaterialID(mat) != null) {
                            matList.add(Util.parseMaterialID(mat));
                        }
                        else {
                            sender.sendMessage(ChatColor.RED + "Invalid Material: " + mat);
                        }
                    }
                    List<String> ids = new ArrayList<String>();
                    for(ItemStack is : matList) {
                        if (is.getData().getData() == 0)
                            ids.add("" + is.getTypeId());
                        else
                            ids.add(is.getTypeId() + ":" + is.getData().getData());
                    }
                    ConfigurationSection groupsSec = plugin.getConfig().getConfigurationSection("customGroups");
                    groupsSec.set(groupName, ids);
                    plugin.saveConfig();
                    AutoSort.customMatGroups.put(groupName, matList);
                    sender.sendMessage(ChatColor.GREEN + "AutoSort group modified.");
                }
                else {
                    sender.sendMessage(ChatColor.RED + "That group does not exist!");
                }
                return true;
            }
        }
        else if (commandName.equalsIgnoreCase("delasgroup") && plugin.playerCanUseCommand(player, "autosort.delasgroup")) {
            if (args.length == 1) {
                String groupName = args[0].toUpperCase();
                if (AutoSort.customMatGroups.containsKey(groupName)) {
                    ConfigurationSection groupsSec = plugin.getConfig().getConfigurationSection("customGroups");
                    groupsSec.set(groupName, null);
                    plugin.saveConfig();
                    AutoSort.customMatGroups.remove(groupName);
                    sender.sendMessage(ChatColor.GREEN + "AutoSort group deleted.");
                }
                else {
                    sender.sendMessage(ChatColor.RED + "That group does not exist!");
                }
                return true;
            }
        }
        else if (commandName.equalsIgnoreCase("ascleanup") && plugin.playerCanUseCommand(player, "autosort.ascleanup")) {
            sender.sendMessage(ChatColor.BLUE + "Cleaning up all AutoSort networks...");
            plugin.cleanupNetwork();
            sender.sendMessage("Check server log for information on cleanup procedure.");
            sender.sendMessage(ChatColor.BLUE + "Done.");
            return true;
        }
        else if (commandName.equalsIgnoreCase("addtonet") && plugin.playerCanUseCommand(player, "autosort.addtonet")) {
            if (args.length > 1) {
                String netName = args[0];
                SortNetwork net = plugin.findNetwork(player.getName(), netName);
                if (net != null) {
                    int count = 0;
                    for(int i = 1; i < args.length; i++) {
                        if (net.members.contains(args[i])) {
                            sender.sendMessage(ChatColor.YELLOW + args[i] + " already added to the network.");
                        }
                        else {
                            net.members.add(args[i]);
                            count++;
                        }
                    }
                    sender.sendMessage(count + " " + ChatColor.BLUE + "Player(s) successfully added to the network.");
                    return true;
                }
                else if (net == null && plugin.playerCanUseCommand(player, "autosort.override")) {
                    if (args.length > 2) {
                        netName = args[1];
                        String owner = args[0];
                        net = plugin.findNetwork(owner, netName);
                        if (net != null) {
                            int count = 0;
                            for(int i = 2; i < args.length; i++) {
                                if (net.members.contains(args[i])) {
                                    sender.sendMessage(ChatColor.YELLOW + args[i] + " already added to the network.");
                                }
                                else {
                                    net.members.add(args[i]);
                                    count++;
                                }
                            }
                            sender.sendMessage(count + " " + ChatColor.BLUE + "Player(s) successfully added to the network.");
                            return true;
                        }
                        else {
                            sender.sendMessage(ChatColor.RED + "The network '" + netName + "' could not be found.");
                        }
                    }
                    else {
                        sender.sendMessage(ChatColor.RED + "Too few arguments! Usage: /addtonet [ownerName] [netName] [players...]");
                    }
                }
                else {
                    sender.sendMessage(ChatColor.RED + "The network '" + netName + "' could not be found.");
                }
            }
            else {
                sender.sendMessage(ChatColor.RED + "Too few arguments! Usage: /addtonet [netName] [players...]");
            }
        }
        else if (commandName.equalsIgnoreCase("remfromnet") && plugin.playerCanUseCommand(player, "autosort.remfromnet")) {
            if (args.length > 1) {
                String netName = args[0];
                SortNetwork net = plugin.findNetwork(player.getName(), netName);
                if (net != null) {
                    int count = 0;
                    for(int i = 1; i < args.length; i++) {
                        if (!net.members.contains(args[i])) {
                            sender.sendMessage(ChatColor.YELLOW + args[i] + " is not a member of the network.");
                        }
                        else {
                            net.members.remove(args[i]);
                            count++;
                        }
                    }
                    sender.sendMessage(count + " " + ChatColor.BLUE + "Player(s) successfully removed from the network.");
                    return true;
                }
                else if (net == null && plugin.playerCanUseCommand(player, "autosort.override")) {
                    if (args.length > 2) {
                        netName = args[1];
                        String owner = args[0];
                        net = plugin.findNetwork(owner, netName);
                        if (net != null) {
                            int count = 0;
                            for(int i = 2; i < args.length; i++) {
                                if (!net.members.contains(args[i])) {
                                    sender.sendMessage(ChatColor.YELLOW + args[i] + " is not a member of the network.");
                                }
                                else {
                                    net.members.remove(args[i]);
                                    count++;
                                }
                            }
                            sender.sendMessage(count + " " + ChatColor.BLUE + "Player(s) successfully removed from the network.");
                            return true;
                        }
                        else {
                            sender.sendMessage(ChatColor.RED + "The network '" + netName + "' could not be found.");
                        }
                    }
                    else {
                        sender.sendMessage(ChatColor.RED + "Too few arguments! Usage: /remfromnet [ownerName] [netName] [players...]");
                    }
                }
                else {
                    sender.sendMessage(ChatColor.RED + "The network '" + netName + "' could not be found.");
                }
            }
            else {
                sender.sendMessage(ChatColor.RED + "Too few arguments! Usage: /remfromnet [netName] [players...]");
            }
        }
        else if (commandName.equalsIgnoreCase("listasgroups") && plugin.playerCanUseCommand(player, "autosort.listasgroups")) {
            sender.sendMessage(ChatColor.GOLD + "Custom AutoSort material groups:");
            List<ItemStack> items;
            StringBuilder list;
            int count = 0;
            for(String groupName : AutoSort.customMatGroups.keySet()) {
                list = new StringBuilder();
                items = AutoSort.customMatGroups.get(groupName);
                list.append(ChatColor.WHITE);
                list.append(groupName);
                list.append(ChatColor.GOLD);
                list.append(": ");
                for(ItemStack item : items) {
                    list.append(getTrueMaterial(item));
                    list.append(ChatColor.WHITE);
                    list.append(count == items.size() - 1 ? "" : ", ");
                    list.append(ChatColor.GOLD);
                    count++;
                }
                count = 0;
                String msg = list.substring(0, list.length() - 2);
                sender.sendMessage(msg);
            }
            return true;
        }
        else if (commandName.equalsIgnoreCase("asremnet") && plugin.playerCanUseCommand(player, "autosort.remnet")) {
            // /asremnet <OwnerName> <networkName>
            String ownerName = args[0];
            String netName = args[1];
            sender.sendMessage(ChatColor.YELLOW + "The network ( " + ChatColor.WHITE + netName + ChatColor.YELLOW + " ) owned by ( " + ChatColor.WHITE + ownerName + ChatColor.YELLOW + " ) is deleted.");
            deleteNetwork(ownerName, netName, sender.getName());
            plugin.saveVersion5Network();
            return true;
        }
        else if (commandName.equals("aswithdraw")){ //Test withdraw inventory.
            //Inventory customInventory = Bukkit.createInventory(null, 9, "Test inventory");
            //((Player)sender).openInventory(customInventory);
            return true;
        }
        return false;
    }
    
    public boolean inConsole(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        String commandName = cmd.getName();
        if (commandName.equalsIgnoreCase("asreload")) {
            if (args.length == 0) {
                plugin.getPluginLoader().disablePlugin(plugin);
                plugin.getPluginLoader().enablePlugin(plugin);
                sender.sendMessage(ChatColor.GREEN + "AutoSort reloaded.");
                return true;
            }
        }
        else if (commandName.equalsIgnoreCase("addtonet")) {
            if (args.length > 2) {
                String netName = args[1];
                String owner = args[0];
                SortNetwork net = plugin.findNetwork(owner, netName);
                net = plugin.findNetwork(owner, netName);
                if (net != null) {
                    int count = 0;
                    for(int i = 2; i < args.length; i++) {
                        if (net.members.contains(args[i])) {
                            sender.sendMessage(ChatColor.YELLOW + args[i] + " already added to the network.");
                        }
                        else {
                            net.members.add(args[i]);
                            count++;
                        }
                    }
                    sender.sendMessage(count + " " + ChatColor.BLUE + "Player(s) successfully added to the network.");
                    return true;
                }
                else {
                    sender.sendMessage(ChatColor.RED + "The network '" + netName + "' could not be found.");
                }
            }
            else {
                sender.sendMessage(ChatColor.RED + "Too few arguments! Usage: /addtonet [ownerName] [netName] [players...]");
            }
        }
        else if (commandName.equalsIgnoreCase("remfromnet")) {
            if (args.length > 2) {
                String netName = args[1];
                String owner = args[0];
                SortNetwork net = plugin.findNetwork(owner, netName);
                net = plugin.findNetwork(owner, netName);
                if (net != null) {
                    int count = 0;
                    for(int i = 2; i < args.length; i++) {
                        if (!net.members.contains(args[i])) {
                            sender.sendMessage(ChatColor.YELLOW + args[i] + " is not a member of the network.");
                        }
                        else {
                            net.members.remove(args[i]);
                            count++;
                        }
                    }
                    sender.sendMessage(count + " " + ChatColor.BLUE + "Player(s) successfully removed from the network.");
                    return true;
                }
                else {
                    sender.sendMessage(ChatColor.RED + "The network '" + netName + "' could not be found.");
                }
            }
            else {
                sender.sendMessage(ChatColor.RED + "Too few arguments! Usage: /remfromnet [ownerName] [netName] [players...]");
            }
        }
        else if (commandName.equalsIgnoreCase("addasgroup")) {
            if (args.length > 1) {
                String groupName = args[0].toUpperCase();
                List<ItemStack> matList = new ArrayList<ItemStack>();
                List<String> ids = new ArrayList<String>();
                for(int i = 1; i < args.length; i++) {
                    String mat = args[i];
                    if (Util.parseMaterialID(mat) != null) {
                        matList.add(Util.parseMaterialID(mat));
                        ids.add(mat);
                    }
                    else {
                        sender.sendMessage(ChatColor.RED + "Invalid Material: " + mat);
                    }
                }
                ConfigurationSection groupsSec = plugin.getConfig().getConfigurationSection("customGroups");
                groupsSec.set(groupName, ids);
                plugin.saveConfig();
                AutoSort.customMatGroups.put(groupName, matList);
                sender.sendMessage(ChatColor.GREEN + "AutoSort group added.");
                return true;
            }
        }
        else if (commandName.equalsIgnoreCase("modasgroup")) {
            if (args.length > 1) {
                String groupName = args[0].toUpperCase();
                if (AutoSort.customMatGroups.containsKey(groupName)) {
                    List<ItemStack> matList = new ArrayList<ItemStack>();
                    for(int i = 1; i < args.length; i++) {
                        String mat = args[i];
                        if (Util.parseMaterialID(mat) != null) {
                            matList.add(Util.parseMaterialID(mat));
                        }
                        else {
                            sender.sendMessage(ChatColor.RED + "Invalid Material: " + mat);
                        }
                    }
                    List<String> ids = new ArrayList<String>();
                    for(ItemStack is : matList) {
                        if (is.getData().getData() == 0)
                            ids.add("" + is.getTypeId());
                        else
                            ids.add(is.getTypeId() + ":" + is.getData().getData());
                    }
                    ConfigurationSection groupsSec = plugin.getConfig().getConfigurationSection("customGroups");
                    groupsSec.set(groupName, ids);
                    plugin.saveConfig();
                    AutoSort.customMatGroups.put(groupName, matList);
                    sender.sendMessage(ChatColor.GREEN + "AutoSort group modified.");
                }
                else {
                    sender.sendMessage(ChatColor.RED + "That group does not exist!");
                }
                return true;
            }
        }
        else if (commandName.equalsIgnoreCase("delasgroup")) {
            if (args.length == 1) {
                String groupName = args[0].toUpperCase();
                if (AutoSort.customMatGroups.containsKey(groupName)) {
                    ConfigurationSection groupsSec = plugin.getConfig().getConfigurationSection("customGroups");
                    groupsSec.set(groupName, null);
                    plugin.saveConfig();
                    AutoSort.customMatGroups.remove(groupName);
                    sender.sendMessage(ChatColor.GREEN + "AutoSort group deleted.");
                }
                else {
                    sender.sendMessage(ChatColor.RED + "That group does not exist!");
                }
                return true;
            }
        }
        else if (commandName.equalsIgnoreCase("ascleanup")) {
            sender.sendMessage(ChatColor.BLUE + "Cleaning up all AutoSort networks...");
            plugin.cleanupNetwork();
            sender.sendMessage("Check server log for information on cleanup procedure.");
            sender.sendMessage(ChatColor.BLUE + "Done.");
            return true;
        }
        else if (commandName.equalsIgnoreCase("listasgroups")) {
            sender.sendMessage(ChatColor.GOLD + "Custom AutoSort material groups:");
            List<ItemStack> items;
            StringBuilder list;
            int count = 0;
            for(String groupName : AutoSort.customMatGroups.keySet()) {
                list = new StringBuilder();
                items = AutoSort.customMatGroups.get(groupName);
                list.append(ChatColor.WHITE);
                list.append(groupName);
                list.append(ChatColor.GOLD);
                list.append(": ");
                for(ItemStack item : items) {
                    list.append(getTrueMaterial(item));
                    list.append(ChatColor.WHITE);
                    list.append(count == items.size() - 1 ? "" : ", ");
                    list.append(ChatColor.GOLD);
                    count++;
                }
                count = 0;
                String msg = list.substring(0, list.length() - 2);
                sender.sendMessage(msg);
            }
            return true;
        }
        else if (commandName.equalsIgnoreCase("asremnet")) {
            // /asremnet <OwnerName> <networkName>
            String ownerName = args[0];
            String netName = args[1];
            sender.sendMessage(ChatColor.YELLOW + "The network ( " + ChatColor.WHITE + netName + ChatColor.YELLOW + " ) owned by ( " + ChatColor.WHITE + ownerName + ChatColor.YELLOW + " ) is deleted.");
            deleteNetwork(ownerName, netName, sender.getName());
            plugin.saveVersion5Network();
            return true;
        }
        return false;
    }

    private void deleteNetwork(String ownerName, String netName, String whoDeleted) {
        SortNetwork network = plugin.findNetwork(ownerName, netName);
        List<Block> netItemsToDel = new ArrayList<Block>();
        for(Entry<Block, NetworkItem> wchest : plugin.withdrawChests.entrySet()) {
            if (wchest.getValue().network.equals(network)) {
                updateSign(wchest.getValue().sign, netName, whoDeleted);
                netItemsToDel.add(wchest.getKey());
            }
        }
        for(Entry<Block, NetworkItem> dchest : plugin.depositChests.entrySet()) {
            if (dchest.getValue().network.equals(network)) {
                updateSign(dchest.getValue().sign, netName, whoDeleted);
                netItemsToDel.add(dchest.getKey());
            }
        }
        for(Entry<Block, NetworkItem> dsign : plugin.dropSigns.entrySet()) {
            if (dsign.getValue().network.equals(network)) {
                updateSign(dsign.getValue().sign, netName, whoDeleted);
                netItemsToDel.add(dsign.getKey());
            }
        }
        for(Block netBlock : netItemsToDel) {
            plugin.depositChests.remove(netBlock);
            plugin.withdrawChests.remove(netBlock);
            plugin.dropSigns.remove(netBlock);
        }
        for(SortChest chest : network.sortChests) {
            updateSign(chest.sign, netName, whoDeleted);
        }
        plugin.networks.get(ownerName).remove(network);
    }

    private void updateSign(Block sign, String netName, String whoDeleted) {
        if (sign.getType().equals(Material.WALL_SIGN) || sign.getType().equals(Material.SIGN_POST)) {
            BlockState sgn = sign.getState();
            Sign s = (Sign) sign.getState();
            s.setLine(0, "§e[ " + netName + " ]");
            s.setLine(1, "§edeleted by");
            s.setLine(2, "§e" + whoDeleted);
            s.setLine(3, "");
            sgn.update(true);
            s.update(true);
        }
    }

    private String getTrueMaterial(ItemStack item) {
        int itemData = 0;
        int itemId = 0;
        if (item != null) {
            itemId = item.getTypeId();
            itemData = item.getData().getData();
        }
        else
            return "";
        if (itemId == 5) {
            if (itemData == 0) return item.getType().name();
            if (itemData == 1) return "SPRUCE_WOOD";
            if (itemData == 2) return "BIRCH_WOOD";
            if (itemData == 3)
                return "JUNGLE_WOOD";
            else
                return "WOOD_" + itemData;
        }
        if (itemId == 6) {
            if (itemData == 0) return item.getType().name();
            if (itemData == 1) return "SPRUCE_SAPLING";
            if (itemData == 2) return "BIRCH_SAPLING";
            if (itemData == 3)
                return "JUNGLE_SAPLING";
            else
                return "SAPLING_" + itemData;
        }
        if (itemId == 17) {
            if (itemData == 0) return item.getType().name();
            if (itemData == 1) return "SPRUCE_LOG";
            if (itemData == 2) return "BIRCH_LOG";
            if (itemData == 3)
                return "JUNGLE_LOG";
            else
                return "LOG_" + itemData;
        }
        if (itemId == 18) {
            if (itemData == 0) return item.getType().name();
            if (itemData == 1) return "SPRUCE_LEAVES";
            if (itemData == 2) return "BIRCH_LEAVES";
            if (itemData == 3)
                return "JUNGLE_LEAVES";
            else
                return "LEAVES_" + itemData;
        }
        if (itemId == 24) {
            if (itemData == 0) return item.getType().name();
            if (itemData == 1) return "SANDSTONE_CHISELED";
            if (itemData == 2) return "SANDSTONE_SMOOTH";
        }
        if (itemId == 31) {
            if (itemData == 0) return "SHRUB";
            if (itemData == 1) return "GRASS";
            if (itemData == 2) return "FERN";
        }
        if (itemId == 35) {
            if (itemData == 0) return item.getType().name();
            if (itemData == 1) return "ORANGE_WOOL";
            if (itemData == 2) return "MAGENTA_WOOL";
            if (itemData == 3) return "LIGHT_BLUE_WOOL";
            if (itemData == 4) return "YELLOW_WOOL";
            if (itemData == 5) return "LIME_WOOL";
            if (itemData == 6) return "PINK_WOOL";
            if (itemData == 7) return "GRAY_WOOL";
            if (itemData == 8) return "LIGHT_GRAY_WOOL";
            if (itemData == 9) return "CYAN_WOOL";
            if (itemData == 10) return "PURPLE_WOOL";
            if (itemData == 11) return "BLUE_WOOL";
            if (itemData == 12) return "BROWN_WOOL";
            if (itemData == 13) return "GREEN_WOOL";
            if (itemData == 14) return "RED_WOOL";
            if (itemData == 15) return "BLACK_WOOL";
        }
        if (itemId == 43 || itemId == 44) {
            String type = "";
            String end = "_STEP";
            if (itemId == 43) type = "DOUBLE_";
            if (itemData == 0) return item.getType().name();
            if (itemData == 1) return type + "SANDSTONE" + end;
            if (itemData == 2) return type + "WOODSTONE" + end;
            if (itemData == 3) return type + "COBBLE" + end;
            if (itemData == 4) return type + "BRICK" + end;
            if (itemData == 5) return type + "STONEBRICK" + end;
            if (itemData == 6) return type + "NETHER" + end;
            if (itemData == 7) return type + "QUARTZ" + end;
            if (itemData == 8) return type + "SMOOTH_STONE" + end;
            if (itemData == 9) return type + "SMOOTH_SANDSTONE" + end;
            if (itemData == 15) return type + "TILE_QUARTZ" + end;
        }
        if (itemId == 98) {
            if (itemData == 0) return item.getType().name();
            if (itemData == 1) return "MOSSY_STONEBRICK";
            if (itemData == 2) return "CRACK_STONEBRICK";
            if (itemData == 3) return "CHISELED_STONEBRICK";
        }
        if (itemId == 125 || itemId == 126) {
            String type = "";
            String end = "_SLAB";
            if (itemId == 125) type = "DOUBLE_";
            if (itemId == 126) type = "SINGLE_";
            if (itemData == 0) return item.getType().name();
            if (itemData == 1) return type + "SPRUCE" + end;
            if (itemData == 2) return type + "BIRCH" + end;
            if (itemData == 3) return type + "JUNGLE" + end;
        }
        if (itemId == 263) {
            if (itemData == 0) return "COAL";
            if (itemData == 1) return "CHARCOAL";
        }
        if (itemId == 351) {
            if (itemData == 0) return item.getType().name();
            if (itemData == 1) return "RED_DYE";
            if (itemData == 2) return "CACTUS_GREEN";
            if (itemData == 3) return "COCOA_BEANS";
            if (itemData == 4) return "LAPIS_LAZULI";
            if (itemData == 5) return "PURPLE_DYE";
            if (itemData == 6) return "CYAN_DYE";
            if (itemData == 7) return "LIGHT_GRAY_DYE";
            if (itemData == 8) return "GRAY_DYE";
            if (itemData == 9) return "PINK_DYE";
            if (itemData == 10) return "LIME_DYE";
            if (itemData == 11) return "YELLOW_DYE";
            if (itemData == 12) return "LIGHT_BLUE_DYE";
            if (itemData == 13) return "MAGENTA_DYE";
            if (itemData == 14) return "ORANGE_DYE";
            if (itemData == 15) return "BONE_MEAL";
        }
        return item.getType().name();
    }
}