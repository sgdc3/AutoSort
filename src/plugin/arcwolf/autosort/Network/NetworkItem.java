package plugin.arcwolf.autosort.Network;

import org.bukkit.block.Block;

public class NetworkItem {

    public String owner = "";
    public String netName = "";
    public Block chest;
    public Block sign;
    
    public NetworkItem(String netName, String owner, Block chest, Block sign){
        this.netName = netName;
        this.owner = owner;
        this.chest = chest;
        this.sign = sign;
    }
}
