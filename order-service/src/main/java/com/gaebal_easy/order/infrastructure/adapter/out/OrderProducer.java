package com.gaebal_easy.order.infrastructure.adapter.out;

import com.gaebal_easy.order.domain.entity.Order;
import com.gaebal_easy.order.presentation.dto.CreateOrderRequest;


public interface OrderProducer {

    void sendMessageToStore(CreateOrderRequest dto, Order order);
    void sendMessageToHubDecreaseRealStock(CreateOrderRequest dto);

}
