package com.gaebal_easy.order.domain.entity;


import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Table(name = "p_order_product")
public class OrderProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "price")
    private Long price;

    @Column(name = "quantity")
    private Long quantity;

    public static OrderProduct create(UUID productId, Long price, Long quantity){
        return OrderProduct.builder()
                .productId(productId)
                .price(price)
                .quantity(quantity)
                .build();
    }

    protected void setOrder(Order order) {
        if(this.order != null){
            this.order.getOrderProducts().remove(this);
        }
        this.order = order;
        this.order.getOrderProducts().add(this);
    }

    public Long calculatePrice(){
        return this.price * this.quantity;
    }


}
