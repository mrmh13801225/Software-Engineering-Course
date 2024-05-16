package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
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

import java.time.LocalDateTime;
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

    @BeforeEach
    void setup() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        auctionSecurity = AuctionSecurity.builder().isin("CBA").price(630).build();
        securityRepository.addSecurity(security);
        securityRepository.addSecurity(auctionSecurity);

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






















}
