package com.codestep.judge;

public class SandboxTimeoutException extends Exception {

    public SandboxTimeoutException() {
        super("Sandbox exceeded its wall-clock limit");
    }
}
