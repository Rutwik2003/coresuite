/*
 * Copyright (c) 2024. CoreSuite. All rights reserved.
 */
package net.rocky.coresuite.common.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * A global listener that intercepts all inventory events and routes them to the correct Gui instance.
 */
public final class GuiManager implements Listener {

    /**
     * Handles inventory click events.
     *
     * @param event the event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Gui gui) {
            gui.handleClick(event);
        }
    }

    /**
     * Handles inventory drag events.
     *
     * @param event the event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Gui gui) {
            gui.handleDrag(event);
        }
    }

    /**
     * Handles inventory close events.
     *
     * @param event the event
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Gui gui) {
            gui.handleClose(event);
        }
    }
}
