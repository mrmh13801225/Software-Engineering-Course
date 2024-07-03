package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.setPrintAssertionsDescription;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext

public class AuctionTest {
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
    private AuctionSecurity auctionSecurity;
    private List<Order> orders;

    private AuctionSecurity newAuctionSecurity;
    private OrderBook testOrderBook;
    private AuctionMatcher matcher = new AuctionMatcher();

    @BeforeEach
    void setup() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        auctionSecurity = AuctionSecurity.builder().isin("CBA").price(630).build();
        newAuctionSecurity = AuctionSecurity.builder().isin("BCA").build();
        securityRepository.addSecurity(security);
        securityRepository.addSecurity(auctionSecurity);
        securityRepository.addSecurity(newAuctionSecurity);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        broker1 = Broker.builder().brokerId(1).build();
        broker2 = Broker.builder().brokerId(2).build();
        broker3 = Broker.builder().brokerId(2).build();
        brokerRepository.addBroker(broker1);
        brokerRepository.addBroker(broker2);
        brokerRepository.addBroker(broker3);
        testOrderBook = newAuctionSecurity.getOrderBook();
        orders = Arrays.asList(
                new Order(1, newAuctionSecurity, Side.BUY, 5, 30, broker1, shareholder, 0),
                new Order(2, newAuctionSecurity, Side.BUY, 5,20 , broker1, shareholder, 0),
                new Order(3, newAuctionSecurity, Side.BUY, 5, 10, broker1, shareholder, 0),
                new Order(6, newAuctionSecurity, Side.SELL, 5, 5, broker2, shareholder, 0),
                new Order(7, newAuctionSecurity, Side.SELL, 5, 14, broker2, shareholder, 0),
                new Order(8, newAuctionSecurity, Side.SELL, 5, 25, broker2, shareholder, 0)
        );
        orders.forEach(order -> testOrderBook.enqueue(order));
    }

    @Test
    void iceberg_order_accepted_in_auction(){
        shareholder.incPosition(auctionSecurity,100_000_000);
        brokerRepository.findBrokerById(broker2.getBrokerId()).increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "CBA", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                10));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
    }

    @Test
    void accept_convert_auction_to_continuous() {

        shareholder.incPosition(auctionSecurity,100_000_000);
        brokerRepository.findBrokerById(broker2.getBrokerId()).increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "CBA", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "CBA", 10,
                LocalDateTime.now(), Side.BUY, 300, 600, broker2.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(2, "CBA", MatchingState.CONTINUOUS));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(eventPublisher).publish(new OpeningPriceEvent(auctionSecurity.getIsin(), 630, 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(3, 10));
        verify(eventPublisher).publish(new OpeningPriceEvent(auctionSecurity.getIsin(), 600, 300));
        verify(eventPublisher).publish(any(SecurityStateChangedEvent.class));
        verify(eventPublisher).publish(any(TradeEvent.class));
    }

    @Test
    void opening_price_calculation_is_correct() {
        shareholder.incPosition(auctionSecurity,100_000_000);
        brokerRepository.findBrokerById(broker2.getBrokerId()).increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "CBA", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "CBA", 10,
                LocalDateTime.now(), Side.BUY, 300, 600, broker2.getBrokerId(), shareholder.getShareholderId(),
                0));

        assertThat(auctionSecurity.getOrderBook().getOpeningPrice()).isEqualTo(600);
    }

    @Test
    void tradable_quantity_calculation_is_correct() {
        shareholder.incPosition(auctionSecurity,100_000_000);
        brokerRepository.findBrokerById(broker2.getBrokerId()).increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "CBA", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "CBA", 10,
                LocalDateTime.now(), Side.BUY, 300, 600, broker2.getBrokerId(), shareholder.getShareholderId(),
                0));

        assertThat(auctionSecurity.getOrderBook().getTradableQuantity()).isEqualTo(300);
    }

    @Test
    void security_last_price_equals_to_opening_price_after_trade() {

        shareholder.incPosition(auctionSecurity,100_000_000);
        brokerRepository.findBrokerById(broker2.getBrokerId()).increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "CBA", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "CBA", 10,
                LocalDateTime.now(), Side.BUY, 300, 600, broker2.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(2, "CBA", MatchingState.AUCTION));
        assertThat(auctionSecurity.getPrice()).isEqualTo(600);
    }

    @Test
    void accept_convert_auction_to_auction() {
        shareholder.incPosition(auctionSecurity,100_000_000);
        brokerRepository.findBrokerById(broker2.getBrokerId()).increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "CBA", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "CBA", 10,
                LocalDateTime.now(), Side.BUY, 300, 600, broker2.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(2, "CBA", MatchingState.AUCTION));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(eventPublisher).publish(new OpeningPriceEvent(auctionSecurity.getIsin(), 630, 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(3, 10));
        verify(eventPublisher).publish(new OpeningPriceEvent(auctionSecurity.getIsin(), 600, 300));
        verify(eventPublisher).publish(any(SecurityStateChangedEvent.class));
        verify(eventPublisher).publish(any(TradeEvent.class));
    }

    @Test
    void accept_convert_continuous_to_auction() {

        shareholder.incPosition(auctionSecurity,100_000_000);
        brokerRepository.findBrokerById(broker2.getBrokerId()).increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "ABC", 10,
                LocalDateTime.now(), Side.BUY, 300, 600, broker2.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(2, "ABC", MatchingState.AUCTION));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(eventPublisher).publish(new OrderAcceptedEvent(3, 10));
        verify(eventPublisher).publish(any(OrderExecutedEvent.class));
        verify(eventPublisher).publish(any(SecurityStateChangedEvent.class));

    }

    @Test
    void accept_convert_continuous_to_continuous() {

        shareholder.incPosition(auctionSecurity,100_000_000);
        brokerRepository.findBrokerById(broker2.getBrokerId()).increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "ABC", 10,
                LocalDateTime.now(), Side.BUY, 300, 600, broker2.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(2, "ABC", MatchingState.CONTINUOUS));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(eventPublisher).publish(new OrderAcceptedEvent(3, 10));
        verify(eventPublisher).publish(any(OrderExecutedEvent.class));
        verify(eventPublisher).publish(any(SecurityStateChangedEvent.class));

    }

    @Test
    void reject_new_min_exec_quantity_order_to_auction(){
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "CBA", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0, 10));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
    }

    @Test
    void reject_new_stop_limit_order_to_auction(){
        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(1, "CBA", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0, 10));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
    }

    @Test
    void buyer_with_not_enough_credit_is_rejected()
    {
        shareholder.incPosition(auctionSecurity,100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "ABC", 10,
                LocalDateTime.now(), Side.BUY, 300, 600, broker2.getBrokerId(), shareholder.getShareholderId(),
                0));
        verify(eventPublisher).publish(new OrderRejectedEvent(3, 10, List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
    }

    @Test
    void successful_update_of_auction(){
        shareholder.incPosition(auctionSecurity,100_000_000);
        brokerRepository.findBrokerById(broker2.getBrokerId()).increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "CBA", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "CBA", 10,
                LocalDateTime.now(), Side.BUY, 300, 600, broker2.getBrokerId(), shareholder.getShareholderId(),
                0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(eventPublisher).publish(new OpeningPriceEvent(auctionSecurity.getIsin(), 630, 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(3, 10));
        verify(eventPublisher).publish(new OpeningPriceEvent(auctionSecurity.getIsin(), 600, 300));

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(2, "CBA", 200,
                LocalDateTime.now(), Side.SELL, 290, 598, broker1.getBrokerId(), shareholder.getShareholderId(),
                0));
        verify(eventPublisher).publish(new OrderUpdatedEvent(2, 200));
        verify(eventPublisher).publish(new OpeningPriceEvent(auctionSecurity.getIsin(), 600, 290));
    }

    @Test
    void reject_update_of_stop_limit_order_auction(){
        brokerRepository.findBrokerById(broker1.getBrokerId()).increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(1, "ABC", 200,
                LocalDateTime.now(), Side.BUY, 400, 670, broker1.getBrokerId(), shareholder.getShareholderId(),
                0, 650));
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(2, "ABC", MatchingState.AUCTION));
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(3, "ABC", 200,
                LocalDateTime.now(), Side.BUY, 400, 680, broker1.getBrokerId(), shareholder.getShareholderId(), 0, 650));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
    }

    @Test
    void reject_delete_of_stop_limit_order_in_auction(){
        brokerRepository.findBrokerById(broker1.getBrokerId()).increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(1, "ABC", 200,
                LocalDateTime.now(), Side.BUY, 400, 670, broker1.getBrokerId(), shareholder.getShareholderId(),
                0, 650));
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(2, "ABC", MatchingState.AUCTION));
        orderHandler.handleDeleteOrder(new DeleteOrderRq(4, security.getIsin(), Side.BUY, 200));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(eventPublisher).publish(any(SecurityStateChangedEvent.class));
        verify(eventPublisher).publish(any(OrderRejectedEvent.class));
    }

    @Test
    void successful_deletion_of_order_in_auction(){
        shareholder.incPosition(auctionSecurity,100_000_000);
        brokerRepository.findBrokerById(broker2.getBrokerId()).increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "CBA", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "CBA", 10,
                LocalDateTime.now(), Side.BUY, 300, 600, broker2.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleDeleteOrder(new DeleteOrderRq(100, auctionSecurity.getIsin(), Side.SELL, 200));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(eventPublisher).publish(new OpeningPriceEvent(auctionSecurity.getIsin(), 630, 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(3, 10));
        verify(eventPublisher).publish(new OpeningPriceEvent(auctionSecurity.getIsin(), 600, 300));
        verify(eventPublisher).publish(new OrderDeletedEvent(100, 200));
        verify(eventPublisher).publish(new OpeningPriceEvent(auctionSecurity.getIsin(), 600, 0));
    }

    @Test
    void auction_debt_to_buyer_broker_payed(){
        shareholder.incPosition(auctionSecurity,100_000_000);
        brokerRepository.findBrokerById(broker2.getBrokerId()).increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "CBA", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "CBA", 10,
                LocalDateTime.now(), Side.BUY, 300, 660, broker2.getBrokerId(), shareholder.getShareholderId(),
                0));
        assertThat(brokerRepository.findBrokerById(broker2.getBrokerId()).getCredit()).isEqualTo(100_000_000 - (660 * 300));
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(2, "CBA", MatchingState.AUCTION));
        assertThat(brokerRepository.findBrokerById(broker2.getBrokerId()).getCredit()).isEqualTo(100_000_000 - (630 * 300));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(eventPublisher).publish(new OpeningPriceEvent(auctionSecurity.getIsin(), 630, 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(3, 10));
        verify(eventPublisher).publish(new OpeningPriceEvent(auctionSecurity.getIsin(), 630, 300));
        verify(eventPublisher).publish(any(SecurityStateChangedEvent.class));
        verify(eventPublisher).publish(any(TradeEvent.class));
    }

    @Test
    void stop_limit_order_activates_after_reopening_from_auction_to_continuous(){
        auctionSecurity.updatePrice(5);
        shareholderRepository.findShareholderById(shareholder.getShareholderId()).incPosition(newAuctionSecurity,100_000_000);
        brokerRepository.findBrokerById(broker1.getBrokerId()).increaseCreditBy(100_000_000);
        brokerRepository.findBrokerById(broker2.getBrokerId()).increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewStopLimitOrderRq(1, "ABC", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(),
                0, 10));
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(10, "ABC", MatchingState.AUCTION));
        security.updatePrice(15);
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(10, "ABC", MatchingState.CONTINUOUS));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 200));
        verify(eventPublisher).publish(new SecurityStateChangedEvent("ABC", MatchingState.AUCTION));
        verify(eventPublisher).publish(new SecurityStateChangedEvent("ABC", MatchingState.CONTINUOUS));
    }

    @Test
    void active_stop_limit_can_be_updated(){
        broker1.increaseCreditBy(100_000_000);
        StopLimitOrder stopLimitOrder = new StopLimitOrder(10, newAuctionSecurity, Side.BUY, 5, 30, broker1, shareholder, 25);
        testOrderBook.enqueue(stopLimitOrder);
        MatchResult matchResult = matcher.execute(stopLimitOrder);
        assertThat(matchResult.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
    }
}
