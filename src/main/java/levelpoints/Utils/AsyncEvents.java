package levelpoints.Utils;


import com.sk89q.worldedit.internal.expression.runtime.For;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import levelpoints.Cache.FileCache;
import levelpoints.Containers.BoostersContainer;
import levelpoints.Containers.LevelsContainer;
import levelpoints.Containers.PlayerContainer;
import levelpoints.events.CustomEvents.*;
import levelpoints.levelpoints.Formatting;
import levelpoints.levelpoints.LevelPoints;
import levelpoints.levelpoints.SQL;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

public class AsyncEvents {

    static Plugin plugin = LevelPoints.getInstance();
    static HashSet<UUID> playersInCache = new HashSet<>();
    public static HashMap<UUID, PlayerContainer> playerContainers = new HashMap<>();
    static HashMap<UUID, Integer> LevelsCache = new HashMap<>();
    static HashMap<UUID, Integer> ids = new HashMap<>();
    static HashMap<Player, Integer> Multipliers = new HashMap<>();


    public static void updateLevelInCache(UUID uuid, Integer value){
        LevelsCache.put(uuid, value);
        float percentage = (float) AsyncEvents.getPlayerContainer(uuid).getEXP();
        double val = (percentage / AsyncEvents.getPlayerContainer(uuid).getRequiredEXP());
        if (!Bukkit.getVersion().contains("1.8")) {
            if (Bukkit.getPlayer(uuid) != null) {
                MessagesUtil.sendBossBar(Bukkit.getPlayer(uuid),
                        FileCache.getConfig("langConfig").getString("Formats.LevelUp.BossBar.Text"),
                        BarColor.valueOf(LevelPoints.getInstance().getConfig().getString("BossBarColor")),
                        BarStyle.SOLID,
                        val);
            }
        }

    }
    public static Integer getLevelTopInCache(Player player){
        return LevelsCache.get(player);
    }
    public static Boolean isInTopCache(UUID uuid){
        return LevelsCache.containsKey(uuid);
    }
    public static void startVersionCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getConfig().getBoolean("CheckUpdates")) {
                    plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_AQUA + "Checking for Updates...");
                    String key = "key=98BE0FE67F88AB82B4C197FAF1DC3B69206EFDCC4D3B80FC83A00037510B99B4&resource=";
                    try {
                        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.spigotmc.org/legacy/update.php?resource=63996").openConnection();
                        String version = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();
                        connection.disconnect();

                        if (plugin.getDescription().getVersion().equalsIgnoreCase(version)) {
                            plugin.getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "No New Updates for LevelPoints");

                            return;
                        } else {
                            plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + "You do not have the most updated version of LevelPoints");
                            plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + "Download it from: https://www.spigotmc.org/resources/levelpoints-1-8-1-15-mysql.63996/");
                        }
                    } catch (IOException e) {
                        plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + "Couldnt connect");
                        e.printStackTrace();
                    }
                } else {
                    plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_RED + "You Are Not Checking For LevelPoints Updates");
                    return;
                }
            }
        }.runTaskAsynchronously(LevelPoints.getInstance());
    }


    public static void RunPlugin() {
        new BukkitRunnable() {

            @Override
            public void run() {
                UtilCollector.RunModuels();
            }
        }.runTaskAsynchronously(plugin);
    }
    public static void addPlayerToContainerCache(UUID player){
        playersInCache.add(player);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!AsyncEvents.isInTopCache(player)) {
                    AsyncEvents.updateLevelInCache(player, AsyncEvents.getPlayerContainer(player).getLevel());
                }
            }
        }.runTaskLaterAsynchronously(LevelPoints.getInstance(), 30);

    }
    public static void removePlayerFromContainerCache(UUID uuid){
        playersInCache.remove(uuid);
        playerContainers.remove(uuid);
    }
    public static Boolean isPlayersCacheEmpty(){
        return playersInCache.isEmpty();
    }
    public static void MassSaveCache() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if(LevelPoints.getInstance().getConfig().getBoolean("UseSQL")) {
                SQL.RunSQLDownload(player);
            }

            PlayerContainer container = getPlayerContainer(player.getUniqueId());
            container.saveCacheToFile();
        }
        playersInCache.clear();
        playerContainers.clear();
    }

    public static int getRankMultiplier(Player player) {
        ConfigurationSection cs = FileCache.getConfig("rankMultiplierConfig").getConfigurationSection("");

        for (String x : cs.getKeys(false)) {

            boolean perm = player.hasPermission(FileCache.getConfig("rankMultiplierConfig").getString(x + ".Permission"));
            if (perm) {

                if (Multipliers.isEmpty() || !Multipliers.containsKey(player)) {
                    Multipliers.put(player, FileCache.getConfig("rankMultiplierConfig").getInt(x + ".Multiplier"));
                } else {
                    if (Multipliers.get(player) < FileCache.getConfig("rankMultiplierConfig").getInt(x + ".Multiplier")) {

                        Multipliers.put(player, FileCache.getConfig("rankMultiplierConfig").getInt(x + ".Multiplier"));
                    }
                }
            }


        }
        if(Multipliers.isEmpty() || !Multipliers.containsKey(player)){
            Multipliers.put(player, 1);
        }
        return Multipliers.get(player);
    }

    public static String getProgressBar(Player player) {
        PlayerContainer container = AsyncEvents.getPlayerContainer(player.getUniqueId());

        double  required_progress = container.getRequiredEXP();
        double current_progress = container.getEXP();
        double progress_percentage = current_progress / required_progress;
        StringBuilder sb = new StringBuilder();
        int bar_length = LevelPoints.getInstance().getConfig().getInt("ProgressBarSize");
        String completed = Formatting.basicColor(FileCache.getConfig("langConfig").getString("ProgressBar.Complete"));
        String need = Formatting.basicColor(FileCache.getConfig("langConfig").getString("ProgressBar.Required"));
        for (int i = 0; i < bar_length; i++) {
            if (i < bar_length * progress_percentage) {
                sb.append(completed); //what to append if percentage is covered (e.g. GREEN '|'s)
            } else {
                sb.append(need); //what to append if percentage is not covered (e.g. GRAY '|'s)
            }
        }
        return sb.toString();
    }

    public static Boolean isPlayerInCache(Player player){
        return playersInCache.contains(player.getUniqueId());
    }
    public static void RunSaveCache(Player player) {
        PlayerContainer container = getPlayerContainer(player.getUniqueId());
        container.saveCacheToFile();
    }
    public static void RunPlayerCache(UUID uuid, String playerName) {
        new BukkitRunnable() {

            @Override
            public void run() {
                File userFile = new File(LevelPoints.getUserFolder(), uuid + ".yml");
                if (Bukkit.getPlayer(uuid) != null) {
                    if (!userFile.exists()) {
                        try {
                            userFile.createNewFile();
                            LevelPoints.getInstance().getServer().getConsoleSender().sendMessage(Formatting.basicColor("&bCreated File: &3" + uuid));
                        } catch (IOException ex) {
                            LevelPoints.getInstance().getLogger().log(Level.SEVERE, ChatColor.DARK_RED + "Can't create " + playerName + " user file");
                        }
                    }
                }
            }
        }.runTaskAsynchronously(plugin);
    }


    public static void LoadPlayerData(UUID uuid, String name){

        File userdata = new File(LevelPoints.getUserFolder(), uuid + ".yml");
        FileConfiguration UsersConfig = YamlConfiguration.loadConfiguration(userdata);

        new BukkitRunnable(){

            @Override
            public void run() {

                if (!UsersConfig.contains("Name")) {

                    LevelPoints.getInstance().getServer().getConsoleSender().sendMessage(Formatting.basicColor("&3Creating Player Data within file"));
                    UsersConfig.set("Name", name);

                    UsersConfig.set("Level", LevelsContainer.getStartingLevel());
                    UsersConfig.set("EXP.Amount", LevelsContainer.getStartingEXP());
                    UsersConfig.set("Prestige", 0);
                    UsersConfig.set("ActionBar", true);
                    UsersConfig.set("ActiveBooster", 1);
                    UsersConfig.set("BoosterOff", LevelPoints.getCurrentDate());


                    try {
                        UsersConfig.save(userdata);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    LoadPlayerData(uuid, name);

                } else {
                    LevelPoints.getInstance().getServer().getConsoleSender().sendMessage(Formatting.basicColor("&3Loading Player Data"));
                    if (!UsersConfig.getString("Name").equals(name)) {
                        LevelPoints.getInstance().getServer().getConsoleSender().sendMessage(Formatting.basicColor("&4Found a new username"));
                        UsersConfig.set("Name", name);
                        try {
                            UsersConfig.save(userdata);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    FileCache.addFileToCache(uuid.toString(), UsersConfig);
                    Integer level = FileCache.getConfig(uuid.toString()).getInt("Level");
                    Double exp = FileCache.getConfig(uuid.toString()).getDouble("EXP.Amount");
                    Integer prestige = FileCache.getConfig(uuid.toString()).getInt("Prestige");
                    Double value = FileCache.getConfig(uuid.toString()).getDouble("ActiveBooster");
                    String date = FileCache.getConfig(uuid.toString()).getString("BoosterOff");
                    SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyy HH:mm:ss");
                    Date finish = null;
                    try {
                         finish = format.parse(date);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    playerContainers.put(uuid, new PlayerContainer(uuid, level, exp, value, finish, UtilCollector.registerRequiredEXP(level), prestige));
                    addPlayerToContainerCache(uuid);
                    getPlayerContainer(uuid).setMultiplier(UsersConfig.getDouble("ActiveBooster"));
                    int i = 0;
                    if(UsersConfig.getConfigurationSection("").getKeys(false).contains("Boosters")) {
                        for (String x : UsersConfig.getConfigurationSection("Boosters").getKeys(false)) {
                            double multiplier = UsersConfig.getDouble("Boosters." + x + ".Multiplier");
                            String time = UsersConfig.getString("Boosters." + x + ".Time");
                            int amount = UsersConfig.getInt("Boosters." + x + ".Amount");
                            i++;
                            ids.put(uuid, i);
                            getPlayerContainer(uuid).giveBooster(multiplier, time, amount);
                        }
                    }
                    LevelPoints.getInstance().getServer().getConsoleSender().sendMessage(Formatting.basicColor("&bLoaded Data Successfully"));
                    getPlayerContainer(uuid).setXpBar();
                }

            }
        }.runTaskAsynchronously(plugin);
    }
    public static Integer getIds(UUID uuid){
        if(ids.isEmpty() || !ids.containsKey(uuid)){
            ids.put(uuid, 0);
        }
        return ids.get(uuid);
    }
    public static void addIds(UUID uuid){
        ids.put(uuid, getIds(uuid) + 1);
    }
    public static PlayerContainer getPlayerContainer(UUID uuid){

        return playerContainers.get(uuid);
    }
    public static PlayerContainer getPlayerContainer(Player uuid){
        return playerContainers.get(uuid.getUniqueId());
    }
    public static void triggerEarnEXPEvent(TasksEnum task, Event event, double amount, Player player) {

        new BukkitRunnable() {
            @Override
            public void run() {
                EarnExpEvent expEvent = new EarnExpEvent(task, event, amount, player);
                Bukkit.getPluginManager().callEvent(expEvent);
                if (!expEvent.isCancelled()) {
                    PlayerContainer playerContainer = getPlayerContainer(player.getUniqueId());
                    playerContainer.addEXP(amount);
                }
            }
        }.runTaskAsynchronously(LevelPoints.getInstance());
    }
    public static void triggerBoosterActivation(double multiplier, String time, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                BoosterActivationEvent boosterEvent = new BoosterActivationEvent(multiplier, time, player);

                Bukkit.getPluginManager().callEvent(boosterEvent);
                if (!boosterEvent.isCancelled()) {
                    PlayerContainer playerContainer = getPlayerContainer(player.getUniqueId());
                    playerContainer.removeBooster(multiplier, time);
                    playerContainer.setBoosterDate(time);
                    playerContainer.setMultiplier(multiplier);
                    if(FileCache.getConfig("langConfig").getBoolean("Booster.Enabled")) {
                        for (String x : FileCache.getConfig("langConfig").getStringList("Booster.ActivateBooster")) {
                            player.sendMessage(Formatting.basicColor(x).replace("{lp_booster_multiplier}", String.valueOf(multiplier)).replace("{lp_booster_time}", time));
                        }
                    }
                }else{
                    for (String x : FileCache.getConfig("langConfig").getStringList("Booster.AlreadyInUse")) {
                        player.sendMessage(Formatting.basicColor(x));
                    }
                }
            }
        }.runTaskAsynchronously(LevelPoints.getInstance());
    }
    public static void triggerLevelUpEvent(Player player, int level) {
        new BukkitRunnable() {
            @Override
            public void run() {
                LevelUpEvent levelEvent = new LevelUpEvent(player, level);
                Bukkit.getPluginManager().callEvent(levelEvent);
            }
        }.runTaskAsynchronously(LevelPoints.getInstance());


    }

    public static double getLevelEXP(String name, int level, FileConfiguration configuration){

        for(String x : configuration.getConfigurationSection("").getKeys(false)){
            if(x.equalsIgnoreCase(name)){
                for(String xx : configuration.getConfigurationSection(x + ".Levels").getKeys(false)){
                    if(Integer.parseInt(xx) == level){
                        return configuration.getDouble(x + ".Levels." + level + ".EXP");
                    }
                }
            }
        }
        return 0;
    }
    public static Integer getLevelRequired(String name, int level, FileConfiguration configuration){

        for(String x : configuration.getConfigurationSection("").getKeys(false)){
            if(x.equalsIgnoreCase(name)){
                for(String xx : configuration.getConfigurationSection(x + ".Levels").getKeys(false)){
                    if(Integer.parseInt(xx) == level){
                        return configuration.getInt(x + ".Levels." + level + ".Required");
                    }
                }
            }
        }
        return 0;
    }


    public static Boolean canEnterRegion(Player player, Block block) {
        boolean value = false;
        WorldGuardAPI worldGuardAPI = new WorldGuardAPI(LevelPoints.getInstance().getServer().getPluginManager().getPlugin("WorldGuard"), LevelPoints.getInstance());
        ApplicableRegionSet checkSet = worldGuardAPI.getRegionSet(block.getLocation());
        if(checkSet == null || worldGuardAPI == null){
            return false;
        }
        if (!checkSet.getRegions().isEmpty()) {
            for (ProtectedRegion x : checkSet.getRegions()) {
                if (FileCache.getConfig("expConfig").getConfigurationSection("Anti-Abuse.WorldGuard.LevelRegions.Regions").getKeys(false).contains(x.getId())) {
                    if (getPlayerContainer(player.getUniqueId()).getLevel() >= FileCache.getConfig("expConfig").getInt("Anti-Abuse.WorldGuard.LevelRegions.Regions." + x.getId() + ".Level.Min") && getPlayerContainer(player.getUniqueId()).getLevel() <= FileCache.getConfig("expConfig").getInt("Anti-Abuse.WorldGuard.LevelRegions.Regions." + x.getId() + ".Level.Max")) {
                        value = true;
                    }else{
                        value = false;
                    }
                } else {
                    value = true;
                }
            }
        } else {
            value = true;
        }
        return value;
    }

    public static Location getTeleportLocation(Player player) {
        WorldGuardAPI worldGuardAPI = new WorldGuardAPI(LevelPoints.getInstance().getServer().getPluginManager().getPlugin("WorldGuard"), LevelPoints.getInstance());
        ApplicableRegionSet checkSet = worldGuardAPI.getRegionSet(player.getLocation());

        if (!checkSet.getRegions().isEmpty()) {
            for (ProtectedRegion x : checkSet.getRegions()) {
                if (FileCache.getConfig("expConfig").getConfigurationSection("Anti-Abuse.WorldGuard.LevelRegions.Regions").getKeys(false).contains(x.getId())) {
                    if (FileCache.getConfig("expConfig").getBoolean("Anti-Abuse.WorldGuard.LevelRegions.Regions." + x.getId() + ".Teleport.Enabled")) {

                        Location loc = new Location(player.getWorld(),
                                FileCache.getConfig("expConfig").getInt("Anti-Abuse.WorldGuard.LevelRegions.Regions." + x.getId() + ".Teleport.Location.x"),
                                FileCache.getConfig("expConfig").getInt("Anti-Abuse.WorldGuard.LevelRegions.Regions." + x.getId() + ".Teleport.Location.y"),
                                FileCache.getConfig("expConfig").getInt("Anti-Abuse.WorldGuard.LevelRegions.Regions." + x.getId() + ".Teleport.Location.z"),
                                player.getLocation().getYaw(),
                                player.getLocation().getPitch());


                        return loc;
                    }
                }
            }
        }

        return null;
    }

    public static Boolean isInRegion(Player player, Block block) {
        boolean value = false;
        WorldGuardAPI worldGuardAPI = new WorldGuardAPI(LevelPoints.getInstance().getServer().getPluginManager().getPlugin("WorldGuard"), LevelPoints.getInstance());
        ApplicableRegionSet checkSet = worldGuardAPI.getRegionSet(block.getLocation());
        if (!checkSet.getRegions().isEmpty()) {
            for (ProtectedRegion regions : checkSet) {
                if(FileCache.getConfig("expConfig").getBoolean("Anti-Abuse.WorldGuard.RestrictedRegions.WorldSupport.Enabled")) {
                    if (FileCache.getConfig("expConfig").getStringList("Anti-Abuse.WorldGuard.RestrictedRegions.WorldSupport.List").contains(block.getWorld().getName())) {
                        if(!regions.getOwners().contains(player.getUniqueId())){
                            value = true;
                        }
                    }
                }
                if (FileCache.getConfig("expConfig").getBoolean("Anti-Abuse.WorldGuard.RestrictedRegions.Whitelist.Enabled")) {
                    if (FileCache.getConfig("expConfig").getStringList("Anti-Abuse.WorldGuard.RestrictedRegions.Whitelist.List").contains(regions.getId())) {
                        value = true;
                    }
                } else if (FileCache.getConfig("expConfig").getBoolean("Anti-Abuse.WorldGuard.RestrictedRegions.Blacklist.Enabled")){
                    value = true;
                    if (FileCache.getConfig("expConfig").getStringList("Anti-Abuse.WorldGuard.RestrictedRegions.Blacklist.List").contains(regions.getId())) {
                        return false;
                    }
                }
            }
        }
        return value;
    }
    public static void giveTimedEXP() {
        if (FileCache.getConfig("expConfig").getBoolean("TimedEXP.Enabled")) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player x : Bukkit.getOnlinePlayers()) {
                        PlayerContainer container = getPlayerContainer(x.getUniqueId());
                        container.addEXP(FileCache.getConfig("expConfig").getDouble("TimedEXP.Amount"));

                        x.sendMessage(Formatting.basicColor(FileCache.getConfig("expConfig").getString("TimedEXP.Message")
                                .replace("{lp_timed_amount}", String.valueOf(FileCache.getConfig("expConfig").getDouble("TimedEXP.Amount")))
                                .replace("{lp_timed_delay}", String.valueOf(FileCache.getConfig("expConfig").getInt("TimedEXP.Delay")))));

                    }
                }
            }.runTaskTimerAsynchronously(LevelPoints.getInstance(),0, 20 * FileCache.getConfig("expConfig").getInt("TimedEXP.Delay"));
        }
    }
    public static void giveReward(Player player, int value, RewardsType rewardsType) {

        PlayerContainer container = getPlayerContainer(player.getUniqueId());
        new BukkitRunnable() {
            @Override
            public void run() {
                switch (rewardsType) {
                    case One:
                        List<String> cmd = FileCache.getConfig("rewardsConfig").getStringList("Rewards.Prestige-0.Level-1.cmds");
                        for (String x : cmd) {
                            runReward(x.replace("%player%", player.getName()));
                        }
                        if (FileCache.getConfig("rewardsConfig").getBoolean("Settings.Message.Global")) {
                            MessageType type = MessageType.valueOf(FileCache.getConfig("rewardsConfig")
                                    .getString("Settings.Message.Type"));
                            runRewardsFormat(player, type, value);
                        } else {
                            runMessage(player, MessageType.valueOf(FileCache.getConfig("rewardsConfig")
                                    .getString("Settings.Message.Type")), FileCache.getConfig("rewardsConfig")
                                    .getString("Rewards.Prestige-" + container.getPrestige() +
                                            ".Level-" + container.getLevel() + ".Message").replace("{lp_Reward}", String.valueOf(value)));
                        }
                        break;
                    case Regular:

                        ConfigurationSection configuration = FileCache.getConfig("rewardsConfig").getConfigurationSection("Rewards.Prestige-" + container.getPrestige() + ".Level-" + value);

                        if (configuration == null) {
                            break;
                        }

                        List<String> cm = FileCache.getConfig("rewardsConfig").getStringList("Rewards.Prestige-" + container.getPrestige() + ".Level-" + value + ".cmds");
                        for (String x : cm) {

                            runReward(x.replace("%player%", player.getName()));
                        }
                        if (FileCache.getConfig("rewardsConfig").getBoolean("Settings.Message.Global")) {
                            MessageType type = MessageType.valueOf(FileCache.getConfig("rewardsConfig")
                                    .getString("Settings.Message.Type"));
                            runRewardsFormat(player, type, value);
                        } else {

                            runMessage(player, MessageType.valueOf(FileCache.getConfig("rewardsConfig")
                                    .getString("Settings.Message.Type")), FileCache.getConfig("rewardsConfig")
                                    .getString("Rewards.Prestige-" + container.getPrestige() +
                                            ".Level-" + container.getLevel() + ".Message").replace("{lp_Reward}", String.valueOf(value)));

                        }
                        break;
                    case FileChance:
                        break;
                }
            }
        }.runTask(LevelPoints.getInstance());

    }
    public static void runReward(String cmd){
        Bukkit.dispatchCommand(LevelPoints.getInstance().getServer().getConsoleSender(), cmd);
    }
    private static void runRewardsFormat(Player player, MessageType type, int value){
        String message = "";
        switch (type){
            case Chat:
            case Actionbar:
                message = FileCache.getConfig("langConfig")
                        .getString("Formats.Rewards." + type.toString()).replace("{lp_Reward}", String.valueOf(value));
                runMessage(player, type, message);
                break;
            case Title:
                String top = FileCache.getConfig("langConfig")
                        .getString("Formats.Rewards." + type.toString() + ".Top")
                        .replace("{lp_Reward}", String.valueOf(value))
                        .replace("{lp_player}", player.getName());
                String bottom = FileCache.getConfig("langConfig")
                        .getString("Formats.Rewards." + type.toString() + ".Bottom")
                        .replace("{lp_Reward}", String.valueOf(value))
                        .replace("{lp_player}", player.getName());
                runMessage(player, type, Formatting.basicColor(top), Formatting.basicColor(bottom));
                break;
            case Bossbar:
                message = FileCache.getConfig("langConfig")
                        .getString("Formats.Rewards." + type.toString() + ".Message").replace("{lp_Reward}", String.valueOf(value));
                runMessage(player, type, message);
                break;
        }
    }

    public static void runMessage(Player player, MessageType type, String message){

        double val = FileCache.getConfig("langConfig").getDouble("Formats.Rewards.BossBar.Length");
        switch (type){
            case Chat:
                if(!message.equalsIgnoreCase("")) {
                    player.sendMessage(Formatting.formatInfoTags(player, message));
                }
                break;
            case Title:
                break;
            case Bossbar:
                MessagesUtil.sendBossBar(player,
                        FileCache.getConfig("langConfig").getString("Formats.Rewards.BossBar.Message"),
                        BarColor.valueOf(FileCache.getConfig("langConfig").getString("Formats.Rewards.BossBar.Color")),
                        BarStyle.SOLID, val);
                break;
            case Actionbar:
                MessagesUtil.sendActionBar(player, message);
                break;
        }
    }
    public static void runMessage(Player player, MessageType type, String message, String messageBottom){
        double val = FileCache.getConfig("langConfig").getDouble("Formats.Rewards.BossBar.Length");
        switch (type){
            case Chat:
                player.sendMessage(Formatting.formatInfoTags(player, message));
                break;
            case Title:
                MessagesUtil.sendTitle(player, message, messageBottom);
                break;
            case Bossbar:
                MessagesUtil.sendBossBar(player,
                        FileCache.getConfig("langConfig").getString("Formats.Rewards.BossBar.Message"),
                        BarColor.valueOf(FileCache.getConfig("langConfig").getString("Formats.Rewards.BossBar.Color")),
                        BarStyle.SOLID, val);
                break;
            case Actionbar:
                MessagesUtil.sendActionBar(player, message);
                break;
        }
    }
    public static int getOfflineLevel(String name){
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        File userdata = new File(LevelPoints.getUserFolder(), player.getUniqueId() + ".yml");
        FileConfiguration UsersConfig = YamlConfiguration.loadConfiguration(userdata);
        return UsersConfig.getInt("Level");
    }
    public static int getOfflinePrestige(String name){
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        File userdata = new File(LevelPoints.getUserFolder(), player.getUniqueId() + ".yml");
        FileConfiguration UsersConfig = YamlConfiguration.loadConfiguration(userdata);
        return UsersConfig.getInt("Prestige");
    }
    public static double getOfflineEXP(String name){
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        File userdata = new File(LevelPoints.getUserFolder(), player.getUniqueId() + ".yml");
        FileConfiguration UsersConfig = YamlConfiguration.loadConfiguration(userdata);
        return UsersConfig.getDouble("EXP.Amount");
    }
    public static double getOfflineRequiredEXP(String name){
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        File userdata = new File(LevelPoints.getUserFolder(), player.getUniqueId() + ".yml");
        FileConfiguration UsersConfig = YamlConfiguration.loadConfiguration(userdata);



         return Math.round(Double.valueOf(String.valueOf(LevelsContainer.generateFormula(player, LevelsContainer.getFormula()))) * 10) / 10.0;
    }

}
