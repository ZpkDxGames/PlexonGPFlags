package com.plexon.gpflags.integration.core;

public final class StandaloneCoreBridge implements CoreBridge {
    private final String mode;
    private final String detail;

    public StandaloneCoreBridge(String mode, String detail) {
        this.mode = mode;
        this.detail = detail;
    }

    @Override public String mode() { return mode; }
    @Override public String state() { return "NOT_REGISTERED"; }
    @Override public String detail() { return detail; }
    @Override public void registerStarting() {}
    @Override public void markReady(String detail) {}
    @Override public void markFailed(String detail) {}
    @Override public void unregister() {}
}
