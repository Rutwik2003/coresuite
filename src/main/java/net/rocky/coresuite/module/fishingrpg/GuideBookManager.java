package net.rocky.coresuite.module.fishingrpg;

import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;

public class GuideBookManager {

    private final FileConfiguration config;

    public GuideBookManager(FileConfiguration config) {
        this.config = config;
    }

    public void openBook(Player player) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) item.getItemMeta();
        
        if (bookMeta != null) {
            String title = config.getString("guide-book.title", "Fishing Guide");
            String author = config.getString("guide-book.author", "Server");
            
            bookMeta.title(TextUtil.colorize(title));
            bookMeta.author(TextUtil.colorize(author));
            bookMeta.setGeneration(BookMeta.Generation.ORIGINAL);
            
            List<String> pages = config.getStringList("guide-book.pages");
            for (String pageStr : pages) {
                bookMeta.addPages(TextUtil.colorize(pageStr));
            }
            
            item.setItemMeta(bookMeta);
        }
        
        player.openBook(item);
    }
}
