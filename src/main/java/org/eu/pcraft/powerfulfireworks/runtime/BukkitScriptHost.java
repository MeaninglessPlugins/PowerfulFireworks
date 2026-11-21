package org.eu.pcraft.powerfulfireworks.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.eu.pcraft.powerfulfireworks.PowerfulFireworks;
import org.eu.pcraft.powerfulfireworks.script.AudienceRules;
import org.eu.pcraft.powerfulfireworks.script.AudienceSpec;
import org.eu.pcraft.powerfulfireworks.script.FireworkRequest;
import org.eu.pcraft.powerfulfireworks.script.ParticleRequest;
import org.eu.pcraft.powerfulfireworks.script.PointSetRequest;
import org.eu.pcraft.powerfulfireworks.script.ScriptHost;
import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
import org.eu.pcraft.powerfulfireworks.utils.FlightUtil;
import org.slf4j.Logger;

/**
 * 脚本的宿主实现：把请求变成实际的烟花与粒子。
 *
 * <p>原点在构造时定格，观众在每次发射时解析——脚本传 {@link AudienceSpec} 就按它的规则筛人，
 * 不传就按原版规则（烟花按实体跟踪范围，粒子按 32/512 格）。
 */
public final class BukkitScriptHost implements ScriptHost {

    /** 烟花之间的随机水平偏移半径：避免同一发里几支火箭完全重叠。点集传 0，要精确落点。 */
    private static final double FIREWORK_JITTER = 0.75;
    /** 每个宿主最多记住的告警 key 数：避免循环点名或反复发射时刷屏。 */
    private static final int MAX_WARN_KEYS = 32;

    private final Plugin plugin;
    private final Logger logger;
    private final Location origin;
    private Random random = new Random();
    private final Set<String> warnedKeys = new HashSet<>();
    private boolean warnedTooMany;

    public BukkitScriptHost(Plugin plugin, Logger logger, Location origin) {
        this.plugin = plugin;
        this.logger = logger;
        this.origin = origin.clone();
    }

    @Override
    public void seed(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public ScriptHost child() {
        return new BukkitScriptHost(plugin, logger, origin);
    }

    @Override
    public void firework(FireworkRequest request, AudienceSpec audience) {
        emitFirework(request, origin, FIREWORK_JITTER, audience);
    }

    @Override
    public void particle(ParticleRequest request, AudienceSpec audience) {
        emitParticle(request, origin, audience);
    }

    @Override
    public void points(PointSetRequest request, AudienceSpec audience) {
        for (double[] point : request.points()) {
            Location at = origin.clone().add(point[0], point[1], point[2]);
            if (request.emission() instanceof FireworkRequest firework) {
                emitFirework(firework, at, 0, audience);
            } else if (request.emission() instanceof ParticleRequest particle) {
                emitParticle(particle, at, audience);
            } else {
                throw new IllegalArgumentException(
                        "不支持的发射物 " + request.emission().getClass().getSimpleName());
            }
        }
    }

    @Override
    public void log(String message) {
        logger.info("[脚本] {}", message);
    }

    @Override
    public void warn(String message) {
        logger.warn("[脚本] {}", message);
    }

    @Override
    public BitmapFont font(String id) {
        return loadedFonts().get(id);
    }

    @Override
    public Set<String> fonts() {
        return loadedFonts().keySet();
    }

    private Map<String, BitmapFont> loadedFonts() {
        return plugin instanceof PowerfulFireworks fireworks ? fireworks.getFonts() : Map.of();
    }

    private void emitFirework(FireworkRequest request, Location at, double jitter, AudienceSpec audience) {
        // 一次发射的观众只取一次：创建、爆炸、移除三个包必须发给同一批人，
        // 否则掉出范围的人会看到一支永远飞的火箭（客户端不模拟假实体自爆）。
        List<Player> recipients = fireworkRecipients(audience, at);
        if (recipients.isEmpty()) {
            return;
        }
        Location base = at.clone().add(request.offsetX(), request.offsetY(), request.offsetZ());
        warnIfFlightExceedsVisibleRange(request, base, recipients);
        for (int i = 0; i < request.count(); i++) {
            Location point = base.clone().add(jitter(jitter), 0, jitter(jitter));
            FireworkUtil.CreatedFirework created = FireworkUtil.broadcastFireworkCreate(
                    recipients, createRocket(request), point,
                    request.launchChance(), random, 0, request.velocity(), 0);
            scheduleExplosion(created.id(), request.fuse(), recipients,
                    created.silent(), request.explosionChance());
        }
    }

    /**
     * 可见性由脚本负责：这里不裁剪 fuse，只在 fuse 超过"观众还能看见"的上限时告警一次。
     *
     * <p>火箭是纯客户端假实体，只渲染 64 格以内；超出后火箭会中途消失，爆炸发生在视野外。
     * 上限直接复用 {@code FlightUtil.clampFlyTime}，与 YAML 节点的裁剪是同一套几何：
     * 超过上限不代表爆炸一定看不见，而是已经进入会被裁剪的区间。
     * 是否可接受交给脚本作者判断（例如允许远处的观众看不到），所以只提示、不改变行为。
     */
    private void warnIfFlightExceedsVisibleRange(FireworkRequest request, Location at, List<Player> recipients) {
        int visibleFuse = FlightUtil.clampFlyTime(request.fuse(), at, recipients, request.velocity());
        if (visibleFuse >= request.fuse()) {
            return;
        }
        World world = at.getWorld();
        warnOnce("visibility:" + request.fuse() + ":" + request.velocity(),
                "脚本在 {} ({}, {}, {}) 发射了 fuse={} tick、velocity={} 的烟花：按当前观众位置，"
                        + "安全可见上限约 {} tick（客户端 64 格渲染上限，已留 8 格余量）。"
                        + "超出后火箭可能中途消失、爆炸落在视野外；可见性由脚本负责，请调小 fuse 或 velocity。",
                world == null ? "?" : world.getName(),
                at.getBlockX(), at.getBlockY(), at.getBlockZ(),
                request.fuse(), request.velocity(), visibleFuse);
    }

    /**
     * 假实体自己不会爆：服务端不 tick 它，客户端也不会替它触发自爆。
     * 不排这个任务的话，火箭会一直飞、实体一直留着。
     */
    private void scheduleExplosion(int id, int fuse, List<Player> recipients,
                                   boolean createSilent, double explosionChance) {
        long generation = stopGeneration();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> {
                    if (stopGeneration() != generation) {
                        // 演出在飞行途中被停掉：只清实体，不补爆炸
                        FireworkUtil.broadcastFireworkRemove(recipients, id);
                        return;
                    }
                    FireworkUtil.broadcastFireworkExplosion(recipients, explosionChance, random,
                            new boolean[]{createSilent}, id);
                }, fuse);
    }

