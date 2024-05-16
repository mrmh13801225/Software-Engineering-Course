package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@SuperBuilder
public class AuctionSecurity extends Security{

    AuctionSecurity (Security security){
        super(security.getIsin(), security.getTickSize(), security.getLotSize(), security.getOrderBook(),
                security.getPrice(), security.getStopLimitOrderBook(), security.getActivatedStopOrder());
    }

    @Override
    protected MatchResult handleOrderExecution (Order order ,Matcher matcher){
        if (order.getSide() == Side.BUY) {
            if(!order.getBroker().hasEnoughCredit(order.getValue()))
                return MatchResult.notEnoughCredit();
            order.getBroker().decreaseCreditBy(order.getValue());
        }
        orderBook.enqueue(order);
        order.queue();
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

    @Override
    protected MatchResult handleUpdateOrderExecution (EnterOrderRq updateOrderRq ,Matcher matcher ,Order order ,
                                                      Order originalOrder){

        orderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        if (updateOrderRq.getSide() == Side.BUY) {
            if(order.getBroker().hasEnoughCredit(order.getValue())) {
                order.getBroker().decreaseCreditBy(order.getValue());
                orderBook.enqueue(order);
            }
            else {
                originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
                orderBook.enqueue(originalOrder);
                return MatchResult.notEnoughCredit();
            }
        } else {
            orderBook.enqueue(order);
        }

        return MatchResult.changeAuctionOrderBook(orderBook.calculateOpeningPrice(price), orderBook.getTradableQuantity());
    }

    @Override
    protected MatchResult handlePriorityLoss (Order order ,EnterOrderRq updateOrderRq ,boolean loosesPriority ){
        if (!loosesPriority) {
            if (updateOrderRq.getSide() == Side.BUY) {
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            return MatchResult.changeAuctionOrderBook(orderBook.calculateOpeningPrice(price), orderBook.getTradableQuantity());
        }
        return null ;
    }

    public MatchResult deleteAuctionOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = findOrder(deleteOrderRq);

        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        else if (order instanceof StopLimitOrder) {
            throw new InvalidRequestException(Message.CANNOT_DELETE_STOP_LIMIT_ORDER_IN_AUCTION);
        }

        handleDeletedOrderCredit(order);
        removeOrder(order ,deleteOrderRq);

        return MatchResult.changeAuctionOrderBook(orderBook.calculateOpeningPrice(price), orderBook.getTradableQuantity());
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
        results.addAll(matchTradableOrders(matcher));
        return results;
    }

}
