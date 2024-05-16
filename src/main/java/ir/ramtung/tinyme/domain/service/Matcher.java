package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

@Service
public class Matcher {

    protected MatchResult handleTrade(Order newOrder ,Order matchingOrder ,LinkedList<Trade> trades){

        Trade trade = new Trade(newOrder.getSecurity(), matchingOrder.getPrice(), Math.min(newOrder.getQuantity(),
                matchingOrder.getQuantity()), newOrder, matchingOrder);

        if (newOrder.getSide() == Side.BUY) {
            if (trade.buyerHasEnoughCredit())
                trade.decreaseBuyersCredit();
            else {
                rollbackTrades(newOrder, trades);
                return MatchResult.notEnoughCredit();
            }
        }

        trade.increaseSellersCredit();
        trades.add(trade);

        return null;
    }

    protected void handleIcebergMatchingOrder(IcebergOrder icebergOrder ,OrderBook orderBook){
        icebergOrder.decreaseQuantity(icebergOrder.getQuantity());
        icebergOrder.replenish();
        if (icebergOrder.getQuantity() > 0)
            orderBook.enqueue(icebergOrder);
    }

    protected void handleNewOrderHasRemainderSituation(Order newOrder ,Order matchingOrder ,OrderBook orderBook ){
        newOrder.decreaseQuantity(matchingOrder.getQuantity());
        orderBook.removeFirst(matchingOrder.getSide());
        if (matchingOrder instanceof IcebergOrder icebergOrder) {
            handleIcebergMatchingOrder(icebergOrder ,orderBook );
        }
    }

    protected void handleMatchingOrderHasRemainderSituation(Order newOrder ,Order matchingOrder ,OrderBook orderBook ){
        matchingOrder.decreaseQuantity(newOrder.getQuantity());
        newOrder.makeQuantityZero();
    }

    protected void handleTradeSidesRemainder(Order newOrder ,Order matchingOrder ,OrderBook orderBook ){
        if (newOrder.getQuantity() >= matchingOrder.getQuantity())
            handleNewOrderHasRemainderSituation(newOrder ,matchingOrder ,orderBook );
        else
            handleMatchingOrderHasRemainderSituation(newOrder ,matchingOrder ,orderBook );
    }

    protected MatchResult matchFirstMatchingOrder(Order newOrder ,OrderBook orderBook ,LinkedList<Trade> trades ,
                                                Order matchingOrder){
        MatchResult tradeResult = handleTrade(newOrder ,matchingOrder ,trades );
        if (tradeResult != null)
            return tradeResult;

        handleTradeSidesRemainder(newOrder ,matchingOrder ,orderBook );

        return null;
    }

    public MatchResult match(Order newOrder) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();

        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;
            MatchResult matchResult = matchFirstMatchingOrder(newOrder ,orderBook ,trades ,matchingOrder);
            if (matchResult != null)
                return matchResult;
        }
        if (!newOrder.isMinExecQuantityConditionMet()){
            rollbackTrades(newOrder, trades);
            return MatchResult.minExecQuantityHaveNotMet();
        }
        return MatchResult.executed(newOrder, trades);
    }

    protected void rollbackBuyOrder(Order newOrder, LinkedList<Trade> trades, ListIterator<Trade> it){
        newOrder.getBroker().increaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
        trades.forEach(trade -> trade.getSell().getBroker().decreaseCreditBy(trade.getTradedValue()));

        while (it.hasPrevious()) {
            newOrder.getSecurity().getOrderBook().restoreOrder(it.previous().getSell());
        }
    }

    protected void rollbackSellOrder(Order newOrder, LinkedList<Trade> trades, ListIterator<Trade> it){
        newOrder.getBroker().decreaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());

        while (it.hasPrevious()) {
            newOrder.getSecurity().getOrderBook().restoreOrder(it.previous().getBuy());
        }

    }
    protected void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {
        ListIterator<Trade> it = trades.listIterator(trades.size());
        if (newOrder.getSide() == Side.BUY)
            rollbackBuyOrder(newOrder, trades, it);
        else
            rollbackSellOrder(newOrder, trades, it);
    }

    protected MatchResult handleOrderRemainderCredit(MatchResult result ,Order order){
        if (!order.getBroker().hasEnoughCredit(order.getValue())) {
            rollbackTrades(order, result.trades());
            return MatchResult.notEnoughCredit();
        }
        order.getBroker().decreaseCreditBy(order.getValue());
        return null;
    }

    protected MatchResult handleOrderRemainder(MatchResult result ,Order order){
        if (result.remainder().getQuantity() > 0) {
            if (order.getSide() == Side.BUY) {
                MatchResult creditHandlingResult = handleOrderRemainderCredit(result ,order) ;
                if(creditHandlingResult != null)
                    return creditHandlingResult ;
            }
            order.getSecurity().getOrderBook().enqueue(result.remainder());
        }
        return null;
    }

    protected void handleTradesPossitions(MatchResult result){
        if (!result.trades().isEmpty()) {
            for (Trade trade : result.trades()) {
                trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
                trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
            }
        }
    }

    protected void updateSecurityPrice(MatchResult result){
        if (!result.getTrades().isEmpty())
            result.remainder().getSecurity().updatePrice(result.getTrades().getLast().getPrice());
    }

    public MatchResult execute(Order order) {
        MatchResult result = match(order);
        if (result.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT)
            return result;
        else if (result.outcome() == MatchingOutcome.MIN_EXEC_QUANTITY_HAVE_NOT_MET){
            return result;
        }
        MatchResult remainderHandlingResult = handleOrderRemainder(result ,order);
        if (remainderHandlingResult != null)
            return remainderHandlingResult;
        handleTradesPossitions(result);
        updateSecurityPrice(result);
        return result;
    }

}
