package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

@SuperBuilder
public class AuctionSecurity extends Security{

    AuctionSecurity (Security security){
        super(security.getIsin(), security.getTickSize(), security.getLotSize(), security.getOrderBook(),
                security.getPrice(), security.getStopLimitOrderBook(), security.getActivatedStopOrder());
    }

    @Override
    protected MatchResult handleOrderExecution (Order order ,Matcher matcher){
        orderBook.enqueue(order);
        return MatchResult.orderAddedToAuction(orderBook.calculateOpeningPrice(price), orderBook.getTradableQuantity());
    }

    @Override
    public ArrayList<MatchResult> handleActivation() {
        return new ArrayList<>();
    }

    @Override
    public ArrayList<MatchResult> executeActivatedStopOrders(Matcher matcher){
        return new ArrayList<>();
    }

    @Override
    protected ChangeSecurityResult changeToAuction(){
        return ChangeSecurityResult.createVirtualAuctionSuccessFullChange(this);
    }

    @Override
    protected ChangeSecurityResult changeToContinues(){
        return ChangeSecurityResult.createRealSuccessFullChange(new Security(this));
    }

}
