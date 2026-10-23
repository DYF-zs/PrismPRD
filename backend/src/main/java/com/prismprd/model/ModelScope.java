package com.prismprd.model;

import java.util.function.Supplier;

/** 一次操作及其异步任务共享同一份模型快照。 */
public final class ModelScope implements AutoCloseable {
    private static final ThreadLocal<ModelScope> CURRENT = new ThreadLocal<>();
    private final ModelScope previous;
    private final Supplier<ModelClientRegistry.Selected> supplier;
    private ModelClientRegistry.Selected selected;

    private ModelScope(Supplier<ModelClientRegistry.Selected> supplier) {
        previous = CURRENT.get(); this.supplier = supplier; CURRENT.set(this);
    }
    public static ModelScope open(Supplier<ModelClientRegistry.Selected> supplier) { return new ModelScope(supplier); }
    static ModelClientRegistry.Selected current(Supplier<ModelClientRegistry.Selected> fallback) {
        ModelScope scope = CURRENT.get();
        return scope == null ? fallback.get() : scope.resolve();
    }
    private ModelClientRegistry.Selected resolve() {
        if (selected == null) selected = supplier.get();
        return selected;
    }
    public static Runnable decorate(Runnable runnable) {
        ModelScope scope = CURRENT.get();
        if (scope == null) return runnable;
        ModelClientRegistry.Selected captured = scope.resolve();
        return () -> { try (var ignored = open(() -> captured)) { runnable.run(); } };
    }
    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}

