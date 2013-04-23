package plugin.arcwolf.autosort;

import java.util.Comparator;


public class IntegerComparator implements Comparator<Object> {

    @Override
    public int compare(Object o1, Object o2) {
        InventoryItem value1 = (InventoryItem) o1;
        InventoryItem value2 = (InventoryItem) o2;
        Integer itemId1 = value1.itemId;
        Integer itemId2 = value2.itemId;
        return itemId1.compareTo(itemId2);
    }
}
