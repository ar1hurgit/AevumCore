package me.ar1hurgit.aevumcore.core.module;

public interface Module {

    String getName();

    void enable();

    void disable();

    boolean isEnabled();
}