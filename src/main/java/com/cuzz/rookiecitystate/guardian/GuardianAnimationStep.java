package com.cuzz.rookiecitystate.guardian;

public record GuardianAnimationStep(String animation, long durationTicks, double speed, boolean force) {
    public GuardianAnimationStep {
        if (animation == null || animation.isBlank()) throw new IllegalArgumentException("动画名称不能为空");
        if (durationTicks < 1) throw new IllegalArgumentException("动画持续时间必须为正数");
        if (!Double.isFinite(speed) || speed <= 0D) throw new IllegalArgumentException("动画速度必须为正数");
    }
}
