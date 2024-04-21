package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class OrderHandlerTest {
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
    void new_order_matched_completely_with_one_trade() {
        Order matchingBuyOrder = new Order(100, security, Side.BUY, 1000, 15500, broker1, shareholder);
        Order incomingSellOrder = new Order(200, security, Side.SELL, 300, 15450, broker2, shareholder);
        security.getOrderBook().enqueue(matchingBuyOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 300, 15450, 2, shareholder.getShareholderId(), 0));

        Trade trade = new Trade(security, matchingBuyOrder.getPrice(), incomingSellOrder.getQuantity(),
                matchingBuyOrder, incomingSellOrder);
        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 200)));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade))));
    }

    @Test
    void new_order_queued_with_no_trade() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 300, 15450, 2, shareholder.getShareholderId(), 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
    }
    @Test
    void new_order_matched_partially_with_two_trades() {
        Order matchingBuyOrder1 = new Order(100, security, Side.BUY, 300, 15500, broker1, shareholder);
        Order matchingBuyOrder2 = new Order(110, security, Side.BUY, 300, 15500, broker1, shareholder);
        Order incomingSellOrder = new Order(200, security, Side.SELL, 1000, 15450, broker2, shareholder);
        security.getOrderBook().enqueue(matchingBuyOrder1);
        security.getOrderBook().enqueue(matchingBuyOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,
                incomingSellOrder.getSecurity().getIsin(),
                incomingSellOrder.getOrderId(),
                incomingSellOrder.getEntryTime(),
                incomingSellOrder.getSide(),
                incomingSellOrder.getTotalQuantity(),
                incomingSellOrder.getPrice(),
                incomingSellOrder.getBroker().getBrokerId(),
                incomingSellOrder.getShareholder().getShareholderId(), 0));

        Trade trade1 = new Trade(security, matchingBuyOrder1.getPrice(), matchingBuyOrder1.getQuantity(),
                matchingBuyOrder1, incomingSellOrder);
        Trade trade2 = new Trade(security, matchingBuyOrder2.getPrice(), matchingBuyOrder2.getQuantity(),
                matchingBuyOrder2, incomingSellOrder.snapshotWithQuantity(700));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade1), new TradeDTO(trade2))));
    }

    @Test
    void iceberg_order_behaves_normally_before_being_queued() {
        Order matchingBuyOrder = new Order(100, security, Side.BUY, 1000, 15500, broker1, shareholder);
        Order incomingSellOrder = new IcebergOrder(200, security, Side.SELL, 300, 15450, broker2, shareholder, 100);
        security.getOrderBook().enqueue(matchingBuyOrder);
        Trade trade = new Trade(security, matchingBuyOrder.getPrice(), incomingSellOrder.getQuantity(),
                matchingBuyOrder, incomingSellOrder);

        EventPublisher mockEventPublisher = mock(EventPublisher.class, withSettings().verboseLogging());
        OrderHandler myOrderHandler = new OrderHandler(securityRepository, brokerRepository, shareholderRepository, mockEventPublisher, new Matcher());
        myOrderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,
                incomingSellOrder.getSecurity().getIsin(),
                incomingSellOrder.getOrderId(),
                incomingSellOrder.getEntryTime(),
                incomingSellOrder.getSide(),
                incomingSellOrder.getTotalQuantity(),
                incomingSellOrder.getPrice(),
                incomingSellOrder.getBroker().getBrokerId(),
                incomingSellOrder.getShareholder().getShareholderId(), 100));

        verify(mockEventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(mockEventPublisher).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade))));
    }

    @Test
    void invalid_new_order_with_multiple_errors() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "XXX", -1, LocalDateTime.now(), Side.SELL, 0, 0, -1, -1, 0));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(-1);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.UNKNOWN_SECURITY_ISIN,
                Message.INVALID_ORDER_ID,
                Message.ORDER_PRICE_NOT_POSITIVE,
                Message.ORDER_QUANTITY_NOT_POSITIVE,
                Message.INVALID_PEAK_SIZE,
                Message.UNKNOWN_BROKER_ID,
                Message.UNKNOWN_SHAREHOLDER_ID
        );
    }

    @Test
    void invalid_new_order_with_tick_and_lot_size_errors() {
        Security aSecurity = Security.builder().isin("XXX").lotSize(10).tickSize(10).build();
        securityRepository.addSecurity(aSecurity);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "XXX", 1, LocalDateTime.now(), Side.SELL, 12, 1001, 1, shareholder.getShareholderId(), 0));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(1);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE,
                Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE
        );
    }

    @Test
    void update_order_causing_no_trades() {
        Order queuedOrder = new Order(200, security, Side.SELL, 500, 15450, broker1, shareholder);
        security.getOrderBook().enqueue(queuedOrder);
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 1000, 15450, 1, shareholder.getShareholderId(), 0));
        verify(eventPublisher).publish(new OrderUpdatedEvent(1, 200));
    }

    @Test
    void handle_valid_update_with_trades() {
        Order matchingOrder = new Order(1, security, Side.BUY, 500, 15450, broker1, shareholder);
        Order beforeUpdate = new Order(200, security, Side.SELL, 1000, 15455, broker2, shareholder);
        Order afterUpdate = new Order(200, security, Side.SELL, 500, 15450, broker2, shareholder);
        security.getOrderBook().enqueue(matchingOrder);
        security.getOrderBook().enqueue(beforeUpdate);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 1000, 15450, broker2.getBrokerId(), shareholder.getShareholderId(), 0));

        Trade trade = new Trade(security, 15450, 500, matchingOrder, afterUpdate);
        verify(eventPublisher).publish(new OrderUpdatedEvent(1, 200));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade))));
    }

    @Test
    void invalid_update_with_order_id_not_found() {
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 1000, 15450, 1, shareholder.getShareholderId(), 0));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 200, any()));
    }

    @Test
    void invalid_update_with_multiple_errors() {
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "XXX", -1, LocalDateTime.now(), Side.SELL, 0, 0, -1, shareholder.getShareholderId(), 0));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(-1);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.UNKNOWN_SECURITY_ISIN,
                Message.UNKNOWN_BROKER_ID,
                Message.INVALID_ORDER_ID,
                Message.ORDER_PRICE_NOT_POSITIVE,
                Message.ORDER_QUANTITY_NOT_POSITIVE,
                Message.INVALID_PEAK_SIZE
        );
    }

    @Test
    void delete_buy_order_deletes_successfully_and_increases_credit() {
        Broker buyBroker = Broker.builder().credit(1_000_000).build();
        brokerRepository.addBroker(buyBroker);
        Order someOrder = new Order(100, security, Side.BUY, 300, 15500, buyBroker, shareholder);
        Order queuedOrder = new Order(200, security, Side.BUY, 1000, 15500, buyBroker, shareholder);
        security.getOrderBook().enqueue(someOrder);
        security.getOrderBook().enqueue(queuedOrder);
        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, security.getIsin(), Side.BUY, 200));
        verify(eventPublisher).publish(new OrderDeletedEvent(1, 200));
        assertThat(buyBroker.getCredit()).isEqualTo(1_000_000 + 1000*15500);
    }

    @Test
    void delete_sell_order_deletes_successfully_and_does_not_change_credit() {
        Broker sellBroker = Broker.builder().credit(1_000_000).build();
        brokerRepository.addBroker(sellBroker);
        Order someOrder = new Order(100, security, Side.SELL, 300, 15500, sellBroker, shareholder);
        Order queuedOrder = new Order(200, security, Side.SELL, 1000, 15500, sellBroker, shareholder);
        security.getOrderBook().enqueue(someOrder);
        security.getOrderBook().enqueue(queuedOrder);
        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, security.getIsin(), Side.SELL, 200));
        verify(eventPublisher).publish(new OrderDeletedEvent(1, 200));
        assertThat(sellBroker.getCredit()).isEqualTo(1_000_000);
    }


    @Test
    void invalid_delete_with_order_id_not_found() {
        Broker buyBroker = Broker.builder().credit(1_000_000).build();
        brokerRepository.addBroker(buyBroker);
        Order queuedOrder = new Order(200, security, Side.BUY, 1000, 15500, buyBroker, shareholder);
        security.getOrderBook().enqueue(queuedOrder);
        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, "ABC", Side.SELL, 100));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 100, List.of(Message.ORDER_ID_NOT_FOUND)));
        assertThat(buyBroker.getCredit()).isEqualTo(1_000_000);
    }

    @Test
    void invalid_delete_order_with_non_existing_security() {
        Order queuedOrder = new Order(200, security, Side.BUY, 1000, 15500, broker1, shareholder);
        security.getOrderBook().enqueue(queuedOrder);
        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, "XXX", Side.SELL, 200));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 200, List.of(Message.UNKNOWN_SECURITY_ISIN)));
    }

    @Test
    void buyers_credit_decreases_on_new_order_without_trades() {
        Broker broker = Broker.builder().brokerId(10).credit(10_000).build();
        brokerRepository.addBroker(broker);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 30, 100, 10, shareholder.getShareholderId(), 0));
        assertThat(broker.getCredit()).isEqualTo(10_000-30*100);
    }

    @Test
    void buyers_credit_decreases_on_new_iceberg_order_without_trades() {
        Broker broker = Broker.builder().brokerId(10).credit(10_000).build();
        brokerRepository.addBroker(broker);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 30, 100, 10, shareholder.getShareholderId(), 10));
        assertThat(broker.getCredit()).isEqualTo(10_000-30*100);
    }

    @Test
    void credit_does_not_change_on_invalid_new_order() {
        Broker broker = Broker.builder().brokerId(10).credit(10_000).build();
        brokerRepository.addBroker(broker);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", -1, LocalDateTime.now(), Side.BUY, 30, 100, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        assertThat(broker.getCredit()).isEqualTo(10_000);
    }

    @Test
    void credit_updated_on_new_order_matched_partially_with_two_orders() {
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(100_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));

        Order matchingSellOrder1 = new Order(100, security, Side.SELL, 30, 500, broker1, shareholder);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 500, broker2, shareholder);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 100, 550, broker3.getBrokerId(), shareholder.getShareholderId(), 0));

        assertThat(broker1.getCredit()).isEqualTo(100_000 + 30*500);
        assertThat(broker2.getCredit()).isEqualTo(100_000 + 20*500);
        assertThat(broker3.getCredit()).isEqualTo(100_000 - 50*500 - 50*550);
    }

    @Test
    void new_order_from_buyer_with_not_enough_credit_no_trades() {
        Broker broker = Broker.builder().brokerId(10).credit(1000).build();
        brokerRepository.addBroker(broker);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 30, 100, 10, shareholder.getShareholderId(), 0));
        assertThat(broker.getCredit()).isEqualTo(1000);
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 200, List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
    }

    @Test
    void new_order_from_buyer_with_enough_credit_based_on_trades() {
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(52_500).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));
        Order matchingSellOrder1 = new Order(100, security, Side.SELL, 30, 500, broker1, shareholder);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 500, broker2, shareholder);
        Order incomingBuyOrder = new Order(200, security, Side.BUY, 100, 550, broker3, shareholder);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);
        Trade trade1 = new Trade(security, matchingSellOrder1.getPrice(), matchingSellOrder1.getQuantity(),
                incomingBuyOrder, matchingSellOrder1);
        Trade trade2 = new Trade(security, matchingSellOrder2.getPrice(), matchingSellOrder2.getQuantity(),
                incomingBuyOrder.snapshotWithQuantity(700), matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 100, 550, broker3.getBrokerId(), shareholder.getShareholderId(), 0));

        assertThat(broker1.getCredit()).isEqualTo(100_000 + 30*500);
        assertThat(broker2.getCredit()).isEqualTo(100_000 + 20*500);
        assertThat(broker3.getCredit()).isEqualTo(0);

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade1), new TradeDTO(trade2))));
    }

    @Test
    void new_order_from_buyer_with_not_enough_credit_based_on_trades() {
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(2).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(3).credit(50_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));
        Order matchingSellOrder1 = new Order(100, security, Side.SELL, 30, 500, broker1, shareholder);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 500, broker2, shareholder);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 100, 550, broker3.getBrokerId(), shareholder.getShareholderId(), 0));

        assertThat(broker1.getCredit()).isEqualTo(100_000);
        assertThat(broker2.getCredit()).isEqualTo(100_000);
        assertThat(broker3.getCredit()).isEqualTo(50_000);

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 200, List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
    }

    @Test
    void update_buy_order_changing_price_with_no_trades_changes_buyers_credit() {
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);
        Order order = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder);
        security.getOrderBook().enqueue(order);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.BUY, 30, 550, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        assertThat(broker1.getCredit()).isEqualTo(100_000 - 1_500);
    }
    @Test
    void update_sell_order_changing_price_with_no_trades_does_not_changes_sellers_credit() {
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);
        Order order = new Order(100, security, Side.SELL, 30, 500, broker1, shareholder);
        security.getOrderBook().enqueue(order);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.SELL, 30, 550, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        assertThat(broker1.getCredit()).isEqualTo(100_000);
    }

    @Test
    void update_order_changing_price_with_trades_changes_buyers_and_sellers_credit() {
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(100_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 2, LocalDateTime.now(), Side.BUY, 500, 590, broker3.getBrokerId(), shareholder.getShareholderId(), 0));

        assertThat(broker1.getCredit()).isEqualTo(100_000 + 350*580);
        assertThat(broker2.getCredit()).isEqualTo(100_000 + 100*581);
        assertThat(broker3.getCredit()).isEqualTo(100_000 + 430*550 - 350*580 - 100*581 - 50*590);
    }

    @Test
    void update_order_changing_price_with_trades_for_buyer_with_insufficient_quantity_rolls_back() {
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(54_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        Order originalOrder = orders.get(1).snapshot();
        originalOrder.queue();

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 2, LocalDateTime.now(), Side.BUY, 500, 590, broker3.getBrokerId(), shareholder.getShareholderId(), 0));

        assertThat(broker1.getCredit()).isEqualTo(100_000);
        assertThat(broker2.getCredit()).isEqualTo(100_000);
        assertThat(broker3.getCredit()).isEqualTo(54_000);
        assertThat(originalOrder).isEqualTo(security.getOrderBook().findByOrderId(Side.BUY, 2));
    }

    @Test
    void update_order_without_trade_decreasing_quantity_changes_buyers_credit() {
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(100_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 2, LocalDateTime.now(), Side.BUY, 400, 550, broker3.getBrokerId(), shareholder.getShareholderId(), 0));

        assertThat(broker1.getCredit()).isEqualTo(100_000);
        assertThat(broker2.getCredit()).isEqualTo(100_000);
        assertThat(broker3.getCredit()).isEqualTo(100_000 + 30*550);
    }

    @Test
    void new_sell_order_without_enough_positions_is_rejected() {
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 200, List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
    }

    @Test
    void update_sell_order_without_enough_positions_is_rejected() {
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 6, LocalDateTime.now(), Side.SELL, 450, 580, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 6, List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
    }

    @Test
    void update_sell_order_with_enough_positions_is_executed() {
        Shareholder shareholder1 = Shareholder.builder().build();
        shareholder1.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder1);
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder1),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder1),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder1),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 6, LocalDateTime.now(), Side.SELL, 250, 570, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        verify(eventPublisher).publish(any(OrderExecutedEvent.class));
        assertThat(shareholder1.hasEnoughPositionsOn(security, 100_000 + 250)).isTrue();
        assertThat(shareholder.hasEnoughPositionsOn(security, 99_500 - 251)).isFalse();
    }

    @Test
    void new_buy_order_does_not_check_for_position() {
        Shareholder shareholder1 = Shareholder.builder().build();
        shareholder1.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder1);
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder1),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder1),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder1),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 500, 570, broker3.getBrokerId(), shareholder.getShareholderId(), 0));

        verify(eventPublisher).publish(any(OrderAcceptedEvent.class));
        assertThat(shareholder1.hasEnoughPositionsOn(security, 100_000)).isTrue();
        assertThat(shareholder.hasEnoughPositionsOn(security, 500)).isTrue();
    }

    @Test
    void update_buy_order_does_not_check_for_position() {
        Shareholder shareholder1 = Shareholder.builder().build();
        shareholder1.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder1);
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder1),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder1),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder1),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 500, 545, broker3.getBrokerId(), shareholder1.getShareholderId(), 0));

        verify(eventPublisher).publish(any(OrderAcceptedEvent.class));
        assertThat(shareholder1.hasEnoughPositionsOn(security, 100_000)).isTrue();
        assertThat(shareholder.hasEnoughPositionsOn(security, 500)).isTrue();
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
        //shareholder.decPosition(security, 99_500);
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
        //assertThat(broker3.getCredit()).isEqualTo(100_000_000);
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
        //shareholder.decPosition(security, 99_500);
        broker1.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 6, LocalDateTime.now(), Side.SELL, 350, 580, broker1.getBrokerId(), shareholder.getShareholderId(), 0, 1));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 6, List.of(Message.CANNOT_CHANGE_MIN_EXEC_QUANTITY_WHILE_UPDATING_REQUEST)));
        //assertThat(broker1.getCredit()).isEqualTo(100_000_000);
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
        //shareholder.decPosition(security, 99_500);
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
        //shareholder.decPosition(security, 99_500);
        broker1.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 6, LocalDateTime.now(), Side.SELL, 350, 580, broker1.getBrokerId(), shareholder.getShareholderId(), 0, 1));

        verify(eventPublisher).publish(any(OrderUpdatedEvent.class));
        assertThat(orders.get(3).getMinimumExecutionQuantity()).isEqualTo(120);
        assertThat(broker1.getCredit()).isEqualTo(100_000_000);

    }



    ////test phase5
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
        //assertThat(broker2.getCredit()).isEqualTo(1000_000 - 133_400);

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
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.BUY, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        // public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, long minimumExecutionQuantity, long stopPrice)
        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.BUY, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 450));
        verify(eventPublisher).publish(any(OrderAcceptedEvent.class));
        //assertThat(broker3.getCredit()).isEqualTo(1000_000 - 131_100);
    }

    @Test
    void update_buyer_stop_limit() {
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.BUY, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        // public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, long minimumExecutionQuantity, long stopPrice)
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.BUY, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 490));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
    }

    @Test
    void update_seller_stop_limit() {
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.SELL, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        // public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, long minimumExecutionQuantity, long stopPrice)
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 490));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
    }

    @Test
    void update_seller_stop_limit_order_when_stop_price_is_active() {

        Order sellOrder1 = new Order(100, security, Side.SELL, 30, 400, broker1, shareholder);
        Order buyOrder1 = new Order(102, security, Side.BUY, 50, 500, broker2, shareholder);

        broker1.increaseCreditBy(1000_000);
        broker2.increaseCreditBy(1000_000);

        security.getOrderBook().enqueue(sellOrder1);
        security.getOrderBook().enqueue(buyOrder1);

//        orders.forEach(order -> security.getOrderBook().enqueue(order))

        Trade trade1 = new Trade(security, sellOrder1.getPrice(), sellOrder1.getQuantity(),
                buyOrder1, sellOrder1);

        StopLimitOrder stopLimitOrder1 = new StopLimitOrder(8, security, Side.SELL, 230, 500, broker2, shareholder, 0, 450); //stoplimit

        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 450));
        verify(eventPublisher).publish(any(OrderAcceptedEvent.class));


        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 480));

        verify(eventPublisher).publish(any(OrderRejectedEvent.class));

    }

    @Test
    void update_buyer_stop_limit_order_when_stop_price_is_active() {

    }

    @Test
    void update_min_quantity_of_stop_limit_order() {        /////Separate seller and buyer?

        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.SELL, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        // public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, long minimumExecutionQuantity, long stopPrice)
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 100, 450));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
        ///publish ham mikone?
    }

    @Test
    void create_new_iceberg_seller_order_with_stop_limit_order() {
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.SELL, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        // public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, long minimumExecutionQuantity, long stopPrice)
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.SELL, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 90, 490));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 8, List.of(Message.STOP_LIMIT_ORDER_CANNOT_BE_ICEBERG)));   ///chderaaaa publish nemikoni
        ///publish ham mikone????

    }

    @Test
    void create_new_iceberg_buyer_order_with_stop_limit_order() {
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.BUY, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        // public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, long minimumExecutionQuantity, long stopPrice)
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.BUY, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 90, 490));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
        //verify(eventPublisher).publish(new OrderExecutedEvent(1, 8, List.of(Message.STOP_LIMIT_ORDER_CANNOT_BE_ICEBERG)));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 8, List.of(Message.STOP_LIMIT_ORDER_CANNOT_BE_ICEBERG)));
    }


