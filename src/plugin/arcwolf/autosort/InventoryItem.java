package plugin.arcwolf.autosort;

public class InventoryItem {

    public int itemId;
    public int itemData;
    public int amount;

    public InventoryItem(int itemId, int itemData, int amount) {
        this.itemId = itemId;
        this.itemData = itemData;
        this.amount = amount;
    }
}
