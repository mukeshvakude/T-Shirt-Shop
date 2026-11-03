package com.shop.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shop.dto.PageResponse;
import com.shop.dto.design.DesignAssetResponse;
import com.shop.dto.design.DesignRequest;
import com.shop.dto.design.DesignResponse;
import com.shop.dto.design.DesignSummary;
import com.shop.dto.design.DesignerOptionsResponse;
import com.shop.dto.design.DesignerOptionsResponse.Availability;
import com.shop.dto.design.DesignerOptionsResponse.Choice;
import com.shop.dto.design.DesignerOptionsResponse.Font;
import com.shop.dto.design.DesignerOptionsResponse.Placement;
import com.shop.dto.design.DesignerOptionsResponse.TshirtType;
import com.shop.entity.CustomDesign;
import com.shop.entity.CustomizationOption;
import com.shop.entity.DesignAsset;
import com.shop.entity.Inventory;
import com.shop.entity.Product;
import com.shop.entity.ProductColor;
import com.shop.entity.ProductSize;
import com.shop.entity.ProductStatus;
import com.shop.entity.User;
import com.shop.exception.BadRequestException;
import com.shop.exception.BusinessRuleException;
import com.shop.exception.ResourceNotFoundException;
import com.shop.repository.CustomDesignRepository;
import com.shop.repository.CustomizationOptionRepository;
import com.shop.repository.DesignAssetRepository;
import com.shop.repository.InventoryRepository;
import com.shop.repository.ProductRepository;
import com.shop.repository.UserRepository;
import com.shop.security.RateLimiter;
import com.shop.service.AppSettingService;
import com.shop.service.DesignService;
import com.shop.service.PricingService;
import com.shop.service.StorageService;
import com.shop.util.DesignCanvasValidator;
import com.shop.util.ImageValidator;
import com.shop.util.SecureTokens;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class DesignServiceImpl implements DesignService {

    private static final Logger log = LoggerFactory.getLogger(DesignServiceImpl.class);
    static final int MAX_DESIGNS_PER_USER = 100;
    static final int UPLOADS_PER_HOUR = 40;
    static final long MAX_PREVIEW_BYTES = 3L * 1024 * 1024;
    static final int MAX_PREVIEW_DIMENSION = 2400;
    private static final String PNG_DATA_URL = "data:image/png;base64,";
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomizationOptionRepository options;
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final CustomDesignRepository designs;
    private final DesignAssetRepository assets;
    private final UserRepository users;
    private final StorageService storage;
    private final AppSettingService settings;
    private final RateLimiter rateLimiter;
    private final JsonMapper json;
    private final DesignRules rules;
    private final PricingService pricing;

    // ------------------------------------------------------------------ options

    @Override
    @Transactional(readOnly = true)
    public DesignerOptionsResponse options() {
        List<TshirtType> types = new ArrayList<>();
        Map<Long, String> typeByProduct = new HashMap<>();
        for (CustomizationOption option : active(CustomizationOption.TSHIRT_TYPE)) {
            JsonNode meta = metadata(option);
            String slug = text(meta, "baseProductSlug");
            Optional<Product> product = slug == null ? Optional.empty()
                    : products.findVisibleBySlug(slug, ProductStatus.ACTIVE);
            if (product.isEmpty() || !product.get().isCustomizable()) {
                continue;
            }
            Product p = product.get();
            typeByProduct.put(p.getId(), option.getCode());
            types.add(new TshirtType(option.getCode(), option.getLabel(), p.getSlug(), p.getEffectivePrice(),
                    text(meta, "mockup")));
        }

        List<Inventory> variants = typeByProduct.isEmpty() ? List.of()
                : inventory.findActiveForProducts(typeByProduct.keySet());
        Map<String, ProductColor> colors = new LinkedHashMap<>();
        Map<String, ProductSize> sizes = new LinkedHashMap<>();
        List<Availability> availability = new ArrayList<>();
        variants.stream()
                .sorted(Comparator.comparingInt((Inventory i) -> i.getColor().getSortOrder())
                        .thenComparingInt(i -> i.getSize().getSortOrder()))
                .forEach(i -> {
                    colors.putIfAbsent(i.getColor().getCode(), i.getColor());
                    sizes.putIfAbsent(i.getSize().getCode(), i.getSize());
                    availability.add(new Availability(typeByProduct.get(i.getProduct().getId()), i.getColor().getCode(),
                            i.getSize().getCode(), i.getAvailable() > 0));
                });

        List<Placement> placements = active(CustomizationOption.PRINT_PLACEMENT).stream().map(o -> {
            JsonNode m = metadata(o);
            List<String> excludes = new ArrayList<>();
            if (m.has("excludes")) {
                m.get("excludes").forEach(e -> excludes.add(e.stringValue()));
            }
            return new Placement(o.getCode(), o.getLabel(), text(m, "side"), m.path("x").asDouble(),
                    m.path("y").asDouble(), m.path("w").asDouble(), m.path("h").asDouble(), excludes);
        }).toList();

        List<Font> fonts = active(CustomizationOption.FONT).stream()
                .map(o -> new Font(o.getCode(), o.getLabel(), text(metadata(o), "family")))
                .toList();

        return new DesignerOptionsResponse(types,
                colors.values().stream().map(c -> new Choice(c.getCode(), c.getName(), c.getHex())).toList(),
                sizes.values().stream().sorted(Comparator.comparingInt(ProductSize::getSortOrder))
                        .map(s -> new Choice(s.getCode(), s.getLabel(), null)).toList(),
                placements, fonts, availability,
                new DesignerOptionsResponse.Limits(maxUploadBytes(), maxUploadDimension(), List.of("image/png", "image/jpeg"),
                        DesignCanvasValidator.MAX_OBJECTS, 100));
    }

    // ------------------------------------------------------------------ uploads

    @Override
    @Transactional
    public DesignAssetResponse uploadAsset(Long userId, byte[] content, String originalFilename) {
        rateLimiter.check("design-upload:" + userId, UPLOADS_PER_HOUR, Duration.ofHours(1));
        ImageValidator.ValidatedImage image = ImageValidator.validate(content, maxUploadBytes(), maxUploadDimension());
        StorageService.StoredFile stored = storage.store("designs", image.content(), image.type().extension());
        afterRollback(() -> storage.delete(stored.key()));

        DesignAsset asset = new DesignAsset();
        asset.setUserId(userId);
        asset.setStorageKey(stored.key());
        asset.setOriginalFilename(safeFilename(originalFilename, image.type().extension()));
        asset.setMimeType(image.type().mimeType());
        asset.setSizeBytes(content.length);
        asset.setWidthPx(image.width());
        asset.setHeightPx(image.height());
        asset.setSha256(SecureTokens.sha256Hex(Base64.getEncoder().encodeToString(content)));
        assets.save(asset);
        return toAssetResponse(asset);
    }

    // ------------------------------------------------------------------ designs

    @Override
    @Transactional
    public DesignResponse create(Long userId, DesignRequest request) {
        if (designs.countByUserId(userId) >= MAX_DESIGNS_PER_USER) {
            throw new BusinessRuleException("TOO_MANY_DESIGNS",
                    "You can keep up to " + MAX_DESIGNS_PER_USER + " saved designs. Delete some to save new ones.");
        }
        CustomDesign design = new CustomDesign();
        design.setUserId(userId);
        design.setDesignCode(newCode());
        Validated v = validate(userId, request);
        apply(design, request, v);
        storePreviews(design, request, v);
        designs.saveAndFlush(design);
        if (!v.assets().isEmpty()) {
            assets.attach(v.assets().stream().map(DesignAsset::getId).toList(), design.getId());
        }
        log.info("User {} saved design {}", userId, design.getDesignCode());
        return toResponse(design);
    }

    @Override
    @Transactional
    public DesignResponse update(Long userId, String code, DesignRequest request) {
        CustomDesign design = owned(userId, code);
        if (design.isLocked()) {
            throw new BusinessRuleException("DESIGN_LOCKED",
                    "This design is part of an order and cannot be changed. Save it as a new design instead.");
        }
        Validated v = validate(userId, request);
        String oldFront = design.getPreviewFrontKey();
        String oldBack = design.getPreviewBackKey();
        apply(design, request, v);
        storePreviews(design, request, v);
        designs.saveAndFlush(design);
        if (!v.assets().isEmpty()) {
            assets.attach(v.assets().stream().map(DesignAsset::getId).toList(), design.getId());
        }
        afterCommit(() -> {
            if (oldFront != null && !oldFront.equals(design.getPreviewFrontKey())) storage.delete(oldFront);
            if (oldBack != null && !oldBack.equals(design.getPreviewBackKey())) storage.delete(oldBack);
        });
        return toResponse(design);
    }

    @Override
    @Transactional(readOnly = true)
    public DesignResponse get(Long userId, boolean admin, String code) {
        CustomDesign design = designs.findByCode(code)
                .filter(d -> admin || d.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Design", code));
        return toResponse(design);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DesignSummary> mine(Long userId) {
        return designs.findForUser(userId).stream().map(d -> toSummary(d, null)).toList();
    }

    @Override
    @Transactional
    public void delete(Long userId, String code) {
        CustomDesign design = owned(userId, code);
        if (design.isLocked()) {
            throw new BusinessRuleException("DESIGN_LOCKED", "This design is part of an order and cannot be deleted.");
        }
        String front = design.getPreviewFrontKey();
        String back = design.getPreviewBackKey();
        assets.detach(design.getId());
        designs.delete(design);
        afterCommit(() -> {
            if (front != null) storage.delete(front);
            if (back != null) storage.delete(back);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DesignSummary> adminList(int page, int size) {
        Page<CustomDesign> result = designs.findAllNewestFirst(PageRequest.of(page, size));
        Map<Long, User> owners = users.findAllById(result.getContent().stream().map(CustomDesign::getUserId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, u -> u));
        return PageResponse.of(result, d -> toSummary(d, owners.get(d.getUserId())));
    }

    @Override
    @Transactional(readOnly = true)
    public void writeZip(String code, OutputStream out) throws IOException {
        CustomDesign design = designs.findByCode(code).orElseThrow(() -> new ResourceNotFoundException("Design", code));
        User owner = users.findById(design.getUserId()).orElse(null);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("code", design.getDesignCode());
        info.put("name", design.getName());
        info.put("customer", owner == null ? null : Map.of("id", owner.getId(), "name", owner.getFullName(),
                "email", owner.getEmail()));
        info.put("tshirtType", design.getTshirtType());
        info.put("product", design.getBaseProduct().getName());
        info.put("color", design.getColorCode());
        info.put("size", design.getSizeCode());
        info.put("placements", readTree(design.getPlacements()));
        info.put("premiumPrint", design.isPremiumDesign());
        info.put("textItems", readTree(design.getTextItems()));
        info.put("canvasFront", readTree(design.getCanvasFront()));
        info.put("canvasBack", readTree(design.getCanvasBack()));
        info.put("createdAt", design.getCreatedAt());
        info.put("updatedAt", design.getUpdatedAt());

        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("design.json"));
            zip.write(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(info));
            zip.closeEntry();
            copy(zip, design.getPreviewFrontKey(), "preview-front.png");
            copy(zip, design.getPreviewBackKey(), "preview-back.png");
            Set<String> names = new HashSet<>();
            for (DesignAsset asset : assets.findByCustomDesignId(design.getId())) {
                String name = asset.getId() + "-" + asset.getOriginalFilename();
                if (names.add(name)) {
                    copy(zip, asset.getStorageKey(), "artwork/" + name);
                }
            }
        }
    }

    // ------------------------------------------------------------------ validation

    private record Validated(Product product, String type, List<String> placements, String front, String back,
            List<DesignAsset> assets, boolean frontUsed, boolean backUsed) {
    }

    private Validated validate(Long userId, DesignRequest r) {
        CustomizationOption type = rules.type(r.tshirtType());
        Product product = rules.baseProduct(type);
        rules.requireVariant(product, r.colorCode(), r.sizeCode(), type.getLabel());

        // Placements: known, one per side, no excluded combinations.
        List<String> placements = r.placements().stream().distinct().toList();
        Set<String> sides = rules.checkPlacements(placements);

        // Assets must belong to the customer.
        List<DesignAsset> owned = r.assetIds().isEmpty() ? List.of()
                : assets.findByIdInAndUserId(new HashSet<>(r.assetIds()), userId);
        if (owned.size() != new HashSet<>(r.assetIds()).size()) {
            throw new BadRequestException("INVALID_DESIGN", "One of the images is not yours. Upload it again.");
        }
        Set<String> allowedSrcs = owned.stream().map(DesignAsset::url).collect(Collectors.toSet());

        DesignCanvasValidator.Summary front = DesignCanvasValidator.validate(r.front(), allowedSrcs);
        DesignCanvasValidator.Summary back = DesignCanvasValidator.validate(r.back(), allowedSrcs);
        if (front.objects() + back.objects() == 0) {
            throw new BadRequestException("EMPTY_DESIGN", "Add an image or some text before saving.");
        }
        checkSide("FRONT", front, sides);
        checkSide("BACK", back, sides);

        Set<String> fonts = active(CustomizationOption.FONT).stream().map(o -> text(metadata(o), "family"))
                .collect(Collectors.toSet());
        for (DesignRequest.TextItem item : r.textItems()) {
            if (!fonts.contains(item.font())) {
                throw new BadRequestException("UNKNOWN_FONT", "The font " + item.font() + " is not available.");
            }
        }
        return new Validated(product, type.getCode(), placements,
                front.objects() > 0 ? r.front().toString() : null,
                back.objects() > 0 ? r.back().toString() : null,
                owned, front.objects() > 0, back.objects() > 0);
    }

    private static void checkSide(String side, DesignCanvasValidator.Summary summary, Set<String> sides) {
        String label = side.equals("FRONT") ? "front" : "back";
        if (summary.objects() > 0 && !sides.contains(side)) {
            throw new BadRequestException("PLACEMENT_REQUIRED", "Choose a print placement for the " + label + ".");
        }
        if (summary.objects() == 0 && sides.contains(side)) {
            throw new BadRequestException("EMPTY_PLACEMENT",
                    "The " + label + " print placement is selected but the " + label + " is empty.");
        }
    }

    private void apply(CustomDesign design, DesignRequest r, Validated v) {
        design.setBaseProduct(v.product());
        design.setName(r.name() == null || r.name().isBlank() ? null : r.name().trim());
        design.setTshirtType(v.type());
        design.setColorCode(r.colorCode());
        design.setSizeCode(r.sizeCode());
        design.setPlacements(json.writeValueAsString(v.placements()));
        design.setCanvasFront(v.front());
        design.setCanvasBack(v.back());
        design.setTextItems(json.writeValueAsString(r.textItems()));
        design.setPremiumDesign(r.premiumPrint());
    }

    private void storePreviews(CustomDesign design, DesignRequest r, Validated v) {
        // The front preview doubles as the thumbnail, so it is always stored.
        design.setPreviewFrontKey(storePreview(r.previewFront(), "front"));
        design.setPreviewBackKey(v.backUsed() ? storePreview(r.previewBack(), "back") : null);
    }

    private String storePreview(String dataUrl, String side) {
        if (dataUrl == null || !dataUrl.startsWith(PNG_DATA_URL)) {
            throw new BadRequestException("PREVIEW_REQUIRED", "The " + side + " preview image is missing.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(PNG_DATA_URL.length()));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("PREVIEW_INVALID", "The " + side + " preview image is not valid.");
        }
        ImageValidator.ValidatedImage image = ImageValidator.validate(bytes, MAX_PREVIEW_BYTES, MAX_PREVIEW_DIMENSION);
        if (image.type() != ImageValidator.ImageType.PNG) {
            throw new BadRequestException("PREVIEW_INVALID", "The " + side + " preview must be a PNG image.");
        }
        StorageService.StoredFile stored = storage.store("previews", bytes, "png");
        afterRollback(() -> storage.delete(stored.key()));
        return stored.key();
    }

    // ------------------------------------------------------------------ mapping

    private DesignResponse toResponse(CustomDesign d) {
        List<DesignAssetResponse> used = assets.findByCustomDesignId(d.getId()).stream().map(this::toAssetResponse).toList();
        return new DesignResponse(d.getDesignCode(), d.getName(), d.getTshirtType(), d.getColorCode(), d.getSizeCode(),
                placementList(d), readTree(d.getCanvasFront()), readTree(d.getCanvasBack()), readTree(d.getTextItems()),
                d.isPremiumDesign(), mediaUrl(d.getPreviewFrontKey()), mediaUrl(d.getPreviewBackKey()), used,
                d.getStatus().name(), d.isLocked(), d.getBaseProduct().getSlug(), pricing.quote(d), d.getCreatedAt(), d.getUpdatedAt());
    }

    private DesignSummary toSummary(CustomDesign d, User owner) {
        return new DesignSummary(d.getDesignCode(), d.getName(), d.getTshirtType(), d.getColorCode(), d.getSizeCode(),
                placementList(d), d.isPremiumDesign(), mediaUrl(d.getPreviewFrontKey()), mediaUrl(d.getPreviewBackKey()),
                d.getStatus().name(), d.isLocked(), d.getUpdatedAt(),
                owner == null ? null : owner.getId(), owner == null ? null : owner.getFullName(),
                owner == null ? null : owner.getEmail());
    }

    private DesignAssetResponse toAssetResponse(DesignAsset a) {
        return new DesignAssetResponse(a.getId(), a.url(), a.getOriginalFilename(), a.getMimeType(), a.getSizeBytes(),
                a.getWidthPx(), a.getHeightPx());
    }

    private List<String> placementList(CustomDesign d) {
        List<String> out = new ArrayList<>();
        JsonNode node = readTree(d.getPlacements());
        if (node != null) node.forEach(n -> out.add(n.stringValue()));
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private CustomDesign owned(Long userId, String code) {
        return designs.findByCode(code)
                .filter(d -> d.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Design", code));
    }

    private List<CustomizationOption> active(String type) {
        return options.findAllByOptionTypeAndActiveTrueOrderBySortOrderAsc(type);
    }

    private JsonNode metadata(CustomizationOption option) {
        JsonNode node = readTree(option.getMetadata());
        return node == null ? json.createObjectNode() : node;
    }

    private JsonNode readTree(String raw) {
        return raw == null ? null : json.readTree(raw);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.stringValue() : null;
    }

    private static String mediaUrl(String key) {
        return key == null ? null : "/media/" + key;
    }

    private long maxUploadBytes() {
        return settings.getInt("design.upload_max_bytes");
    }

    private int maxUploadDimension() {
        return settings.getInt("design.upload_max_dimension_px");
    }

    private String newCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder("DSN-");
            for (int i = 0; i < 8; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            if (!designs.existsByDesignCode(sb.toString())) {
                return sb.toString();
            }
        }
        throw new IllegalStateException("Could not generate a unique design code");
    }

    static String safeFilename(String original, String extension) {
        String base = original == null ? "" : original.replaceAll("[^A-Za-z0-9._-]", "_");
        base = base.replaceAll("_+", "_").replaceAll("^[._]+", "");
        if (base.length() > 80) base = base.substring(base.length() - 80);
        if (base.isBlank() || !base.contains(".")) base = "artwork." + extension;
        return base;
    }

    private void copy(ZipOutputStream zip, String key, String entryName) throws IOException {
        if (key == null) return;
        Optional<Resource> resource = storage.load(key);
        if (resource.isEmpty()) return;
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = resource.get().getInputStream()) {
            in.transferTo(zip);
        }
        zip.closeEntry();
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static void afterRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) action.run();
                }
            });
        }
    }
}
