package org.eu.pcraft.powerfulfireworks.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.eu.pcraft.powerfulfireworks.script.FireworkRequest;
import org.eu.pcraft.powerfulfireworks.script.ParticleRequest;
import org.eu.pcraft.powerfulfireworks.script.PointSetRequest;
import org.eu.pcraft.powerfulfireworks.script.ScriptHost;
import org.eu.pcraft.powerfulfireworks.utils.FireworkUtil;
import org.slf4j.Logger;

/**
 * 脚本的宿主实现：把请求变成实际的烟花与粒子。
 *
 * <p>原点与接收者在构造时定格，之后一个脚本里的所有发射都以它为基准；子脚本沿用同一份，
 * 因为它们同属一场演出，只是拆成了两个文件。
 */
public final class BukkitScriptHost implements ScriptHost {

    /** 烟花之间的随机水平偏移半径：避免同一发里几支火箭完全重叠。点集传 0，要精确落点。 */
    private static final double FIREWORK_JITTER = 0.75;

    private final Logger logger;
    private final Location origin;
    private final List<Player> receivers;
    private final Random random = new Random();

    public BukkitScriptHost(Logger logger, Location origin, Collection<Player> receivers) {
        this.logger = logger;
        this.origin = origin.clone();
        this.receivers = List.copyOf(receivers);
    }

    @Override
    public ScriptHost child() {
        return new BukkitScriptHost(logger, origin, receivers);
    }

    @Override
    public void firework(FireworkRequest request) {
        emitFirework(request, origin, FIREWORK_JITTER);
    }

    @Override
    public void particle(ParticleRequest request) {
        emitParticle(request, origin);
    }

    @Override
    public void points(PointSetRequest request) {
        if (receivers.isEmpty()) {
            return;
        }
        for (double[] point : request.points()) {
            Location at = origin.clone().add(point[0], point[1], point[2]);
            if (request.emission() instanceof FireworkRequest firework) {
                emitFirework(firework, at, 0);
            } else if (request.emission() instanceof ParticleRequest particle) {
                emitParticle(particle, at);
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

    private void emitFirework(FireworkRequest request, Location at, double jitter) {
        if (receivers.isEmpty()) {
            return;
        }
        Location base = at.clone().add(request.offsetX(), request.offsetY(), request.offsetZ());
        for (int i = 0; i < request.count(); i++) {
            Location point = base.clone().add(jitter(jitter), 0, jitter(jitter));
            FireworkUtil.broadcastFireworkCreate(receivers, createRocket(request), point,
                    request.launchChance(), 0, request.velocity(), 0);
        }
    }

    private void emitParticle(ParticleRequest request, Location at) {
        if (receivers.isEmpty()) {
            return;
        }
        Location point = at.clone().add(request.offsetX(), request.offsetY(), request.offsetZ());
        World world = point.getWorld();
        if (world == null) {
            return;
        }
        Particle particle = resolveParticle(request.particle());
        world.spawnParticle(particle, receivers, null,
                point.getX(), point.getY(), point.getZ(),
                request.count(), request.spreadX(), request.spreadY(), request.spreadZ(),
                request.speed(), particleData(particle, request), request.force());
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
