package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class AuctionSecurity extends Security{

    @Override
    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        if (!doseShareholderHaveEnoughPositions(null ,enterOrderRq ,shareholder))
            return MatchResult.notEnoughPositions();
        Order order = createNewOrder(enterOrderRq, broker, shareholder);
        return handleOrderExecution(order ,matcher);
    }
}
