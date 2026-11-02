package com.shop.controller.admin;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shop.dto.PageResponse;
import com.shop.dto.admin.AdminProductListItem;
import com.shop.dto.admin.AdminProductResponse;
import com.shop.dto.admin.AdminVariantResponse;
import com.shop.dto.admin.ImageUpdateRequest;
import com.shop.dto.admin.ProductRequest;
import com.shop.dto.admin.ProductStatusRequest;
import com.shop.dto.admin.VariantsUpdateRequest;
import com.shop.dto.product.ProductImageResponse;
import com.shop.entity.ProductStatus;
import com.shop.exception.BadRequestException;
import com.shop.repository.ProductSearchCriteria;
import com.shop.repository.ProductSort;
import com.shop.service.AdminProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin: products", description = "Manage products, sizes, colours, stock and images (ROLE_ADMIN)")
public class AdminProductController {

    private final AdminProductService service;

    @GetMapping
    @Operation(summary = "List products in any status, with search and filters")
    public PageResponse<AdminProductListItem> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "UPDATED_DESC") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        ProductSort productSort;
        try {
            productSort = ProductSort.valueOf(sort.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("INVALID_SORT", "Unknown sort " + sort);
        }
        Set<ProductStatus> statuses = status != null ? Set.of(status) : null;
        ProductSearchCriteria criteria = new ProductSearchCriteria(q, categoryId, null, null, null, null, null,
                null, null, false, statuses, false, productSort);
        return service.list(criteria, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Full product with images and all variants (including inactive)")
    public AdminProductResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Create a product (DRAFT or INACTIVE; publish after adding sizes and colours)")
    public ResponseEntity<AdminProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product details, prices and status")
    public AdminProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Publish, hide or archive a product")
    public AdminProductResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ProductStatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product that was never ordered (otherwise archive it)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/variants")
    @Operation(summary = "Set the sizes, colours and stock of a product")
    public List<AdminVariantResponse> replaceVariants(@PathVariable Long id,
            @Valid @RequestBody VariantsUpdateRequest request) {
        return service.replaceVariants(id, request);
    }

    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PNG or JPG image (max 5 MB, 4000 x 4000 px)")
    public ResponseEntity<ProductImageResponse> uploadImage(@PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long colorId,
            @RequestParam(required = false) @Size(max = 200) String altText) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addImage(id, file.getBytes(), colorId, altText));
    }

    @PatchMapping("/{id}/images/{imageId}")
    @Operation(summary = "Change alt text, colour link, order or make the image primary")
    public ProductImageResponse updateImage(@PathVariable Long id, @PathVariable Long imageId,
            @Valid @RequestBody ImageUpdateRequest request) {
        return service.updateImage(id, imageId, request);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @Operation(summary = "Delete an image")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        service.deleteImage(id, imageId);
        return ResponseEntity.noContent().build();
    }
}
