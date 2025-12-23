package com.gaebal_easy.order.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
public class UpdateOrderDto {

    private String address;

    public static UpdateOrderDto create(String address) {
        return UpdateOrderDto.builder().address(address).build();
    }
}