    /** 当前停止代数；没有 RuntimeService（测试或独立宿主）时恒为 -1。 */
    private long stopGeneration() {
        return plugin instanceof PowerfulFireworks fireworks && fireworks.getRuntimeService() != null
                ? fireworks.getRuntimeService().stopGeneration()
                : -1L;
    }

    private void emitParticle(ParticleRequest request, Location at, AudienceSpec audience) {
        Location point = at.clone().add(request.offsetX(), request.offsetY(), request.offsetZ());
        World world = point.getWorld();
        if (world == null) {
            return;
        }
        List<Player> recipients = particleRecipients(audience, point, request.force());
        if (recipients.isEmpty()) {
            return;
        }
        Particle particle = resolveParticle(request.particle());
        world.spawnParticle(particle, recipients, null,
                point.getX(), point.getY(), point.getZ(),
                request.count(), request.spreadX(), request.spreadY(), request.spreadZ(),
                request.speed(), particleData(particle, request), request.force());
    }

    private List<Player> fireworkRecipients(AudienceSpec spec, Location at) {
        return recipients(spec, at, player -> {
            Location loc = player.getLocation();
            return AudienceRules.fireworkVisible(loc.getX() - at.getX(), loc.getZ() - at.getZ(),
                    player.getViewDistance());
        });
    }

    private List<Player> particleRecipients(AudienceSpec spec, Location at, boolean force) {
        return recipients(spec, at, player -> {
            Location loc = player.getLocation();
            return AudienceRules.particleVisible(
                    loc.getBlockX() + 0.5 - at.getX(),
                    loc.getBlockY() + 0.5 - at.getY(),
                    loc.getBlockZ() + 0.5 - at.getZ(), force);
        });
    }

    /**
     * 把观众规则解析成具体名单。
     *
     * <p>{@code vanilla} 只在 {@code DEFAULT} 时使用：实体按水平 64 格（还要和玩家视距取小），
     * 粒子按三维 32/512 格。显式的 {@code radius} 一律是三维球。
     */
    private List<Player> recipients(AudienceSpec spec, Location at, Predicate<Player> vanilla) {
        World world = at.getWorld();
        if (world == null) {
            return List.of();
        }
        AudienceSpec actual = spec == null ? AudienceSpec.vanilla() : spec;
        return switch (actual.kind()) {
            case NONE -> List.of();
            case WORLD -> playersOf(actual.world() == null ? world : Bukkit.getWorld(actual.world()));
            case PLAYERS -> namedPlayers(world, actual);
            case RADIUS -> radiusPlayers(actual);
            case DEFAULT -> defaultPlayers(world, vanilla);
        };
    }

    private List<Player> playersOf(World world) {
        return world == null ? List.of() : List.copyOf(world.getPlayers());
    }

    private List<Player> namedPlayers(World world, AudienceSpec spec) {
        List<Player> players = new ArrayList<>(spec.names().size());
        for (String name : spec.names()) {
            Player player = Bukkit.getPlayerExact(name);
            if (player == null) {
                warnOnce(name, "观众名单里的玩家 {} 不在线，已跳过", name);
                continue;
            }
            if (player.getWorld() != world) {
                warnOnce(name + "@" + world.getName(), "观众 {} 不在发射所在的世界 {}，已跳过", name, world.getName());
                continue;
            }
            players.add(player);
        }
        return players;
    }

