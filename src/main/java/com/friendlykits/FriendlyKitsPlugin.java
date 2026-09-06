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
 * الكلاس الرئيسي لإضافة Friendly-Kits: يدير إنشاء الأطقم، وحفظها، وعرضها للمستخدمين
 */
public class FriendlyKitsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private File kitsFile;
    private FileConfiguration kitsConfig;

    // عناوين النوافذ البصرية
    private final String MAIN_MENU_TITLE = ChatColor.DARK_GREEN + "Select a Kit";
    private final String PREVIEW_TITLE_PREFIX = ChatColor.DARK_BLUE + "Preview: ";

    @Override
    public void onEnable() {
        // حفظ ملف config.yml الافتراضي
        saveDefaultConfig();

        // إعداد ملف kits.yml لتخزين بيانات الأطقم
        initKitsConfig();

        // تسجيل مستمع الأحداث والأوامر
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
     * إعداد وإنشاء ملف kits.yml
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

    /**
     * حفظ التعديلات داخل kits.yml
     */
    public void saveKitsConfig() {
        try {
            kitsConfig.save(kitsFile);
        } catch (IOException e) {
            getLogger().severe("Could not save kits.yml!");
        }
    }

    /**
     * دالة جلب النصوص الملونة من config.yml
     */
    public String getMsg(String path) {
        String msg = getConfig().getString("messages." + path, "");
        String prefix = getConfig().getString("messages.prefix", "");
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
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

        // أمر فتح قائمة الأطقم للمستخدم العادي
        if (command.getName().equalsIgnoreCase("kits")) {
            if (!player.hasPermission("friendlykits.use")) {
                player.sendMessage(getMsg("no-permission"));
                return true;
            }
            openKitsMenu(player);
            return true;
        }

        // أوامر الإدارة الخاصة بـ /kit
        if (command.getName().equalsIgnoreCase("kit")) {
            if (args.length == 0) {
                player.sendMessage(ChatColor.YELLOW + "Usage: /kit <create|delete|reload> [name]");
                return true;
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

            // أمر إنشاء طقم جديد من حقيبة المشرف
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

            // أمر حذف طقم معين
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
        }

        return true;
    }

    /**
     * حفظ عناصر وحقيبة ودروع اللاعب بالكامل داخل kits.yml
     */
    private void saveKitFromPlayer(String kitName, Player player) {
        String path = "kits." + kitName + ".";

        // حفظ محتويات الحقيبة والدروع واليد الإضافية
        kitsConfig.set(path + "inventory", player.getInventory().getStorageContents());
        kitsConfig.set(path + "armor", player.getInventory().getArmorContents());
        kitsConfig.set(path + "offhand", player.getInventory().getItemInOffHand());

        // تعيين الأيقونة الافتراضية
        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemStack icon = (hand != null && hand.getType() != Material.AIR) ? hand.clone() : new ItemStack(Material.CHEST);
        icon.setAmount(1);
        kitsConfig.set(path + "icon", icon);

        saveKitsConfig();
    }

    /**
     * فتح واجهة عرض قائمة الأطقم للمستخدم
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
        player.openInventory(inv);
    }

    /**
     * فتح نافذة معاينة الطقم قبل استلامه
     */
    public void openPreviewMenu(Player player, String kitName) {
        Inventory previewInv = Bukkit.createInventory(null, 54, PREVIEW_TITLE_PREFIX + kitName);
        String path = "kits." + kitName + ".";

        // استرجاع أغراض الحقيبة
        List<?> storageItems = kitsConfig.getList(path + "inventory");
        if (storageItems != null) {
            for (int i = 0; i < Math.min(storageItems.size(), 36); i++) {
                if (storageItems.get(i) instanceof ItemStack item) {
                    previewInv.setItem(i, item);
                }
            }
        }

        // استرجاع الدروع في الخانات 36 إلى 39
        List<?> armorItems = kitsConfig.getList(path + "armor");
        if (armorItems != null) {
            for (int i = 0; i < armorItems.size(); i++) {
                if (armorItems.get(i) instanceof ItemStack armor) {
                    previewInv.setItem(36 + i, armor);
                }
            }
        }

        // استرجاع أداة اليد الإضافية Offhand في الخانة 41
        ItemStack offhand = kitsConfig.getItemStack(path + "offhand");
        if (offhand != null) {
            previewInv.setItem(41, offhand);
        }

        // زر استلام الطقم: لوح زجاجي أخضر في الزاوية السفلية اليمنى (الخانة 53)
        ItemStack claimButton = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = claimButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "CLICK TO CLAIM KIT");
            claimButton.setItemMeta(meta);
        }
        previewInv.setItem(53, claimButton);

        player.openInventory(previewInv);
    }

    /**
     * إعطاء محتويات الطقم كاملة للاعب
     */
    private void giveKit(Player player, String kitName) {
        String path = "kits." + kitName + ".";

        // إضافة محتويات الحقيبة
        List<?> storageItems = kitsConfig.getList(path + "inventory");
        if (storageItems != null) {
            for (Object obj : storageItems) {
                if (obj instanceof ItemStack item && item.getType() != Material.AIR) {
                    player.getInventory().addItem(item.clone());
                }
            }
        }

        // تجهيز الدروع للاعب إن كانت خاناته فارغة
        List<?> armorItems = kitsConfig.getList(path + "armor");
        if (armorItems != null) {
            ItemStack[] currentArmor = player.getInventory().getArmorContents();
            for (int i = 0; i < armorItems.size(); i++) {
                if (armorItems.get(i) instanceof ItemStack armor && armor.getType() != Material.AIR) {
                    if (i < currentArmor.length && (currentArmor[i] == null || currentArmor[i].getType() == Material.AIR)) {
                        currentArmor[i] = armor.clone();
                    } else {
                        player.getInventory().addItem(armor.clone());
                    }
                }
            }
            player.getInventory().setArmorContents(currentArmor);
        }

        // تجهيز اليد الإضافية
        ItemStack offhand = kitsConfig.getItemStack(path + "offhand");
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
     * معالجة النقر داخل واجهات القوائم
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // عند التفاعل في القائمة الرئيسية /kits
        if (title.equals(MAIN_MENU_TITLE)) {
            event.setCancelled(true);
            ItemStack currentItem = event.getCurrentItem();
            if (currentItem == null || !currentItem.hasItemMeta() || !currentItem.getItemMeta().hasDisplayName()) return;

            String kitName = ChatColor.stripColor(currentItem.getItemMeta().getDisplayName()).toLowerCase();

            // النقر بزر الفأرة الأيمن: تغيير الأيقونة للمشرفين
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
                openKitsMenu(player); // تحديث القائمة فورياً
                return;
            }

            // النقر بزر الفأرة الأيسر: فتح نافذة المعاينة
            if (event.isLeftClick()) {
                openPreviewMenu(player, kitName);
            }
            return;
        }

        // عند التفاعل في نافذة المعاينة
        if (title.startsWith(PREVIEW_TITLE_PREFIX)) {
            event.setCancelled(true); // منع أخذ الأغراض من نافذة المعاينة

            // فحص النقر على الزاوية السفلية اليمنى (الخانة 53)
            if (event.getRawSlot() == 53) {
                String kitName = title.replace(PREVIEW_TITLE_PREFIX, "").toLowerCase();

                // التحقق من الصلاحية المستقلة لكل طقم
                String requiredPerm = "friendlykits.kit." + kitName;
                if (!player.hasPermission(requiredPerm) && !player.hasPermission("friendlykits.admin")) {
                    player.closeInventory();
                    player.sendMessage(getMsg("no-permission"));
                    return;
                }

                player.closeInventory();
                giveKit(player, kitName);
            }
        }
    }
}
