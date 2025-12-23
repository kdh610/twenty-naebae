package com.gaebal_easy.client.hub.presentation.adapter.in;

import com.gaebal_easy.client.hub.application.dto.HubDirectDto;
import com.gaebal_easy.client.hub.application.dto.HubLocationDto;
import com.gaebal_easy.client.hub.application.dto.HubRouteDto;
import com.gaebal_easy.client.hub.application.service.HubDirectRedisService;
import com.gaebal_easy.client.hub.application.service.HubMovementService;
import com.gaebal_easy.client.hub.application.service.HubRouteRedisService;
import com.gaebal_easy.client.hub.application.dto.checkStockDto.CheckStockDto;
import com.gaebal_easy.client.hub.application.dto.checkStockDto.CheckStockResponse;
import com.gaebal_easy.client.hub.application.service.HubService;
import com.gaebal_easy.client.hub.presentation.dto.HubCreateRequestDto;
import com.gaebal_easy.client.hub.presentation.dto.HubRequestDto;
import gaebal_easy.common.global.dto.ApiResponseData;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/hub-service/api")
@RequiredArgsConstructor
@Slf4j
public class HubController {

    private final HubService hubService;
    private final HubDirectRedisService hubDirectRedisService;
    private final HubMovementService hubMovementService;
    private final HubRouteRedisService hubRouteRedisService;

    @GetMapping("/getProduct")
    public ResponseEntity<?> requestGetProduct(@RequestParam UUID productId,
                                               @RequestParam UUID hubId) {
        return ResponseEntity.ok(ApiResponseData.success(hubService.getProduct(productId,hubId), "허브에 해당 상품이 존재합니다."));
    }

    //TODO : 헤더에 로그인 유저 전달, deleteHub 메서드에 유저 정보 전달 추가
//    @PreAuthorize("hasRole('MASTER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteHub(@PathVariable UUID id) {
        hubService.deleteHub(id);
        return ResponseEntity.ok(ApiResponseData.success(null));
    }

//    @PreAuthorize("hasRole('MASTER')")
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateHub(@PathVariable UUID id, @RequestBody(required = false) HubRequestDto hubRequestDto) {
        return ResponseEntity.ok(ApiResponseData.success(null,"담당자가 확인 후 수정하겠습니다."));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> requireHub(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponseData.success(hubService.requireHub(id)));
    }
    @PostMapping
    public ResponseEntity<?> createHub(@RequestBody HubCreateRequestDto hubCreateRequestDto) {
        return ResponseEntity.ok(ApiResponseData.success(null,"담당자가 확인 후 허브를 추가하겠습니다."));
    }

    @GetMapping("/direct")
    public HubDirectDto getDirectHub(@RequestParam String depart,
                                        @RequestParam String arrive) {
        HubDirectDto hubDirectDto = hubDirectRedisService.getDirectRedis(depart,arrive);
        if(hubDirectDto!=null) return hubDirectDto;
        return hubMovementService.getDirectHub(depart, arrive);
    }

    @GetMapping("/route")
    public ResponseEntity<?> getHubRoute(@RequestParam String depart,
                                          @RequestParam String arrive) {
        HubRouteDto hubRouteDto = hubRouteRedisService.getRouteRedis(depart,arrive);
        if(hubRouteDto!=null) return ResponseEntity.ok(ApiResponseData.success(hubRouteDto));
        return ResponseEntity.ok(ApiResponseData.success(hubMovementService.getHubRoute(depart, arrive)));
    }

    @GetMapping("/coordinate")
    public HubLocationDto getCoordinate(@RequestParam String hubName) {
        return hubService.getCoordinate(hubName);
    }

    @GetMapping("/hello")
    public ResponseEntity<?> hello() {
        return ResponseEntity.ok(ApiResponseData.success("Hello"));
    }


    @PostMapping("/products/stock")
    public ResponseEntity<?> checkStock(@RequestBody CheckStockDto stockCheckDto) {
        log.info("stockCheck {}", stockCheckDto.toString());
        Long checkStockResult = hubService.checkStockAndDecrease(stockCheckDto);
        return ResponseEntity.ok(checkStockResult);
    }

    @GetMapping("/route-feign")
    public HubRouteDto getRouteForFeign(@RequestParam String depart, @RequestParam String arrive) {
        HubRouteDto hubRouteDto = hubRouteRedisService.getRouteRedis(depart,arrive);
        if(hubRouteDto!=null) return hubRouteDto;
        return hubMovementService.getHubRoute(depart, arrive);
    }
}
