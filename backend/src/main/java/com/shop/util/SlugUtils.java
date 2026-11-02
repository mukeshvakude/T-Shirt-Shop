package com.shop.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Builds URL slugs, for example "Oxford Button-Down Shirt" becomes "oxford-button-down-shirt". */
public final class SlugUtils {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("(^-+|-+$)");
    private static final int MAX_LENGTH = 160;

    private SlugUtils() {
    }

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Cannot build a slug from empty text");
        }
        String ascii = DIACRITICS.matcher(Normalizer.normalize(input, Normalizer.Form.NFD)).replaceAll("");
        String slug = NON_ALNUM.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = EDGE_DASHES.matcher(slug).replaceAll("");
        if (slug.length() > MAX_LENGTH) {
            slug = EDGE_DASHES.matcher(slug.substring(0, MAX_LENGTH)).replaceAll("");
        }
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("Text contains no letters or digits: " + input);
        }
        return slug;
    }
}
