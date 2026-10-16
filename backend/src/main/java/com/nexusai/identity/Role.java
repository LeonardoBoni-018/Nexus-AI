package com.nexusai.identity;

/** Papeis ordenados por privilegio crescente. */
public enum Role {
    VIEWER,
    ANALYST,
    MANAGER,
    ADMIN;

    public boolean atLeast(Role required) {
        return this.ordinal() >= required.ordinal();
    }
}
