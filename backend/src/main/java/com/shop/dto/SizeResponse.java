package com.shop.dto;

import com.shop.entity.SizeGroup;

public record SizeResponse(Long id, String code, String label, SizeGroup sizeGroup, int sortOrder) {
}
