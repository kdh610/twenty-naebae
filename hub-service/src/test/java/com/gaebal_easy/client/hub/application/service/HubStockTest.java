package com.gaebal_easy.client.hub.application.service;

import com.gaebal_easy.client.hub.application.dto.checkStockDto.CheckStockDto;
import com.gaebal_easy.client.hub.application.dto.checkStockDto.CheckStockResponse;
import com.gaebal_easy.client.hub.application.dto.checkStockDto.CheckStokProductDto;
import com.gaebal_easy.client.hub.domain.repository.HubProductListRepository;
import com.gaebal_easy.client.hub.domain.repository.HubRepository;
import com.gaebal_easy.client.hub.domain.repository.ReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;
import redis.embedded.RedisServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@ExtendWith(MockitoExtension.class)
class HubStockTest {

    //    @Autowired
    @InjectMocks
    private HubService hubService;

    @Mock
    private HubRepository hubRepository;
    @Mock
    private HubProductListRepository hubProductListRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private RedisTemplate<String, String> redisTemplate;
    private RedisServer redisServer;

    @BeforeEach
    void setUp() {

        redisServer = RedisServer.builder()
                .port(6379)
                .setting("maxheap 128M")
                .build();
        redisServer.start();

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet(); // 중요: 초기화

        hubService = new HubService(
                hubRepository,
                hubProductListRepository,
                redisTemplate, // <-- 실제 RedisTemplate
                kafkaTemplate
        );
    }

