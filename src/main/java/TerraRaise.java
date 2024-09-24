import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.ParticleEffect;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

import static com.projectkorra.projectkorra.ProjectKorra.plugin;

public class TerraRaise extends EarthAbility implements AddonAbility {

    private long cooldown;
    private double range;
    private double speed;
    private int maxHeight;
    private boolean dynamicCooldown;
    private long maxCooldown;

    private Location location;
    private BoundingBox tarBoundingBox;
    private Map<Block, Vector> sourcedBlocksMap;
    private boolean addSourceBlock;
    private double time;
    private long doubleTapShiftDelay;
    private boolean raiseCol;
    private BukkitTask delayedTask;
    private boolean shouldNotAddBlocks;
    private Map<Block, Vector> updatedBlocksMap = new HashMap<>();
    Map<Block, Integer> remainingMovesMap = new HashMap<>();
    private long interval;
    private int maxSources;
    int totalSourcedBlocks = 0;
    Block prevBlock;
    private int particleAmount;

    public TerraRaise(Player player) {
        super(player);

        if (!bPlayer.canBendIgnoreBinds(this)) return;

        sourcedBlocksMap = new HashMap<>();

        cooldown = ConfigManager.getConfig().getLong("ExtraAbilities.Vincentcf.TerraRaise.Cooldown");
        range = ConfigManager.getConfig().getDouble("ExtraAbilities.Vincentcf.TerraRaise.Range");
        maxHeight = ConfigManager.getConfig().getInt("ExtraAbilities.Vincentcf.TerraRaise.MaxHeight");
        speed = ConfigManager.getConfig().getDouble("ExtraAbilities.Vincentcf.TerraRaise.Speed");
        maxSources = ConfigManager.getConfig().getInt("ExtraAbilities.Vincentcf.TerraRaise.MaxSources");
        doubleTapShiftDelay = ConfigManager.getConfig().getLong("ExtraAbilities.Vincentcf.TerraRaise.DoubleTapShiftDelay");
        dynamicCooldown = ConfigManager.getConfig().getBoolean("ExtraAbilities.Vincentcf.TerraRaise.DynamicCooldown");
        maxCooldown = ConfigManager.getConfig().getLong("ExtraAbilities.Vincentcf.TerraRaise.MaxCooldown");
        particleAmount = ConfigManager.getConfig().getInt("ExtraAbilities.Vincentcf.TerraRaise.ParticleAmount");

        if (bPlayer.isAvatarState()) {
            cooldown = ConfigManager.getConfig().getLong("ExtraAbilities.Vincentcf.TerraRaise.AvatarState.Cooldown");
            range = ConfigManager.getConfig().getDouble("ExtraAbilities.Vincentcf.TerraRaise.AvatarState.Range");
            maxHeight = ConfigManager.getConfig().getInt("ExtraAbilities.Vincentcf.TerraRaise.AvatarState.MaxHeight");
            speed = ConfigManager.getConfig().getDouble("ExtraAbilities.Vincentcf.TerraRaise.AvatarState.Speed");
            maxSources = ConfigManager.getConfig().getInt("ExtraAbilities.Vincentcf.TerraRaise.AvatarState.MaxSources");
        }

        interval = (long) (1000.0 / speed);

        start();
    }

    @Override
    public void progress() {

        if (!bPlayer.canBend(this)) {
            remove();
            if (raiseCol) {
                if (dynamicCooldown) setDynamicCooldown();
                bPlayer.addCooldown(this);
            }
            return;
        }

        focusBlocks();

        if (raiseCol) {

            if (delayedTask != null) {
                delayedTask.cancel();
                shouldNotAddBlocks = true;
            }

            if (System.currentTimeMillis() - time < interval) return;
            time = System.currentTimeMillis();

            Iterator<Map.Entry<Block, Vector>> iterator = sourcedBlocksMap.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<Block, Vector> entry = iterator.next();
                Block block1 = entry.getKey();
                Vector direction = entry.getValue();

                location = block1.getLocation();

                int movesLeft = remainingMovesMap.getOrDefault(block1, 0);

                if (movesLeft > 0) {
                    moveEarth(location.getBlock(), direction, getEarthbendableBlocksLength(block1, direction.clone().multiply(-1), maxHeight));
                    remainingMovesMap.put(block1, movesLeft - 1);

                    Block newBlock = location.add(direction.clone().normalize()).getBlock();
                    updatedBlocksMap.put(newBlock, direction);

                    remainingMovesMap.put(newBlock, movesLeft - 1);
                }

                if (movesLeft-1 <= 0) {
                    iterator.remove();
                    remainingMovesMap.remove(block1);
                }

            }
            sourcedBlocksMap.clear();
            sourcedBlocksMap.putAll(updatedBlocksMap);
            updatedBlocksMap.clear();


            if (!player.isSneaking()) {
                remove();
                if (dynamicCooldown) setDynamicCooldown();
                bPlayer.addCooldown(this);
            }

            return;
        }


