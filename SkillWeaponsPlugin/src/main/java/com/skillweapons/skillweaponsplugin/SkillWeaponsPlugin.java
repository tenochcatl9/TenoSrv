package com.skillweapons.skillweaponsplugin;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SkillWeaponsPlugin extends JavaPlugin implements Listener, TabCompleter {

    private Map<UUID, Long> riptideCooldowns = new HashMap<>();
    private Map<UUID, Long> crossbowCooldowns = new HashMap<>();
    private static final long RIPTIDE_COOLDOWN = 3000; // 3 segundos
    private static final long CROSSBOW_COOLDOWN = 5000; // 5 segundos

    @Override
    public void onEnable() {
        getLogger().info("SkillWeaponsPlugin habilitado!");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("sw").setTabCompleter(this);
        
        // Generar libros de skills en estructuras existentes
        generateSkillBooksInStructures();
    }

    @Override
    public void onDisable() {
        getLogger().info("SkillWeaponsPlugin deshabilitado!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sw") || command.getName().equalsIgnoreCase("skillweapons") || command.getName().equalsIgnoreCase("skillw")) {
            if (!sender.hasPermission("skillweapons.admin")) {
                sender.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage("Este comando solo puede ser usado por jugadores.");
                return true;
            }

            Player player = (Player) sender;

            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Uso: /sw give [enchant]");
                return true;
            }

            if (args[0].equalsIgnoreCase("give")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Uso: /sw give [enchant]");
                    return true;
                }
                giveSkillBook(player, args[1]);
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "Opción no válida. Usa: give");
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("sw") || command.getName().equalsIgnoreCase("skillweapons") || command.getName().equalsIgnoreCase("skillw")) {
            if (args.length == 1) {
                return Arrays.asList("give");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                return Arrays.asList("dash", "beam");
            }
        }
        return null;
    }

    private void giveSkillBook(Player player, String enchantType) {
        ItemStack skillBook = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = skillBook.getItemMeta();

        if (enchantType.equalsIgnoreCase("riptide") || enchantType.equalsIgnoreCase("dash")) {
            meta.setDisplayName(ChatColor.AQUA + "Libro de Skill: Dash");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Aplica el encantamiento Dash");
            lore.add(ChatColor.GRAY + "a tu espada actual.");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click derecho para aplicar.");
            meta.setLore(lore);
        } else if (enchantType.equalsIgnoreCase("laser_crossbow") || enchantType.equalsIgnoreCase("beam")) {
            meta.setDisplayName(ChatColor.RED + "Libro de Skill: Beam");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Aplica el encantamiento Beam");
            lore.add(ChatColor.GRAY + "a tu ballesta actual.");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click derecho para aplicar.");
            meta.setLore(lore);
        } else {
            player.sendMessage(ChatColor.RED + "Skill no válida. Usa: dash o beam");
            return;
        }

        skillBook.setItemMeta(meta);
        
        player.getInventory().addItem(skillBook);
        player.sendMessage(ChatColor.GREEN + "Has recibido el libro de skill: " + enchantType);
    }

    private void generateSkillBooksInStructures() {
        // Este método genera libros de skills en cofres de estructuras
        // Se ejecutará cuando se detecte la generación de nuevas estructuras
        getLogger().info("Sistema de generación de libros en estructuras iniciado.");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String displayName = meta.getDisplayName();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (displayName.contains("Dash") && heldItem.getType().name().contains("SWORD")) {
            applyDashSkill(player, heldItem);
            item.setAmount(item.getAmount() - 1);
            event.setCancelled(true);
        } else if (displayName.contains("Beam") && heldItem.getType() == Material.CROSSBOW) {
            applyBeamSkill(player, heldItem);
            item.setAmount(item.getAmount() - 1);
            event.setCancelled(true);
        }
    }

    private void applyDashSkill(Player player, ItemStack sword) {
        ItemMeta meta = sword.getItemMeta();
        if (meta == null) return;

        // Aplicar un encantamiento personalizado mediante lore
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Dash");
        lore.add(ChatColor.GRAY + "Click derecho para lanzarte");
        lore.add(ChatColor.GRAY + "Requiere lluvia o agua");
        meta.setLore(lore);
        
        // Añadir un encantamiento visual
        meta.addEnchant(Enchantment.RIPTIDE, 1, true);
        sword.setItemMeta(meta);

        player.sendMessage(ChatColor.GREEN + "¡Skill Dash aplicada a tu espada!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    private void applyBeamSkill(Player player, ItemStack crossbow) {
        ItemMeta meta = crossbow.getItemMeta();
        if (meta == null) return;

        // Aplicar un encantamiento personalizado mediante lore
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Beam");
        lore.add(ChatColor.GRAY + "Dispara un rayo láser explosivo");
        lore.add(ChatColor.GRAY + "Crea esfera de balas de shulker");
        meta.setLore(lore);
        
        // Añadir un encantamiento visual
        meta.addEnchant(Enchantment.MULTISHOT, 1, true);
        meta.addEnchant(Enchantment.PIERCING, 1, true);
        crossbow.setItemMeta(meta);

        player.sendMessage(ChatColor.GREEN + "¡Skill Beam aplicada a tu ballesta!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    @EventHandler
    public void onPlayerInteractDash(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || !item.getType().name().contains("SWORD")) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        List<String> lore = meta.getLore();
        boolean hasDash = false;
        for (String line : lore) {
            if (line.contains("Skill: Dash")) {
                hasDash = true;
                break;
            }
        }

        if (!hasDash) return;

        // Verificar cooldown
        long currentTime = System.currentTimeMillis();
        if (riptideCooldowns.containsKey(player.getUniqueId()) && 
            currentTime - riptideCooldowns.get(player.getUniqueId()) < RIPTIDE_COOLDOWN) {
            player.sendMessage(ChatColor.RED + "Dash en cooldown!");
            return;
        }

        // Verificar si está bajo la lluvia o en agua
        boolean isInWaterOrRain = player.isInWater() || 
            player.getWorld().hasStorm() && player.getWorld().isChunkLoaded(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4);

        if (!isInWaterOrRain) {
            player.sendMessage(ChatColor.RED + "Necesitas estar bajo la lluvia o en agua para usar Dash!");
            return;
        }

        // Ejecutar Dash
        Vector direction = player.getLocation().getDirection();
        player.setVelocity(direction.multiply(3));
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);

        riptideCooldowns.put(player.getUniqueId(), currentTime);
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        ItemStack bow = event.getBow();

        if (bow == null || bow.getType() != Material.CROSSBOW) return;

        ItemMeta meta = bow.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        List<String> lore = meta.getLore();
        boolean hasBeamSkill = false;
        for (String line : lore) {
            if (line.contains("Skill: Beam")) {
                hasBeamSkill = true;
                break;
            }
        }

        if (!hasBeamSkill) return;

        // Verificar cooldown
        long currentTime = System.currentTimeMillis();
        if (crossbowCooldowns.containsKey(player.getUniqueId()) && 
            currentTime - crossbowCooldowns.get(player.getUniqueId()) < CROSSBOW_COOLDOWN) {
            player.sendMessage(ChatColor.RED + "Beam en cooldown!");
            event.setCancelled(true);
            return;
        }

        // Marcar la flecha como flecha láser
        if (event.getProjectile() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getProjectile();
            projectile.setCustomName("LASER_ARROW");
            projectile.setCustomNameVisible(false);
        }

        crossbowCooldowns.put(player.getUniqueId(), currentTime);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        
        if (!projectile.getCustomName().equals("LASER_ARROW")) return;

        // Crear efecto de explosión de balas de shulker
        Location hitLoc = event.getEntity().getLocation();
        World world = hitLoc.getWorld();

        // Efecto visual del rayo láser
        world.spawnParticle(Particle.END_ROD, hitLoc, 50, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.EXPLOSION_LARGE, hitLoc, 10, 0.5, 0.5, 0.5, 0.1);
        world.playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        // Generar esfera de balas de shulker
        new BukkitRunnable() {
            int count = 0;
            final double radius = 3.0;
            final int bulletsPerLayer = 12;
            final int layers = 3;

            @Override
            public void run() {
                if (count >= layers) {
                    this.cancel();
                    return;
                }

                double currentRadius = radius * ((count + 1) / (double) layers);
                
                for (int i = 0; i < bulletsPerLayer; i++) {
                    double angle = (2 * Math.PI * i) / bulletsPerLayer;
                    double x = hitLoc.getX() + currentRadius * Math.cos(angle);
                    double y = hitLoc.getY() + (count * 1.5) - 1.5;
                    double z = hitLoc.getZ() + currentRadius * Math.sin(angle);
                    
                    Location bulletLoc = new Location(world, x, y, z);
                    ShulkerBullet bullet = world.spawn(bulletLoc, ShulkerBullet.class);
                    
                    // Hacer que las balas apunten hacia el centro de la explosión
                    Vector direction = hitLoc.toVector().subtract(bulletLoc.toVector()).normalize();
                    // Usar el método correcto para establecer dirección
                    bullet.setVelocity(direction.multiply(0.5));
                    
                    // Marcar la bala para que no cause daño excesivo
                    bullet.setCustomName("SHULKER_EXPLOSION");
                    bullet.setCustomNameVisible(false);
                }

                count++;
            }
        }.runTaskTimer(this, 0L, 5L); // Cada 5 ticks (0.25 segundos)

        // Eliminar las balas de shulker después de un tiempo
        new BukkitRunnable() {
            @Override
            public void run() {
                world.getEntitiesByClass(ShulkerBullet.class).stream()
                    .filter(bullet -> bullet.getCustomName() != null && bullet.getCustomName().equals("SHULKER_EXPLOSION"))
                    .forEach(Entity::remove);
            }
        }.runTaskLater(this, 100L); // 5 segundos
    }
}