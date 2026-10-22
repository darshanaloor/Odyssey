package com.odyssey.util;

import java.util.regex.Pattern;

public final class NotificationMasker {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private NotificationMasker() {}

    public static boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidPhone(String phone) {
        return normalizePhone(phone) != null;
    }

    public static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String clean = phone.replaceAll("[^0-9]", "");
        if (clean.length() == 12 && clean.startsWith("91")) {
            clean = clean.substring(2);
        } else if (clean.length() == 11 && clean.startsWith("0")) {
            clean = clean.substring(1);
        }
        if (clean.length() != 10) {
            return null;
        }
        return "+91" + clean;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "Unknown";
        }
        String normalized = normalizePhone(phone);
        if (normalized == null) {
            // Fallback for odd string
            String digits = phone.replaceAll("[^0-9]", "");
            if (digits.length() >= 4) {
                return "+91*****" + digits.substring(digits.length() - 4);
            }
            return "+91*****0000";
        }
        // normalized is +91XXXXXXXXXX (length 13: "+91" + 10 digits)
        String lastFour = normalized.substring(normalized.length() - 4);
        return "+91*****" + lastFour;
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "Unknown";
        }
        String trimmed = email.trim();
        int atIdx = trimmed.indexOf('@');
        if (atIdx <= 0 || atIdx == trimmed.length() - 1) {
            return "u***@domain.com";
        }
        String local = trimmed.substring(0, atIdx);
        String domain = trimmed.substring(atIdx + 1);
        char firstChar = local.charAt(0);
        return firstChar + "***@" + domain;
    }
}
