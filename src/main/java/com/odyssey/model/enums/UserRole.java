package com.odyssey.model.enums;

/**
 * Roles for platform users.
 * TRAVELLER: Regular customer (default).
 * PROVIDER: Registered transport or hotel operator.
 * ADMIN: Project owner/administrator with review, approval, and suspension permissions.
 */
public enum UserRole {
    TRAVELLER,
    PROVIDER,
    ADMIN
}
