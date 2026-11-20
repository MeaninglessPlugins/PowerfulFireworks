package org.eu.pcraft.powerfulfireworks.script;

/**
 * 一次发射的参数。
 *
 * <p>它只描述"放什么"，不描述"放在哪"——位置由调用方给（{@code ctx.firework} 给原点，
 * {@code ctx.points} 给一串坐标）。实现有烟花与粒子两种，点集对两者一视同仁。
 */
public interface Emission {
}
