package com.frescuratotal.erp.common;

import java.util.Arrays;

/** Keeps the name and role of the person using the application. */
public final class UserSession {

    private static String username = "system";
    private static Role role = Role.ADMIN;

    private UserSession() {
    }

    public static void start(String name, Role userRole) {
        username = name;
        role = userRole;
    }

    public static String getUsername() {
        return username;
    }

    public static Role getRole() {
        return role;
    }

    /** True if the current user is an administrator or has one of the given roles. */
    public static boolean can(Role... allowedRoles) {
        return role == Role.ADMIN || Arrays.asList(allowedRoles).contains(role);
    }
}