    private void warnOnce(String key, String message, Object... args) {
        if (warnedKeys.size() >= MAX_WARN_KEYS) {
            if (!warnedTooMany) {
                warnedTooMany = true;
                logger.warn("脚本告警数量已达上限 {} 条，后续告警不再显示", MAX_WARN_KEYS);
            }
            return;
        }
        if (!warnedKeys.add(key)) {
            return;
        }
        logger.warn(message, args);
    }

    private List<Player> radiusPlayers(AudienceSpec spec) {
        World world = origin.getWorld();
        if (world == null) {
            return List.of();
        }
        double centerX = origin.getX() + spec.centerX(0);
        double centerY = origin.getY() + spec.centerY(0);
        double centerZ = origin.getZ() + spec.centerZ(0);
        List<Player> players = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (AudienceRules.sphereVisible(at.getX() - centerX, at.getY() - centerY, at.getZ() - centerZ,
                    spec.radius())) {
                players.add(player);
            }
        }
        return players;
    }

    private List<Player> defaultPlayers(World world, Predicate<Player> vanilla) {
        List<Player> players = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (vanilla.test(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private double jitter(double radius) {
        if (radius <= 0) {
            return 0;
        }
        return (random.nextDouble() - 0.5) * 2 * radius;
    }

    /** 每支火箭单独造一份物品：没写形状与颜色时，每一支都该有自己的随机结果。 */
    private ItemStack createRocket(FireworkRequest request) {
        FireworkEffect.Builder effect = FireworkEffect.builder()
                .with(effectType(request))
                .trail(request.trail())
                .flicker(request.flicker());

        if (request.colors().isEmpty()) {
            effect.withColor(randomColor(), randomColor());
        } else {
            effect.withColor(request.colors().stream().map(BukkitScriptHost::parseColor).toList());
        }
        if (!request.fade().isEmpty()) {
            effect.withFade(request.fade().stream().map(BukkitScriptHost::parseColor).toList());
        }

        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
        FireworkMeta meta = (FireworkMeta) item.getItemMeta();
        meta.addEffect(effect.build());
        meta.setPower(request.power());
        item.setItemMeta(meta);
        return item;
    }

    /** 没指定形状时随机一个，保持"随手一放也有变化"的默认手感。 */
    private FireworkEffect.Type effectType(FireworkRequest request) {
        return request.type().isEmpty()
                ? FireworkEffect.Type.values()[random.nextInt(FireworkEffect.Type.values().length)]
                : FireworkEffect.Type.valueOf(request.type());
    }

    private Color randomColor() {
        return Color.fromRGB(random.nextInt(0x1000000));
    }

    /**
     * 按名字找粒子。
     *
     * <p>1.20.5 把带颜色的粒子从 REDSTONE 改名为 DUST，两边的名字都认，
     * 这样同一份脚本在 1.18 与 26.3 上都能跑。
     */
    private static Particle resolveParticle(String name) {
        String key = name.trim().toUpperCase(Locale.ROOT);
        Particle particle = byName(key);
        if (particle != null) {
            return particle;
        }
        String alias = switch (key) {
            case "DUST" -> "REDSTONE";
            case "REDSTONE" -> "DUST";
            default -> null;
        };
        particle = alias == null ? null : byName(alias);
        if (particle == null) {
            throw new IllegalArgumentException("未知粒子 " + name);
        }
        return particle;
    }

    private static Particle byName(String key) {
        try {
            return Particle.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 需要额外数据的粒子目前只支持带颜色的两种：纯色与颜色过渡。
     *
     * <p>颜色过渡（dust_color_transition）必须给 fade，纯色（dust）给了 fade 也不会生效，
     * 所以这里直接报错，免得作者以为设置成功。
     */
    private static Object particleData(Particle particle, ParticleRequest request) {
        Class<?> type = particle.getDataType();
        if (type == Void.class) {
            return null;
        }
        if (type == Particle.DustTransition.class) {
            if (request.fade().isEmpty()) {
                throw new IllegalArgumentException("粒子 " + particle.name() + " 需要 fade 颜色");
            }
            return new Particle.DustTransition(parseColor(request.color()), parseColor(request.fade()),
                    (float) request.size());
        }
        if (type == Particle.DustOptions.class) {
            if (!request.fade().isEmpty()) {
                throw new IllegalArgumentException(
                        "fade 只对 dust_color_transition 有效，" + particle.name() + " 不支持");
            }
            return new Particle.DustOptions(parseColor(request.color()), (float) request.size());
        }
        throw new IllegalArgumentException("粒子 " + particle.name() + " 需要额外的数据，暂不支持");
    }

    private static Color parseColor(String hex) {
        return Color.fromRGB(Integer.parseInt(hex.substring(1), 16));
    }
}
