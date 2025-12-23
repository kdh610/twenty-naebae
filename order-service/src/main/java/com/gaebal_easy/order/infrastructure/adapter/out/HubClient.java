package com.gaebal_easy.order.infrastructure.adapter.out;

import com.gaebal_easy.order.application.dto.CheckStockDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "hub-service", contextId = "hubOrderClient")
public interface HubClient {
    @PostMapping("/hub-service/api/products/stock")
    ResponseEntity<?> checkStock(@RequestBody CheckStockDto stockCheckRequest);
}
