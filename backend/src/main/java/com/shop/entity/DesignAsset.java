package com.shop.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An image a customer uploaded for a design (validated PNG or JPEG). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "design_assets")
public class DesignAsset extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The design that first used this asset; null until a design is saved. */
    @Column(name = "custom_design_id")
    private Long customDesignId;

    @Column(name = "storage_key", nullable = false, length = 255, unique = true)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String sha256;

    public String url() {
        return "/media/" + storageKey;
    }
}
