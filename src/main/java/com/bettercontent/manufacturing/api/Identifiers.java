package com.bettercontent.manufacturing.api;

import java.util.Objects;
import java.util.regex.Pattern;

public final class Identifiers {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern ROLE = Pattern.compile("[a-z][a-z0-9_]{0,47}");
    private Identifiers() {}
    public static String id(String value) {
        Objects.requireNonNull(value, "identifier");
        if (value.length() > 192 || !ID.matcher(value).matches()) throw new IllegalArgumentException("Invalid identifier: " + value);
        return value;
    }
    public static String role(String value) {
        Objects.requireNonNull(value, "role");
        if (!ROLE.matcher(value).matches()) throw new IllegalArgumentException("Invalid role: " + value);
        return value;
    }
    public static String variant(String value) {
        Objects.requireNonNull(value, "material variant");
        String[] parts = value.split("#", -1);
        id(parts[0]);
        if (parts.length > 2 || (parts.length == 2 && (parts[1].isEmpty() || parts[1].length() > 64 || !parts[1].matches("[a-z0-9/._-]+")))) {
            throw new IllegalArgumentException("Invalid material variant: " + value);
        }
        return value;
    }
    public static double finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }
}
