package com.plexon.gpflags.integration.core;

public interface CoreBridge {
    String MODULE_ID = "gpflags";
    String SUPPORTED_API_RANGE = ">=1.0 <3.0";

    String mode();
    String state();
    String detail();
    void registerStarting();
    void markReady(String detail);
    void markFailed(String detail);
    void unregister();
}
