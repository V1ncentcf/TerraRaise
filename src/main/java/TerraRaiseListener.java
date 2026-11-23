import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.event.PlayerSwingEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;

public class TerraRaiseListener implements Listener {

    @EventHandler
    public void onClick(PlayerSwingEvent event) {

        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);

        if (bPlayer.getBoundAbilityName().equalsIgnoreCase("TerraRaise")) {

            if (!CoreAbility.hasAbility(player, TerraRaise.class)) {
                new TerraRaise(player);
            }
            if (CoreAbility.getAbility(player, TerraRaise.class) != null) {
                CoreAbility.getAbility(player, TerraRaise.class).setAddSourceBlock(true);
            }
        }

    }

    final Map<Player, Long> lastSneakTime = new HashMap<>();

    @EventHandler
    public void whileSneaking(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);

        if (bPlayer.getBoundAbilityName().equalsIgnoreCase("TerraRaise")) {

            if (!CoreAbility.hasAbility(player, TerraRaise.class)) {
                new TerraRaise(player);
                return;
            }

            Long currentTime = System.currentTimeMillis();
            Long lastTime = lastSneakTime.get(player);
            long doubleTapThreshold = ConfigManager.getConfig().getLong("ExtraAbilities.Vincentcf.TerraRaise.DoubleTapShiftDelay");

            if (lastTime != null && (currentTime - lastTime <= doubleTapThreshold)) {
                // Detected double-tap
                CoreAbility.getAbility(player, TerraRaise.class).setRaiseCol(true);
                return;
            }

            // Update last sneak time
            lastSneakTime.put(player, currentTime);

        }
    }

}