//    @Test
//    void new_order_matched_completely_with_one_trade() {
//        Order matchingBuyOrder = new Order(100, security, Side.BUY, 1000, 15500, broker1, shareholder);
//        Order incomingSellOrder = new Order(200, security, Side.SELL, 300, 15450, broker2, shareholder);
//        security.getOrderBook().enqueue(matchingBuyOrder);
//
//        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 300, 15450, 2, shareholder.getShareholderId(), 0));
//
//        Trade trade = new Trade(security, matchingBuyOrder.getPrice(), incomingSellOrder.getQuantity(),
//                matchingBuyOrder, incomingSellOrder);
//        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 200)));
//        verify(eventPublisher).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade))));
//    }

    @Test
    void wtf() {     ///should I separate buyer and seller?

        Order sellOrder1 = new Order(100, security, Side.SELL, 30, 400, broker1, shareholder);
        Order buyOrder1 = new Order(102, security, Side.BUY, 50, 500, broker2, shareholder);

        broker1.increaseCreditBy(1000_000);
        broker2.increaseCreditBy(1000_000);

        security.getOrderBook().enqueue(sellOrder1);
        security.getOrderBook().enqueue(buyOrder1);

//        orders.forEach(order -> security.getOrderBook().enqueue(order))

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.SELL, 30, 400, broker1.getBrokerId(), shareholder.getShareholderId(), 0));


        Trade trade1 = new Trade(security, buyOrder1.getPrice(), sellOrder1.getQuantity(),
                buyOrder1, sellOrder1);

        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 100)));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 100, List.of(new TradeDTO(trade1))));     ///mishe bedune trade execute kard?

        assertThat(broker1.getCredit()).isEqualTo(1000_000 + (500 * 30));


    }

    @Test
    void when_stop_price_is_active_it_will_activate_all_stop_limit_orders_with_the_same_stop_price() {
        StopLimitOrder stopLimitOrder1 = new StopLimitOrder(8, security, Side.SELL, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        StopLimitOrder stopLimitOrder2 = new StopLimitOrder(9, security, Side.SELL, 260, 520, broker3, shareholder, 0, 460);
        StopLimitOrder stopLimitOrder3 = new StopLimitOrder(10, security, Side.SELL, 200, 530, broker3, shareholder, 0, 470);


        security.getStopLimitOrderBook().enqueue(stopLimitOrder1);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder2);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder3);

        Order sellOrder2 = new Order(22, security, Side.SELL, 20, 400, broker1, shareholder);
        Order buyOrder2 = new Order(24, security, Side.BUY, 50, 440, broker2, shareholder);

        security.getOrderBook().enqueue(sellOrder2);
        security.getOrderBook().enqueue(buyOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 22, LocalDateTime.now(), Side.SELL, 20, 400, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 22)));
        verify(eventPublisher).publish(any(OrderExecutedEvent.class));

        //////////stopLimit har 3tashun faal mishe va bayad check konim ke har 3ta trade beshan
        //verify(eventPublisher).publish(new OrderExecutedEvent(2, 103, List.of(new TradeDTO(trade2))));

        //assertThat(broker1.getCredit()).isEqualTo(1000_000 + (500 * 30) + (440 * 20));
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
        broker2.increaseCreditBy(1000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.SELL, 30, 400, broker1.getBrokerId(), shareholder.getShareholderId(), 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 100));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(8, "ABC", 9, LocalDateTime.now(), Side.SELL, 230, 500, broker3.getBrokerId(), shareholder.getShareholderId(), 0, 450));

        orderHandler.handleDeleteOrder(new DeleteOrderRq(2, "ABC", Side.SELL, 8));

        verify(eventPublisher).publish(new OrderDeletedEvent(2, 8));

    }

    @Test
    void new_order_will_activate_new_stop_limit_order_which_will_activate_another_stop_limit_order() {
        //add orders
        //trade
        //add some stop limit orders
        //active stop limit order
        //it activates another stop limit order

    }

    @Test
    void new_order_will_not_activate_the_stop_limit_order() {

    }

    @Test
    void stop_limit_order_will_act_like_normal_order_when_stop_price_is_active() {
        //no need to test
    }

    @Test
    void update_stop_limit_order_and_stop_price_is_passive() {
        //some orders
        //add stop limit order
        //update stop limit order
        //pass
    }

    @Test
    void new_stop_limit_order_with_min_quantity() {
        StopLimitOrder order1 = new StopLimitOrder(8, security, Side.BUY, 230, 500, broker3, shareholder, 0, 450); //stoplimit
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(1, "ABC", 8, LocalDateTime.now(), Side.BUY, 230, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 30, 450));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
    }

    @Test
    void hashemTest(){
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








