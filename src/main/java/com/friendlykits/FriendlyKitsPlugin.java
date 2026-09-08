package com.friendlykits;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * الكلاس الرئيسي لإضافة Friendly-Kits:
 * يعالج حفظ واستنساخ الأدوات والدروع بشكل عميق (Deep Clone) لمنع إعادة كتابة الطقم
 * عند استلامه بواسطة أمر /kit <name>.
 */
public class FriendlyKitsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private File kitsFile;
    private FileConfiguration kitsConfig;

    // تسجيل فترات الانتظار لكل لاعب
    private final Map<String, Long> cooldowns = new HashMap<>();

    private final String MAIN_MENU_TITLE = ChatColor.DARK_GREEN + "Select a Kit";
    private final String PREVIEW_TITLE_PREFIX = ChatColor.DARK_BLUE + "Preview: ";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initKitsConfig();

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("kits") != null) {
            getCommand("kits").setExecutor(this);
            getCommand("kits").setTabCompleter(this);
        }
        if (getCommand("kit") != null) {
            getCommand("kit").setExecutor(this);
            getCommand("kit").setTabCompleter(this);
        }

        getLogger().info("Friendly-Kits has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        saveKitsConfig();
        cooldowns.clear();
        getLogger().info("Friendly-Kits has been disabled.");
    }

    private void initKitsConfig() {
        kitsFile = new File(getDataFolder(), "kits.yml");
        if (!kitsFile.exists()) {
            try {
                kitsFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create kits.yml!");
            }
        }
        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
    }

    public void saveKitsConfig() {
        try {
            kitsConfig.save(kitsFile);
            // إعادة قراءة الملف فوراً لتحديث الذاكرة
            kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
        } catch (IOException e) {
            getLogger().severe("Could not save kits.yml!");
        }
    }

    public String getMsg(String path) {
        String msg = getConfig().getString("messages." + path, "");
        String prefix = getConfig().getString("messages.prefix", "");
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    private ItemStack createFillerItem() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        return glass;
    }

    private long parseTimeToSeconds(String input) {
        input = input.toLowerCase().trim();
        long multiplier = 1;

        if (input.endsWith("s")) {
            multiplier = 1;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("m")) {
            multiplier = 60;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("h")) {
            multiplier = 3600;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("d")) {
            multiplier = 86400;
            input = input.substring(0, input.length() - 1);
        }

        try {
            long value = Long.parseLong(input);
            return value * multiplier;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String formatSeconds(long seconds) {
        if (seconds <= 0) return "0s";

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0 || sb.isEmpty()) sb.append(secs).append("s");

        return sb.toString().trim();
    }

    private boolean checkCooldown(Player player, String kitName) {
        if (player.hasPermission("friendlykits.admin") || player.hasPermission("friendlykits.bypass")) {
            return true;
        }

        long cooldownSeconds = kitsConfig.getLong("kits." + kitName + ".cooldown", 0);
        if (cooldownSeconds <= 0) {
            return true;
        }

        String key = player.getUniqueId().toString() + "_" + kitName.toLowerCase();
        long now = System.currentTimeMillis();

        if (cooldowns.containsKey(key)) {
            long lastClaim = cooldowns.get(key);
            long elapsedSeconds = (now - lastClaim) / 1000;

            if (elapsedSeconds < cooldownSeconds) {
                long remainingSeconds = cooldownSeconds - elapsedSeconds;
                player.sendMessage(getMsg("cooldown-active")
                        .replace("%kit%", kitName)
                        .replace("%time%", formatSeconds(remainingSeconds)));
                return false;
            }
        }

        return true;
    }

    private void setCooldown(Player player, String kitName) {
        String key = player.getUniqueId().toString() + "_" + kitName.toLowerCase();
        cooldowns.put(key, System.currentTimeMillis());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("kits")) {
            if (!player.hasPermission("friendlykits.use")) {
                player.sendMessage(getMsg("no-permission"));
                return true;
            }
            openKitsMenu(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("kit")) {
            if (args.length == 0) {
                if (player.hasPermission("friendlykits.admin")) {
                    player.sendMessage(ChatColor.YELLOW + "Admin: /kit <create|delete|reload|add timer> [params]");
                    player.sendMessage(ChatColor.GRAY + "Player: /kit <kitname>");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /kit <kitname>");
                }
                return true;
            }

            // أمر إضافة مؤقت
            if (args[0].equalsIgnoreCase("add")) {
                if (!player.hasPermission("friendlykits.admin")) {
                    player.sendMessage(getMsg("no-permission"));
                    return true;
                }

                if (args.length >= 4 && args[1].equalsIgnoreCase("timer")) {
                    String kitName = args[2].toLowerCase();
                    if (!kitsConfig.contains("kits." + kitName)) {
                        player.sendMessage(getMsg("kit-not-found"));
                        return true;
                    }

                    long seconds = parseTimeToSeconds(args[3]);
                    if (seconds < 0) {
                        player.sendMessage(getMsg("invalid-time"));
                        return true;
                    }

                    kitsConfig.set("kits." + kitName + ".cooldown", seconds);
                    saveKitsConfig();
                    player.sendMessage(getMsg("timer-set")
                            .replace("%kit%", kitName)
                            .replace("%time%", formatSeconds(seconds)));
                    return true;
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /kit add timer <kitname> <time>");
                    return true;
                }
            }

            // أمر إعادة التحميل
            if (args[0].equalsIgnoreCase("reload")) {
                if (!player.hasPermission("friendlykits.admin")) {
                    player.sendMessage(getMsg("no-permission"));
                    return true;
                }
                reloadConfig();
                kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
                player.sendMessage(getMsg("reload-success"));
                return true;
            }

            // أمر إنشاء الطقم (فقط عبر كلمة create الصريحة)
            if (args[0].equalsIgnoreCase("create")) {
                if (!player.hasPermission("friendlykits.admin")) {
                    player.sendMessage(getMsg("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Please specify a name: /kit create <name>");
                    return true;
                }

                String kitName = args[1].toLowerCase();
                saveKitFromPlayer(kitName, player);
                player.sendMessage(getMsg("kit-created").replace("%kit%", kitName));
                return true;
            }

            // أمر حذف طقم
            if (args[0].equalsIgnoreCase("delete")) {
                if (!player.hasPermission("friendlykits.admin")) {
                    player.sendMessage(getMsg("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Please specify a name: /kit delete <name>");
                    return true;
                }

                String kitName = args[1].toLowerCase();
                if (!kitsConfig.contains("kits." + kitName)) {
                    player.sendMessage(getMsg("kit-not-found"));
                    return true;
                }

                kitsConfig.set("kits." + kitName, null);
                saveKitsConfig();
                player.sendMessage(getMsg("kit-deleted").replace("%kit%", kitName));
                return true;
            }

            // استلام الطقم: /kit <kitname> (دون المساس ببيانات الطقم المحفوظة)
            String kitName = args[0].toLowerCase();
            if (kitsConfig.contains("kits." + kitName)) {
                String requiredPerm = "friendlykits.use." + kitName;
                if (!player.hasPermission(requiredPerm) && !player.hasPermission("friendlykits.admin")) {
                    player.sendMessage(getMsg("no-permission"));
                    return true;
                }

                if (!checkCooldown(player, kitName)) {
                    return true;
                }

                giveKit(player, kitName);
                setCooldown(player, kitName);
                return true;
            } else {
                player.sendMessage(getMsg("kit-not-found"));
                return true;
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player player)) return completions;

        if (command.getName().equalsIgnoreCase("kit")) {
            if (args.length == 1) {
                List<String> suggestions = new ArrayList<>();
                if (player.hasPermission("friendlykits.admin")) {
                    suggestions.add("create");
                    suggestions.add("delete");
                    suggestions.add("reload");
                    suggestions.add("add");
                }

                if (kitsConfig.isConfigurationSection("kits")) {
                    for (String kitName : kitsConfig.getConfigurationSection("kits").getKeys(false)) {
                        String perm = "friendlykits.use." + kitName.toLowerCase();
                        if (player.hasPermission(perm) || player.hasPermission("friendlykits.admin")) {
                            suggestions.add(kitName);
                        }
                    }
                }

                for (String s : suggestions) {
                    if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(s);
                    }
                }
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("add") && player.hasPermission("friendlykits.admin")) {
                    if ("timer".startsWith(args[1].toLowerCase())) completions.add("timer");
                } else if (args[0].equalsIgnoreCase("delete") && player.hasPermission("friendlykits.admin")) {
                    if (kitsConfig.isConfigurationSection("kits")) {
                        for (String kitName : kitsConfig.getConfigurationSection("kits").getKeys(false)) {
                            if (kitName.toLowerCase().startsWith(args[1].toLowerCase())) completions.add(kitName);
                        }
                    }
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("add") && args[1].equalsIgnoreCase("timer") && player.hasPermission("friendlykits.admin")) {
                if (kitsConfig.isConfigurationSection("kits")) {
                    for (String kitName : kitsConfig.getConfigurationSection("kits").getKeys(false)) {
                        if (kitName.toLowerCase().startsWith(args[2].toLowerCase())) completions.add(kitName);
                    }
                }
            } else if (args.length == 4 && args[0].equalsIgnoreCase("add") && args[1].equalsIgnoreCase("timer") && player.hasPermission("friendlykits.admin")) {
                List<String> times = Arrays.asList("30s", "1m", "5m", "30m", "1h", "24h", "1d");
                for (String t : times) {
                    if (t.startsWith(args[3].toLowerCase())) completions.add(t);
                }
            }
        }

        return completions;
    }

    /**
     * حفظ أغراض ودروع اللاعب واليد الإضافية باستخدام استنساخ حقيقي (Deep Clone)
     */
    private void saveKitFromPlayer(String kitName, Player player) {
        String basePath = "kits." + kitName;
        long existingCooldown = kitsConfig.getLong(basePath + ".cooldown", 0);
        ItemStack existingIcon = kitsConfig.getItemStack(basePath + ".icon");

        // مسح القسم القديم بالكامل لمنع تراكم أي عناصر قديمة
        kitsConfig.set(basePath, null);

        // 1. استنساخ وحفظ خانات الحقيبة
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack item = storage[i];
            if (item != null && item.getType() != Material.AIR) {
                kitsConfig.set(basePath + ".storage." + i, item.clone());
            }
        }

        // 2. استنساخ وحفظ الدروع الأربعة
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chest = player.getInventory().getChestplate();
        ItemStack legs = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();

        if (helmet != null && helmet.getType() != Material.AIR) kitsConfig.set(basePath + ".armor.helmet", helmet.clone());
        if (chest != null && chest.getType() != Material.AIR) kitsConfig.set(basePath + ".armor.chestplate", chest.clone());
        if (legs != null && legs.getType() != Material.AIR) kitsConfig.set(basePath + ".armor.leggings", legs.clone());
        if (boots != null && boots.getType() != Material.AIR) kitsConfig.set(basePath + ".armor.boots", boots.clone());

        // 3. استنساخ وحفظ اليد الإضافية
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            kitsConfig.set(basePath + ".offhand", offhand.clone());
        }

        // 4. تعيين الأيقونة
        if (existingIcon != null) {
            kitsConfig.set(basePath + ".icon", existingIcon.clone());
        } else {
            ItemStack hand = player.getInventory().getItemInMainHand();
            ItemStack icon = (hand != null && hand.getType() != Material.AIR) ? hand.clone() : new ItemStack(Material.CHEST);
            icon.setAmount(1);
            kitsConfig.set(basePath + ".icon", icon);
        }

        // الحفاظ على المؤقت المسجل
        kitsConfig.set(basePath + ".cooldown", existingCooldown);

        saveKitsConfig();
    }

    public void openKitsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, MAIN_MENU_TITLE);

        if (kitsConfig.isConfigurationSection("kits")) {
            for (String kitName : kitsConfig.getConfigurationSection("kits").getKeys(false)) {
                ItemStack icon = kitsConfig.getItemStack("kits." + kitName + ".icon");
                if (icon == null) icon = new ItemStack(Material.CHEST);
                icon = icon.clone();

                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + kitName.toUpperCase());
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Left-Click to preview and claim.");

                    long cd = kitsConfig.getLong("kits." + kitName + ".cooldown", 0);
                    if (cd > 0) {
                        lore.add(ChatColor.AQUA + "Timer: " + ChatColor.WHITE + formatSeconds(cd));
                    }

                    if (player.hasPermission("friendlykits.admin")) {
                        lore.add(ChatColor.YELLOW + "Right-Click with an item to change icon.");
                    }
                    meta.setLore(lore);
                    icon.setItemMeta(meta);
                }
                inv.addItem(icon);
            }
        }

        ItemStack filler = createFillerItem();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, filler);
            }
        }

        player.openInventory(inv);
    }

    public void openPreviewMenu(Player player, String kitName) {
        Inventory previewInv = Bukkit.createInventory(null, 54, PREVIEW_TITLE_PREFIX + kitName);
        String basePath = "kits." + kitName;

        if (kitsConfig.isConfigurationSection(basePath + ".storage")) {
            for (String key : kitsConfig.getConfigurationSection(basePath + ".storage").getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack item = kitsConfig.getItemStack(basePath + ".storage." + key);
                    if (slot < 36 && item != null) {
                        previewInv.setItem(slot, item.clone());
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        ItemStack helmet = kitsConfig.getItemStack(basePath + ".armor.helmet");
        ItemStack chest = kitsConfig.getItemStack(basePath + ".armor.chestplate");
        ItemStack legs = kitsConfig.getItemStack(basePath + ".armor.leggings");
        ItemStack boots = kitsConfig.getItemStack(basePath + ".armor.boots");

        if (helmet != null) previewInv.setItem(36, helmet.clone());
        if (chest != null) previewInv.setItem(37, chest.clone());
        if (legs != null) previewInv.setItem(38, legs.clone());
        if (boots != null) previewInv.setItem(39, boots.clone());

        ItemStack offhand = kitsConfig.getItemStack(basePath + ".offhand");
        if (offhand != null) previewInv.setItem(41, offhand.clone());

        ItemStack claimBtn = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta btnMeta = claimBtn.getItemMeta();
        if (btnMeta != null) {
            btnMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "CLICK TO CLAIM KIT");
            claimBtn.setItemMeta(btnMeta);
        }
        previewInv.setItem(53, claimBtn);

        ItemStack filler = createFillerItem();
        for (int i = 0; i < previewInv.getSize(); i++) {
            if (previewInv.getItem(i) == null || previewInv.getItem(i).getType() == Material.AIR) {
                previewInv.setItem(i, filler);
            }
        }

        player.openInventory(previewInv);
    }

    /**
     * إعطاء الطقم للاعب عبر استنساخ الأدوات بالكامل لعدم التعديل على الملف
     */
    private void giveKit(Player player, String kitName) {
        String basePath = "kits." + kitName;

        // 1. إضافة الأغراض للحقيبة بنسخ جديدة
        if (kitsConfig.isConfigurationSection(basePath + ".storage")) {
            for (String key : kitsConfig.getConfigurationSection(basePath + ".storage").getKeys(false)) {
                ItemStack item = kitsConfig.getItemStack(basePath + ".storage." + key);
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().addItem(item.clone());
                }
            }
        }

        // 2. إلباس الدروع بنسخ مستقلة
        ItemStack helmet = kitsConfig.getItemStack(basePath + ".armor.helmet");
        ItemStack chest = kitsConfig.getItemStack(basePath + ".armor.chestplate");
        ItemStack legs = kitsConfig.getItemStack(basePath + ".armor.leggings");
        ItemStack boots = kitsConfig.getItemStack(basePath + ".armor.boots");

        if (helmet != null && helmet.getType() != Material.AIR) {
            if (player.getInventory().getHelmet() == null || player.getInventory().getHelmet().getType() == Material.AIR) {
                player.getInventory().setHelmet(helmet.clone());
            } else {
                player.getInventory().addItem(helmet.clone());
            }
        }

        if (chest != null && chest.getType() != Material.AIR) {
            if (player.getInventory().getChestplate() == null || player.getInventory().getChestplate().getType() == Material.AIR) {
                player.getInventory().setChestplate(chest.clone());
            } else {
                player.getInventory().addItem(chest.clone());
            }
        }

        if (legs != null && legs.getType() != Material.AIR) {
            if (player.getInventory().getLeggings() == null || player.getInventory().getLeggings().getType() == Material.AIR) {
                player.getInventory().setLeggings(legs.clone());
            } else {
                player.getInventory().addItem(legs.clone());
            }
        }

        if (boots != null && boots.getType() != Material.AIR) {
            if (player.getInventory().getBoots() == null || player.getInventory().getBoots().getType() == Material.AIR) {
                player.getInventory().setBoots(boots.clone());
            } else {
                player.getInventory().addItem(boots.clone());
            }
        }

        // 3. إضافة أداة اليد الإضافية بنسخة مستقلة
        ItemStack offhand = kitsConfig.getItemStack(basePath + ".offhand");
        if (offhand != null && offhand.getType() != Material.AIR) {
            if (player.getInventory().getItemInOffHand().getType() == Material.AIR) {
                player.getInventory().setItemInOffHand(offhand.clone());
            } else {
                player.getInventory().addItem(offhand.clone());
            }
        }

        player.sendMessage(getMsg("kit-received").replace("%kit%", kitName));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            event.setCancelled(true);
            return;
        }

        if (title.equals(MAIN_MENU_TITLE)) {
            event.setCancelled(true);
            if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;

            String kitName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).toLowerCase();

            if (event.isRightClick()) {
                if (!player.hasPermission("friendlykits.admin")) {
                    player.sendMessage(getMsg("no-permission"));
                    return;
                }
                ItemStack handItem = player.getInventory().getItemInMainHand();
                if (handItem.getType() == Material.AIR) {
                    player.sendMessage(getMsg("hold-item-error"));
                    return;
                }
                ItemStack newIcon = handItem.clone();
                newIcon.setAmount(1);
                kitsConfig.set("kits." + kitName + ".icon", newIcon);
                saveKitsConfig();
                player.sendMessage(getMsg("icon-updated").replace("%kit%", kitName));
                openKitsMenu(player);
                return;
            }

            if (event.isLeftClick()) {
                openPreviewMenu(player, kitName);
            }
            return;
        }

        if (title.startsWith(PREVIEW_TITLE_PREFIX)) {
            event.setCancelled(true);

            if (event.getRawSlot() == 53) {
                String kitName = title.replace(PREVIEW_TITLE_PREFIX, "").toLowerCase();
                String requiredPerm = "friendlykits.use." + kitName;

                if (!player.hasPermission(requiredPerm) && !player.hasPermission("friendlykits.admin")) {
                    player.closeInventory();
                    player.sendMessage(getMsg("no-permission"));
                    return;
                }

                if (!checkCooldown(player, kitName)) {
                    player.closeInventory();
                    return;
                }

                player.closeInventory();
                giveKit(player, kitName);
                setCooldown(player, kitName);
            }
        }
    }
}
