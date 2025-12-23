package com.gaebal_easy.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaebal_easy.order.application.dto.CheckStockDto;
import com.gaebal_easy.order.application.dto.OrderResponse;
import com.gaebal_easy.order.application.dto.UpdateOrderDto;
import com.gaebal_easy.order.domain.entity.Order;
import com.gaebal_easy.order.domain.entity.OrderProduct;
import com.gaebal_easy.order.domain.repository.OrderRepository;
import com.gaebal_easy.order.infrastructure.adapter.out.HubClient;
import com.gaebal_easy.order.infrastructure.adapter.out.OrderProducer;
import com.gaebal_easy.order.presentation.dto.CreateOrderRequest;
import com.gaebal_easy.order.presentation.dto.ProductRequestDto;
import gaebal_easy.common.global.exception.Code;
import gaebal_easy.common.global.exception.OrderFailExceiption;
import gaebal_easy.common.global.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final HubClient hubClient;
    private final OrderProducer orderProducer;
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
        Long stockStatus = checkStockFromHub(dto);

        if(outOfStock(stockStatus)){
            throw new OrderFailExceiption(Code.ORDER_FAIL_EXCEIPTION);
        }

        Order order = createOrder(dto);

        orderProducer.sendMessageToHubDecreaseRealStock(dto);
        orderProducer.sendMessageToStore(dto, order);

        return OrderResponse.from(order);
    }

    private static boolean outOfStock(Long stockStatus) {
        return stockStatus == -1;
    }

    /**
     * 재고확인 FeignClient order -> hub
     * @param dto
     * @return
     */
    private Long checkStockFromHub(CreateOrderRequest dto) {
        Object obj = hubClient.checkStock(CheckStockDto.builder()
                .hubId(dto.getHubId())
                .products(dto.getProducts())
                .build()).getBody();

        return objectMapper.convertValue(obj, Long.class);
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


}
