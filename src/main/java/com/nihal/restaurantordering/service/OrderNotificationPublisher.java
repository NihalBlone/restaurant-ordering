package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.dto.events.OrderSocketEvent;
import com.nihal.restaurantordering.events.OrderCreatedEvent;
import com.nihal.restaurantordering.events.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing order created event to restaurant topic. restaurantId={} orderId={}",
                event.order().restaurantId(), event.order().orderId());
        messagingTemplate.convertAndSend("/topic/restaurant/" + event.order().restaurantId(),
                OrderSocketEvent.builder()
                        .eventType("ORDER_CREATED")
                        .order(event.order())
                        .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Publishing order status event to table topic. tableId={} orderId={} status={}",
                event.order().tableId(), event.order().orderId(), event.order().status());
        messagingTemplate.convertAndSend("/topic/table/" + event.order().tableId(),
                OrderSocketEvent.builder()
                        .eventType("ORDER_STATUS_UPDATED")
                        .order(event.order())
                        .build());
    }
}
