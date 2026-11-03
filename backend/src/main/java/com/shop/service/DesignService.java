package com.shop.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import com.shop.dto.PageResponse;
import com.shop.dto.design.DesignAssetResponse;
import com.shop.dto.design.DesignRequest;
import com.shop.dto.design.DesignResponse;
import com.shop.dto.design.DesignSummary;
import com.shop.dto.design.DesignerOptionsResponse;

/** Custom T-Shirt designer: options, uploads, saved designs and admin access. */
public interface DesignService {

    DesignerOptionsResponse options();

    DesignAssetResponse uploadAsset(Long userId, byte[] content, String originalFilename);

    DesignResponse create(Long userId, DesignRequest request);

    DesignResponse update(Long userId, String code, DesignRequest request);

    /** Owners see their designs; admins see every design. */
    DesignResponse get(Long userId, boolean admin, String code);

    List<DesignSummary> mine(Long userId);

    void delete(Long userId, String code);

    PageResponse<DesignSummary> adminList(int page, int size);

    /** ZIP with design.json, previews and the original uploaded images. */
    void writeZip(String code, OutputStream out) throws IOException;
}
