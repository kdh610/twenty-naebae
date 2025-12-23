package com.gaebal_easy.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaebal_easy.order.application.dto.*;
import com.gaebal_easy.order.domain.entity.Order;
import com.gaebal_easy.order.domain.entity.OrderProduct;
import com.gaebal_easy.order.domain.enums.ReservationState;
import com.gaebal_easy.order.domain.repository.OrderRepository;
import com.gaebal_easy.order.presentation.dto.CreateOrderRequest;
import com.gaebal_easy.order.presentation.dto.ProductRequestDto;
import gaebal_easy.common.global.exception.Code;
import gaebal_easy.common.global.exception.OrderFailExceiption;
import gaebal_easy.common.global.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final HubClient hubClient;
    private final KafkaTemplate kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() ->new OrderNotFoundException(Code.ORDER_NOT_FOUND_EXCEIPTION));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateOrder(UUID orderId, UpdateOrderDto updateOrderDto) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.changeAddress(updateOrderDto.getAddress());
        orderRepository.save(order);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse processOrder(CreateOrderRequest dto)  {
        Long stockStatus = checkStock(dto);

        if(stockStatus == -1){
            throw new OrderFailExceiption(Code.ORDER_FAIL_EXCEIPTION);
        }

        Order order = createOrder(dto);


        // 허브에 선점했던 재고 확정 처리 요청. Kafka: Order -> Hub
        kafkaTemplate.send("confirm_stock", "product_list", dto.getProducts());

        // 업체에 orderId, receiver, supplier 전송. Kafka: Order -> Store
        kafkaTemplate.send("order_create", "order", CreateOrderKafkaDto.builder()
                .orderId(order.getId())
                .supplierId(dto.getSupplierId())
                .receiverId(dto.getReceiverId())
                .products(dto.getProducts())
                .build());


        kafkaTemplate.send("refill_stock","refill",  objectMapper.writeValueAsString(response));



        return OrderResponse.from(order);
    }

    private Order createOrder(CreateOrderRequest orderRequest) {

        Order order =Order.create(orderRequest.getSupplierId(), orderRequest.getReceiverId(), orderRequest.getOrderRequest(), orderRequest.getAddress());
        for(ProductRequestDto product: orderRequest.getProducts()){
            OrderProduct orderProduct = OrderProduct.create(product.getProductId(), product.getPrice(), product.getQuantity());
            order.addOrderProduct(orderProduct);
        }
        orderRepository.save(order);
        return order;
    }



    /**
     * 재고확인 FeignClient order -> hub
     * @param dto
     * @return
     */
    private Long checkStock(CreateOrderRequest dto) {
        Object obj = hubClient.checkStock(CheckStockDto.builder()
                .hubId(dto.getHubId())
                .products(dto.getProducts())
                .build()).getBody();

        return objectMapper.convertValue(obj, Long.class);
    }


}
