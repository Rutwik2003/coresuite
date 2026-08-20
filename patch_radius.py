with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

old_chunk_logic = """            String chunkKey = player.getWorld().getName() + ":" + player.getLocation().getChunk().getX() + ":" + player.getLocation().getChunk().getZ();
            chunkCatches.computeIfAbsent(chunkKey, k -> new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();
            if (chunkCatches.get(chunkKey).get() > 30) {
                TextUtil.sendActionBar(player, "&c[!] These waters are overfished! Move away!");
                return;
            }"""

new_chunk_logic = """            org.bukkit.Chunk pChunk = player.getLocation().getChunk();
            String chunkKey = player.getWorld().getName() + ":" + pChunk.getX() + ":" + pChunk.getZ();
            chunkCatches.computeIfAbsent(chunkKey, k -> new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();
            
            int radiusSum = 0;
            String wName = player.getWorld().getName();
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    String ck = wName + ":" + (pChunk.getX() + dx) + ":" + (pChunk.getZ() + dz);
                    if (chunkCatches.containsKey(ck)) {
                        radiusSum += chunkCatches.get(ck).get();
                    }
                }
            }
            
            if (radiusSum > 50) {
                TextUtil.sendActionBar(player, "&c[!] These waters are overfished! Move away!");
                return;
            }"""

if old_chunk_logic in content:
    content = content.replace(old_chunk_logic, new_chunk_logic)
    with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
        f.write(content)
    print("Radius chunk logic applied!")
else:
    print("Could not find the old chunk logic.")
