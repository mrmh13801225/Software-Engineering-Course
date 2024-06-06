package ir.ramtung.tinyme.config;

import ir.ramtung.tinyme.domain.service.ChangeMatchingStateRequestStrategy;
import ir.ramtung.tinyme.domain.service.DeleteOrderRequestStrategy;
import ir.ramtung.tinyme.domain.service.EnterOrderRequestStrategy;
import ir.ramtung.tinyme.domain.service.RequestHandlingStrategy;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.RequestDispatcher;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.Request;
import ir.ramtung.tinyme.repository.DataLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.Map;

@TestConfiguration
public class MockedJMSTestConfig {
    @MockBean
    EventPublisher eventPublisher;
    @MockBean
    RequestDispatcher requestDispatcher;

    @Bean
    public Map<Class<? extends Request>, RequestHandlingStrategy> strategyMap(
            EnterOrderRequestStrategy enterOrderRequestStrategy,
            DeleteOrderRequestStrategy deleteOrderRequestStrategy,
            ChangeMatchingStateRequestStrategy changeMatchingStateRequestStrategy) {
        Map<Class<? extends Request>, RequestHandlingStrategy> strategyMap = new HashMap<>();
        strategyMap.put(EnterOrderRq.class, enterOrderRequestStrategy);
        strategyMap.put(DeleteOrderRq.class, deleteOrderRequestStrategy);
        strategyMap.put(ChangeMatchingStateRq.class, changeMatchingStateRequestStrategy);
        return strategyMap;
    }
}
