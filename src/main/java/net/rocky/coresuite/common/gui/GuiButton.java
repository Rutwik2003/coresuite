/*
 * Copyright (c) 2024. CoreSuite. All rights reserved.
 */
package net.rocky.coresuite.common.gui;

import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * A simple record-like class pairing an ItemStack with a click handler for GUI usage.
 */
public final class GuiButton {
    private final ItemStack item;
    private final Consumer<InventoryClickEvent> handler;

    /**
     * Constructs a new GuiButton.
     *
     * @param item    the item to display
     * @param handler the click handler to execute when clicked
     */
    public GuiButton(ItemStack item, Consumer<InventoryClickEvent> handler) {
        this.item = item;
        this.handler = handler;
    }

    /**
     * Creates a new GuiButton with the specified material, name, and lore.
     *
     * @param material the material of the item
     * @param name     the name of the item
     * @param lore     the lore of the item
     * @param handler  the click handler
     * @return a new GuiButton
     */
    public static GuiButton of(Material material, String name, List<String> lore, Consumer<InventoryClickEvent> handler) {
        ItemStack item = TextUtil.createItem(material, name, lore.toArray(String[]::new));
        return new GuiButton(item, handler);
    }

    /**
     * Creates a filler button with no click behavior and an invisible name.
     *
     * @param material the material to use for the filler
     * @return a filler GuiButton
     */
    public static GuiButton filler(Material material) {
        ItemStack item = TextUtil.createItem(material, " "); // invisible name
        return new GuiButton(item, e -> {}); // no-op
    }

    /**
     * Gets the ItemStack associated with this button.
     *
     * @return the ItemStack
     */
    public ItemStack getItem() {
        return item;
    }

    /**
     * Gets the handler associated with this button.
     *
     * @return the click handler
     */
    public Consumer<InventoryClickEvent> getHandler() {
        return handler;
    }
}
