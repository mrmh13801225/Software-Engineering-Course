package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.*;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.RequestDispatcher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.Request;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class NewOrderHandlerTest {

    @Autowired
    NewOrderHandler orderHandler;

    @TestConfiguration
    public static class MockedJMSTestConfig {
        @MockBean
        EventPublisher eventPublisher;
        @MockBean
        RequestDispatcher requestDispatcher;

        @Bean(name = "testRequestHandlingStrategyMap")
        public Map<Class<? extends Request>, RequestHandlingStrategy> requestHandlingStrategyMap(
                EnterOrderRequestStrategy enterOrderRequestStrategy,
                DeleteOrderRequestStrategy deleteOrderRequestStrategy,
                ChangeMatchingStateRequestStrategy changeMatchingStateRequestStrategy) {
            Map<Class<? extends Request>, RequestHandlingStrategy> strategyMap = new HashMap<>();
            strategyMap.put(EnterOrderRq.class, enterOrderRequestStrategy);
            strategyMap.put(DeleteOrderRq.class, deleteOrderRequestStrategy);
            strategyMap.put(ChangeMatchingStateRq.class, changeMatchingStateRequestStrategy);
            return strategyMap;
        }

        @Bean(name = "testResultPublishingStrategyMap")
        public Map<Class<? extends Request>, ResultPublishingStrategy> resultPublishingStrategyMap(
                EnterOrderRequestResultPublishingStrategy enterOrderRequestStrategy,
                DeleteOrderRequestResultPublishingStrategy deleteOrderRequestStrategy,
                ChangeMatchingStateRequestResultPublishingStrategy changeMatchingStateRequestStrategy) {
            Map<Class<? extends Request>, ResultPublishingStrategy> strategyMap = new HashMap<>();
            strategyMap.put(EnterOrderRq.class, enterOrderRequestStrategy);
            strategyMap.put(DeleteOrderRq.class, deleteOrderRequestStrategy);
            strategyMap.put(ChangeMatchingStateRq.class, changeMatchingStateRequestStrategy);
            return strategyMap;
        }
    }
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

        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 300, 15450, 2, shareholder.getShareholderId(), 0));

        Trade trade = new Trade(security, matchingBuyOrder.getPrice(), incomingSellOrder.getQuantity(),
                matchingBuyOrder, incomingSellOrder);
        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 200)));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade))));
    }

}
