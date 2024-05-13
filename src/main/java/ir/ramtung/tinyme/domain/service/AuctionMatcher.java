package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;

import java.util.LinkedList;

public class AuctionMatcher extends Matcher {

    private void payDebtToBroker (Order newOrder, Trade trade, int price){
        int priceDifference = price - newOrder.getPrice();
        int debtToBroker = priceDifference * trade.getQuantity();
        newOrder.getBroker().increaseCreditBy(debtToBroker);
    }

    protected void handleTrade(Order newOrder ,Order matchingOrder ,LinkedList<Trade> trades, int price){

        Trade trade = new Trade(newOrder.getSecurity(), price, Math.min(newOrder.getQuantity(),
                matchingOrder.getQuantity()), newOrder, matchingOrder);

        if (newOrder.getSide() == Side.BUY) {
            payDebtToBroker(newOrder, trade, price);
        }

        trade.increaseSellersCredit();
        trades.add(trade);
    }

    protected void matchFirstMatchingOrder(Order newOrder ,OrderBook orderBook ,LinkedList<Trade> trades ,
                                                  Order matchingOrder, int price){
        handleTrade(newOrder ,matchingOrder ,trades, price );
        handleTradeSidesRemainder(newOrder ,matchingOrder ,orderBook);

    }

    public MatchResult match(Order newOrder, int price) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();

        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;
            matchFirstMatchingOrder(newOrder ,orderBook ,trades ,matchingOrder, price);

        }
        return MatchResult.executed(newOrder, trades);
    }

    protected void handleAuctionOrderRemainder(MatchResult result ,Order order){
        if (result.remainder().getQuantity() > 0)
            order.getSecurity().getOrderBook().putBack(result.remainder());
    }

    public MatchResult execute(Order order, int price) {
        MatchResult result = match(order, price);
        handleAuctionOrderRemainder(result ,order);
        handleTradesPossitions(result);
        return result;
    }

}
