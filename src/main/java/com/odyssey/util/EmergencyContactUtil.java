package com.odyssey.util;

public final class EmergencyContactUtil {

    private EmergencyContactUtil() {
    }

    /**
     * Normalises any 10-digit Indian phone number to +91XXXXXXXXXX.
     * Does NOT echo input value in validation error messages to protect privacy.
     */
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
            throw new IllegalArgumentException("Emergency contact phone must be a valid 10-digit Indian phone number");
        }
        return "+91" + clean;
    }

    /**
     * Masks the phone number for safe display in logs and UI (e.g., +91******3210).
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "-";
        }
        String trimmed = phone.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        String last4 = trimmed.substring(trimmed.length() - 4);
        return "+91******" + last4;
    }
}
