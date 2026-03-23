package com.timestop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TimeStopPlugin extends JavaPlugin implements Listener {

    private final Set<UUID> activeUsers = new HashSet<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("TimeStop 플러그인 활성화!");
    }

    @Override
    public void onDisable() {
        for (UUID uuid : frozenPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
            }
        }
        frozenPlayers.clear();
        activeUsers.clear();
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        ItemStack offhandItem = event.getOffHandItem();
        if (offhandItem == null || offhandItem.getType() != Material.CLOCK) return;
        if (activeUsers.contains(player.getUniqueId())) return;

        event.setCancelled(true);
        player.getInventory().setItemInMainHand(null);
        activateTimeStop(player);
    }

    private void activateTimeStop(Player caster) {
        activeUsers.add(caster.getUniqueId());

        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4, false, false));

        caster.getWorld().spawnParticle(Particle.PORTAL, caster.getLocation().add(0, 1, 0), 200, 2, 1, 2, 0.3);
        caster.getWorld().spawnParticle(Particle.END_ROD, caster.getLocation().add(0, 1, 0), 80, 2, 1, 2, 0.1);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, 1.2f);

        List<Entity> nearby = caster.getNearbyEntities(50, 50, 50);
        List<UUID> frozenThisRound = new ArrayList<>();

        for (Entity entity : nearby) {
            if (entity instanceof Player) {
                Player target = (Player) entity;
                if (target.getUniqueId().equals(caster.getUniqueId())) continue;
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 254, false, false));
                target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 128, false, false));
                frozenPlayers.add(target.getUniqueId());
                frozenThisRound.add(target.getUniqueId());
                target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);
            } else if (entity instanceof LivingEntity) {
                LivingEntity mob = (LivingEntity) entity;
                mob.setAI(false);
                mob.setVelocity(new Vector(0, 0, 0));
                frozenThisRound.add(mob.getUniqueId());
                entity.getWorld().spawnParticle(Particle.SNOWFLAKE, entity.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
            }
        }

        final int[] tick = {0};
        BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                tick[0]++;
                if (!caster.isOnline()) return;
                caster.getWorld().spawnParticle(Particle.END_ROD, caster.getLocation().add(0, 1, 0), 5, 3, 1, 3, 0.05);
            }
        }, 0L, 2L);

        final List<UUID> frozen = frozenThisRound;
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                particleTask.cancel();
                releaseTimeStop(caster, frozen);
            }
        }, 60L);
    }

    private void releaseTimeStop(Player caster, List<UUID> frozenThisRound) {
        activeUsers.remove(caster.getUniqueId());

        if (caster.isOnline()) {
            caster.getWorld().spawnParticle(Particle.EXPLOSION, caster.getLocation().add(0, 1, 0), 5, 2, 1, 2, 0.1);
            caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 2.0f, 1.0f);
            caster.removePotionEffect(PotionEffectType.SPEED);
            caster.removePotionEffect(PotionEffectType.RESISTANCE);
        }

        for (UUID uuid : frozenThisRound) {
            frozenPlayers.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.1);
            }
            Entity e = Bukkit.getEntity(uuid);
            if (e instanceof LivingEntity) {
                LivingEntity mob = (LivingEntity) e;
                mob.setAI(true);
            }
        }
    }
}
