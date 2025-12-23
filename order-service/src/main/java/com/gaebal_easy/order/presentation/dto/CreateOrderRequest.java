package com.gaebal_easy.order.presentation.dto;

import com.gaebal_easy.order.domain.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class CreateOrderRequest {

    private UUID hubId;
    private List<ProductRequestDto> products;
    private String orderRequest;
    private UUID receiverId;
    private UUID supplierId;
    private String address;



}
