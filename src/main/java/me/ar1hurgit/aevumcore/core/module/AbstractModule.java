package me.ar1hurgit.aevumcore.core.module;

public abstract class AbstractModule implements Module {

    private boolean enabled = false;

    @Override
    public void enable() {
        if (!enabled) {
            enabled = true;
            onEnable();
        }
    }

    @Override
    public void disable() {
        if (enabled) {
            enabled = false;
            onDisable();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    protected abstract void onEnable();

    protected abstract void onDisable();
}