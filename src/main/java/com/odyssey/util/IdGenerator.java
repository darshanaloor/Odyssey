package com.odyssey.util;

import java.util.UUID;

public final class IdGenerator {

    private IdGenerator() {}

    public static String generate(String prefix) {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return prefix != null && !prefix.isBlank() ? prefix + "-" + randomSuffix : randomSuffix;
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }
}
