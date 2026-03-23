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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TimeStopPlugin extends JavaPlugin implements Listener {

    // 현재 시간 멈춤 상태인 플레이어
    private final Set<UUID> activeUsers = new HashSet<>();
    // 시간 멈춤으로 얼어버린 플레이어 UUID
    private final Set<UUID> frozenPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("TimeStop 플러그인 활성화!");
    }

    @Override
    public void onDisable() {
        // 서버 종료 시 모든 효과 제거
        for (UUID uuid : frozenPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.setWalkSpeed(0.2f);
            }
        }
        frozenPlayers.clear();
        activeUsers.clear();
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        // 왼손(오프핸드)으로 오는 아이템이 시계인지 확인
        ItemStack offhandItem = event.getOffHandItem();
        if (offhandItem == null || offhandItem.getType() != Material.CLOCK) return;

        // 이미 스킬 사용 중이면 무시
        if (activeUsers.contains(player.getUniqueId())) return;

        // 이벤트 취소 (아이템 교환 막기)
        event.setCancelled(true);

        // 시계 삭제
        player.getInventory().setItemInMainHand(null);

        // 스킬 발동!
        activateTimeStop(player);
    }

    private void activateTimeStop(Player caster) {
        activeUsers.add(caster.getUniqueId());

        // ① 시전자 효과: 스피드 + 저항
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4, false, false));

        // ② 파티클 + 사운드 연출
        caster.getWorld().spawnParticle(Particle.PORTAL, caster.getLocation().add(0, 1, 0), 200, 2, 1, 2, 0.3);
        caster.getWorld().spawnParticle(Particle.END_ROD, caster.getLocation().add(0, 1, 0), 80, 2, 1, 2, 0.1);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, 1.2f);

        // ③ 주변 플레이어 & 몹 동결
        List<Entity> nearby = caster.getNearbyEntities(50, 50, 50);
        List<UUID> frozenThisRound = new ArrayList<>();

        for (Entity entity : nearby) {
            if (entity instanceof Player target) {
                if (target.getUniqueId().equals(caster.getUniqueId())) continue;
                // 슬로우니스 255 = 완전 정지
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 254, false, false));
                target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 128, false, false)); // 점프 금지
                frozenPlayers.add(target.getUniqueId());
                frozenThisRound.add(target.getUniqueId());
                // 파티클
                target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);
            } else if (entity instanceof LivingEntity mob) {
                // 몹은 AI 완전 정지
                mob.setAI(false);
                mob.setVelocity(new Vector(0, 0, 0));
                frozenThisRound.add(mob.getUniqueId());
                entity.getWorld().spawnParticle(Particle.SNOWFLAKE, entity.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
            }
        }

        // ④ 틱 카운터로 파티클 주기적으로 뿌리기 (멈춘 느낌 강조)
        final int[] tick = {0};
        BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            tick[0]++;
            if (!caster.isOnline()) return;
            // 시전자 주변에 계속 파티클
            caster.getWorld().spawnParticle(Particle.END_ROD,
                caster.getLocation().add(0, 1, 0), 5, 3, 1, 3, 0.05);
        }, 0L, 2L);

        // ⑤ 3초(60틱) 후 해제
        Bukkit.getScheduler().runTaskLater(this, () -> {
            particleTask.cancel();
            releaseTimeStop(caster, frozenThisRound);
        }, 60L);
    }

    private void releaseTimeStop(Player caster, List<UUID> frozenThisRound) {
        activeUsers.remove(caster.getUniqueId());

        // 해제 파티클 + 사운드
        if (caster.isOnline()) {
            caster.getWorld().spawnParticle(Particle.EXPLOSION, caster.getLocation().add(0, 1, 0), 5, 2, 1, 2, 0.1);
            caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 2.0f, 1.0f);
            caster.removePotionEffect(PotionEffectType.SPEED);
            caster.removePotionEffect(PotionEffectType.RESISTANCE);
        }

        // 동결 해제
        for (UUID uuid : frozenThisRound) {
            frozenPlayers.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                // 해제 파티클
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.1);
            }
            // 몹 AI 복구
            Entity e = Bukkit.getEntity(uuid);
            if (e instanceof LivingEntity mob) {
                mob.setAI(true);
            }
        }
    }
}
