package com.nihal.restaurantordering.config;

import com.nihal.restaurantordering.repository.RestaurantAdminRepository;
import com.nihal.restaurantordering.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:websocket-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class WebSocketIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private RestaurantAdminRepository accounts;
    @Autowired private JwtService jwt;
    @Autowired private SimpMessagingTemplate messages;

    @Test
    void staffCookieAuthenticatesTheHandshakeAndReceivesRestaurantEvents() throws Exception {
        var account = accounts.findByUsernameNormalized("admin").orElseThrow();
        var headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "restaurant_access_token=" + jwt.createAccessToken(account));
        headers.setOrigin("http://localhost:" + port);
        var received = new LinkedBlockingQueue<String>();
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setDefaultHeartbeat(new long[]{0, 0});
        client.setMessageConverter(new StringMessageConverter());
        StompSession session = null;
        try {
            session = client.connectAsync("ws://localhost:" + port + "/ws-orders/websocket", headers,
                    new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
            String topic = "/topic/restaurant/" + account.getRestaurantId();
            session.subscribe(topic, new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders ignored) { return String.class; }
                @Override public void handleFrame(StompHeaders ignored, Object payload) {
                    received.add((String) payload);
                }
            });
            // The simple broker does not acknowledge subscriptions; retry a harmless test event
            // until the asynchronous SUBSCRIBE is registered, with a bounded deadline.
            String body = null;
            for (int attempt = 0; attempt < 30 && body == null; attempt++) {
                messages.convertAndSend(topic, "restaurant-event-test");
                body = received.poll(100, TimeUnit.MILLISECONDS);
            }
            assertThat(body).isEqualTo("restaurant-event-test");
        } finally {
            if (session != null && session.isConnected()) session.disconnect();
            client.stop();
        }
    }
}
