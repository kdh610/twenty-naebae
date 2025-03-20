package com.gaebal_easy.hub.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka  // Kafka 관련 어노테이션 활성화
public class KafkaConfig {

    // 환경변수에서 Kafka 서버 주소 가져오기
    @Value("${spring.kafka.url}")
    private String kafkaServerUrl;

    /**
     * Kafka Producer Factory 설정
     * 객체를 JSON 형식으로 직렬화하여 Kafka에 전송
     */
    @Bean
    public <T> ProducerFactory<String, T> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        // Kafka 서버 주소 설정
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServerUrl);
        // 키 직렬화 설정 (String 타입)
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 값 직렬화 설정 (JSON 타입)
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // 모든 복제본이 메시지를 받았을 때만 성공으로 간주
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka Template 설정
     * 제네릭 타입을 사용하여 다양한 객체 타입을 지원
     */
    @Bean
    public <T> KafkaTemplate<String, T> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Kafka Consumer Factory 설정
     * JSON 메시지를 자동으로 객체로 변환
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        // Kafka 서버 주소 설정
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServerUrl);
        // 컨슈머 그룹 ID 설정
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "gaebal-group");
        // 키 역직렬화 설정 (String 타입)
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        // 값 역직렬화 설정 (JSON 타입)
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        // 자동 커밋 설정
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        // 처음 시작 시 가장 오래된 메시지부터 읽음
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // 실제 역직렬화기 지정
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

        // 타입 매핑 추가 - producer 패키지를 현재 서비스의 패키지로 매핑한다. 
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                "com.gaebal_easy.client.user.application.dto.HubManagerInfoMessage:" +
                        "com.gaebal_easy.hub.domain.dto.HubManagerInfoMessage");

        // JsonDeserializer 관련 설정
        // 모든 패키지의 클래스 역직렬화 허용 (보안에 주의)
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        // 타입 정보가 없는 경우 Map으로 변환
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new JsonDeserializer<>(Object.class));
    }

    /**
     * Kafka Listener Container Factory 설정
     * ConcurrentKafkaListenerContainerFactory는 Kafka 메세지를 비동기적으로 수신하는 리스너 컨테이너를 생성하는데 사용.
     * 이 팩토리는 @KafkaListener 어노테이션이 붙은 메서드들을 실행할 컨테이너를 제공.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        // 컨슈머 팩토리 설정
        factory.setConsumerFactory(consumerFactory());
        // 동시에 처리할 수 있는 스레드 수 (파티션 수에 맞게 조정)
        factory.setConcurrency(3);
        // 배치 리스너 비활성화 (개별 메시지 처리)
        factory.setBatchListener(false);

        return factory;
    }
}