    @AfterEach
    void tearDown() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @Test
    @DisplayName("데드락 test")
    @Transactional
    void test() throws InterruptedException {
        redisTemplate.opsForValue().set("stock:6bc4dbbc-05d2-11f0-82d4-0242ac110004", "10000");
        redisTemplate.opsForValue().set("stock:6bc5a25a-05d2-11f0-82d4-0242ac110004", "20000");
        int thred =2;

        ExecutorService executor = Executors.newFixedThreadPool(thred);
        CountDownLatch latch = new CountDownLatch(thred);


        for (int i = 1; i <= thred; i++) {
            CheckStockDto check = null;
            if((i%2)==0) {
                List<CheckStokProductDto> products = new ArrayList<>();
                CheckStokProductDto product1 = CheckStokProductDto.builder()
                        .productId(UUID.fromString("6bc4dbbc-05d2-11f0-82d4-0242ac110004"))
                        .quantity(20L)
                        .build();

                CheckStokProductDto product2 = CheckStokProductDto.builder()
                        .productId(UUID.fromString("6bc5a25a-05d2-11f0-82d4-0242ac110004"))
                        .quantity(10L)
                        .build();

                products.add(product1);
                products.add(product2);

                check = CheckStockDto.builder()
                        .hubId(UUID.fromString("3479b1c5-05d2-11f0-82d4-0242ac110004"))
                        .products(products)
                        .build();
            }else{
                List<CheckStokProductDto> products = new ArrayList<>();
                CheckStokProductDto product1 = CheckStokProductDto.builder()
                        .productId(UUID.fromString("6bc4dbbc-05d2-11f0-82d4-0242ac110004"))
                        .quantity(10L)
                        .build();

                CheckStokProductDto product2 = CheckStokProductDto.builder()
                        .productId(UUID.fromString("6bc5a25a-05d2-11f0-82d4-0242ac110004"))
                        .quantity(20L)
                        .build();

                products.add(product2);
                products.add(product1);

                check = CheckStockDto.builder()
                        .hubId(UUID.fromString("3479b1c5-05d2-11f0-82d4-0242ac110004"))
                        .products(products)
                        .build();
            }

            CheckStockDto finalCheck = check;
            executor.submit(() -> {
                try {
                    hubService.checkStockAndDecrease(finalCheck);
                } catch (Exception e) {
                    Assertions.assertThat("현재 해당 상품의 재고가 없습니다.").isEqualTo(e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();


        String stock1Str = redisTemplate.opsForValue().get("stock:6bc4dbbc-05d2-11f0-82d4-0242ac110004");
        String stock2Str = redisTemplate.opsForValue().get("stock:6bc5a25a-05d2-11f0-82d4-0242ac110004");

        Long stock1 = stock1Str != null ? Long.parseLong(stock1Str) : 0L;
        Long stock2 = stock2Str != null ? Long.parseLong(stock2Str) : 0L;

        org.junit.jupiter.api.Assertions.assertAll(
                () -> Assertions.assertThat(stock1).isEqualTo(9970L),
                () -> Assertions.assertThat(stock2).isEqualTo(19970L)
        );
    }




    @Test
    @DisplayName("여러상품 주문 시 하나라도 재고가 부족할 경우 예외가 발생한다")
    @Transactional
    void AllOrNothingTest() throws InterruptedException {

        int thread =10;
        long productAinitialStock = 100L;
        long productBinitialStock = 1L;
        long quantityAPerRequest = 1;
        long quantityBPerRequest = 2;
        UUID productAId = UUID.fromString("6bc4dbbc-05d2-11f0-82d4-0242ac110004");
        UUID productBId = UUID.fromString("6bc5a25a-05d2-11f0-82d4-0242ac110004");
        String stockAKey = "stock:" + productAId;
        String stockBKey = "stock:" + productBId;

        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        ops.set(stockAKey, String.valueOf(productAinitialStock));
        ops.set(stockBKey, String.valueOf(productBinitialStock));

        ExecutorService executor = Executors.newFixedThreadPool(thread);
        CountDownLatch latch = new CountDownLatch(thread);

        for (int i = 0; i < thread; i++) {

            List<CheckStokProductDto> products = new ArrayList<>();
            CheckStokProductDto productA = CheckStokProductDto.builder()
                    .productId(productAId)
                    .quantity(quantityAPerRequest)
                    .build();

            CheckStokProductDto productB = CheckStokProductDto.builder()
                    .productId(productBId)
                    .quantity(quantityBPerRequest)
                    .build();

            products.add(productA);
            products.add(productB);

            CheckStockDto check = CheckStockDto.builder()
                    .hubId(UUID.fromString("3479b1c5-05d2-11f0-82d4-0242ac110004"))
                    .orderId(UUID.fromString("3479b1c5-05d2-11f0-82d4-0242ac110004"))
                    .products(products)
                    .build();

            executor.submit(() -> {
                try {
                    hubService.checkStockAndDecrease(check);
                } catch (Exception e) {
                    Assertions.assertThat("현재 해당 상품의 재고가 없습니다.").isEqualTo(e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        String finalStockA = ops.get(stockAKey);
        String finalStockB = ops.get(stockBKey);

        org.junit.jupiter.api.Assertions.assertAll(
            // 상품 A는 충분했음에도, B 때문에 롤백되어 100개 그대로여야 함
            () -> Assertions.assertThat(finalStockA).isEqualTo("100"),

            // 상품 B는 차감 시도조차 못했거나(순서에 따라), 실패했으므로 1개 그대로여야 함
            () -> Assertions.assertThat(finalStockB).isEqualTo("1")
        );

    }

    @Test
    @DisplayName("여러상품 주문 시 모두 재고가 충분한 경우 테스트 성공")
    @Transactional
    void purchaseAllSuccessTest() throws InterruptedException {

        int thread =10;
        long productAinitialStock = 100L;
        long productBinitialStock = 20L;
        long quantityAPerRequest = 1;
        long quantityBPerRequest = 2;
        UUID productAId = UUID.fromString("6bc4dbbc-05d2-11f0-82d4-0242ac110004");
        UUID productBId = UUID.fromString("6bc5a25a-05d2-11f0-82d4-0242ac110004");
        String stockAKey = "stock:" + productAId;
        String stockBKey = "stock:" + productBId;

        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        ops.set(stockAKey, String.valueOf(productAinitialStock));
        ops.set(stockBKey, String.valueOf(productBinitialStock));

        ExecutorService executor = Executors.newFixedThreadPool(thread);
        CountDownLatch latch = new CountDownLatch(thread);

        for (int i = 0; i < thread; i++) {

            List<CheckStokProductDto> products = new ArrayList<>();
            CheckStokProductDto productA = CheckStokProductDto.builder()
                    .productId(productAId)
                    .quantity(quantityAPerRequest)
                    .build();

            CheckStokProductDto productB = CheckStokProductDto.builder()
                    .productId(productBId)
                    .quantity(quantityBPerRequest)
                    .build();

            products.add(productA);
            products.add(productB);

            CheckStockDto check = CheckStockDto.builder()
                    .hubId(UUID.fromString("3479b1c5-05d2-11f0-82d4-0242ac110004"))
                    .orderId(UUID.fromString("3479b1c5-05d2-11f0-82d4-0242ac110004"))
                    .products(products)
                    .build();

            executor.submit(() -> {
                try {
                    hubService.checkStockAndDecrease(check);
                } catch (Exception e) {

                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        assertEquals("90", ops.get(stockAKey));
        assertEquals("0", ops.get(stockBKey));
    }


    @Test
    @DisplayName("hubService.checkStock 재고부족 테스트")
    @Transactional
    void outOfStockTest() throws InterruptedException {
        int thread =10;
        long initialStock = 9L;
        long quantityPerRequest = 1;
        UUID productId = UUID.fromString("6bc4dbbc-05d2-11f0-82d4-0242ac110004");
        String stockKey = "stock:" + productId;

        ExecutorService executor = Executors.newFixedThreadPool(thread);
        CountDownLatch latch = new CountDownLatch(thread);

        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        ops.set(stockKey, String.valueOf(initialStock));

        for (int i = 0; i < thread; i++) {
            List<CheckStokProductDto> products = new ArrayList<>();

            CheckStokProductDto product1 = CheckStokProductDto.builder()
                    .productId(productId)
                    .quantity(quantityPerRequest)
                    .build();
            products.add(product1);

            CheckStockDto check = CheckStockDto.builder()
                    .hubId(UUID.fromString("3479b1c5-05d2-11f0-82d4-0242ac110004"))
                    .orderId(UUID.fromString("3479b1c5-05d2-11f0-82d4-0242ac110004"))
                    .products(products)
                    .build();

            executor.submit(() -> {
                try {
                    hubService.checkStockAndDecrease(check);
                } catch (Exception e) {
                    Assertions.assertThat("현재 해당 상품의 재고가 없습니다.").isEqualTo(e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
    }

    @Test
    @DisplayName("재고가 10000개인 상품을 10000명의 사용자가 동시에 구매하여 재고가 0이된다")
    @Transactional
    void concurrencyStockTest() throws InterruptedException {
        int thread =100;
        long initialStock = 100L;
        long quantityPerRequest = 1;
        UUID productId = UUID.fromString("6bc4dbbc-05d2-11f0-82d4-0242ac110004");
        String stockKey = "stock:" + productId;

        ExecutorService executor = Executors.newFixedThreadPool(thread);
        CountDownLatch latch = new CountDownLatch(thread);

        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        ops.set(stockKey, String.valueOf(initialStock));

        for (int i = 0; i < thread; i++) {
            List<CheckStokProductDto> products = new ArrayList<>();

            CheckStokProductDto product1 = CheckStokProductDto.builder()
                    .productId(productId)
                    .quantity(quantityPerRequest)
                    .build();
            products.add(product1);

            CheckStockDto check = CheckStockDto.builder()
                    .hubId(UUID.fromString("3479b1c5-05d2-11f0-82d4-0242ac110004"))
                    .orderId(UUID.fromString("3479b1c5-05d2-11f0-82d4-0242ac110004"))
                    .products(products)
                    .build();

            executor.submit(() -> {
                try {
                    hubService.checkStockAndDecrease(check);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        assertEquals("0", ops.get(stockKey));
    }


}