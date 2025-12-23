package com.gaebal_easy.order.application.dto;

import com.gaebal_easy.order.presentation.dto.ProductRequestDto;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CheckStockDto {

    private List<ProductRequestDto> products;
    private UUID hubId;

}
