package com.shop.util;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.shop.exception.BadRequestException;

import tools.jackson.databind.JsonNode;

/**
 * Validates Fabric.js canvas JSON sent by the designer before it is stored and later
 * rendered in customer and admin browsers. Only images and text are allowed, images may
 * only point at the customer's own uploaded files, and no external, data or script URLs
 * may appear anywhere in the document.
 */
public final class DesignCanvasValidator {

    public static final int MAX_OBJECTS = 50;
    public static final int MAX_CHARS = 400_000;
    private static final int MAX_DEPTH = 12;
    private static final Set<String> ALLOWED_TYPES = Set.of("image", "i-text", "itext", "textbox", "text");
    private static final Set<String> TEXT_TYPES = Set.of("i-text", "itext", "textbox", "text");
    private static final String[] FORBIDDEN_PREFIXES = {"data:", "http:", "https:", "javascript:", "vbscript:", "//", "blob:", "file:"};

    /** What a side contains. */
    public record Summary(int objects, int images, int texts, Set<String> imageSources) {
        public static final Summary EMPTY = new Summary(0, 0, 0, Set.of());
    }

    private DesignCanvasValidator() {
    }

    /**
     * @param canvas      Fabric JSON for one side, or null for an empty side
     * @param allowedSrcs URLs of the customer's own assets referenced by this design
     */
    public static Summary validate(JsonNode canvas, Set<String> allowedSrcs) {
        if (canvas == null || canvas.isNull() || canvas.isMissingNode()) {
            return Summary.EMPTY;
        }
        if (!canvas.isObject()) {
            throw invalid("The design data is not in the expected format.");
        }
        if (canvas.toString().length() > MAX_CHARS) {
            throw invalid("The design is too large. Remove some elements and try again.");
        }
        JsonNode objects = canvas.get("objects");
        if (objects == null || objects.isNull()) {
            return Summary.EMPTY;
        }
        if (!objects.isArray()) {
            throw invalid("The design data is not in the expected format.");
        }
        if (objects.size() > MAX_OBJECTS) {
            throw invalid("A side can hold at most " + MAX_OBJECTS + " elements.");
        }

        int images = 0;
        int texts = 0;
        Set<String> sources = new HashSet<>();
        for (JsonNode object : objects) {
            if (!object.isObject()) {
                throw invalid("The design data is not in the expected format.");
            }
            JsonNode typeNode = object.get("type");
            String type = typeNode != null && typeNode.isString() ? typeNode.stringValue().toLowerCase(Locale.ROOT) : "";
            if (!ALLOWED_TYPES.contains(type)) {
                throw invalid("Only images and text can be placed on a T-Shirt.");
            }
            if ("image".equals(type)) {
                JsonNode src = object.get("src");
                if (src == null || !src.isString() || !allowedSrcs.contains(src.stringValue())) {
                    throw invalid("An image in the design is not one of your uploaded images. Upload it again.");
                }
                sources.add(src.stringValue());
                images++;
            } else if (TEXT_TYPES.contains(type)) {
                texts++;
            }
            walk(object, allowedSrcs, 1);
        }
        return new Summary(objects.size(), images, texts, sources);
    }

    private static void walk(JsonNode node, Set<String> allowedSrcs, int depth) {
        if (depth > MAX_DEPTH) {
            throw invalid("The design data is nested too deeply.");
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String name = field.getKey();
                JsonNode value = field.getValue();
                if ("clipPath".equals(name) && !value.isNull()) {
                    throw invalid("The design data contains unsupported elements.");
                }
                if ("src".equals(name)) {
                    if (!value.isString() || !allowedSrcs.contains(value.stringValue())) {
                        throw invalid("An image in the design is not one of your uploaded images. Upload it again.");
                    }
                    continue;
                }
                walk(value, allowedSrcs, depth + 1);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                walk(item, allowedSrcs, depth + 1);
            }
        } else if (node.isString()) {
            String value = node.stringValue().trim().toLowerCase(Locale.ROOT);
            for (String prefix : FORBIDDEN_PREFIXES) {
                if (value.startsWith(prefix)) {
                    throw invalid("The design data contains links that are not allowed.");
                }
            }
        }
    }

    private static BadRequestException invalid(String message) {
        return new BadRequestException("INVALID_DESIGN", message);
    }
}
