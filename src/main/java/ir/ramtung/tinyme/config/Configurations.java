package ir.ramtung.tinyme.config;

import ir.ramtung.tinyme.domain.service.ChangeMatchingStateRequestStrategy;
import ir.ramtung.tinyme.domain.service.DeleteOrderRequestStrategy;
import ir.ramtung.tinyme.domain.service.EnterOrderRequestStrategy;
import ir.ramtung.tinyme.domain.service.RequestHandlingStrategy;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ComponentScan(basePackages = "ir.ramtung.tinyme")
public class Configurations {
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