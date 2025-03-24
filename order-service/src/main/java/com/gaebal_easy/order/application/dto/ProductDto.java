package com.gaebal_easy.order.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ProductDto {
    private UUID productId;
    private String productName;
    private Long quantity;
    private Long price;
}
