package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.dto.events.OrderEventDTO;
import com.nihal.restaurantordering.dto.events.TableSessionEventDTO;
import com.nihal.restaurantordering.events.OrderCreatedEvent;
import com.nihal.restaurantordering.events.OrderStatusChangedEvent;
import com.nihal.restaurantordering.events.TableSessionClosedEvent;
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
    private final OrderEventMapper orderEventMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing order created event to restaurant topic. restaurantId={} orderId={}",
                event.order().restaurantId(), event.order().orderId());
        OrderEventDTO payload = orderEventMapper.toEvent(event.order(), event.order().createdAt());
        messagingTemplate.convertAndSend("/topic/restaurant/" + event.order().restaurantId(), payload);
        messagingTemplate.convertAndSend("/topic/table/" + event.order().tableId(), payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Publishing order status event to table topic. tableId={} orderId={} status={}",
                event.order().tableId(), event.order().orderId(), event.order().status());
        OrderEventDTO payload = orderEventMapper.toEvent(event.order(), event.order().updatedAt());
        messagingTemplate.convertAndSend("/topic/table/" + event.order().tableId(), payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTableSessionClosed(TableSessionClosedEvent event) {
        TableSessionEventDTO payload = TableSessionEventDTO.builder()
                .type("SESSION_CLOSED")
                .tableId(event.session().tableId())
                .restaurantId(event.session().restaurantId())
                .sessionId(event.session().closedSessionId())
                .timestamp(event.session().closedAt())
                .build();
        log.info("Publishing session closed event. tableId={} sessionId={}",
                event.session().tableId(), event.session().closedSessionId());
        messagingTemplate.convertAndSend("/topic/table/" + event.session().tableId(), payload);
        messagingTemplate.convertAndSend("/topic/restaurant/" + event.session().restaurantId(), payload);
    }
}
