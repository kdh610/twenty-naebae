package com.gaebal_easy.order.application.service.consumer;

import com.gaebal_easy.order.domain.entity.Order;
import com.gaebal_easy.order.domain.repository.OrderRepository;
import gaebal_easy.common.global.exception.Code;
import gaebal_easy.common.global.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RollbackOrderService {

    private final OrderRepository orderRepository;

    public void rollbackOrderCreate(UUID orderId){
        Order order = orderRepository.findById(orderId).orElseThrow(()->new OrderNotFoundException(Code.ORDER_NOT_FOUND_EXCEIPTION));
//        orderRepository.delete(order);

    }

}
