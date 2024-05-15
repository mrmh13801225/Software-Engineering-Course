package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
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
        if (!order.getBroker().hasEnoughCredit(order.getValue())) {
            return MatchResult.notEnoughCredit();
        }
        order.getBroker().decreaseCreditBy(order.getValue());
        orderBook.enqueue(order);
        //TODO:maybe need to add order.queued;
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
        return ChangeSecurityResult.createVirtualAuctionSuccessFullChange(new Security(this));
    }

    @Override
    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        if (updateOrderRq.getStopPrice() == 0)
            return updateActiveOrder(updateOrderRq ,matcher );
        else
            return MatchResult.updateAuctionStopLimitError();
    }

    public ArrayList<MatchResult> matchTradableOrders(AuctionMatcher matcher){
        ArrayList<MatchResult> results = new ArrayList<>();
        Order buy = orderBook.getFirstBuy();
        while (buy != null) {
            results.add(matcher.execute(buy, orderBook.getOpeningPrice()));
            buy = orderBook.getFirstBuy();
        }

        return results;
    }

    public ArrayList<MatchResult> open (AuctionMatcher matcher){
        ArrayList<MatchResult> results = new ArrayList<>();
        results.add(MatchResult.auctionOpened(orderBook.getOpeningPrice(), orderBook.getTradableQuantity()));
        results.addAll(matchTradableOrders(matcher));
        return results;
    }

}
