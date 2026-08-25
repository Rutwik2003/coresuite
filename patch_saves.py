import re

path = r'c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old_onquit = '''    public void onQuit(PlayerQuitEvent event) {
        if (legendaryQuestManager != null) legendaryQuestManager.unloadPlayer(event.getPlayer().getUniqueId());
        
        // Grab data before removing from cache, then save async to avoid blocking main thread
        FishData data = playerData.get(event.getPlayer().getUniqueId());
        if (data != null) {
            UUID quitUuid = event.getPlayer().getUniqueId();
            plugin.getDatabaseManager().executeAsync(() -> {
                plugin.getDatabaseManager().execute(
                    "UPDATE coresuite_fishing SET level = ?, xp = ?, total_catches = ?, bag_pages = ?, fatigue_score = ?, fatigue_last_update = ?, legendary_pity = ? WHERE uuid = ?",
                    data.level, data.xp, data.totalCatches, data.bagPages, data.fatigueScore, data.fatigueLastUpdate, data.legendaryPity, quitUuid.toString()
                );
            });
        }'''

new_onquit = '''    public void onQuit(PlayerQuitEvent event) {
        if (legendaryQuestManager != null) legendaryQuestManager.unloadPlayer(event.getPlayer().getUniqueId());
        
        // Grab data and save synchronously to avoid data loss on restart
        FishData data = playerData.get(event.getPlayer().getUniqueId());
        if (data != null) {
            UUID quitUuid = event.getPlayer().getUniqueId();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE coresuite_fishing SET level = ?, xp = ?, total_catches = ?, bag_pages = ?, fatigue_score = ?, fatigue_last_update = ?, legendary_pity = ? WHERE uuid = ?"
                 )) {
                stmt.setInt(1, data.level);
                stmt.setInt(2, data.xp);
                stmt.setInt(3, data.totalCatches);
                stmt.setInt(4, data.bagPages);
                stmt.setDouble(5, data.fatigueScore);
                stmt.setLong(6, data.fatigueLastUpdate);
                stmt.setInt(7, data.legendaryPity);
                stmt.setString(8, quitUuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save data on quit for " + quitUuid, e);
            }
        }'''

content = content.replace(old_onquit, new_onquit)

old_savedata = '''    private void saveData(UUID uuid) {
        FishData data = playerData.get(uuid);
        if (data != null) {
            plugin.getDatabaseManager().execute(
                "UPDATE coresuite_fishing SET level = ?, xp = ?, total_catches = ?, bag_pages = ?, fatigue_score = ?, fatigue_last_update = ?, legendary_pity = ? WHERE uuid = ?",
                data.level, data.xp, data.totalCatches, data.bagPages, data.fatigueScore, data.fatigueLastUpdate, data.legendaryPity, uuid.toString()
            );
        }
    }'''

new_savedata = '''    private void saveData(UUID uuid) {
        FishData data = playerData.get(uuid);
        if (data != null) {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE coresuite_fishing SET level = ?, xp = ?, total_catches = ?, bag_pages = ?, fatigue_score = ?, fatigue_last_update = ?, legendary_pity = ? WHERE uuid = ?"
                 )) {
                stmt.setInt(1, data.level);
                stmt.setInt(2, data.xp);
                stmt.setInt(3, data.totalCatches);
                stmt.setInt(4, data.bagPages);
                stmt.setDouble(5, data.fatigueScore);
                stmt.setLong(6, data.fatigueLastUpdate);
                stmt.setInt(7, data.legendaryPity);
                stmt.setString(8, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save data for " + uuid, e);
            }
        }
    }'''

content = content.replace(old_savedata, new_savedata)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

path2 = r'c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\BaitManager.java'
with open(path2, 'r', encoding='utf-8') as f:
    content2 = f.read()

old_bait_onquit = '''    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ActiveBait bait = activeBaits.remove(uuid);
        
        plugin.getDatabaseManager().executeAsync(() -> {
            if (bait != null) {
                try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                     java.sql.PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO coresuite_active_baits (uuid, bait_id, bait_type, casts_remaining, expires_at, catch_bonus, weight_bonus, rarity_bias) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET " +
                        "bait_id=excluded.bait_id, bait_type=excluded.bait_type, casts_remaining=excluded.casts_remaining, " +
                        "expires_at=excluded.expires_at, catch_bonus=excluded.catch_bonus, weight_bonus=excluded.weight_bonus, rarity_bias=excluded.rarity_bias")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, bait.baitId);
                    stmt.setString(3, bait.type);
                    stmt.setInt(4, bait.castsRemaining);
                    stmt.setLong(5, bait.expiresAt);
                    stmt.setDouble(6, bait.catchChanceBonus);
                    stmt.setDouble(7, bait.weightBonus);
                    stmt.setString(8, bait.rarityBias);
                    stmt.executeUpdate();
                } catch (Exception e) {}
            }
        });
    }'''

new_bait_onquit = '''    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ActiveBait bait = activeBaits.remove(uuid);
        
        if (bait != null) {
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO coresuite_active_baits (uuid, bait_id, bait_type, casts_remaining, expires_at, catch_bonus, weight_bonus, rarity_bias) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET " +
                    "bait_id=excluded.bait_id, bait_type=excluded.bait_type, casts_remaining=excluded.casts_remaining, " +
                    "expires_at=excluded.expires_at, catch_bonus=excluded.catch_bonus, weight_bonus=excluded.weight_bonus, rarity_bias=excluded.rarity_bias")) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, bait.baitId);
                stmt.setString(3, bait.type);
                stmt.setInt(4, bait.castsRemaining);
                stmt.setLong(5, bait.expiresAt);
                stmt.setDouble(6, bait.catchChanceBonus);
                stmt.setDouble(7, bait.weightBonus);
                stmt.setString(8, bait.rarityBias);
                stmt.executeUpdate();
            } catch (Exception e) {}
        }
    }'''

content2 = content2.replace(old_bait_onquit, new_bait_onquit)
with open(path2, 'w', encoding='utf-8') as f:
    f.write(content2)

print('Patch applied successfully.')
