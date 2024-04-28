package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
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

//Test phase 5

public class StopLimitTest {
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
    void create_new_seller_stop_limit_order_successfully() {

        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder, 300),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder, 200),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder, 100),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder, 120),
                new Order(7, security, Side.SELL, 100, 581, broker1, shareholder, 240)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        broker2.increaseCreditBy(1000_000);
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.SELL, 230, 500, broker2, shareholder, 0, 580); //stoplimit
        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 570));
        verify(eventPublisher).publish(any(OrderAcceptedEvent.class));
    }

    @Test
    void create_new_buyer_stop_limit_order_successfully() {
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder, 300),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder, 200),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder, 100),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder, 120),
                new Order(7, security, Side.SELL, 100, 581, broker1, shareholder, 240)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        broker3.increaseCreditBy(1000_000);
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.BUY, 230, 500, broker2, shareholder, 0, 450); //stoplimit
        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.BUY, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 450));
        verify(eventPublisher).publish(any(OrderAcceptedEvent.class));
    }

    @Test
    void update_buyer_stop_price_when_stop_limit_is_passive() {
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.BUY, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        broker3.increaseCreditBy(100_000_000);
        security.getStopLimitOrderBook().enqueue(order1);
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.BUY, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 490));
        verify(eventPublisher).publish(any(OrderUpdatedEvent.class));
    }

    @Test
    void update_seller_stop_price_when_stop_limit_is_passive() {
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.SELL, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        security.getStopLimitOrderBook().enqueue(order1);
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 490));
        verify(eventPublisher).publish(any(OrderUpdatedEvent.class));
    }

    @Test
    void update_stop_price_when_stop_price_is_active() {

        Order sellOrder1 = new Order(100, security, Side.SELL, 30, 400, broker1, shareholder);
        Order buyOrder1 = new Order(102, security, Side.BUY, 50, 500, broker2, shareholder);

        broker1.increaseCreditBy(1000_000);
        broker2.increaseCreditBy(1000_000);

        security.getOrderBook().enqueue(sellOrder1);
        security.getOrderBook().enqueue(buyOrder1);

        Trade trade1 = new Trade(security, sellOrder1.getPrice(), sellOrder1.getQuantity(),
                buyOrder1, sellOrder1);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 450));
        verify(eventPublisher).publish(any(OrderAcceptedEvent.class));


        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 480));

        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 8, List.of(Message.ORDER_ID_NOT_FOUND)));


    }

    @Test
    void update_min_quantity_of_stop_limit_order() {

        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.SELL, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 100, 450));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 8, List.of(Message.STOP_LIMIT_ORDER_CANNOT_HAVE_MIN_EXEC)));
    }

    @Test
    void update_seller_stoplimit_order_with_peaksize() {
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.SELL, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        security.getStopLimitOrderBook().enqueue(order1);
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 90, 490));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 8, List.of(Message.STOP_LIMIT_ORDER_CANNOT_BE_ICEBERG)));

    }

    @Test
    void update_buyer_stoplimit_order_with_peaksize() {
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.BUY, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        security.getStopLimitOrderBook().enqueue(order1);
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.BUY, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 90, 490));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 8, List.of(Message.STOP_LIMIT_ORDER_CANNOT_BE_ICEBERG)));
    }

    @Test
    void when_stop_price_is_active_it_will_activate_all_stop_limit_orders_with_the_same_stop_price() {
        StopLimitOrder stopLimitOrder1 = new StopLimitOrder(8, security, Side.SELL, 10, 350, broker3, shareholder, 0, 450); //stoplimit
        StopLimitOrder stopLimitOrder2 = new StopLimitOrder(9, security, Side.SELL, 5, 370, broker3, shareholder, 0, 460);
        StopLimitOrder stopLimitOrder3 = new StopLimitOrder(10, security, Side.SELL, 7, 390, broker3, shareholder, 0, 470);


        security.getStopLimitOrderBook().enqueue(stopLimitOrder1);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder2);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder3);

        Order sellOrder2 = new Order(22, security, Side.SELL, 20, 400, broker1, shareholder);
        Order buyOrder2 = new Order(24, security, Side.BUY, 50, 440, broker2, shareholder);

        security.getOrderBook().enqueue(sellOrder2);
        security.getOrderBook().enqueue(buyOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 22, LocalDateTime.now(), Side.SELL, 20, 400, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 22)));


        assertThat(broker1.getCredit()).isEqualTo((440 * 20));
        assertThat(broker3.getCredit()).isEqualTo((440 * 22));

    }


    @Test
    void delete_stop_limit_order_when_stop_price_is_passive() {     ///should I separate buyer and seller?
        StopLimitOrder stopLimitOrder1 = new StopLimitOrder(8, security, Side.SELL, 230, 500, broker3, shareholder, 0, 450); //stoplimit

        security.getStopLimitOrderBook().enqueue(stopLimitOrder1);
        orderHandler.handleDeleteOrder(new DeleteOrderRq(2, "ABC", Side.SELL, 8));

        verify(eventPublisher).publish(new OrderDeletedEvent(2, 8));
    }

    @Test
    void delete_stop_limit_order_when_stop_price_is_active() {
        Order buyOrder1 = new Order(102, security, Side.BUY, 50, 500, broker2, shareholder);
        security.getOrderBook().enqueue(buyOrder1);
        broker2.increaseCreditBy(1000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.SELL, 30, 400, broker1.getBrokerId(), shareholder.getShareholderId(), 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 100));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(8, "ABC", 8, LocalDateTime.now(), Side.SELL, 230, 500, broker3.getBrokerId(), shareholder.getShareholderId(), 0, 550));

        orderHandler.handleDeleteOrder(new DeleteOrderRq(2, "ABC", Side.SELL, 8));
        verify(eventPublisher).publish(new OrderDeletedEvent(2, 8));
    }

