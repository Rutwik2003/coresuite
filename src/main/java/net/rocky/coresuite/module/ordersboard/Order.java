package net.rocky.coresuite.module.ordersboard;

import org.bukkit.Material;
import net.rocky.coresuite.common.text.TextUtil;

public class Order {

    public enum OrderType { ITEM, SERVICE }
    public enum Status    { OPEN, ACCEPTED, COMPLETED }

    private final int id;
    private final String posterUuid;
    private final String posterName;
    private final OrderType orderType;

    // ITEM orders
    private final Material material;          // null for SERVICE
    private final int amountNeeded;
    private int amountFilled;

    // SERVICE orders
    private final String description;         // null for ITEM

    private double pricePerItem;        // for ITEM: total/amount. For SERVICE: total price (amount=1)
    private double priceTotal;
    private final long postedAt;
    private final long expiresAt;
    private boolean active;

    // Service fulfillment state
    private Status status;
    private String acceptedByUuid;
    private String acceptedByName;

    /** Full constructor — used when loading from DB. */
    public Order(int id, String posterUuid, String posterName, OrderType orderType,
                 Material material, int amountNeeded, int amountFilled,
                 String description,
                 double pricePerItem, double priceTotal,
                 long postedAt, long expiresAt, boolean active,
                 Status status, String acceptedByUuid, String acceptedByName) {
        this.id = id;
        this.posterUuid = posterUuid;
        this.posterName = posterName;
        this.orderType = orderType;
        this.material = material;
        this.amountNeeded = amountNeeded;
        this.amountFilled = amountFilled;
        this.description = description;
        this.pricePerItem = pricePerItem;
        this.priceTotal = priceTotal;
        this.postedAt = postedAt;
        this.expiresAt = expiresAt;
        this.active = active;
        this.status = status;
        this.acceptedByUuid = acceptedByUuid;
        this.acceptedByName = acceptedByName;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public int getId()               { return id; }
    public String getPosterUuid()    { return posterUuid; }
    public String getPosterName()    { return posterName; }
    public OrderType getOrderType()  { return orderType; }
    public Material getMaterial()    { return material; }          // may be null for SERVICE
    public int getAmountNeeded()     { return amountNeeded; }
    public int getAmountFilled()     { return amountFilled; }
    public String getDescription()   { return description; }       // may be null for ITEM
    public double getPricePerItem()  { return pricePerItem; }
    public double getPriceTotal()    { return priceTotal; }
    public long getPostedAt()        { return postedAt; }
    public long getExpiresAt()       { return expiresAt; }
    public boolean isActive()        { return active; }
    public Status getStatus()        { return status; }
    public String getAcceptedByUuid(){ return acceptedByUuid; }
    public String getAcceptedByName(){ return acceptedByName; }

    // ── Setters ────────────────────────────────────────────────────────────────

    public void setAmountFilled(int amountFilled)     { this.amountFilled = amountFilled; }
    public void setActive(boolean active)             { this.active = active; }
    public void setStatus(Status status)              { this.status = status; }
    public void setPriceTotal(double priceTotal)      { this.priceTotal = priceTotal; }
    public void setPricePerItem(double pricePerItem)  { this.pricePerItem = pricePerItem; }
    public void setAcceptedBy(String uuid, String name) {
        this.acceptedByUuid = uuid;
        this.acceptedByName = name;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    public boolean isItemOrder()    { return orderType == OrderType.ITEM; }
    public boolean isServiceOrder() { return orderType == OrderType.SERVICE; }

    public int getAmountRemaining() {
        return Math.max(0, amountNeeded - amountFilled);
    }

    public double getPriceRemaining() {
        return getAmountRemaining() * pricePerItem;
    }

    public boolean isFilled() {
        return amountFilled >= amountNeeded;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    /** Human-readable time left, e.g. "2d 3h 45m" or "Expired" */
    public String getTimeLeftString() {
        if (isExpired()) return "Expired";
        long timeLeftMillis = expiresAt - System.currentTimeMillis();
        return TextUtil.formatDuration(timeLeftMillis / 1000);
    }

    /**
     * Returns the display material for this order in GUIs.
     * ITEM orders use their actual material; SERVICE orders use WRITABLE_BOOK.
     */
    public Material getDisplayMaterial() {
        if (!isItemOrder()) {
            return Material.WRITABLE_BOOK;
        }
        if (material == null || material.isAir() || material.name().contains("PLAYER_HEAD")) {
            return Material.ITEM_FRAME;
        }
        return material;
    }

    /** Short summary of what's being requested, used in GUI item names. */
    public String getDisplayTitle() {
        if (isItemOrder()) {
            return OrderManager.formatMaterialName(material);
        }
        // Truncate long descriptions for item name display
        String desc = description != null ? description : "Service Request";
        return desc.length() > 30 ? desc.substring(0, 27) + "..." : desc;
    }
}
