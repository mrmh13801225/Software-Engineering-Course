package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
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
        if (!order.getBroker().hasEnoughCredit(order.getValue())) {
            return MatchResult.notEnoughCredit();
        }
        order.getBroker().decreaseCreditBy(order.getValue());
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


    //////////////////check !
    @Override
    protected MatchResult updateActiveOrder(EnterOrderRq updateOrderRq , Matcher matcher )
            throws  InvalidRequestException{
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        MatchResult validationResult = validateUpdateRequest(order ,updateOrderRq );
        if(validationResult != null)
            return validationResult;

        boolean loosesPriority = doesItLosePriority(order ,updateOrderRq );

        updateActiveOrderCreditHandler(order ,updateOrderRq );
        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);

        MatchResult priorityLossResult = handlePriorityLoss(order ,updateOrderRq ,loosesPriority );
        if (priorityLossResult != null)
            return priorityLossResult;


        return handleUpdateOrderExecution(updateOrderRq ,matcher ,order ,originalOrder ) ;
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
