package com.gaebal_easy.order.infrastructure.adapter.out;

import com.gaebal_easy.order.application.dto.CreateOrderKafkaDto;
import com.gaebal_easy.order.domain.entity.Order;
import com.gaebal_easy.order.presentation.dto.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderKafkaProducer implements OrderProducer{

    private final KafkaTemplate kafkaTemplate;

    /**
     * Order -> Store 배송생성 요청
     */
    @Override
    public void sendMessageToStore(CreateOrderRequest dto, Order order) {
        kafkaTemplate.send("order_create", "order", CreateOrderKafkaDto.builder()
                .orderId(order.getId())
                .supplierId(dto.getSupplierId())
                .receiverId(dto.getReceiverId())
                .products(dto.getProducts())
                .build());
    }

    @Override
    public void sendMessageToHubDecreaseRealStock(CreateOrderRequest dto) {
        kafkaTemplate.send("confirm_stock", "product_list", dto.getProducts());
    }
}
