/*
 * Copyright (c) 2024. CoreSuite. All rights reserved.
 */
package net.rocky.coresuite.common.gui;

import net.kyori.adventure.text.Component;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A reusable inventory GUI that supports buttons and click handlers.
 */
public class Gui implements InventoryHolder {
    private final Inventory inventory;
    private final Map<Integer, GuiButton> buttons = new HashMap<>();
    private Consumer<Player> closeHandler;
    private boolean allowPlayerInventoryClicks = false;

    /**
     * Creates a new GUI with the specified string title and row count.
     *
     * @param title the title of the GUI
     * @param rows  the number of rows (1-6)
     */
    public Gui(String title, int rows) {
        int size = Math.min(6, Math.max(1, rows)) * 9;
        this.inventory = Bukkit.createInventory(this, size, TextUtil.colorize(title));
    }

    /**
     * Creates a new GUI with the specified Component title and row count.
     *
     * @param title the title of the GUI
     * @param rows  the number of rows (1-6)
     */
    public Gui(Component title, int rows) {
        int size = Math.min(6, Math.max(1, rows)) * 9;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    /**
     * Sets a button in the specified slot.
     *
     * @param slot   the slot index
     * @param button the button to set
     */
    public void setItem(int slot, GuiButton button) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        buttons.put(slot, button);
        inventory.setItem(slot, button.getItem());
    }

    /**
     * Sets an item and click handler in the specified slot.
     *
     * @param slot    the slot index
     * @param item    the item stack
     * @param handler the click handler
     */
    public void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> handler) {
        setItem(slot, new GuiButton(item, handler));
    }

    /**
     * Fills the border of the GUI with the specified material.
     *
     * @param material the material to use for the border
     */
    public void fillBorder(Material material) {
        GuiButton filler = GuiButton.filler(material);
        int size = inventory.getSize();
        int rows = size / 9;
        for (int i = 0; i < 9; i++) {
            setItem(i, filler); // top row
            setItem(size - 9 + i, filler); // bottom row
        }
        for (int row = 1; row < rows - 1; row++) {
            setItem(row * 9, filler); // left column
            setItem(row * 9 + 8, filler); // right column
        }
    }

    /**
     * Fills all empty slots in the GUI with the specified material.
     *
     * @param material the material to use for filling
     */
    public void fill(Material material) {
        GuiButton filler = GuiButton.filler(material);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!buttons.containsKey(i)) {
                setItem(i, filler);
            }
        }
    }

    /**
     * Sets a handler to be called when the GUI is closed.
     *
     * @param handler the close handler
     */
    public void onClose(Consumer<Player> handler) {
        this.closeHandler = handler;
    }

    /**
     * Sets whether players can click items in their own inventory while this GUI is open.
     *
     * @param allow true to allow player inventory clicks, false to deny
     */
    public void setAllowPlayerInventoryClicks(boolean allow) {
        this.allowPlayerInventoryClicks = allow;
    }

    /**
     * Opens this GUI for the specified player.
     *
     * @param player the player to open the GUI for
     */
    public void open(Player player) {
        player.openInventory(inventory);
    }

    /**
     * Clears all buttons and items from the GUI.
     */
    public void clear() {
        buttons.clear();
        inventory.clear();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /**
     * Gets the number of rows in this GUI (1-6).
     */
    public int getRows() {
        return inventory.getSize() / 9;
    }

    /**
     * Gets the total slot size of this GUI.
     */
    public int getSize() {
        return inventory.getSize();
    }

    // --- Internal: called by GuiManager ---

    /**
     * Handles an inventory click event.
     *
     * @param event the event
     */
    void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        // Only unconditionally cancel clicks in the GUI's own inventory
        if (event.getClickedInventory() == inventory) {
            event.setCancelled(true);
        } else {
            // Player's own inventory — cancel unless explicitly allowed
            // Even when allowed, strictly prevent shift-clicking or moving items into the GUI
            if (!allowPlayerInventoryClicks || event.isShiftClick()
                    || event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        int slot = event.getRawSlot();
        GuiButton button = buttons.get(slot);
        if (button != null && button.getHandler() != null) {
            button.getHandler().accept(event);
        }
    }

    /**
     * Handles an inventory drag event.
     *
     * @param event the event
     */
    void handleDrag(InventoryDragEvent event) {
        // Cancel if any slot is in the top inventory
        for (int slot : event.getRawSlots()) {
            if (slot < inventory.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Handles an inventory close event.
     *
     * @param event the event
     */
    void handleClose(InventoryCloseEvent event) {
        if (closeHandler != null && event.getPlayer() instanceof Player player) {
            try {
                closeHandler.accept(player);
            } catch (Exception e) {
                org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.WARNING, "Error in GUI close handler", e);
            }
        }
    }
}
