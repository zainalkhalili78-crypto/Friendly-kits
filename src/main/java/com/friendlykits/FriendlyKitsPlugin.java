package com.friendlykits;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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
 * يدير تعبئة الخانات الفارغة بالزجاج الرمادي، دعم أمر /kit <name> المباشر، 
 * والتعامل الصامت مع غياب الصلاحيات، وإخفاء أوامر المشرفين عن اللاعبين العاديين.
 */
public class FriendlyKitsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private File kitsFile;
    private FileConfiguration kitsConfig;

    // عناوين النوافذ البصرية التفاعلية
    private final String MAIN_MENU_TITLE = ChatColor.DARK_GREEN + "Select a Kit";
    private final String PREVIEW_TITLE_PREFIX = ChatColor.DARK_BLUE + "Preview: ";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initKitsConfig();

        // تسجيل مستمع الأحداث ومسؤول الأوامر
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("kits") != null) getCommand("kits").setExecutor(this);
        if (getCommand("kit") != null) getCommand("kit").setExecutor(this);

        getLogger().info("Friendly-Kits has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        saveKitsConfig();
        getLogger().info("Friendly-Kits has been disabled.");
    }

    /**
     * إعداد ملف kits.yml
     */
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
        } catch (IOException e) {
            getLogger().severe("Could not save kits.yml!");
        }
    }

    /**
     * جلب النصوص الملونة من config.yml
     */
    public String getMsg(String path) {
        String msg = getConfig().getString("messages." + path, "");
        String prefix = getConfig().getString("messages.prefix", "");
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    /**
     * إنشاء عنصر لوح الزجاج الرمادي لتعبئة الفراغات
     */
    private ItemStack createFillerItem() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" "); // اسم فارغ تماماً ليبدو عنصراً خلفياً جمالياً
            glass.setItemMeta(meta);
        }
        return glass;
    }

    /**
     * معالجة الأوامر /kits و /kit
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        // أمر فتح قائمة الأطقم /kits
        if (command.getName().equalsIgnoreCase("kits")) {
            // صمت تام إذا لم يكن يملك الصلاحية
            if (!player.hasPermission("friendlykits.use")) {
                return true;
            }
            openKitsMenu(player);
            return true;
        }

        // معالجة الأمر /kit
        if (command.getName().equalsIgnoreCase("kit")) {
            // إذا كتب اللاعب /kit بمفرده
            if (args.length == 0) {
                // إظهار الخيارات بحسب الصلاحية
                if (player.hasPermission("friendlykits.admin")) {
                    player.sendMessage(ChatColor.YELLOW + "Admin Usage: /kit <create|delete|reload> [name]");
                    player.sendMessage(ChatColor.GRAY + "Player Usage: /kit <kitname>");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /kit <kitname>");
                }
                return true;
            }

            // أمر إعادة التحميل: /kit reload
            if (args[0].equalsIgnoreCase("reload")) {
                if (!player.hasPermission("friendlykits.admin")) {
                    return true; // صمت تام
                }
                reloadConfig();
                kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
                player.sendMessage(getMsg("reload-success"));
                return true;
            }

            // أمر إنشاء طقم: /kit create <name>
            if (args[0].equalsIgnoreCase("create")) {
                if (!player.hasPermission("friendlykits.admin")) {
                    return true; // صمت تام
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

            // أمر حذف طقم: /kit delete <name>
            if (args[0].equalsIgnoreCase("delete")) {
                if (!player.hasPermission("friendlykits.admin")) {
                    return true; // صمت تام
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

            // في حال كتابة /kit {kitname} لاستلام الطقم مباشرة
            String kitName = args[0].toLowerCase();
            if (kitsConfig.contains("kits." + kitName)) {
                String requiredPerm = "friendlykits.kit." + kitName;
                if (!player.hasPermission(requiredPerm) && !player.hasPermission("friendlykits.admin")) {
                    return true; // صمت تام عند انعدام الصلاحية للطقم
                }
                giveKit(player, kitName);
                return true;
            } else {
                player.sendMessage(getMsg("kit-not-found"));
                return true;
            }
        }

        return true;
    }

    /**
     * حفظ أغراض ودروع اللاعب واليد الإضافية في kits.yml
     */
    private void saveKitFromPlayer(String kitName, Player player) {
        String basePath = "kits." + kitName;
        kitsConfig.set(basePath, null);

        // 1. حفظ خانات الحقيبة
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack item = storage[i];
            if (item != null && item.getType() != Material.AIR) {
                kitsConfig.set(basePath + ".storage." + i, item);
            }
        }

        // 2. حفظ الدروع الأربعة
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chest = player.getInventory().getChestplate();
        ItemStack legs = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();

        if (helmet != null && helmet.getType() != Material.AIR) kitsConfig.set(basePath + ".armor.helmet", helmet);
        if (chest != null && chest.getType() != Material.AIR) kitsConfig.set(basePath + ".armor.chestplate", chest);
        if (legs != null && legs.getType() != Material.AIR) kitsConfig.set(basePath + ".armor.leggings", legs);
        if (boots != null && boots.getType() != Material.AIR) kitsConfig.set(basePath + ".armor.boots", boots);

        // 3. حفظ اليد الإضافية
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            kitsConfig.set(basePath + ".offhand", offhand);
        }

        // 4. تعيين الأيقونة الافتراضية
        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemStack icon = (hand != null && hand.getType() != Material.AIR) ? hand.clone() : new ItemStack(Material.CHEST);
        icon.setAmount(1);
        kitsConfig.set(basePath + ".icon", icon);

        saveKitsConfig();
    }

    /**
     * فتح قائمة الأطقم وتعبئة الخانات الفارغة بألواح زجاج رمادية
     */
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
                    if (player.hasPermission("friendlykits.admin")) {
                        lore.add(ChatColor.YELLOW + "Right-Click with an item to change icon.");
                    }
                    meta.setLore(lore);
                    icon.setItemMeta(meta);
                }
                inv.addItem(icon);
            }
        }

        // تعبئة كل خانة متبقية فارغة بلوح زجاج رمادي
        ItemStack filler = createFillerItem();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, filler);
            }
        }

        player.openInventory(inv);
    }

    /**
     * فتح واجهة معاينة محتويات الطقم مع ملء الفراغات بالزجاج الرمادي
     */
    public void openPreviewMenu(Player player, String kitName) {
        Inventory previewInv = Bukkit.createInventory(null, 54, PREVIEW_TITLE_PREFIX + kitName);
        String basePath = "kits." + kitName;

        // وضع محتويات الحقيبة
        if (kitsConfig.isConfigurationSection(basePath + ".storage")) {
            for (String key : kitsConfig.getConfigurationSection(basePath + ".storage").getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack item = kitsConfig.getItemStack(basePath + ".storage." + key);
                    if (slot < 36 && item != null) {
                        previewInv.setItem(slot, item);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // وضع الدروع في الخانات 36 إلى 39
        ItemStack helmet = kitsConfig.getItemStack(basePath + ".armor.helmet");
        ItemStack chest = kitsConfig.getItemStack(basePath + ".armor.chestplate");
        ItemStack legs = kitsConfig.getItemStack(basePath + ".armor.leggings");
        ItemStack boots = kitsConfig.getItemStack(basePath + ".armor.boots");

        if (helmet != null) previewInv.setItem(36, helmet);
        if (chest != null) previewInv.setItem(37, chest);
        if (legs != null) previewInv.setItem(38, legs);
        if (boots != null) previewInv.setItem(39, boots);

        // وضع اليد الإضافية في الخانة 41
        ItemStack offhand = kitsConfig.getItemStack(basePath + ".offhand");
        if (offhand != null) previewInv.setItem(41, offhand);

        // زر استلام الطقم في الخانة 53 (الزاوية السفلية اليمنى)
        ItemStack claimBtn = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta btnMeta = claimBtn.getItemMeta();
        if (btnMeta != null) {
            btnMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "CLICK TO CLAIM KIT");
            claimBtn.setItemMeta(btnMeta);
        }
        previewInv.setItem(53, claimBtn);

        // ملء أي خانة فارغة في الواجهة بالزجاج الرمادي
        ItemStack filler = createFillerItem();
        for (int i = 0; i < previewInv.getSize(); i++) {
            if (previewInv.getItem(i) == null || previewInv.getItem(i).getType() == Material.AIR) {
                previewInv.setItem(i, filler);
            }
        }

        player.openInventory(previewInv);
    }

    /**
     * منح محتويات الطقم والدروع واليد الإضافية للاعب
     */
    private void giveKit(Player player, String kitName) {
        String basePath = "kits." + kitName;

        // 1. منح عناصر الحقيبة
        if (kitsConfig.isConfigurationSection(basePath + ".storage")) {
            for (String key : kitsConfig.getConfigurationSection(basePath + ".storage").getKeys(false)) {
                ItemStack item = kitsConfig.getItemStack(basePath + ".storage." + key);
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().addItem(item.clone());
                }
            }
        }

        // 2. إلباس الدروع أو وضعها بالحقيبة
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

        // 3. منح اليد الإضافية
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

    /**
     * معالجة النقر داخل النوافذ وحماية الألواح والعناصر
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // إلغاء أي تفاعل مع ألواح الزجاج الرمادية في أي واجهة
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            event.setCancelled(true);
            return;
        }

        // التفاعل داخل القائمة الرئيسية /kits
        if (title.equals(MAIN_MENU_TITLE)) {
            event.setCancelled(true);
            if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;

            String kitName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).toLowerCase();

            // كليك يمين: تغيير الأيقونة للمشرف
            if (event.isRightClick()) {
                if (!player.hasPermission("friendlykits.admin")) {
                    return; // صمت تام
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
                openKitsMenu(player); // تحديث الواجهة
                return;
            }

            // كليك يسار: معاينة الطقم
            if (event.isLeftClick()) {
                openPreviewMenu(player, kitName);
            }
            return;
        }

        // التفاعل داخل نافذة المعاينة
        if (title.startsWith(PREVIEW_TITLE_PREFIX)) {
            event.setCancelled(true); // منع أخذ أي عنصر من المعاينة

            // النقر على زر استلام الطقم الأخضر (الخانة 53)
            if (event.getRawSlot() == 53) {
                String kitName = title.replace(PREVIEW_TITLE_PREFIX, "").toLowerCase();
                String requiredPerm = "friendlykits.kit." + kitName;

                // التحقق من الصلاحية (صمت تام إذا كان لا يملكها)
                if (!player.hasPermission(requiredPerm) && !player.hasPermission("friendlykits.admin")) {
                    player.closeInventory();
                    return;
                }

                player.closeInventory();
                giveKit(player, kitName);
            }
        }
    }
}
