package com.gaebal_easy.client.hub.application.service;

import com.gaebal_easy.client.hub.application.dto.HubLocationDto;
import com.gaebal_easy.client.hub.application.dto.HubResponseDto;
import com.gaebal_easy.client.hub.application.dto.ProductResponseDto;
import com.gaebal_easy.client.hub.application.dto.checkStockDto.CheckStockDto;
import com.gaebal_easy.client.hub.application.dto.checkStockDto.CheckStockResponse;
import com.gaebal_easy.client.hub.application.dto.checkStockDto.CheckStokProductDto;
import com.gaebal_easy.client.hub.application.dto.checkStockDto.ReservationDto;
import com.gaebal_easy.client.hub.domain.entity.Hub;
import com.gaebal_easy.client.hub.domain.entity.HubProductList;
import com.gaebal_easy.client.hub.domain.entity.Reservation;
import com.gaebal_easy.client.hub.domain.enums.ReservationState;
import com.gaebal_easy.client.hub.domain.repository.HubProductListRepository;
import com.gaebal_easy.client.hub.domain.repository.HubRepository;
import com.gaebal_easy.client.hub.domain.repository.ReservationRepository;
import gaebal_easy.common.global.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class HubService {

    private final HubRepository hubRepository;
    private final HubProductListRepository hubProductListRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ReservationRepository reservationRepository;
    private final KafkaTemplate<String,String> kafkaTemplate;

    public ProductResponseDto getProduct(UUID productId, UUID hubId) {
        Hub hub = getHub(hubId);
        HubProductList hubProductList = getHubProductList(productId);
        if(!hubProductList.getHub().getId().equals(hub.getId()))
            throw new CanNotFindProductInHubException(Code.HUB_CAN_NOT_FIND_PRODUCT_IN_HUB);
        return ProductResponseDto.of(hubProductList,hub);
    }

    public void deleteHub(UUID id) {
        Hub hub = getHub(id);
        hub.delete("deleted by");
        hubRepository.save(hub);
    }

    public HubLocationDto getCoordinate(String hubName) {
        List<Hub> hubList = hubRepository.findAll();
        HubLocationDto hubLocationDto = new HubLocationDto();
        for(Hub hub : hubList){
            String fourHubName = hub.getHubLocation().getName().substring(0,4);
            if(fourHubName.equals(hubName)) {
                hubLocationDto = new HubLocationDto(hub.getHubLocation().getLatitude(), hub.getHubLocation().getLongitude());
            }
        }
        return hubLocationDto;
    }

    public HubResponseDto requireHub(UUID id) {
        Hub hub = getHub(id);
        return HubResponseDto.of(hub);
    }

    private Hub getHub(UUID id){
        return hubRepository.getHub(id).orElseThrow(() -> new HubNotFoundException(Code.HUB_NOT_FOUND));
    }
    private HubProductList getHubProductList(UUID id){
        return hubProductListRepository.getProduct(id).orElseThrow(() -> new ProductNotFoundException(Code.HUB_PRODUCT_NOT_FOUND));
    }

    private final RedisScript<Long> stockScript = RedisScript.of(
            new ClassPathResource("scripts/stock_check.lua"),
            Long.class
    );

    public Long checkStockAndDecrease(CheckStockDto checkStockDto){
        List<CheckStokProductDto> products = checkStockDto.getProducts();
        List<String> keys = products.stream()
                .map(p -> "stock:" + p.getProductId())
                .toList();
        List<String> args = products.stream()
            .map(p -> String.valueOf(p.getQuantity()))
            .toList();

        return redisTemplate.execute(stockScript, keys, args.toArray());
    }




    @Transactional
    public CheckStockResponse checkStock(CheckStockDto stockCheckDto){

        ArrayList<ReservationDto> reservations = new ArrayList<>();
        ReservationState stockState = ReservationState.RESERVE;
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        for(int i=0; i<stockCheckDto.getProducts().size(); i++) {
            CheckStokProductDto product = stockCheckDto.getProducts().get(i);
            UUID productId = product.getProductId();

            String cacheStock = ops.get("stock:" + productId);
            if (cacheStock == null) {
                // 캐싱 Miss
                ProductResponseDto findProduct = getProduct(productId, stockCheckDto.getHubId());
                Long stock = findProduct.getAmount();

                ops.set("stock:" +productId, stock.toString());
            }

            // 재고 감소
            Long tempStock = ops.decrement("stock:" + productId.toString(), product.getQuantity());

            // 예약 생성
            UUID reservationId = UUID.randomUUID();
            Reservation reservation = Reservation.builder()
                    .reservationId(reservationId)
                    .orderId(stockCheckDto.getOrderId())
                    .productId(productId)
                    .quantity(product.getQuantity())
                    .state(ReservationState.RESERVE)
                    .build();

            if(tempStock == 0L) {
                stockState = ReservationState.RE_FILL;
                reservations.add(ReservationDto.from(reservation));
                reservation.changeState(ReservationState.RE_FILL);
            }
            else if (tempStock < 0L) {
                // stock 캐시 롤백
                rollbackStockCache(stockCheckDto, i, ops);

                // order create 보상 트랜잭션
                kafkaTemplate.send("out_of_stock","order-service", stockCheckDto.getOrderId().toString());

                // reservation 테이블 롤백
                throw new OutOfStockException(Code.OUT_OF_STOCK);
            }

            reservationRepository.save(reservation);
        }

        return CheckStockResponse.builder()
                .reservations(reservations)
                .state(stockState)
                .build();
    }

    private static void rollbackStockCache(CheckStockDto stockCheckDto, int i, ValueOperations<String, String> ops) {
        for(int j = 0; j< i +1; j++){
            CheckStokProductDto rollbackProduct = stockCheckDto.getProducts().get(j);
            UUID rollBakcProductId = rollbackProduct.getProductId();

            ops.increment("stock:" + rollBakcProductId.toString(), rollbackProduct.getQuantity());
        }
    }


}
