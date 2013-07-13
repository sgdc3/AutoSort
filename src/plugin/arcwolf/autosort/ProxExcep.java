package plugin.arcwolf.autosort;

public class ProxExcep {

    private String owner, network;
    private int distance;

    /**
     * 
     * @param owner
     * @param network
     * @param distance
     */
    public ProxExcep(String owner, String network, int distance) {
        this.owner = owner;
        this.network = network;
        this.distance = distance;
    }

    /**
     * @return the owner
     */
    public String getOwner() {
        return owner;
    }

    /**
     * @return the network
     */
    public String getNetwork() {
        return network;
    }

    /**
     * @return the distance
     */
    public int getDistance() {
        return distance;
    }

    public String toString() {
        return "Proximity: [Owner] " + owner + " [Network] " + network + " [Dist] " + distance;
    }
}
