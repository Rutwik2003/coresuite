import sys

def process_file():
    path = r'c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\LegendaryQuestManager.java'
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Refactor onChat to be sync
    on_chat_old = '''    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID u = player.getUniqueId();
        
        if (chatState.containsKey(u)) {
            event.setCancelled(true);
            
            if (inDialogueSequence.contains(u)) {
                TextUtil.sendMessage(player, "&cWait until the Angler finishes speaking...");
                return;
            }
            
            String state = chatState.get(u);
            String msg = event.getMessage().trim();
            
            if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("leave")) {
                TextUtil.sendMessage(player, "&7You walked away from the Angler.");
                chatState.remove(u);
                return;
            }'''
            
    on_chat_new = '''    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID u = player.getUniqueId();
        
        if (chatState.containsKey(u)) {
            event.setCancelled(true);
            String msg = event.getMessage().trim();
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                processChatSync(player, u, msg);
            });
        }
    }
    
    private void processChatSync(Player player, UUID u, String msg) {
        if (!chatState.containsKey(u)) return;
        
        if (inDialogueSequence.contains(u)) {
            TextUtil.sendMessage(player, "&cWait until the Angler finishes speaking...");
            return;
        }
        
        String state = chatState.get(u);
        
        if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("leave")) {
            TextUtil.sendMessage(player, "&7You walked away from the Angler.");
            chatState.remove(u);
            return;
        }'''

    content = content.replace(on_chat_old, on_chat_new)
    
    # 2. Re-add the "Who awakened me" to performRitual
    ritual_old = '''        if (isFirstTime) {
            setStage(player.getUniqueId(), 10);
            startIntroDialogue(player);
        } else {'''
        
    ritual_new = '''        if (isFirstTime) {
            setStage(player.getUniqueId(), 10);
            
            UUID u = player.getUniqueId();
            inDialogueSequence.add(u);
            new BukkitRunnable() {
                int tick = 0;
                @Override
                public void run() {
                    if (tick == 0) TextUtil.sendMessage(player, "&e[Ancient Angler]&f Who awakens the Ancient Angler from the depths...?");
                    else if (tick == 1) TextUtil.sendMessage(player, "&e[Ancient Angler]&f Ah... another mortal seeking the legends of the deep.");
                    else if (tick == 2) {
                        inDialogueSequence.remove(u);
                        startIntroDialogue(player);
                        cancel();
                    }
                    tick++;
                }
            }.runTaskTimer(plugin, 40L, 80L);
            
        } else {'''
        
    content = content.replace(ritual_old, ritual_new)
    
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

process_file()
