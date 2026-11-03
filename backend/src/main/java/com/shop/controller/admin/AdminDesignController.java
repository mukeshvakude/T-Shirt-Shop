package com.shop.controller.admin;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.shop.dto.PageResponse;
import com.shop.dto.design.DesignResponse;
import com.shop.dto.design.DesignSummary;
import com.shop.service.DesignService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/designs")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin: designs", description = "View and download customer T-Shirt designs (ROLE_ADMIN)")
public class AdminDesignController {

    private static final String CODE = "^DSN-[A-Z0-9]{8}$";

    private final DesignService designService;

    @GetMapping
    @Operation(summary = "All customer designs, newest first")
    public PageResponse<DesignSummary> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return designService.adminList(page, size);
    }

    @GetMapping("/{code}")
    @Operation(summary = "One design with canvases, text and artwork")
    public DesignResponse get(@PathVariable @Pattern(regexp = CODE) String code) {
        return designService.get(null, true, code);
    }

    @GetMapping("/{code}/download")
    @Operation(summary = "ZIP with design.json, front/back previews and original artwork")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable @Pattern(regexp = CODE) String code) {
        designService.get(null, true, code); // 404 before streaming starts
        StreamingResponseBody body = out -> designService.writeZip(code, out);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(code + ".zip").build().toString())
                .body(body);
    }
}