        if (addSourceBlock || player.isSneaking()) {

            if (sourcedBlocksMap.size() >= maxSources) {
                TextComponent message = new TextComponent("Max Sources Reached!");
                message.setColor(ChatColor.RED);
                message.setUnderlined(true);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, message);
                return;
            }

            Block block = getEarthSourceBlock(player, "TerraRaise", range);

            if (block == null || !isEarthbendable(block) || RegionProtection.isRegionProtected(this, block.getLocation())) {
                return;
            }

            tarBoundingBox = block.getBoundingBox();
            RayTraceResult intersection = tarBoundingBox.rayTrace(player.getEyeLocation().toVector(), player.getEyeLocation().getDirection(), range);

            if (intersection != null) {
                Vector hitBlockFaceDir = intersection.getHitBlockFace().getDirection();

                delayedTask = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() { // Delays so blocks aren't added when you double tap
                    @Override
                    public void run() {
                        if (!shouldNotAddBlocks && sourcedBlocksMap.size() < maxSources) {
                            sourcedBlocksMap.put(block, hitBlockFaceDir);
                            remMoves();
                        }
                    }
                }, (doubleTapShiftDelay/50) );

                if (addSourceBlock) {
                    sourcedBlocksMap.put(block, hitBlockFaceDir);
                    remMoves();
                    delayedTask.cancel();
                }
                totalSourcedBlocks = sourcedBlocksMap.size();
                prevBlock = block;
            }
            addSourceBlock = false;
        }

    } // Progress Ends Here


    public void setAddSourceBlock(Boolean value) {
        addSourceBlock = value;
    }

    public void setRaiseCol(Boolean value) {
        raiseCol = value;
    }

    public void remMoves() {
        for (Map.Entry<Block, Vector> entry : sourcedBlocksMap.entrySet()) {
            Block block = entry.getKey();
            Vector direction = entry.getValue();

            if (!remainingMovesMap.containsKey(block)) {
                int maxLength = getEarthbendableBlocksLength(block, direction.clone().multiply(-1), maxHeight);
                remainingMovesMap.put(block, maxLength);
            }
        }
    }

    public void focusBlocks() {
        for (Map.Entry<Block, Vector> entry : sourcedBlocksMap.entrySet()) {
            Block block = entry.getKey();
            Vector direction = entry.getValue().normalize().multiply(0.5);
            Location loc = block.getLocation().add(0.5, 0.5, 0.5).add(direction);
            ParticleEffect.BLOCK_CRACK.display(loc, particleAmount, 0.1, 0.1, 0.1, block.getBlockData());
        }
    }

    public void setDynamicCooldown() {
        double x = ((double) totalSourcedBlocks/(double) maxSources);
        double newCooldown = (maxCooldown*x);
        cooldown = Math.max((long)newCooldown, cooldown);
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public String getName() {
        return "TerraRaise";
    }

    @Override
    public String getInstructions() {
        return "Select earth sources by Left-Clicking or Tapping/Holding shift. Double-tap and hold Shift to move the selected earth, release Shift to stop the move!";
    }

    @Override
    public String getDescription() {
        return "TerraRaise is a powerful earthbending ability! With the incredible power to summon massive pillars of earth, you can turn the ground into towering stairways that seem to defy gravity, launch yourself into the sky with stunning force, or shape the land like a master sculptor! Control the earth to reshape your surroundings and watch as the world bends to your elemental will!";
    }

    @Override
    public Location getLocation() {
        return location;
    }

    @Override
    public void load() {

        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.Cooldown", 2000);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.Range", 20);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.MaxHeight", 8);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.Speed", 10);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.DoubleTapShiftDelay", 250);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.MaxSources", 20);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.MaxCooldown", 5000);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.DynamicCooldown", true);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.ParticleAmount", 3);

        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.AvatarState.Cooldown", 0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.AvatarState.Range", 50);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.AvatarState.MaxHeight", 20);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.AvatarState.Speed", 20);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Vincentcf.TerraRaise.AvatarState.MaxSources", 50);

        Listener listener = new TerraRaiseListener();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        ConfigManager.defaultConfig.save();

        plugin.getLogger().info("Successfully enabled " + getName() + " " + getVersion() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        if (remainingMovesMap != null) remainingMovesMap.clear();
        if (updatedBlocksMap != null) updatedBlocksMap.clear();
        if (sourcedBlocksMap != null) sourcedBlocksMap.clear();
        remove();
    }

    @Override
    public String getAuthor() {
        return "Vincentcf";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }
}
