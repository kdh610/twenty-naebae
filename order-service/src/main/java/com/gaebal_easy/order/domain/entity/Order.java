package com.gaebal_easy.order.domain.entity;


import gaebal_easy.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SQLRestriction("is_deleted = false")
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Table(name = "p_order")
public class Order extends BaseTimeEntity {

    @Builder(access = AccessLevel.PROTECTED)
    public Order(UUID supplier, UUID receiver, String orderRequest, Long totalPrice, String address) {
        this.supplierId = supplier;
        this.receiverId = receiver;
        this.orderRequest = orderRequest;
        this.totalPrice = totalPrice;
        this.address = address;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "supplier", nullable = false)
    private UUID supplierId;
    @Column(name = "recevier", nullable = false)
    private UUID receiverId;
    @Column(name = "order_request")
    private String orderRequest;
    @Column(name = "total_price", nullable = false)
    private Long totalPrice;
    @Column(name = "address", nullable = false)
    private String address;

    @OneToMany(mappedBy = "order", cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
    private List<OrderProduct> orderProducts = new ArrayList<>();


    public void addOrderProduct(OrderProduct orderProduct) {
        if (this.orderProducts == null) {
            this.orderProducts = new ArrayList<>();
        }
        this.orderProducts.add(orderProduct);
        if(orderProduct.getOrder() != this) {
            orderProduct.setOrder(this);
        }

        this.totalPrice += orderProduct.calculatePrice();
    }

    public static Order create(UUID supplier, UUID receiver, String orderRequest, String address){
        return Order.builder()
                .supplier(supplier)
                .receiver(receiver)
                .orderRequest(orderRequest)
                .totalPrice(0L)
                .address(address)
                .build();
    }

    public void changeAddress(String address) {
        this.address = address;
    }



}
