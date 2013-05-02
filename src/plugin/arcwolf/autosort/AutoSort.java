package plugin.arcwolf.autosort;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.milkbowl.vault.permission.Permission;
import org.anjocaido.groupmanager.GroupManager;
import com.nijikokun.bukkit.Permissions.Permissions;
import de.bananaco.bpermissions.api.ApiLayer;
import de.bananaco.bpermissions.api.util.CalculableType;
import ru.tehkode.permissions.bukkit.PermissionsEx;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import plugin.arcwolf.autosort.Network.NetworkItem;
import plugin.arcwolf.autosort.Network.SortChest;
import plugin.arcwolf.autosort.Network.SortNetwork;
import plugin.arcwolf.autosort.Task.CleanupTask;
import plugin.arcwolf.autosort.Task.SortTask;

public class AutoSort extends JavaPlugin {

    public static final int SAVEVERSION = 5;
    public static final Logger LOGGER = Logger.getLogger("Minecraft.AutoSort");

    public List<Item> items = new ArrayList<Item>();
    public List<Item> stillItems = new ArrayList<Item>();

    public Map<Block, SortNetwork> allNetworkBlocks = new HashMap<Block, SortNetwork>();
    public Map<String, List<SortNetwork>> networks = new HashMap<String, List<SortNetwork>>();

    public static Map<String, List<ItemStack>> customMatGroups = new HashMap<String, List<ItemStack>>();
    public static Map<String, Integer> proximities = new HashMap<String, Integer>();
    public static int defaultProx = 0;

    public static boolean worldRestrict = false;
    public static boolean emptiesFirst = true;
    private boolean v4Loaded = false;

    private Server server;
    private BukkitScheduler scheduler;
    private PluginDescriptionFile pdfFile;
    private PluginManager pm;
    private String pluginName;

    private FileConfiguration customConfig = null;
    private File customConfigFile = null;
    public AutoSortListener asListener;
    private CommandHandler commandHandler;
    public Util util;

    private GroupManager groupManager;
    private net.milkbowl.vault.permission.Permission vaultPerms;
    private Permissions permissionsPlugin;
    private PermissionsEx permissionsExPlugin;
    private de.bananaco.bpermissions.imp.Permissions bPermissions;

    private boolean permissionsEr = false;
    private boolean permissionsSet = false;
    private int debug = 0;

