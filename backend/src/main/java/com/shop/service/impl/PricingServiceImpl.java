package com.shop.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.dto.pricing.AdminChargeResponse;
import com.shop.dto.pricing.ChargeUpdateRequest;
import com.shop.dto.pricing.PriceEstimateRequest;
import com.shop.dto.pricing.PriceLine;
import com.shop.dto.pricing.PriceQuote;
import com.shop.dto.pricing.PricingInfo;
import com.shop.dto.pricing.ShippingSettings;
import com.shop.entity.AppSetting;
import com.shop.entity.CustomDesign;
import com.shop.entity.CustomizationCharge;
import com.shop.entity.CustomizationOption;
import com.shop.entity.Product;
import com.shop.entity.ProductColor;
import com.shop.entity.ProductSize;
import com.shop.entity.SizeGroup;
import com.shop.exception.BadRequestException;
import com.shop.repository.AppSettingRepository;
import com.shop.repository.CustomizationChargeRepository;
import com.shop.repository.ProductColorRepository;
import com.shop.repository.ProductSizeRepository;
import com.shop.service.AppSettingService;
import com.shop.service.PricingService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Price of a custom T-Shirt = selling price of the blank T-Shirt for the chosen type + active
 * charges for the type, each print placement, custom text (once), premium print, size and
 * colour. All amounts include GST and are rounded to paise.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PricingServiceImpl implements PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingServiceImpl.class);
    static final String CURRENCY = "INR";
    static final String SHIPPING_FLAT = "shipping.flat_charge";
    static final String SHIPPING_FREE = "shipping.free_threshold";
    static final String MAX_QTY = "cart.max_quantity_per_item";

    private final DesignRules rules;
    private final CustomizationChargeRepository charges;
    private final ProductSizeRepository sizes;
    private final ProductColorRepository colors;
    private final AppSettingService settings;
    private final AppSettingRepository settingRepository;
    private final JsonMapper json;

    // ------------------------------------------------------------------ public

    @Override
    public PricingInfo info() {
        List<PricingInfo.Charge> list = charges.findAllByActiveTrue().stream()
                .filter(c -> c.getAmount().signum() > 0)
                .map(c -> new PricingInfo.Charge(c.getChargeType(), c.getCode(), money(c.getAmount())))
                .toList();
        return new PricingInfo(list, money(settings.getDecimal(SHIPPING_FLAT)), money(settings.getDecimal(SHIPPING_FREE)),
                settings.getInt(MAX_QTY));
    }

    @Override
    public PriceQuote estimate(PriceEstimateRequest r) {
        CustomizationOption type = rules.type(r.tshirtType());
        Product product = rules.baseProduct(type);
        rules.requireVariant(product, r.colorCode(), r.sizeCode(), type.getLabel());
        List<String> placements = r.placements().stream().distinct().toList();
        rules.checkPlacements(placements);
        int quantity = quantity(r.quantity());
        return calculate(product, type.getCode(), type.getLabel(), r.colorCode(), r.sizeCode(), placements, r.hasText(),
                r.premiumPrint(), quantity);
    }

    @Override
    public PriceQuote quote(CustomDesign design) {
        return quote(design, 1);
    }

    @Override
    public PriceQuote quote(CustomDesign design, int quantity) {
        String typeLabel = rules.active(CustomizationOption.TSHIRT_TYPE).stream()
                .filter(o -> o.getCode().equals(design.getTshirtType()))
                .map(CustomizationOption::getLabel)
                .findFirst()
                .orElse(design.getTshirtType());
        List<String> placements = new ArrayList<>();
        JsonNode stored = read(design.getPlacements());
        if (stored != null) stored.forEach(n -> placements.add(n.stringValue()));
        JsonNode texts = read(design.getTextItems());
        boolean hasText = texts != null && texts.isArray() && !texts.isEmpty();
        return calculate(design.getBaseProduct(), design.getTshirtType(), typeLabel, design.getColorCode(),
                design.getSizeCode(), placements, hasText, design.isPremiumDesign(), quantity(quantity));
    }

    // ------------------------------------------------------------------ admin

    @Override
    public List<AdminChargeResponse> adminCharges() {
        Map<String, CustomizationCharge> existing = charges.findAll().stream()
                .collect(Collectors.toMap(c -> key(c.getChargeType(), c.getCode()), Function.identity()));
        List<AdminChargeResponse> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> group : chargeable().entrySet()) {
            for (Map.Entry<String, String> choice : group.getValue().entrySet()) {
                CustomizationCharge c = existing.get(key(group.getKey(), choice.getKey()));
                out.add(c == null
                        ? new AdminChargeResponse(null, group.getKey(), choice.getKey(), choice.getValue(), money(BigDecimal.ZERO),
                                false, null, null)
                        : new AdminChargeResponse(c.getId(), c.getChargeType(), c.getCode(), choice.getValue(),
                                money(c.getAmount()), c.isActive(), c.getDescription(), c.getUpdatedAt()));
            }
        }
        return out;
    }

    @Override
    @Transactional
    public AdminChargeResponse saveCharge(ChargeUpdateRequest r) {
        String label = chargeable().getOrDefault(r.type(), Map.of()).get(r.code());
        if (label == null) {
            throw new BadRequestException("UNKNOWN_CHARGE", "There is no " + r.type() + " choice with code " + r.code() + ".");
        }
        CustomizationCharge charge = charges.findByChargeTypeAndCode(r.type(), r.code()).orElseGet(() -> {
            CustomizationCharge c = new CustomizationCharge();
            c.setChargeType(r.type());
            c.setCode(r.code());
            if (CustomizationCharge.TSHIRT_TYPE.equals(r.type()) || CustomizationCharge.PRINT_PLACEMENT.equals(r.type())) {
                rules.active(r.type()).stream().filter(o -> o.getCode().equals(r.code())).findFirst()
                        .ifPresent(o -> c.setOptionId(o.getId()));
            }
            return c;
        });
        BigDecimal before = charge.getAmount();
        charge.setAmount(money(r.amount()));
        charge.setActive(r.active());
        String description = r.description() == null || r.description().isBlank() ? label + " charge" : r.description().trim();
        charge.setDescription(description);
        CustomizationCharge saved = charges.saveAndFlush(charge);
        log.info("Pricing: {} {} set to {} (was {}), active={}", r.type(), r.code(), saved.getAmount(), before, saved.isActive());
        return new AdminChargeResponse(saved.getId(), saved.getChargeType(), saved.getCode(), label, money(saved.getAmount()),
                saved.isActive(), saved.getDescription(), saved.getUpdatedAt());
    }

    @Override
    public ShippingSettings shipping() {
        return new ShippingSettings(money(settings.getDecimal(SHIPPING_FLAT)), money(settings.getDecimal(SHIPPING_FREE)));
    }

    @Override
    @Transactional
    public ShippingSettings updateShipping(ShippingSettings r) {
        setting(SHIPPING_FLAT, money(r.flatCharge()));
        setting(SHIPPING_FREE, money(r.freeThreshold()));
        log.info("Pricing: shipping set to {} below {}", money(r.flatCharge()), money(r.freeThreshold()));
        return new ShippingSettings(money(r.flatCharge()), money(r.freeThreshold()));
    }

    // ------------------------------------------------------------------ calculation

    private PriceQuote calculate(Product product, String typeCode, String typeLabel, String colorCode, String sizeCode,
            List<String> placements, boolean hasText, boolean premium, int quantity) {
        Map<String, CustomizationCharge> active = charges.findAllByActiveTrue().stream()
                .collect(Collectors.toMap(c -> key(c.getChargeType(), c.getCode()), Function.identity()));
        Map<String, String> placementLabels = new HashMap<>();
        rules.active(CustomizationOption.PRINT_PLACEMENT).forEach(o -> placementLabels.put(o.getCode(), o.getLabel()));

        List<PriceLine> lines = new ArrayList<>();
        add(lines, active, CustomizationCharge.TSHIRT_TYPE, typeCode, typeLabel + " style");
        for (String p : placements) {
            add(lines, active, CustomizationCharge.PRINT_PLACEMENT, p, placementLabels.getOrDefault(p, p) + " print");
        }
        if (hasText) {
            add(lines, active, CustomizationCharge.TEXT, CustomizationCharge.CUSTOM_TEXT, "Custom text");
        }
        if (premium) {
            add(lines, active, CustomizationCharge.DESIGN, CustomizationCharge.PREMIUM_DESIGN, "Premium print");
        }
        add(lines, active, CustomizationCharge.SIZE, sizeCode, "Size " + sizeCode);
        add(lines, active, CustomizationCharge.COLOR, colorCode, label(colorCode) + " colour");

        BigDecimal basePrice = money(product.getEffectivePrice());
        BigDecimal custom = money(lines.stream().map(PriceLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal unit = money(basePrice.add(custom));
        BigDecimal subtotal = money(unit.multiply(BigDecimal.valueOf(quantity)));
        BigDecimal flat = money(settings.getDecimal(SHIPPING_FLAT));
        BigDecimal threshold = money(settings.getDecimal(SHIPPING_FREE));
        boolean free = subtotal.compareTo(threshold) >= 0;
        return new PriceQuote(CURRENCY, product.getSlug(), money(product.getBasePrice()), basePrice, List.copyOf(lines), custom,
                unit, quantity, subtotal, free ? money(BigDecimal.ZERO) : flat, threshold,
                free ? money(BigDecimal.ZERO) : money(threshold.subtract(subtotal)), true);
    }

    private static void add(List<PriceLine> lines, Map<String, CustomizationCharge> active, String type, String code,
            String label) {
        CustomizationCharge charge = active.get(key(type, code));
        if (charge != null && charge.getAmount().signum() > 0) {
            lines.add(new PriceLine(type, code, label, money(charge.getAmount())));
        }
    }

    /** Every choice that can carry a charge, as type -> (code -> label), in display order. */
    private Map<String, Map<String, String>> chargeable() {
        Map<String, Map<String, String>> out = new java.util.LinkedHashMap<>();
        out.put(CustomizationCharge.TSHIRT_TYPE, labels(rules.active(CustomizationOption.TSHIRT_TYPE)));
        out.put(CustomizationCharge.PRINT_PLACEMENT, labels(rules.active(CustomizationOption.PRINT_PLACEMENT)));
        out.put(CustomizationCharge.TEXT, Map.of(CustomizationCharge.CUSTOM_TEXT, "Custom text"));
        out.put(CustomizationCharge.DESIGN, Map.of(CustomizationCharge.PREMIUM_DESIGN, "Premium print"));
        Map<String, String> sizeLabels = new java.util.LinkedHashMap<>();
        for (ProductSize s : sizes.findAllBySizeGroupAndActiveTrueOrderBySortOrderAsc(SizeGroup.APPAREL)) {
            sizeLabels.put(s.getCode(), s.getCode());
        }
        out.put(CustomizationCharge.SIZE, sizeLabels);
        Map<String, String> colorLabels = new java.util.LinkedHashMap<>();
        for (ProductColor c : colors.findAllByActiveTrueOrderBySortOrderAsc()) {
            colorLabels.put(c.getCode(), c.getName());
        }
        out.put(CustomizationCharge.COLOR, colorLabels);
        return out;
    }

    private static Map<String, String> labels(List<CustomizationOption> options) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        options.forEach(o -> map.put(o.getCode(), o.getLabel()));
        return map;
    }

    private void setting(String key, BigDecimal value) {
        AppSetting setting = settingRepository.findByKey(key)
                .orElseThrow(() -> new IllegalStateException("Missing application setting: " + key));
        setting.setValue(value.toPlainString());
    }

    private int quantity(Integer requested) {
        int q = requested == null ? 1 : requested;
        int max = settings.getInt(MAX_QTY);
        if (q < 1 || q > max) {
            throw new BadRequestException("INVALID_QUANTITY", "Quantity must be between 1 and " + max + ".");
        }
        return q;
    }

    private JsonNode read(String raw) {
        return raw == null ? null : json.readTree(raw);
    }

    private static String key(String type, String code) {
        return type + ":" + code;
    }

    static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** SKY_BLUE -> Sky blue */
    static String label(String code) {
        String text = code == null ? "" : code.toLowerCase().replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
