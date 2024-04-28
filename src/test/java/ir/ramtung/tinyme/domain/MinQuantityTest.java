package ir.ramtung.tinyme.domain;


import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
import ir.ramtung.tinyme.messaging.event.OrderUpdatedEvent;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext

public class MinQuantityTest {
    @Autowired
    OrderHandler orderHandler;
    @Autowired
    EventPublisher eventPublisher;
    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;
    private Security security;
    private Shareholder shareholder;
    private Broker broker1;
    private Broker broker2;
    private Broker broker3;

    @BeforeEach
    void setup() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        broker1 = Broker.builder().brokerId(1).build();
        broker2 = Broker.builder().brokerId(2).build();
        broker3 = Broker.builder().brokerId(2).build();
        brokerRepository.addBroker(broker1);
        brokerRepository.addBroker(broker2);
        brokerRepository.addBroker(broker3);
    }

    @Test
    void does_not_match_because_not_satisfying_min_execution_and_rollback_on_buyer() { //fail
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 550, broker3, shareholder, LocalDateTime.now(), OrderStatus.NEW, 200),
                new Order(2, security, Side.BUY, 430, 500, broker3, shareholder, LocalDateTime.now(), OrderStatus.NEW, 400),
                new Order(3, security, Side.SELL, 100, 520, broker3, shareholder, LocalDateTime.now(), OrderStatus.NEW, 200),
                new Order(6, security, Side.SELL, 350, 560, broker1, shareholder, LocalDateTime.now(), OrderStatus.NEW, 300),
                new Order(7, security, Side.SELL, 100, 590, broker2, shareholder, LocalDateTime.now(), OrderStatus.NEW,50)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), Side.BUY, 304, 550, broker3.getBrokerId(), shareholder.getShareholderId(), 0, 305));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 1, List.of(Message.SO_BIG_ORDER_MIN_EXEC_QUANTITY)));
        assertThat(broker3.getCredit()).isEqualTo(100_000_000);
    }

    @Test
    void does_not_match_because_not_satisfying_min_execution_and_rollback_on_seller() { //2
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 100, 550, broker3, shareholder, LocalDateTime.now(), OrderStatus.NEW, 200),
                new Order(2, security, Side.BUY, 430, 500, broker3, shareholder, LocalDateTime.now(), OrderStatus.NEW, 400),
                new Order(3, security, Side.SELL, 320, 520, broker3, shareholder, LocalDateTime.now(), OrderStatus.NEW, 200),
                new Order(6, security, Side.SELL, 350, 560, broker1, shareholder, LocalDateTime.now(), OrderStatus.NEW, 300),
                new Order(7, security, Side.SELL, 100, 590, broker2, shareholder, LocalDateTime.now(), OrderStatus.NEW,50)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.SELL, 320, 520, broker3.getBrokerId(), shareholder.getShareholderId(), 0, 330));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 3, List.of(Message.SO_BIG_ORDER_MIN_EXEC_QUANTITY)));
        assertThat(broker3.getCredit()).isEqualTo(100_000_000);
    }

    @Test
    void does_not_match_because_negative_min_execution_and_rollback_on_buyer() {
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 550, broker3, shareholder, LocalDateTime.now(), OrderStatus.NEW, -200),
                new Order(2, security, Side.BUY, 430, 500, broker3, shareholder, LocalDateTime.now(), OrderStatus.NEW, 400),
                new Order(3, security, Side.SELL, 100, 520, broker3, shareholder, LocalDateTime.now(), OrderStatus.NEW, 200),
                new Order(6, security, Side.SELL, 350, 560, broker1, shareholder, LocalDateTime.now(), OrderStatus.NEW, 300),
                new Order(7, security, Side.SELL, 100, 590, broker2, shareholder, LocalDateTime.now(), OrderStatus.NEW,50)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), Side.BUY, 304, 550, broker3.getBrokerId(), shareholder.getShareholderId(), 0, -100));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 1, List.of(Message.ORDER_MIN_EXEC_QUANTITY_NOT_POSITIVE)));
        assertThat(broker3.getCredit()).isEqualTo(100_000_000);
    }

    @Test
    void does_not_match_because_negative_min_execution_and_rollback_on_seller() {
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 550, broker3, shareholder, LocalDateTime.now(), OrderStatus.NEW, -200),
                new Order(2, security, Side.BUY, 430, 500, broker3, shareholder, LocalDateTime.now(), OrderStatus.NEW, 400),
                new Order(3, security, Side.SELL, 100, 520, broker3, shareholder, LocalDateTime.now(), OrderStatus.NEW, -200),
                new Order(6, security, Side.SELL, 350, 560, broker1, shareholder, LocalDateTime.now(), OrderStatus.NEW, 300),
                new Order(7, security, Side.SELL, 100, 590, broker2, shareholder, LocalDateTime.now(), OrderStatus.NEW,50)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.SELL, 100, 520, broker3.getBrokerId(), shareholder.getShareholderId(), 0, -200));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 3, List.of(Message.ORDER_MIN_EXEC_QUANTITY_NOT_POSITIVE)));
        assertThat(broker3.getCredit()).isEqualTo(100_000_000);
    }

    @Test
    void update_buyer_min_execution_quantity_does_not_match() {
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder, 300),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder, 200),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder, 100),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder, 120),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder, 240)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 1, LocalDateTime.now(), Side.BUY, 304, 570, broker3.getBrokerId(), shareholder.getShareholderId(), 0, 301));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 1, List.of(Message.CANNOT_CHANGE_MIN_EXEC_QUANTITY_WHILE_UPDATING_REQUEST)));
        assertThat(broker3.getCredit()).isEqualTo(100_000_000);
    }

    @Test
    void update_seller_min_execution_quantity_does_not_match() {

        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder, 300),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder, 200),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder, 100),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder, 120),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder, 240)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));

        broker1.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 6, LocalDateTime.now(), Side.SELL, 350, 580, broker1.getBrokerId(), shareholder.getShareholderId(), 0, 1));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 6, List.of(Message.CANNOT_CHANGE_MIN_EXEC_QUANTITY_WHILE_UPDATING_REQUEST)));
        assertThat(broker1.getCredit()).isEqualTo(100_000_000);
    }

    @Test
    void update_buy_order_and_do_not_change_min_quantity() {

        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder, 420),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 440, 545, broker3.getBrokerId(), shareholder.getShareholderId(), 0, 1));

        verify(eventPublisher).publish(any(OrderUpdatedEvent.class));
        assertThat(orders.get(2).getMinimumExecutionQuantity()).isEqualTo(420);
    }

    @Test
    void update_sell_order_and_do_not_change_min_quantity() {

        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder, 300),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder, 200),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder, 100),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder, 120),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder, 240)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        broker1.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 6, LocalDateTime.now(), Side.SELL, 350, 580, broker1.getBrokerId(), shareholder.getShareholderId(), 0, 1));

        verify(eventPublisher).publish(any(OrderUpdatedEvent.class));
        assertThat(orders.get(3).getMinimumExecutionQuantity()).isEqualTo(120);
        assertThat(broker1.getCredit()).isEqualTo(100_000_000);

    }

}
