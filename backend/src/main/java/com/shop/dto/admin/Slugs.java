package com.shop.dto.admin;

/** Slug validation shared by admin requests. Empty means "generate from the name". */
final class Slugs {

    static final String PATTERN = "^$|^[a-z0-9]+(-[a-z0-9]+)*$";
    static final String MESSAGE = "must be lowercase letters, digits and single dashes";

    private Slugs() {
    }
}