    public void onEnable() {

        server = this.getServer();
        scheduler = server.getScheduler();
        pdfFile = getDescription();
        pluginName = pdfFile.getName();
        pm = server.getPluginManager();
        util = new Util(this);

        asListener = new AutoSortListener(this);
        commandHandler = new CommandHandler(this);

        debug = getConfig().getInt("debug", 0);
        worldRestrict = getConfig().getBoolean("worldRestrict", false);
        emptiesFirst = getConfig().getBoolean("fill-emptier-first", false);
        defaultProx = getConfig().getInt("proximity", 0);
        ConfigurationSection proxSec = getConfig().getConfigurationSection("proximity-exceptions");
        for(String key : proxSec.getKeys(false)) {
            proximities.put(key, proxSec.getInt(key));
        }

        getPermissionsPlugin();
        loadCustomGroups();
        v4Loaded = loadVersion4Save();
        if (!v4Loaded) {
            loadVersion5Save();
        }
        else {
            saveVersion5Network();
            loadVersion5Save();
        }

        pm.registerEvents(asListener, this);
        scheduler.scheduleSyncRepeatingTask(this, new SortTask(this), 5L, 10L);
        scheduler.scheduleSyncRepeatingTask(this, new CleanupTask(this), 12000L, 36000L);

        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    public void onDisable() {
        scheduler.cancelTasks(this);
        saveVersion5Network();
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (sender instanceof Player)
            return commandHandler.inGame(sender, cmd, commandLabel, args);
        else
            return commandHandler.inConsole(sender, cmd, commandLabel, args);
    }

    public boolean playerHasPermission(Player player, String command) {
        getPermissionsPlugin();
        if (vaultPerms != null) {
            if (debug == 1) {
                String pName = player.getName();
                String gName = vaultPerms.getPrimaryGroup(player);
                Boolean permissions = vaultPerms.has(player, command);
                LOGGER.info("Vault permissions, group for '" + pName + "' = " + gName);
                LOGGER.info("Permission for " + command + " is " + permissions);
            }
            return vaultPerms.has(player, command);
        }
        else if (groupManager != null) {
            if (debug == 1) {
                String pName = player.getName();
                String gName = groupManager.getWorldsHolder().getWorldData(player.getWorld().getName()).getPermissionsHandler().getGroup(player.getName());
                Boolean permissions = groupManager.getWorldsHolder().getWorldPermissions(player).has(player, command);
                LOGGER.info("group for '" + pName + "' = " + gName);
                LOGGER.info("Permission for " + command + " is " + permissions);
                LOGGER.info("");
                LOGGER.info("permissions available to '" + pName + "' = " + groupManager.getWorldsHolder().getWorldData(player.getWorld().getName()).getGroup(gName).getPermissionList());
            }
            return groupManager.getWorldsHolder().getWorldPermissions(player).has(player, command);
        }
        else if (permissionsPlugin != null) {
            if (debug == 1) {
                String pName = player.getName();
                String wName = player.getWorld().getName();
                String gName = Permissions.Security.getGroup(wName, pName);
                Boolean permissions = Permissions.Security.permission(player, command);
                LOGGER.info("Niji permissions, group for '" + pName + "' = " + gName);
                LOGGER.info("Permission for " + command + " is " + permissions);
            }
            return (Permissions.Security.permission(player, command));
        }
        else if (permissionsExPlugin != null) {
            if (debug == 1) {
                String pName = player.getName();
                String wName = player.getWorld().getName();
                String[] gNameA = PermissionsEx.getUser(player).getGroupsNames(wName);
                StringBuffer gName = new StringBuffer();
                for(String groups : gNameA) {
                    gName.append(groups + " ");
                }
                Boolean permissions = PermissionsEx.getPermissionManager().has(player, command);
                LOGGER.info("PermissionsEx permissions, group for '" + pName + "' = " + gName.toString());
                LOGGER.info("Permission for " + command + " is " + permissions);
            }
            return (PermissionsEx.getPermissionManager().has(player, command));
        }
        else if (bPermissions != null) {
            if (debug == 1) {
                String pName = player.getName();
                String wName = player.getWorld().getName();
                String[] gNameA = ApiLayer.getGroups(wName, CalculableType.USER, pName);
                StringBuffer gName = new StringBuffer();
                for(String groups : gNameA) {
                    gName.append(groups + " ");
                }
                Boolean permissions = bPermissions.has(player, command);
                LOGGER.info("bPermissions, group for '" + pName + "' = " + gName);
                LOGGER.info("bPermission for " + command + " is " + permissions);
            }
            return bPermissions.has(player, command);
        }
        else if (player.hasPermission(command)) {
            if (debug == 1) {
                LOGGER.info("Bukkit Permissions " + command + " " + player.hasPermission(command));
            }
            return true;
        }
        else if (permissionsEr && player.isOp()) {
            if (debug == 1) {
                LOGGER.info("Ops permissions " + command + " " + player.hasPermission(command));
            }
            return true;
        }
        else {
            if (debug == 1 && permissionsEr == true) {
                LOGGER.info("No permissions?? " + command + " " + player.hasPermission(command));
            }
            return false;
        }
    }

    // permissions plugin enabled test
    private void getPermissionsPlugin() {
        if (server.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
            if (!permissionsSet) {
                LOGGER.info(pluginName + ": Vault detected, permissions enabled...");
                permissionsSet = true;
            }
            vaultPerms = rsp.getProvider();
        }
        else if (server.getPluginManager().getPlugin("GroupManager") != null) {
            Plugin p = server.getPluginManager().getPlugin("GroupManager");
            if (!permissionsSet) {
                LOGGER.info(pluginName + ": GroupManager detected, permissions enabled...");
                permissionsSet = true;
            }
            groupManager = (GroupManager) p;
        }
        else if (server.getPluginManager().getPlugin("Permissions") != null) {
            Plugin p = server.getPluginManager().getPlugin("Permissions");
            if (!permissionsSet) {
                LOGGER.info(pluginName + ": Permissions detected, permissions enabled...");
                permissionsSet = true;
            }
            permissionsPlugin = (Permissions) p;
        }
        else if (server.getPluginManager().getPlugin("PermissionsBukkit") != null) {
            if (!permissionsSet) {
                LOGGER.info(pluginName + ": Bukkit permissions detected, permissions enabled...");
                permissionsSet = true;
            }
        }
        else if (server.getPluginManager().getPlugin("PermissionsEx") != null) {
            Plugin p = server.getPluginManager().getPlugin("PermissionsEx");
            if (!permissionsSet) {
                LOGGER.info(pluginName + ": PermissionsEx detected, permissions enabled...");
                permissionsSet = true;
            }
            permissionsExPlugin = (PermissionsEx) p;
        }
        else if (server.getPluginManager().getPlugin("bPermissions") != null) {
            Plugin p = server.getPluginManager().getPlugin("bPermissions");
            if (!permissionsSet) {
                LOGGER.info(pluginName + ": bPermissions detected, permissions enabled...");
                permissionsSet = true;
            }
            bPermissions = (de.bananaco.bpermissions.imp.Permissions) p;
        }
        else {
            if (!permissionsEr) {
                LOGGER.info(pluginName + ": No known permissions detected, Using Server OPs");
                permissionsEr = true;
            }
        }
    }

    private void loadCustomGroups() {
        ConfigurationSection groupSec = getConfig().getConfigurationSection("customGroups");
        Map<String, Object> groups = groupSec.getValues(false);
        for(String key : groups.keySet()) {
            List<String> idList = groupSec.getStringList(key);
            List<ItemStack> matList = new ArrayList<ItemStack>();
            for(String id : idList) {
                ItemStack is = Util.parseMaterialID(id);
                matList.add(is);
            }
            customMatGroups.put(key.toUpperCase(), matList);
        }
    }

    private boolean loadVersion4Save() {
        int version = getCustomConfig().getInt("version");
        if (version == 4) {
            ConfigurationSection netsSec = getCustomConfig().getConfigurationSection("networks");
            if (netsSec != null) {
                for(String netName : netsSec.getKeys(false)) {
                    ConfigurationSection netSec = netsSec.getConfigurationSection(netName);
                    if (netSec != null) {
                        String owner = netSec.getString("owner");
                        SortNetwork net = new SortNetwork(owner, netName, "");
                        net.members = netSec.getStringList("members");
                        ConfigurationSection chests = netSec.getConfigurationSection("chests");
                        if (chests != null) {
                            for(String chestLocStr : chests.getKeys(false)) {
                                ConfigurationSection cSec = chests.getConfigurationSection(chestLocStr);
                                String[] chestData = chestLocStr.split(",");
                                World world = getServer().getWorld(chestData[0].replace("(dot)", "."));
                                if (world != null) {
                                    if (!net.world.equals(world.getName())) {
                                        net.world = world.getName();
                                    }
                                    Location chestLoc = new Location(world, Integer.parseInt(chestData[1]), Integer.parseInt(chestData[2]), Integer.parseInt(chestData[3]));
                                    Block chest = chestLoc.getBlock();
                                    String signLocStr = cSec.getString("sign");
                                    String[] signData = signLocStr.split(",");
                                    Location signLoc = new Location(world, Integer.parseInt(signData[1]), Integer.parseInt(signData[2]), Integer.parseInt(signData[3]));
                                    if (signLoc.getBlock().getState() instanceof Sign) {
                                        Block sign = signLoc.getBlock();
                                        String signText = cSec.getString("signText");
                                        int priority = cSec.getInt("priority");
                                        boolean disregardDamage = cSec.getBoolean("disregardDamage");
                                        net.sortChests.add(new SortChest(chest, sign, signText, priority, disregardDamage));
                                    }
                                    else {
                                        LOGGER.warning(pluginName + ": SortChest Sign Didnt exist at " + signLoc.getBlockX() + "," + signLoc.getBlockY() + "," + signLoc.getBlockZ() + ":" + signLoc.getWorld().getName());
                                    }
                                }
                                else {
                                    LOGGER.warning(pluginName + ": Null world: " + chestData[0] + " . Does this world still exist?");
                                }
                            }
                        }
                        if (networks.containsKey(owner)) {
                            networks.get(owner).add(net);
                        }
                        else {
                            List<SortNetwork> nets = new ArrayList<SortNetwork>();
                            nets.add(net);
                            networks.put(owner, nets);
                        }
                    }
                }
            }
            ConfigurationSection chestsSec = getCustomConfig().getConfigurationSection("dropChests");
            if (chestsSec != null) {
                Map<String, Object> dchests = chestsSec.getValues(false);
                for(String key : dchests.keySet()) {
                    String[] data = key.split(",");
                    Location loc = new Location(getServer().getWorld(data[0].replace("(dot)", ".")), Integer.parseInt(data[1]), Integer.parseInt(data[2]), Integer.parseInt(data[3]));
                    if (loc.getWorld() == null) {
                        LOGGER.warning(pluginName + ": Null drop chest location! " + key + " Does this world still exist?");
                    }
                    else {
                        SortNetwork network = findV4Network((String) dchests.get(key));
                        Block sign = findSignFromChest(loc.getBlock());
                        if (sign == null) {
                            LOGGER.warning(pluginName + ": Failed to find sign for deposit chest");
                            break;
                        }
                        else if (network == null) {
                            LOGGER.warning(pluginName + ": Could not find network to add dropchest to.");
                        }
                        NetworkItem netItem = new NetworkItem(network, loc.getBlock(), sign);
                        network.depositChests.put(loc.getBlock(), netItem);
                    }
                }
            }
            chestsSec = getCustomConfig().getConfigurationSection("withdrawChests");
            if (chestsSec != null) {
                Map<String, Object> wchests = chestsSec.getValues(false);
                for(String key : wchests.keySet()) {
                    String[] data = key.split(",");
                    Location loc = new Location(getServer().getWorld(data[0].replace("(dot)", ".")), Integer.parseInt(data[1]), Integer.parseInt(data[2]), Integer.parseInt(data[3]));
                    if (loc.getWorld() == null) {
                        LOGGER.warning(pluginName + ": Null Withdraw chest location! " + key + " Does this world still exist?");
                    }
                    else {
                        SortNetwork network = findV4Network((String) wchests.get(key));
                        Block sign = findSignFromChest(loc.getBlock());
                        if (sign == null) {
                            LOGGER.warning(pluginName + ": Failed to find sign for Withdraw chest");
                            break;
                        }
                        else if (network == null) {
                            LOGGER.warning(pluginName + ": Could not find network to add withdraw chest to");
                            break;
                        }
                        NetworkItem netItem = new NetworkItem(network, loc.getBlock(), sign);
                        network.withdrawChests.put(loc.getBlock(), netItem);
                    }
                }
            }
            return true;
        }
        return false;
    }

    private void loadVersion5Save() {
        int version = getCustomConfig().getInt("version", 5);
        if (version == 5) {
            ConfigurationSection netsSec = getCustomConfig().getConfigurationSection("Owners");
            for(String owner : netsSec.getKeys(false)) {
                ConfigurationSection netSec = netsSec.getConfigurationSection(owner);
                ConfigurationSection newnet = netSec.getConfigurationSection("NetworkNames");
                for(String netNameSec : newnet.getKeys(false)) {
                    ConfigurationSection network = newnet.getConfigurationSection(netNameSec);
                    SortNetwork net = new SortNetwork(owner, netNameSec, "");
                    net.members = network.getStringList("Members");
                    ConfigurationSection chests = network.getConfigurationSection("Chests");
                    for(String chestLocStr : chests.getKeys(false)) {
                        ConfigurationSection cSec = chests.getConfigurationSection(chestLocStr);
                        String[] chestData = chestLocStr.split(",");
                        World world = getServer().getWorld(chestData[0].replace("(dot)", "."));
                        if (world != null) {
                            if (!net.world.equals(world.getName())) {
                                net.world = world.getName();
                            }
                            Location chestLoc = new Location(world, Integer.parseInt(chestData[1]), Integer.parseInt(chestData[2]), Integer.parseInt(chestData[3]));
                            Block chest = chestLoc.getBlock();

                            String signLocStr = cSec.getString("Sign");
                            String[] signData = signLocStr.split(",");
                            Location signLoc = new Location(world, Integer.parseInt(signData[1]), Integer.parseInt(signData[2]), Integer.parseInt(signData[3]));

                            if (signLoc.getBlock().getState() instanceof Sign) {
                                Block sign = signLoc.getBlock();

                                String signText = cSec.getString("SignText");

                                int priority = cSec.getInt("Priority");

                                boolean disregardDamage = cSec.getBoolean("DisregardDamage");

                                net.sortChests.add(new SortChest(chest, sign, signText, priority, disregardDamage));
                                allNetworkBlocks.put(chest, net);
                                allNetworkBlocks.put(util.doubleChest(chest), net);
                                allNetworkBlocks.put(sign, net);
                            }
                            else {
                                LOGGER.warning(pluginName + ": SortChest Sign Didnt exist at " + signLoc.getBlockX() + "," + signLoc.getBlockY() + "," + signLoc.getBlockZ() + ":" + signLoc.getWorld().getName());
                            }
                        }
                        else {
                            LOGGER.warning(pluginName + ": Null world: " + chestData[0] + " . Does this world still exist?");
                        }
                    }
                    if (networks.containsKey(owner)) {
                        networks.get(owner).add(net);
                    }
                    else {
                        List<SortNetwork> nets = new ArrayList<SortNetwork>();
                        nets.add(net);
                        networks.put(owner, nets);
                    }
                    try {
                        ConfigurationSection depChest = network.getConfigurationSection("DepositChests");
                        for(String depChestLocStr : depChest.getKeys(false)) {
                            ConfigurationSection dSec = depChest.getConfigurationSection(depChestLocStr);
                            String[] depChestData = depChestLocStr.split(",");
                            World world = getServer().getWorld(depChestData[0].replace("(dot)", "."));
                            if (world != null) {
                                if (!net.world.equals(world.getName())) {
                                    net.world = world.getName();
                                }
                                Location depChestLoc = new Location(world, Integer.parseInt(depChestData[1]), Integer.parseInt(depChestData[2]), Integer.parseInt(depChestData[3]));
                                Block chest = depChestLoc.getBlock();

                                String signLocStr = dSec.getString("Sign");
                                String[] signData = signLocStr.split(",");
                                Location signLoc = new Location(world, Integer.parseInt(signData[1]), Integer.parseInt(signData[2]), Integer.parseInt(signData[3]));

                                if (signLoc.getBlock().getState() instanceof Sign) {
                                    Block sign = signLoc.getBlock();
                                    NetworkItem netItem = new NetworkItem(net, chest, sign);
                                    net.depositChests.put(chest, netItem);
                                    allNetworkBlocks.put(chest, net);
                                    allNetworkBlocks.put(util.doubleChest(chest), net);
                                    allNetworkBlocks.put(sign, net);
                                }
                                else {
                                    LOGGER.warning(pluginName + ": SortChest Sign Didnt exist at " + signLoc.getBlockX() + "," + signLoc.getBlockY() + "," + signLoc.getBlockZ() + ":" + signLoc.getWorld().getName());
                                }
                            }
                            else {
                                LOGGER.warning(pluginName + ": Null world: " + depChestData[0] + " . Does this world still exist?");
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warning(pluginName + ": did not find dropchest section in database");
                        LOGGER.warning(pluginName + ": it will be added next save update");
                    }

                    try {
                        ConfigurationSection wdChest = network.getConfigurationSection("WithdrawChests");
                        for(String wdChestLocStr : wdChest.getKeys(false)) {
                            ConfigurationSection dSec = wdChest.getConfigurationSection(wdChestLocStr);
                            String[] wdChestData = wdChestLocStr.split(",");
                            World world = getServer().getWorld(wdChestData[0].replace("(dot)", "."));
                            if (world != null) {
                                if (!net.world.equals(world.getName())) {
                                    net.world = world.getName();
                                }
                                Location depChestLoc = new Location(world, Integer.parseInt(wdChestData[1]), Integer.parseInt(wdChestData[2]), Integer.parseInt(wdChestData[3]));
                                Block chest = depChestLoc.getBlock();

                                String signLocStr = dSec.getString("Sign");
                                String[] signData = signLocStr.split(",");
                                Location signLoc = new Location(world, Integer.parseInt(signData[1]), Integer.parseInt(signData[2]), Integer.parseInt(signData[3]));
                                if (signLoc.getBlock().getState() instanceof Sign) {
                                    Block sign = signLoc.getBlock();
                                    NetworkItem netItem = new NetworkItem(net, chest, sign);
                                    net.withdrawChests.put(chest, netItem);
                                    allNetworkBlocks.put(chest, net);
                                    allNetworkBlocks.put(util.doubleChest(chest), net);
                                    allNetworkBlocks.put(sign, net);
                                }
                                else {
                                    LOGGER.warning(pluginName + ": SortChest Sign Didnt exist at " + signLoc.getBlockX() + "," + signLoc.getBlockY() + "," + signLoc.getBlockZ() + ":" + signLoc.getWorld().getName());
                                }
                            }
                            else {
                                LOGGER.warning(pluginName + ": Null world: " + wdChestData[0] + " . Does this world still exist?");
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warning(pluginName + ": did not find withdraw chest section in database");
                        LOGGER.warning(pluginName + ": it will be added next save update");
                    }

                    try {
                        ConfigurationSection dSign = network.getConfigurationSection("DropSigns");
                        for(String dSignLocStr : dSign.getKeys(false)) {
                            String[] dSignData = dSignLocStr.split(",");
                            World world = getServer().getWorld(dSignData[0].replace("(dot)", "."));
                            if (world != null) {
                                if (!net.world.equals(world.getName())) {
                                    net.world = world.getName();
                                }
                                Location dSignLoc = new Location(world, Integer.parseInt(dSignData[1]), Integer.parseInt(dSignData[2]), Integer.parseInt(dSignData[3]));
                                Block sign = dSignLoc.getBlock();
                                if (sign.getState() instanceof Sign) {
                                    NetworkItem netItem = new NetworkItem(net, null, sign);
                                    net.dropSigns.put(sign, netItem);
                                    allNetworkBlocks.put(sign, net);
                                }
                                else {
                                    LOGGER.warning(pluginName + ": Drop Sign Didnt exist at " + dSignLoc.getBlockX() + "," + dSignLoc.getBlockY() + "," + dSignLoc.getBlockZ() + ":" + dSignLoc.getWorld().getName());
                                }
                            }
                            else {
                                LOGGER.warning(pluginName + ": Null world: " + dSignData[0] + " . Does this world still exist?");
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warning(pluginName + ": did not find dropsigns section in database");
                        LOGGER.warning(pluginName + ": it will be added next save update");
                    }
                }
            }
        }
    }

    public void cleanupNetwork() {
        List<Block> removeNetMapBlock = new ArrayList<Block>();
        List<SortNetwork> removedNets = new ArrayList<SortNetwork>();
        List<String> players = new ArrayList<String>();

        for(Entry<Block, SortNetwork> networkObject : allNetworkBlocks.entrySet()) {
            SortNetwork network = networkObject.getValue();
            List<Block> removeDepositChests = new ArrayList<Block>();
            List<Block> removeDropSigns = new ArrayList<Block>();
            List<Block> removeWithdrawChests = new ArrayList<Block>();
            List<SortChest> removedChests = new ArrayList<SortChest>();

            for(NetworkItem netItem : network.depositChests.values()) {
                Block chest = netItem.chest;
                Block sign = netItem.sign;
                if (!chest.getChunk().isLoaded()) chest.getChunk().load();
                if (!sign.getChunk().isLoaded()) sign.getChunk().load();
                if (!util.isValidDepositWithdrawBlock(chest)) {
                    removeDepositChests.add(chest);
                    removeNetMapBlock.add(chest);
                    removeNetMapBlock.add(sign);
                    LOGGER.info(pluginName + ": Chest at " + chest.getWorld().getName() + "," + chest.getLocation().getX() + "," + chest.getLocation().getY() + "," + chest.getLocation().getZ() + " in network " + netItem.network.netName + " removed (Not a chest block).");
                }
                else if (sign.getChunk().isLoaded() && !sign.getType().equals(Material.WALL_SIGN)) {
                    removeDepositChests.add(chest);
                    removeNetMapBlock.add(chest);
                    removeNetMapBlock.add(sign);
                    LOGGER.info(pluginName + ": Chest at " + chest.getWorld().getName() + "," + chest.getLocation().getX() + "," + chest.getLocation().getY() + "," + chest.getLocation().getZ() + " in network " + netItem.network.netName + " removed (No deposit sign).");
                }
            }

            for(Block chest : removeDepositChests)
                network.depositChests.remove(chest);

            for(NetworkItem netItem : network.dropSigns.values()) {
                Block sign = netItem.sign;
                if (!sign.getChunk().isLoaded()) sign.getChunk().load();
                if (!sign.getType().equals(Material.SIGN_POST)) {
                    removeDropSigns.add(sign);
                    removeNetMapBlock.add(sign);
                    LOGGER.info(pluginName + ": Sign at " + sign.getWorld().getName() + "," + sign.getLocation().getX() + "," + sign.getLocation().getY() + "," + sign.getLocation().getZ() + " in network " + netItem.network.netName + " removed (No drop sign).");
                }
            }

            for(Block sign : removeDropSigns)
                network.dropSigns.remove(sign);

            for(NetworkItem netItem : network.withdrawChests.values()) {
                Block chest = netItem.chest;
                Block sign = netItem.sign;
                if (!chest.getChunk().isLoaded()) chest.getChunk().load();
                if (!sign.getChunk().isLoaded()) sign.getChunk().load();
                if (!util.isValidDepositWithdrawBlock(chest)) {
                    removeWithdrawChests.add(chest);
                    removeNetMapBlock.add(chest);
                    removeNetMapBlock.add(sign);
                    LOGGER.info(pluginName + ": Chest at " + chest.getWorld().getName() + "," + chest.getLocation().getX() + "," + chest.getLocation().getY() + "," + chest.getLocation().getZ() + " in network " + netItem.network.netName + " removed (Not a chest block).");
                }
                else if (sign.getChunk().isLoaded() && !sign.getType().equals(Material.WALL_SIGN)) {
                    removeWithdrawChests.add(chest);
                    removeNetMapBlock.add(chest);
                    removeNetMapBlock.add(sign);
                    LOGGER.info(pluginName + ": Chest at " + chest.getWorld().getName() + "," + chest.getLocation().getX() + "," + chest.getLocation().getY() + "," + chest.getLocation().getZ() + " in network " + netItem.network.netName + " removed (No Withdraw sign).");
                }
            }

            for(Block chest : removeWithdrawChests)
                network.withdrawChests.remove(chest);

            for(SortChest sortChest : network.sortChests) {
                Block chest = sortChest.block;
                Block sign = sortChest.sign;
                if (!chest.getChunk().isLoaded()) chest.getChunk().load();
                if (!sign.getChunk().isLoaded()) sign.getChunk().load();
                if (util.isValidInventoryBlock(chest)) {
                    if (sign.getType().equals(Material.WALL_SIGN)) {
                        if (!((Sign) sign.getState()).getLine(0).startsWith("*")) {
                            removedChests.add(sortChest);
                            LOGGER.info(pluginName + ": Chest at " + chest.getWorld().getName() + "," + chest.getX() + "," + chest.getY() + "," + chest.getZ() + " in network " + network.netName + " removed (No sort sign).");
                        }
                    }
                    else {
                        removedChests.add(sortChest);
                        removeNetMapBlock.add(chest);
                        removeNetMapBlock.add(sign);
                        LOGGER.info(pluginName + ": Chest at " + chest.getWorld().getName() + "," + chest.getX() + "," + chest.getY() + "," + chest.getZ() + " in network " + network.netName + " removed (No sort sign).");
                    }
                }
                else if (sortChest.block.getChunk().isLoaded() && !util.isValidInventoryBlock(chest)) {
                    removedChests.add(sortChest);
                    removeNetMapBlock.add(chest);
                    removeNetMapBlock.add(sign);
                    LOGGER.info(pluginName + ": Chest at " + chest.getWorld().getName() + "," + chest.getX() + "," + chest.getY() + "," + chest.getZ() + " in network " + network.netName + " removed (Not a chest block).");
                }
            }
            for(SortChest chest : removedChests) {
                network.sortChests.remove(chest);
            }

            String netName = network.netName;

            if (network.sortChests.size() == 0 && network.depositChests.size() == 0 && network.depositChests.size() == 0 && network.dropSigns.size() == 0) {
                removedNets.add(network);
                LOGGER.info(pluginName + ": Network " + netName + " removed (Empty Network).");
            }
        }

        for(Entry<String, List<SortNetwork>> nets : networks.entrySet())
            for(SortNetwork network : nets.getValue())
                if (network.sortChests.size() == 0 && network.depositChests.size() == 0 && network.depositChests.size() == 0 && network.dropSigns.size() == 0) {
                    removedNets.add(network);
                    LOGGER.info(pluginName + ": Network " + network.netName + " removed (Empty Network).");
                }

        for(SortNetwork netName : removedNets)
            networks.get(netName.owner).remove(netName);

        for(Block block : removeNetMapBlock)
            allNetworkBlocks.remove(block);

        for(Entry<String, List<SortNetwork>> nets : networks.entrySet())
            if (nets.getValue().size() == 0) players.add(nets.getKey());

        for(String player : players) {
            networks.remove(player);
            LOGGER.info(pluginName + ": Player: " + player + " removed from database, No active networks.");
        }
    }

    public void saveVersion5Network() {
        cleanupNetwork();

        if (v4Loaded) {
            customConfigFile.renameTo(new File(getDataFolder(), "v4networks.yml"));
            customConfigFile = null;
            reloadCustomConfig();
            v4Loaded = false;
        }

        getCustomConfig().set("version", SAVEVERSION);

        // Save Owners
        ConfigurationSection netsSec = getCustomConfig().createSection("Owners");
        for(Entry<String, List<SortNetwork>> nets : networks.entrySet()) {
            String key = nets.getKey();

            // Save Networks
            ConfigurationSection owners = netsSec.createSection(key);
            ConfigurationSection newnet = owners.createSection("NetworkNames");
            for(SortNetwork net : nets.getValue()) {

                // Save Members
                ConfigurationSection netnames = newnet.createSection(net.netName);
                netnames.set("Members", net.members);

                // Save Sort Chests
                ConfigurationSection chestSec = netnames.createSection("Chests");
                for(SortChest chest : net.sortChests) {
                    Location loc = chest.block.getLocation();
                    String locStr = loc.getWorld().getName().replace(".", "(dot)") + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                    ConfigurationSection cSec = chestSec.createSection(locStr);
                    Location signLoc = chest.sign.getLocation();
                    String signLocStr = signLoc.getWorld().getName().replace(".", "(dot)") + "," + signLoc.getBlockX() + "," + signLoc.getBlockY() + "," + signLoc.getBlockZ();
                    cSec.set("Sign", signLocStr);
                    cSec.set("SignText", chest.signText);
                    cSec.set("Priority", chest.priority);
                    cSec.set("DisregardDamage", chest.disregardDamage);
                }

                // Save Deposit Chests
                ConfigurationSection depChestSec = netnames.createSection("DepositChests");
                for(Entry<Block, NetworkItem> depChest : net.depositChests.entrySet()) {
                    if (depChest.getValue().network.owner.equals(key) && depChest.getValue().network.netName.equals(net.netName)) {
                        Location depLoc = depChest.getKey().getLocation();
                        Location depSignLoc = depChest.getValue().sign.getLocation();
                        String locString = depLoc.getWorld().getName().replace(".", "(dot)") + "," + depLoc.getBlockX() + "," + depLoc.getBlockY() + "," + depLoc.getBlockZ();
                        String signLocString = depSignLoc.getWorld().getName().replace(".", "(dot)") + "," + depSignLoc.getBlockX() + "," + depSignLoc.getBlockY() + "," + depSignLoc.getBlockZ();
                        ConfigurationSection dSec = depChestSec.createSection(locString);
                        dSec.set("Sign", signLocString);
                    }
                }

                // Save WithdrawChests
                ConfigurationSection wdChestSec = netnames.createSection("WithdrawChests");
                for(Entry<Block, NetworkItem> wdChest : net.withdrawChests.entrySet()) {
                    if (wdChest.getValue().network.owner.equals(key) && wdChest.getValue().network.netName.equals(net.netName)) {
                        Location wdLoc = wdChest.getKey().getLocation();
                        Location wdSignLoc = wdChest.getValue().sign.getLocation();
                        String locString = wdLoc.getWorld().getName().replace(".", "(dot)") + "," + wdLoc.getBlockX() + "," + wdLoc.getBlockY() + "," + wdLoc.getBlockZ();
                        String signLocString = wdSignLoc.getWorld().getName().replace(".", "(dot)") + "," + wdSignLoc.getBlockX() + "," + wdSignLoc.getBlockY() + "," + wdSignLoc.getBlockZ();
                        ConfigurationSection wSec = wdChestSec.createSection(locString);
                        wSec.set("Sign", signLocString);
                    }
                }

                // Save Drop Signs
                ConfigurationSection dSignSec = netnames.createSection("DropSigns");
                for(Entry<Block, NetworkItem> dSign : net.dropSigns.entrySet()) {
                    if (dSign.getValue().network.owner.equals(key) && dSign.getValue().network.netName.equals(net.netName)) {
                        Location dSignLoc = dSign.getKey().getLocation();
                        String locString = dSignLoc.getWorld().getName().replace(".", "(dot)") + "," + dSignLoc.getBlockX() + "," + dSignLoc.getBlockY() + "," + dSignLoc.getBlockZ();
                        dSignSec.createSection(locString);
                    }
                }
            }
        }
        saveCustomConfig();
    }

    public void reloadCustomConfig() {
        if (customConfigFile == null) {
            customConfigFile = new File(getDataFolder(), "networks.yml");
        }
        customConfig = YamlConfiguration.loadConfiguration(customConfigFile);

        // Look for defaults in the jar
        InputStream defConfigStream = getResource("networks.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
            customConfig.setDefaults(defConfig);
        }
    }

    public FileConfiguration getCustomConfig() {
        if (customConfig == null) {
            reloadCustomConfig();
        }
        return customConfig;
    }

    public void saveCustomConfig() {
        if (customConfig == null || customConfigFile == null) { return; }
        try {
            customConfig.save(customConfigFile);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, pluginName + ": Could not save config to " + customConfigFile, ex);
        }
    }

    // Find old version networks
    private SortNetwork findV4Network(String netName) {
        for(List<SortNetwork> sn : networks.values()) {
            for(SortNetwork net : sn)
                if (net.netName.equals(netName)) { return net; }
        }
        return null;
    }

    // Find a network
    public SortNetwork findNetwork(String owner, String netName) {
        if (!networks.containsKey(owner)) return null;
        List<SortNetwork> netList = networks.get(owner);
        SortNetwork net = null;
        for(SortNetwork sn : netList) {
            if (sn.owner.equals(owner) && sn.netName.equals(netName)) {
                net = sn;
                break;
            }
        }
        return net;
    }

    public NetworkItem findNetworkItemBySign(Block signBlock) {
        if (signBlock.getType().equals(Material.WALL_SIGN) || signBlock.getType().equals(Material.SIGN_POST)) {
            SortNetwork network = allNetworkBlocks.get(signBlock);
            Sign sign = (Sign) signBlock.getState();
            if (sign.getLine(0).startsWith("*")) {
                for(NetworkItem ni : network.dropSigns.values()) {
                    if (ni.sign.equals(signBlock)) return ni;
                }
                for(NetworkItem ni : network.depositChests.values()) {
                    if (ni.sign.equals(signBlock)) return ni;
                }
            }
            else if (sign.getLine(0).startsWith("#")) {
                for(NetworkItem ni : network.withdrawChests.values()) {
                    if (ni.sign.equals(signBlock)) return ni;
                }
            }
        }
        return null;
    }

    private Block findSignFromChest(Block chest) {
        BlockFace[] surrounding = { BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST, BlockFace.NORTH_WEST };
        for(BlockFace face : surrounding) {
            Block maybeSign = chest.getRelative(face);
            if (maybeSign.getType().equals(Material.WALL_SIGN)) {
                Block sign = maybeSign;
                String[] lines = ((Sign) sign.getState()).getLines();
                if (lines[0].startsWith("*") || lines[0].startsWith("#")) {
                    String[] options = { lines[3].toUpperCase() };
                    if (lines[3].contains(" ")) {
                        options = lines[3].toUpperCase().split(" ");
                    }
                    BlockFace dir = BlockFace.DOWN;
                    for(String opt : options) {
                        if (opt.startsWith("D:")) {
                            String dStr = opt.split(":")[1];
                            int signData = sign.getData();
                            dir = null;
                            if (dStr.equalsIgnoreCase("L")) {
                                switch (signData) {
                                    case 2:
                                        dir = BlockFace.EAST;
                                    case 3:
                                        dir = BlockFace.WEST;
                                    case 4:
                                        dir = BlockFace.NORTH;
                                    case 5:
                                        dir = BlockFace.SOUTH;
                                }
                            }
                            else if (dStr.equalsIgnoreCase("R")) {
                                switch (signData) {
                                    case 2:
                                        dir = BlockFace.WEST;
                                    case 3:
                                        dir = BlockFace.EAST;
                                    case 4:
                                        dir = BlockFace.SOUTH;
                                    case 5:
                                        dir = BlockFace.NORTH;
                                }
                            }
                            else if (dStr.equalsIgnoreCase("N")) {
                                dir = BlockFace.NORTH;
                            }
                            else if (dStr.equalsIgnoreCase("NE")) {
                                dir = BlockFace.NORTH_EAST;
                            }
                            else if (dStr.equalsIgnoreCase("E")) {
                                dir = BlockFace.EAST;
                            }
                            else if (dStr.equalsIgnoreCase("SE")) {
                                dir = BlockFace.SOUTH_EAST;
                            }
                            else if (dStr.equalsIgnoreCase("S")) {
                                dir = BlockFace.SOUTH;
                            }
                            else if (dStr.equalsIgnoreCase("SW")) {
                                dir = BlockFace.SOUTH_WEST;
                            }
                            else if (dStr.equalsIgnoreCase("W")) {
                                dir = BlockFace.WEST;
                            }
                            else if (dStr.equalsIgnoreCase("NW")) {
                                dir = BlockFace.NORTH_WEST;
                            }
                            else if (dStr.equalsIgnoreCase("U")) {
                                dir = BlockFace.UP;
                            }
                            else if (dStr.equalsIgnoreCase("D")) {
                                dir = BlockFace.DOWN;
                            }
                            if (dir == null) { return null; }
                        }
                        else if (opt.toLowerCase().contains("withdraw")) {
                            // My version 4 setup so guess its ok because I'm the only one who used this version.
                            // TODO remember to remove this code after converting my server!
                            return sign;
                        }
                    }
                    Block maybeChest = sign.getRelative(dir);
                    if (chest.equals(maybeChest)) {
                        return sign;
                    }
                    else {
                        LOGGER.info(pluginName + ": Found " + maybeChest.getType().name() + " needed " + chest.getType().name());
                    }
                }
            }
        }
        return null;
    }
}
