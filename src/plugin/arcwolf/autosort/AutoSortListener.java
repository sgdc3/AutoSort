package plugin.arcwolf.autosort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Furnace;
import org.bukkit.block.Sign;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import plugin.arcwolf.autosort.Network.NetworkItem;
import plugin.arcwolf.autosort.Network.SortChest;
import plugin.arcwolf.autosort.Network.SortNetwork;

public class AutoSortListener implements Listener {

    private AutoSort plugin;
    private Map<String, SortNetwork> chestLock = new Hashtable<String, SortNetwork>();

    public AutoSortListener(AutoSort autoSort) {
        plugin = autoSort;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (block.getType().equals(Material.HOPPER)) {
            List<Block> blocksToTest = getBlocksToTest(block.getData(), block);
            if (hopperDropperStopper(blocksToTest, player)) {
                event.setCancelled(true);
                return;
            }
        }
        else if (block.getType().equals(Material.DROPPER)) {
            List<Block> blocksToTest = new ArrayList<Block>();
            blocksToTest.add(block.getRelative(BlockFace.UP));
            blocksToTest.add(block.getRelative(BlockFace.NORTH));
            blocksToTest.add(block.getRelative(BlockFace.SOUTH));
            blocksToTest.add(block.getRelative(BlockFace.WEST));
            blocksToTest.add(block.getRelative(BlockFace.EAST));
            blocksToTest.add(block.getRelative(BlockFace.DOWN));
            if (hopperDropperStopper(blocksToTest, player)) {
                event.setCancelled(true);
                return;
            }
        }
        else if (block.getType().equals(Material.CHEST) || block.getType().equals(Material.TRAPPED_CHEST)) {
            if (doubleChestPlaceChest(Material.CHEST, block, player) || doubleChestPlaceChest(Material.TRAPPED_CHEST, block, player)) {
                event.setCancelled(true);
                return;
            }
            Block hopper = findHopper(block);
            if (hopper.getType().equals(Material.HOPPER)) {
                List<Block> blocksToTest = getBlocksToTest(hopper.getData(), hopper);
                for(Block blockToTest : blocksToTest)
                    if (block.getLocation().equals(blockToTest.getLocation())) {
                        List<Block> testBlock = new ArrayList<Block>();
                        testBlock.add(hopper);
                        if (hopperDropperStopper(testBlock, player))
                            event.setCancelled(true);
                        return;
                    }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.isCancelled()) return;
        Item item = (Item) event.getEntity();
        plugin.items.add(item);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        CustomPlayer settings = CustomPlayer.getSettings(player);
        restoreWithdrawnInv(settings, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.isCancelled()) return;
        InventoryHolder holder = event.getInventory().getHolder();
        Block block = null;
        Block lChest = null;
        Block rChest = null;
        if (holder instanceof Chest) {
            block = ((Chest) holder).getBlock();
        }
        else if (holder instanceof DoubleChest) {
            DoubleChest dblchest = (DoubleChest) holder;
            lChest = ((Chest) dblchest.getLeftSide()).getBlock();
            rChest = ((Chest) dblchest.getRightSide()).getBlock();
            if (plugin.withdrawChests.containsKey(lChest)) {
                block = lChest;
            }
            else if (plugin.withdrawChests.containsKey(rChest)) {
                block = rChest;
            }
        }
        else
            return;
        Player player = null;
        if (event.getPlayer() instanceof Player)
            player = (Player) event.getPlayer();
        else
            return;
        if (block == null || player == null) return;
        if (block.getState() instanceof Chest) {
            SortNetwork sortNetwork = null;
            String netName = "";
            String owner = "";
            if (plugin.withdrawChests.containsKey(block)) {
                NetworkItem ni = plugin.withdrawChests.get(block);
                netName = ni.network.netName;
                owner = ni.network.owner;
                sortNetwork = ni.network;
            }
            else {
                return;
            }
            if (sortNetwork == null) return;

            //Transaction Start
            chestLock.put(player.getName(), sortNetwork);
            CustomPlayer settings = CustomPlayer.getSettings(player);
            settings.block = block;
            settings.netName = netName;
            settings.owner = owner;
            settings.playerName = player.getName();
            settings.sortNetwork = sortNetwork;
            if (updateInventoryList(player, settings)) {
                Collections.sort(settings.inventory, new IntegerComparator());
                updateChestInventory(player, settings);
            }
            else {
                player.sendMessage("The network - " + ChatColor.YELLOW + netName + ChatColor.WHITE + " - is empty.");
                chestLock.remove(player.getName());
                settings.clearPlayer();
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        int clickedId = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();
        CustomPlayer settings = CustomPlayer.getSettings(player);
        if (chestLock.containsKey(player.getName())) {
            if (clickedId == 0 || clickedId == 8) { // If Player clicks on slot 0 or 8 advance or reverse inventory view
                if (event.getCursor() != null) event.setCancelled(true);
                int maxStartIdx = settings.inventory.size();
                if (clickedId == 0) {
                    if (settings.startItemIdx >= 1)
                        settings.startItemIdx--;
                    else
                        settings.startItemIdx = maxStartIdx - 1;
                }
                else if (clickedId == 8) {
                    if (settings.startItemIdx < maxStartIdx - 1)
                        settings.startItemIdx++;
                    else
                        settings.startItemIdx = 0;
                }
                updateChestTask(player, settings);
            }
            else if (clickedId <= ((Chest) settings.block.getState()).getInventory().getSize()) { // If player removes item from chest resort chest
                updateChestTask(player, settings);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        String pName = player.getName();
        if (Util.isValidInventoryBlock(player, block, false) || isValidSign(block)) {
            NetworkItem netItem = null;
            if (plugin.withdrawChests.containsKey(block))
                netItem = plugin.withdrawChests.get(block);
            else if (plugin.withdrawChests.containsKey(doubleChest(block)))
                netItem = plugin.withdrawChests.get(doubleChest(block));
            SortNetwork net = null;
            if (netItem == null) {
                netItem = plugin.depositChests.get(block);
            }

            if (netItem == null) {
                net = findNetworkBySortChest(block);
                if (net == null) net = findNetworkBySortChest(doubleChest(block));
            }
            else {
                net = netItem.network;
            }

            if (netItem == null && net == null) return;
            if (!pName.equalsIgnoreCase(net.owner) && !net.members.contains(pName)) {
                //Transaction Fail isnt owned by this player
                player.sendMessage("This network is owned by " + ChatColor.YELLOW + net.owner);
                player.sendMessage(ChatColor.RED + "You can not access or modify this Network.");
                event.setCancelled(true);
            }
            else if (chestLock.containsValue(net)) {
                if (netItem == null || (plugin.depositChests.containsKey(block) || plugin.depositChests.containsKey(doubleChest(block)))) return;

                String user = "";
                for(Entry<String, SortNetwork> sortNet : chestLock.entrySet()) {
                    if (sortNet.getValue().equals(net)) {
                        user = sortNet.getKey();
                        break;
                    }
                }
                //Transaction Fail someone else is using the withdraw function
                player.sendMessage("This network is being withdrawn from by " + ChatColor.YELLOW + user);
                player.sendMessage(ChatColor.GOLD + "Please wait...");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String playerName = "";
        CustomPlayer settings = CustomPlayer.getSettings(player);
        playerName = settings.playerName;
        chestLock.remove(playerName);
        settings.clearPlayer();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = "";
        CustomPlayer settings = CustomPlayer.getSettings(player);
        playerName = settings.playerName;
        if (playerName != "") {
            restoreWithdrawnInv(settings, player);
        }
    }

    // TODO Thats a little better... still think it could use a little more cleanup though...
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        if (event.isCancelled()) return;
        String[] lines = event.getLines();
        String netName = "";
        Player player = event.getPlayer();

        SortNetwork sortNetwork = null;
        if (lines[0].startsWith("#") || lines[0].startsWith("*")) {
            netName = lines[0].substring(1);
            sortNetwork = plugin.findNetwork(player.getName(), netName);
            if (sortNetwork == null && plugin.playerCanUseCommand(player, "autosort.create"))
                sortNetwork = createNetwork(player, netName);
            else if (!plugin.playerCanUseCommand(player, "autosort.create")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to create AutoSort networks!" + plugin.playerCanUseCommand(player, "autosort.create"));
                return;
            }
        }
        if (netName.equals("")) return;

        if (event.getBlock().getType().equals(Material.WALL_SIGN)) {
            Block signBlock = event.getBlock();
            if (lines[0].startsWith("#")) { //TODO Withdraw Chest
                if (plugin.playerCanUseCommand(player, "autosort.use.withdraw")) {
                    String option = lines[3].toUpperCase();
                    Block storageBlock = getDirection("", signBlock);
                    if (option.startsWith("D:")) {
                        storageBlock = getDirection(option.split(":")[1], signBlock);
                    }
                    if (Util.isValidDepositWithdrawBlock(player, storageBlock, true) && !isInNetwork(player, storageBlock)) {
                        if (!AutoSort.worldRestrict || sortNetwork.world.equalsIgnoreCase(signBlock.getWorld().getName().toLowerCase())) {
                            int prox = getProximity(netName);
                            Location origin = getOrigin(sortNetwork.sortChests);
                            Location here = storageBlock.getLocation();
                            if (prox == 0 || (origin != null && origin.distance(here) <= prox) || plugin.playerCanUseCommand(player, "autosort.ignoreproximity")) {
                                plugin.withdrawChests.put(storageBlock, new NetworkItem(sortNetwork, storageBlock, signBlock));
                                event.setLine(1, "§fOpen Chest");
                                event.setLine(2, "§fTo Withdraw");
                                player.sendMessage(ChatColor.AQUA + "Withdraw chest added to network " + netName + ".");
                            }
                            else {
                                player.sendMessage(ChatColor.RED + "You can only place chests within " + prox + " blocks of the original chest!");
                                event.setCancelled(true);
                            }
                        }
                        else {
                            player.sendMessage(ChatColor.RED + "You can't add to a network unless you are in the same world as it!");
                            event.setCancelled(true);
                        }
                    }
                    else {
                        event.setCancelled(true);
                        return;
                    }
                }
                else {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You do not have permission to create AutoSort chests.");
                }
            }
            else if (lines[0].startsWith("*")) {
                if (event.getPlayer().hasPermission("autosort.use")) {
                    String mat1 = lines[1].toUpperCase();
                    String mat2 = lines[2].toUpperCase();

                    String[] options = { lines[3].toUpperCase() };
                    if (lines[3].contains(" ")) {
                        options = lines[3].toUpperCase().split(" ");
                    }
                    int priority = 2;
                    Block storageBlock = getDirection("", signBlock);
                    for(String opt : options) {
                        if (opt.startsWith("P:")) {
                            String pStr = opt.split(":")[1];
                            priority = getPriority(pStr);
                            if (priority == -1) {
                                event.setCancelled(true);
                                player.sendMessage(ChatColor.RED + "Invalid Priority: " + pStr);
                            }
                        }
                        else if (opt.startsWith("D:")) {
                            storageBlock = getDirection(opt.split(":")[1], signBlock);
                        }
                    }

                    String mat;
                    if (mat2.equalsIgnoreCase("")) {
                        mat = mat1;
                    }
                    else {
                        mat = mat1 + "," + mat2;
                    }

                    event.setLine(1, mat1);
                    event.setLine(2, mat2);

                    if (mat.equalsIgnoreCase("")) { //TODO Deposit Chest
                        if (Util.isValidDepositWithdrawBlock(player, storageBlock, true) && !isInNetwork(player, storageBlock)) {
                            if (!AutoSort.worldRestrict || sortNetwork.world.equalsIgnoreCase(signBlock.getWorld().getName().toLowerCase())) {
                                int prox = getProximity(netName);
                                Location origin = getOrigin(sortNetwork.sortChests);
                                Location here = storageBlock.getLocation();
                                if (prox == 0 || (origin != null && origin.distance(here) <= prox) || plugin.playerCanUseCommand(player, "autosort.ignoreproximity")) {
                                    event.setLine(1, "§fOpen Chest");
                                    event.setLine(2, "§fTo Deposit");
                                    player.sendMessage(ChatColor.AQUA + "Deposit chest added to " + sortNetwork.netName + ".");
                                    plugin.depositChests.put(storageBlock, new NetworkItem(sortNetwork, storageBlock, signBlock));
                                }
                                else {
                                    player.sendMessage(ChatColor.RED + "You can only place chests within " + prox + " blocks of the original chest!");
                                    event.setCancelled(true);
                                }
                            }
                            else {
                                player.sendMessage(ChatColor.RED + "You can't add to a network unless you are in the same world as it!");
                                event.setCancelled(true);
                            }
                        }
                        else {
                            event.setCancelled(true);
                            return;
                        }
                    }
                    else { //TODO Sort Chest
                        String[] matParts = mat.split(",");
                        for(String part : matParts) {
                            if (!isValid(part)) {
                                event.getPlayer().sendMessage(ChatColor.RED + "Invalid Material: " + part);
                                event.setCancelled(true);
                                return;
                            }
                        }
                        if (Util.isValidInventoryBlock(player, storageBlock, true) && !isInNetwork(player, storageBlock)) {
                            boolean dd = !mat.contains(":");

                            if (plugin.playerCanUseCommand(player, "autosort.override") || sortNetwork.owner.equalsIgnoreCase(player.getName())) {
                                if (!AutoSort.worldRestrict || sortNetwork.world.equalsIgnoreCase(signBlock.getWorld().getName().toLowerCase())) {
                                    int prox = getProximity(netName);
                                    Location origin = getOrigin(sortNetwork.sortChests);
                                    Location here = storageBlock.getLocation();
                                    if (prox == 0 || (origin != null && origin.distance(here) <= prox) || plugin.playerCanUseCommand(player, "autosort.ignoreproximity")) {
                                        player.sendMessage(ChatColor.AQUA + "Deposit chest added to " + sortNetwork.netName + ".");
                                        sortNetwork.sortChests.add(new SortChest(storageBlock, signBlock, mat, priority, dd));
                                        player.sendMessage(ChatColor.AQUA + "Sort chest with material(s) " + mat + " and priority " + priority + " added to network " + netName + ".");
                                    }
                                    else {
                                        player.sendMessage(ChatColor.RED + "You can only place chests within " + prox + " blocks of the original chest!");
                                        event.setCancelled(true);
                                    }
                                }
                                else {
                                    player.sendMessage(ChatColor.RED + "You can't add to a network unless you are in the same world as it!");
                                    event.setCancelled(true);
                                }
                            }
                            else {
                                player.sendMessage(ChatColor.RED + "You don't have permission to use that network!");
                                event.setCancelled(true);
                            }
                        }
                        else {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
                else {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "You do not have permission to create AutoSort chests.");
                }
            }
        }
        else if (event.getBlock().getType().equals(Material.SIGN_POST)) { // TODO Drop Sign
            if (plugin.playerCanUseCommand(player, "autosort.use")) {
                if (lines[0].startsWith("*")) {
                    Block sign = event.getBlock();
                    if (!AutoSort.worldRestrict || sortNetwork.world.equalsIgnoreCase(sign.getWorld().getName().toLowerCase())) {
                        int prox = getProximity(netName);
                        Location origin = getOrigin(sortNetwork.sortChests);
                        Location here = sign.getLocation();
                        if (prox == 0 || (origin != null && origin.distance(here) <= prox) || plugin.playerCanUseCommand(player, "autosort.ignoreproximity")) {
                            plugin.dropSigns.put(sign, new NetworkItem(sortNetwork, null, sign));
                            player.sendMessage(ChatColor.BLUE + "Drop Sign added to network " + netName + ".");
                            event.setLine(1, "§fDrop Items");
                            event.setLine(2, "§fOn Sign");
                        }
                        else {
                            player.sendMessage(ChatColor.RED + "You can only place drop signs within " + prox + " blocks of the original chest!");
                            event.setCancelled(true);
                        }
                    }
                    else {
                        player.sendMessage(ChatColor.RED + "You can't add to a network unless you are in the same world as it!");
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        if (plugin.depositChests.containsKey(block)) {
            NetworkItem netItem = plugin.depositChests.get(block);
            SortNetwork sortNetwork = null;
            if (netItem != null)
                sortNetwork = netItem.network;
            else
                return;
            if (sortNetwork == null) return;
            Sign sign = (Sign) netItem.sign.getState();
            if (sign.getLine(0).startsWith("*")) {
                ItemStack is = event.getResult();
                if (sortNetwork.sortItem(is)) {
                    event.setCancelled(true);
                    if (event.getSource().getAmount() > 1) {
                        event.getSource().setAmount(event.getSource().getAmount() - 1);
                    }
                    else {
                        ((Furnace) block.getState()).getInventory().remove(event.getSource());
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        Block sign = Util.findSign(block);
        if (sign != null) {
            String[] lines = ((Sign) sign.getState()).getLines();
            if (lines[0].startsWith("*") || lines[0].startsWith("#")) {
                if (plugin.findNetworkItemBySign(sign) != null) {
                    event.setCancelled(true);
                    return;
                }
                if (findNetworkBySign(sign) != null) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        Sign sign = null;
        Player player = event.getPlayer();
        String pName = player.getName();

        if (block.getType().equals(Material.WALL_SIGN) || block.getType().equals(Material.SIGN_POST)) {
            sign = (Sign) block.getState();
            String[] lines = sign.getLines();
            Block storageBlock = getDirection("", block);
            String[] options = { lines[3].toUpperCase() };
            if (lines[3].contains(" ")) {
                options = lines[3].toUpperCase().split(" ");
            }
            for(String opt : options) {
                if (opt.startsWith("D:")) {
                    storageBlock = getDirection(opt.split(":")[1], block);
                }
            }
            if (lines[0].startsWith("*")) {
                // Deposit or Sort Chest
                SortNetwork sortNetwork = null;
                NetworkItem netItem = null;
                if (lines[1].equals("§fOpen Chest") || lines[1].equals("§fDrop Items")) {
                    // Deposit Chest
                    sortNetwork = findNetworkBySign(block);
                    if (sortNetwork == null) {
                        // Drop Sign
                        netItem = plugin.findNetworkItemBySign(block);
                        if (netItem != null) {
                            sortNetwork = netItem.network;
                        }
                        else
                            return;
                    }
                }
                else {
                    // Sort Chest
                    sortNetwork = findNetworkBySign(block);
                    if (sortNetwork == null) return;
                }

                if (sortNetwork.owner.equals(pName)) {
                    if (netItem != null) {
                        if (plugin.depositChests.remove(storageBlock) != null) {
                            event.getPlayer().sendMessage(ChatColor.BLUE + "Deposit chest removed.");
                            return;
                        }
                        if (plugin.dropSigns.remove(block) != null) {
                            event.getPlayer().sendMessage(ChatColor.BLUE + "Drop sign removed.");
                            return;
                        }
                    }
                    else {
                        SortChest sc = sortNetwork.findSortChest(storageBlock);
                        if (sortNetwork.sortChests.remove(sc)) {
                            event.getPlayer().sendMessage(ChatColor.BLUE + "Sort chest removed.");
                            return;
                        }
                    }
                }
                else {
                    event.getPlayer().sendMessage("This network is owned by " + ChatColor.YELLOW + sortNetwork.owner);
                    event.getPlayer().sendMessage(ChatColor.RED + "You can't modify this network.");
                    event.setCancelled(true);
                    return;
                }
            }
            else if (lines[0].startsWith("#")) {
                // Withdraw Chest
                SortNetwork sortNetwork = null;
                NetworkItem netItem = plugin.findNetworkItemBySign(block);
                if (netItem != null) {
                    sortNetwork = netItem.network;
                }
                else
                    return;

                if (sortNetwork.owner.equals(pName)) {
                    if (plugin.withdrawChests.remove(storageBlock) != null) {
                        event.getPlayer().sendMessage(ChatColor.BLUE + "Withdraw chest removed.");
                        return;
                    }
                }
                else {
                    event.getPlayer().sendMessage("This network is owned by " + ChatColor.YELLOW + sortNetwork.owner);
                    event.getPlayer().sendMessage(ChatColor.RED + "You can't modify this network.");
                    event.setCancelled(true);
                    return;
                }
            }
        }
        else if (Util.isValidInventoryBlock(player, block, false)) {
            SortNetwork sortNetwork = findNetworkBySortChest(block);
            if (sortNetwork == null) {
                sortNetwork = findNetworkBySortChest(doubleChest(block));
                block = doubleChest(block);
            }
            if (sortNetwork != null) {
                if (sortNetwork.owner.equals(pName)) {
                    SortChest sortChest = sortNetwork.findSortChest(block);
                    if (sortChest.sign.getType().equals(Material.WALL_SIGN)) {
                        Sign chestSign = (Sign) sortChest.sign.getState();
                        for(int line = 0; line < 4; line++)
                            chestSign.setLine(line, "");
                        chestSign.update();
                    }
                    sortNetwork.sortChests.remove(sortChest);
                    event.getPlayer().sendMessage(ChatColor.BLUE + "Sort chest removed.");
                    return;
                }
                else {
                    event.getPlayer().sendMessage("This network is owned by " + ChatColor.YELLOW + sortNetwork.owner);
                    event.getPlayer().sendMessage(ChatColor.RED + "You can't modify this network.");
                    event.setCancelled(true);
                    return;
                }
            }
            else if (plugin.depositChests.containsKey(block) || plugin.depositChests.containsKey(doubleChest(block))) {
                if (plugin.depositChests.containsKey(doubleChest(block)))
                    block = doubleChest(block);
                String owner = plugin.depositChests.get(block).network.owner;
                if (plugin.depositChests.get(block).network.owner.equals(pName)) {
                    Block blockSign = plugin.depositChests.get(block).sign;
                    if (blockSign.getType().equals(Material.WALL_SIGN)) {
                        Sign chestSign = (Sign) blockSign.getState();
                        for(int line = 0; line < 4; line++)
                            chestSign.setLine(line, "");
                        chestSign.update();
                    }
                    plugin.depositChests.remove(block);
                    event.getPlayer().sendMessage(ChatColor.BLUE + "Deposit chest removed.");
                    return;
                }
                else {
                    event.getPlayer().sendMessage("This network is owned by " + ChatColor.YELLOW + owner);
                    event.getPlayer().sendMessage(ChatColor.RED + "You can't modify this network.");
                    event.setCancelled(true);
                    return;
                }
            }
            else if (plugin.withdrawChests.containsKey(block) || plugin.withdrawChests.containsKey(doubleChest(block))) {
                if (plugin.withdrawChests.containsKey(doubleChest(block)))
                    block = doubleChest(block);
                String owner = plugin.withdrawChests.get(block).network.owner;
                SortNetwork net = plugin.withdrawChests.get(block).network;
                if (net.owner.equals(pName)) {
                    CustomPlayer settings = CustomPlayer.getSettings(event.getPlayer());
                    settings.clearPlayer();
                    Block blockSign = plugin.withdrawChests.get(block).sign;
                    if (blockSign.getType().equals(Material.WALL_SIGN)) {
                        Sign chestSign = (Sign) blockSign.getState();
                        for(int line = 0; line < 4; line++)
                            chestSign.setLine(line, "");
                        chestSign.update();
                    }
                    plugin.withdrawChests.remove(block);
                    event.getPlayer().sendMessage(ChatColor.BLUE + "Withdraw chest removed.");
                    return;
                }
                else if (chestLock.containsValue(net)) {
                    String user = "";
                    for(Entry<String, SortNetwork> sortNet : chestLock.entrySet()) {
                        if (sortNet.getValue().equals(net)) {
                            user = sortNet.getKey();
                            break;
                        }
                    }
                    //Transaction Fail someone else is using the withdraw function
                    player.sendMessage("This network is being withdrawn from by " + ChatColor.YELLOW + user);
                    player.sendMessage(ChatColor.GOLD + "Please wait...");
                    event.setCancelled(true);
                }
                else {
                    event.getPlayer().sendMessage("This network is owned by " + ChatColor.YELLOW + owner);
                    event.getPlayer().sendMessage(ChatColor.RED + "You can't modify this network.");
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    //TODO Helper Methods / Classes

    // Roll through the network and pull out the correct amount of resources.
    // If not enough space return a false
    // true is successful
    private boolean makeWithdraw(Player player, CustomPlayer settings) {
        Chest withdrawChest = (Chest) settings.block.getState();
        int wantedAmount = settings.wantedAmount;
        int wantedItem = settings.inventory.get(settings.currentItemIdx).itemId;
        int wantedItemId = settings.inventory.get(settings.currentItemIdx).itemData;
        Map<Integer, ItemStack> couldntFit = null;
        for(SortChest chest : settings.sortNetwork.sortChests) {
            chest.block.getChunk().load();
            Inventory networkInv = null;
            networkInv = Util.getInventory(chest.block);
            if (networkInv == null) return false;
            for(int idx = 0; idx < networkInv.getSize(); idx++) {
                ItemStack networkItem = networkInv.getItem(idx);
                if (networkItem != null) {
                    if (networkItem.getTypeId() == wantedItem && networkItem.getData().getData() == wantedItemId) {
                        int foundAmount = networkItem.getAmount();
                        InventoryHolder withdrawInv = withdrawChest;
                        ItemStack stack = networkItem;
                        if (wantedAmount >= foundAmount && foundAmount != 0) { // Found amount and was less then wanted
                            couldntFit = withdrawInv.getInventory().addItem(stack);
                            if (couldntFit != null && !couldntFit.isEmpty()) { return false; }
                            wantedAmount -= foundAmount;
                            settings.wantedAmount = wantedAmount;
                            networkInv.clear(idx);
                        }
                        else if (wantedAmount != 0 && wantedAmount < foundAmount) { // Found amount and was more then wanted
                            while (wantedAmount > 0) {
                                couldntFit = withdrawInv.getInventory().addItem(stack);
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

    private void updateChestInventory(Player player, CustomPlayer settings) {
        Chest chest = (Chest) settings.block.getState();
        ItemStack dummyItem = new ItemStack(373, 1);
        try {
            settings.block.getChunk().load();
            Inventory inv = ((Chest) settings.block.getState()).getInventory();
            boolean toomanyItems = false;
            for(int i = 0; i < inv.getSize(); i++) {
                if (inv.getItem(i) != null) {
                    if (!settings.sortNetwork.sortItem(inv.getItem(i))) {
                        chest.getLocation().getWorld().dropItem(player.getLocation(), inv.getItem(i));
                        toomanyItems = true;
                    }
                }
            }
            if (toomanyItems) player.sendMessage(ChatColor.GOLD + settings.netName + ChatColor.RED + " is too full to replace withdrawchest Items!");
            chest.getInventory().clear();
            chest.getInventory().setItem(0, dummyItem);
            chest.getInventory().setItem(8, dummyItem);
            for(settings.currentItemIdx = settings.startItemIdx; settings.currentItemIdx < settings.inventory.size(); settings.currentItemIdx++) {
                settings.wantedAmount = settings.inventory.get(settings.currentItemIdx).amount;
                makeWithdraw(player, settings);
            }
        } catch (Exception e) {
            ConsoleCommandSender sender = plugin.getServer().getConsoleSender();
            sender.sendMessage(ChatColor.RED + "AutoSort critical Withdraw Chest error!");
            sender.sendMessage("Chest at " + chest.getLocation());
            sender.sendMessage("Player was " + player.getName());
            sender.sendMessage("Owner was " + settings.owner);
            sender.sendMessage("Network was " + settings.netName);
            sender.sendMessage("Error is as follows: ");
            sender.sendMessage(ChatColor.RED + "---------------------------------------");
            e.printStackTrace();
            sender.sendMessage(ChatColor.RED + "---------------------------------------");
        } finally {
            chest.getInventory().setItem(0, new ItemStack(0));
            chest.getInventory().setItem(8, new ItemStack(0));
        }
    }

    private boolean updateInventoryList(Player player, CustomPlayer settings) {
        for(SortChest chest : settings.sortNetwork.sortChests) {
            Inventory inv = Util.getInventoryHolder(chest.block).getInventory();
            if (inv == null) continue;
            for(ItemStack item : inv) {
                if (item != null) {
                    int itemId = item.getTypeId();
                    int itemData = item.getData().getData();
                    int index = settings.findItem(itemId, itemData);
                    if (index != -1) {
                        settings.inventory.get(index).amount += item.getAmount();
                    }
                    else {
                        settings.inventory.add(new InventoryItem(itemId, itemData, item.getAmount()));
                    }
                }
            }
        }
        return settings.inventory.size() > 0;
    }

    private void updateChestTask(final Player player, final CustomPlayer settings) {
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

            public void run() {
                updateChestInventory(player, settings);
            }
        }, 3);
    }

    private void restoreWithdrawnInv(CustomPlayer settings, Player player) {
        if (settings.sortNetwork != null) {
            if (chestLock.containsKey(player.getName())) {
                chestLock.remove(player.getName());
                Inventory inv = ((Chest) settings.block.getState()).getInventory();
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

    private boolean hopperDropperStopper(List<Block> blocksToTest, Player player) {
        String owner = player.getName();
        for(Block testBlock : blocksToTest) {
            NetworkItem depChest = plugin.depositChests.get(testBlock);
            if (depChest == null) depChest = plugin.depositChests.get(doubleChest(testBlock));
            if (depChest != null) {
                SortNetwork sortNet = depChest.network;
                if (!owner.equals(sortNet.owner)) {
                    player.sendMessage("This network is owned by " + ChatColor.YELLOW + sortNet.owner);
                    player.sendMessage(ChatColor.RED + "You can not access or modify this Network.");
                    return true;
                }
            }
            NetworkItem withChest = plugin.withdrawChests.get(testBlock);
            if (withChest == null) withChest = plugin.withdrawChests.get(doubleChest(testBlock));
            if (withChest != null) {
                SortNetwork sortNet = withChest.network;
                if (!owner.equals(sortNet.owner)) {
                    player.sendMessage("This network is owned by " + ChatColor.YELLOW + sortNet.owner);
                    player.sendMessage(ChatColor.RED + "You can not access or modify this Network.");
                    return true;
                }
            }
            List<SortNetwork> sortNet = plugin.networks.get(owner);
            if (sortNet == null) return false;
            for(SortNetwork network : sortNet) {
                for(SortChest chest : network.sortChests) {
                    if (chest.block.equals(testBlock) || chest.block.equals(doubleChest(testBlock))) {
                        if (!owner.equals(network.owner)) {
                            player.sendMessage("This network is owned by " + ChatColor.YELLOW + network.owner);
                            player.sendMessage(ChatColor.RED + "You can not access or modify this Network.");
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean doubleChestPlaceChest(Material mat, Block block, Player player) {
        Block blockToTest = doubleChest(block);
        if (blockToTest.getType().equals(mat)) {
            SortNetwork net = null;
            NetworkItem netItem = plugin.depositChests.get(blockToTest);
            if (netItem == null) netItem = plugin.withdrawChests.get(blockToTest);
            if (netItem == null)
                net = findNetworkBySortChest(blockToTest);
            else
                net = netItem.network;
            if (net == null) return false;
            if (!net.owner.equals(player.getName())) {
                player.sendMessage("This network is owned by " + ChatColor.YELLOW + net.owner);
                player.sendMessage(ChatColor.RED + "You can not access or modify this Network.");
                return true;
            }
            else if (chestLock.containsValue(net)) {
                String user = "";
                for(Entry<String, SortNetwork> sortNet : chestLock.entrySet()) {
                    if (sortNet.getValue().equals(net)) {
                        user = sortNet.getKey();
                        break;
                    }
                }
                //Transaction Fail someone else is using the withdraw function
                player.sendMessage("This network is being withdrawn from by " + ChatColor.YELLOW + user);
                player.sendMessage(ChatColor.GOLD + "Please wait...");
                return true;
            }
        }
        return false;
    }

    private List<Block> getBlocksToTest(int blockData, Block block) {
        List<Block> blocksToTest = new ArrayList<Block>();
        switch (blockData) {
            case 0:
                blocksToTest.add(block.getRelative(BlockFace.UP));
                blocksToTest.add(block.getRelative(BlockFace.DOWN));
                break;
            case 2:
                blocksToTest.add(block.getRelative(BlockFace.UP));
                blocksToTest.add(block.getRelative(BlockFace.NORTH));
                break;
            case 3:
                blocksToTest.add(block.getRelative(BlockFace.UP));
                blocksToTest.add(block.getRelative(BlockFace.SOUTH));
                break;
            case 4:
                blocksToTest.add(block.getRelative(BlockFace.UP));
                blocksToTest.add(block.getRelative(BlockFace.WEST));
                break;
            case 5:
                blocksToTest.add(block.getRelative(BlockFace.UP));
                blocksToTest.add(block.getRelative(BlockFace.EAST));
                break;
            default:
                blocksToTest.add(block.getRelative(BlockFace.UP));
                break;
        }
        return blocksToTest;
    }

    private Block doubleChest(Block block) {
        if (block.getType().equals(Material.CHEST) || block.getType().equals(Material.TRAPPED_CHEST)) {
            BlockFace[] surchest = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
            for(BlockFace face : surchest) {
                Block otherHalf = block.getRelative(face);
                if (otherHalf.getType().equals(block.getType())) { return otherHalf; }
            }
        }
        return block;
    }

    private Block findHopper(Block block) {
        BlockFace[] surchest = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN };
        for(BlockFace face : surchest) {
            Block otherHalf = block.getRelative(face);
            if (otherHalf.getType().equals(Material.HOPPER)) { return otherHalf; }
        }
        return block;
    }

    private boolean isValidSign(Block block) {
        return block.getType().equals(Material.WALL_SIGN) || block.getType().equals(Material.SIGN_POST);
    }

    private Block getDirection(String dStr, Block signBlock) {
        Location loc = signBlock.getLocation();
        int signData = signBlock.getData();
        int x = 0;
        int y = 0;
        int z = 0;
        World world = loc.getWorld();
        String dir;
        for(int i = 0; i < dStr.length(); i++) {
            dir = new Character(dStr.charAt(i)).toString();
            if (dir.equalsIgnoreCase("L")) {
                switch (signData) {
                    case 2:
                        x++;
                        break;
                    case 3:
                        x--;
                        break;
                    case 4:
                        z--;
                        break;
                    case 5:
                        z++;
                        break;
                }
            }
            if (dir.equalsIgnoreCase("R")) {
                switch (signData) {
                    case 2:
                        x--;
                        break;
                    case 3:
                        x++;
                        break;
                    case 4:
                        z++;
                        break;
                    case 5:
                        z--;
                        break;
                }
            }
            if (dir.equalsIgnoreCase("U")) {
                y++;
            }
            if (dir.equalsIgnoreCase("D")) {
                y--;
            }
            if (dir.equalsIgnoreCase("N")) {
                z--;
            }
            if (dir.equalsIgnoreCase("E")) {
                x++;
            }
            if (dir.equalsIgnoreCase("S")) {
                z++;
            }
            if (dir.equalsIgnoreCase("W")) {
                x--;
            }
        }

        if (x == 0 && y == 0 && z == 0) y--;
        int newX = loc.getBlockX() + x;
        int newY = loc.getBlockY() + y;
        int newZ = loc.getBlockZ() + z;
        return new Location(world, newX, newY, newZ).getBlock();
    }

    private boolean isValid(String str) {
        if (str != null) {
            if (str.contains(":")) {
                String[] parts = str.split(":");
                if (parts.length == 2) {
                    String sid = parts[0];
                    String sdam = parts[1];
                    if (Util.isNumeric(sid) && Util.isNumeric(sdam)) {
                        int id = Integer.parseInt(sid);
                        Material mat = Material.getMaterial(id);
                        if (mat != null) { return true; }
                    }
                }
            }
            else if (Util.isNumeric(str)) {
                Material mat = Material.getMaterial(Integer.parseInt(str));
                if (mat != null) { return true; }
            }
            else if (str.equalsIgnoreCase("MISC")) {
                return true;
            }
            else if (AutoSort.customMatGroups.containsKey(str)) {
                return true;
            }
            else {
                Material mat = Material.getMaterial(str);
                if (mat != null) { return true; }
            }
        }
        return false;
    }

    private int getPriority(String pStr) {
        if (Util.isNumeric(pStr)) {
            int pInt = Integer.parseInt(pStr);
            if (pInt > 0 && pInt < 5) { return pInt; }
        }
        return -1;
    }

    private SortNetwork findNetworkBySortChest(Block chest) {
        for(List<SortNetwork> nets : plugin.networks.values()) {
            for(SortNetwork network : nets)
                for(SortChest sc : network.sortChests) {
                    if (chest.equals(sc.block)) return network;
                }
        }
        return null;
    }

    private SortNetwork findNetworkBySign(Block sign) {
        for(List<SortNetwork> nets : plugin.networks.values()) {
            for(SortNetwork network : nets)
                for(SortChest sc : network.sortChests) {
                    if (sign.equals(sc.sign)) return network;
                }
        }
        return null;
    }

    // Create a new network
    private SortNetwork createNetwork(Player player, String netName) {
        String owner = player.getName();
        SortNetwork newNet = new SortNetwork(owner, netName, player.getWorld().getName());
        if (plugin.networks.containsKey(owner)) {
            plugin.networks.get(owner).add(newNet);
        }
        else {
            ArrayList<SortNetwork> networks = new ArrayList<SortNetwork>();
            networks.add(newNet);
            plugin.networks.put(owner, networks);
        }
        player.sendMessage(ChatColor.BLUE + "New network " + ChatColor.GRAY + netName + ChatColor.BLUE + " by " + ChatColor.GRAY + owner + ChatColor.BLUE + " created in " + ChatColor.GRAY + newNet.world + ChatColor.BLUE + ".");
        return newNet;
    }

    private boolean isInNetwork(Player player, Block storageBlock) {
        if (findNetworkBySortChest(storageBlock) != null || findNetworkBySortChest(doubleChest(storageBlock)) != null || plugin.withdrawChests.containsKey(storageBlock) || plugin.withdrawChests.containsKey(doubleChest(storageBlock)) || plugin.depositChests.containsKey(storageBlock) || plugin.depositChests.containsKey(doubleChest(storageBlock))) {
            player.sendMessage(ChatColor.RED + "You can't add this chest, it is already in a network!");
            return true;
        }
        else
            return false;
    }

    private int getProximity(String netName) {
        if (AutoSort.proximities.containsKey(netName)) {
            return AutoSort.proximities.get(netName);
        }
        else {
            return AutoSort.defaultProx;
        }

    }

    private Location getOrigin(List<SortChest> sortChests) {
        return sortChests.size() > 0 ? sortChests.get(0).block.getLocation() : null;
    }

    private class IntegerComparator implements Comparator<Object> {

        @Override
        public int compare(Object o1, Object o2) {
            InventoryItem value1 = (InventoryItem) o1;
            InventoryItem value2 = (InventoryItem) o2;
            Integer itemId1 = value1.itemId;
            Integer itemId2 = value2.itemId;
            return itemId1.compareTo(itemId2);
        }
    }
}