/*    @Test
    void new_order_will_activate_new_stop_limit_order_which_will_activate_another_stop_limit_order() {
        broker2.increaseCreditBy(1000_000);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(1, "ABC", 1,
                LocalDateTime.now(), Side.BUY, 230, 640, broker3.getBrokerId(), shareholder.getShareholderId(),
                0, 650));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(2, "ABC", 2,
                LocalDateTime.now(), Side.BUY, 200, 590, broker3.getBrokerId(), shareholder.getShareholderId(),
                0, 600));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(3, "ABC", 3,
                LocalDateTime.now(), Side.BUY, 100, 580, broker3.getBrokerId(), shareholder.getShareholderId(),
                0, 590));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(4, "ABC", 4,
                LocalDateTime.now(), Side.BUY, 100, 570, broker3.getBrokerId(), shareholder.getShareholderId(),
                0, 600));

        Order buyOrder1 = new Order(102, security, Side.BUY, 50, 650, broker2, shareholder);
        security.getOrderBook().enqueue(buyOrder1);


        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(5, "ABC", 100,
                LocalDateTime.now(), Side.SELL, 30, 500, broker1.getBrokerId(),
                shareholder.getShareholderId(), 0));   //priceT = 650
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 100));
        verify(eventPublisher).publish(any(OrderExecutedEvent.class));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(6, "ABC", 103,
                LocalDateTime.now(), Side.SELL, 30, 500, broker1.getBrokerId(),
                shareholder.getShareholderId(), 0));    //priceT = 600
        verify(eventPublisher).publish(new OrderAcceptedEvent(2, 103));
        verify(eventPublisher).publish(any(OrderExecutedEvent.class));
        verify(eventPublisher).publish(any(OrderExecutedEvent.class));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(7, "ABC", 104,
                LocalDateTime.now(), Side.SELL, 30, 500, broker1.getBrokerId(),
                shareholder.getShareholderId(), 0));    //priceT = 550
        verify(eventPublisher).publish(new OrderAcceptedEvent(3, 104));
        verify(eventPublisher).publish(any(OrderExecutedEvent.class));


    }*/

    @Test
    void new_stop_limit_order_with_min_quantity() {
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.BUY, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.BUY, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 30, 450));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 8, List.of(Message.STOP_LIMIT_ORDER_CANNOT_HAVE_MIN_EXEC)));
    }

    @Test
    void stop_limit_order_activated_from_beginning(){
        broker1.increaseCreditBy(1000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(),
                Side.BUY, 10, 100, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(),
                Side.BUY, 10, 110, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(4, "ABC", 4, LocalDateTime.now(),
                Side.SELL, 5, 100, broker2.getBrokerId(), shareholder.getShareholderId(), 0));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(3 , "ABC", 3,
                LocalDateTime.now(), Side.SELL, 15, 100, broker2.getBrokerId() ,shareholder.getShareholderId(),
                0, 120));

        assertThat(broker1.getCredit()).isEqualTo(1000_000 - 2100);
    }



}
