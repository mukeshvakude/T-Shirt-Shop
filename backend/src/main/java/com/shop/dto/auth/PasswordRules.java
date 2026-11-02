package com.shop.dto.auth;

/** Shared validation rules for passwords and phone numbers. */
public final class PasswordRules {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    /** BCrypt only uses the first 72 bytes, so longer UTF-8 passwords are rejected. */
    public static final int MAX_BYTES = 72;
    public static final String LETTER_AND_DIGIT = "^(?=.*[A-Za-z])(?=.*[0-9]).*$";
    public static final String LETTER_AND_DIGIT_MESSAGE = "must contain at least one letter and one digit";
    public static final String INDIAN_MOBILE = "^[6-9][0-9]{9}$";

    private PasswordRules() {
    }
}
