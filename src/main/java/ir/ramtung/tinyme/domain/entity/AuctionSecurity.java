package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class AuctionSecurity extends Security{

    @Override
    protected MatchResult handleOrderExecution (Order order ,Matcher matcher){
        orderBook.enqueue(order);
        orderBook.calculateOpeningPrice(price);
        return matcher.execute(order);
    }

}
