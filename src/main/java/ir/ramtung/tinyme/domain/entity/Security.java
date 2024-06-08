package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@Getter
@SuperBuilder
@AllArgsConstructor
public class Security {
    protected String isin;
    @Builder.Default
    protected int tickSize = 1;
    @Builder.Default
    protected int lotSize = 1;
    @Builder.Default
    protected OrderBook orderBook = new OrderBook();
    @Builder.Default
    protected long price = 0;
    @Builder.Default
    protected StopLimitOrderBook stopLimitOrderBook = new StopLimitOrderBook();
    @Builder.Default
    protected LinkedList<StopLimitOrder> activatedStopOrder = new LinkedList<>();

    Security (AuctionSecurity auctionSecurity){
        this(auctionSecurity.getIsin(), auctionSecurity.getTickSize(), auctionSecurity.getLotSize(),
                auctionSecurity.getOrderBook(), auctionSecurity.getPrice(), auctionSecurity.getStopLimitOrderBook(),
                auctionSecurity.getActivatedStopOrder());
    }

    private int calculateExtraSharesNeeded(Order order, EnterOrderRq enterOrderRq) {
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            return enterOrderRq.getQuantity() ;

        return enterOrderRq.getQuantity() - order.getQuantity() ;
    }

    protected boolean doseShareholderHaveEnoughPositions (Order order ,EnterOrderRq enterOrderRq, Shareholder shareholder ){
        int extraSharesNeeded = calculateExtraSharesNeeded(order, enterOrderRq);

        return !(enterOrderRq.getSide() == Side.SELL && !shareholder.hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(shareholder) + extraSharesNeeded));
    }

    protected MatchResult handleStopLimitOrder(StopLimitOrder order) {
        if (canGetEnqueued(order)) {
            stopLimitOrderBook.enqueue(order);
            return MatchResult.stopLimitOrderQueued();
        }
        return MatchResult.notEnoughCredit();
    }

    protected MatchResult handleOrderExecution (Order order ,Matcher matcher){
        if(order instanceof StopLimitOrder)
            return handleStopLimitOrder((StopLimitOrder) order);

        return matcher.execute(order);
    }

    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        if (!doseShareholderHaveEnoughPositions(null ,enterOrderRq ,shareholder))
            return MatchResult.notEnoughPositions();
        Order order = createNewOrder(enterOrderRq, broker, shareholder);
        return handleOrderExecution(order ,matcher);
    }

    protected Order findOrder(DeleteOrderRq deleteOrderRq){
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order instanceof StopLimitOrder)
            order = ((StopLimitOrder) order).toOrder();
        StopLimitOrder inactiveOrder = stopLimitOrderBook.findByOrderId(deleteOrderRq.getSide(),
                deleteOrderRq.getOrderId());
        return (order != null) ? order : inactiveOrder;
    }

    protected void removeOrder(Order order ,DeleteOrderRq deleteOrderRq){
        if (order instanceof StopLimitOrder)
            stopLimitOrderBook.removeByOrderId(deleteOrderRq.getSide(),deleteOrderRq.getOrderId());
        else
            orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
    }

    protected void handleDeletedOrderCredit(Order order){
        if (order.getSide() == Side.BUY){
            if (order instanceof StopLimitOrder)
                order.getBroker().releaseReservedCredit(order.getValue());
            else
                order.getBroker().increaseCreditBy(order.getValue());
        }
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = findOrder(deleteOrderRq);

        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);

        handleDeletedOrderCredit(order);
        removeOrder(order ,deleteOrderRq);
    }

    protected MatchResult validateUpdateRequest(Order order , EnterOrderRq updateOrderRq )
    throws InvalidRequestException{
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
            throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
        if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
            throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);

        if (!order.isYourMinExecQuantity(updateOrderRq.getMinimumExecutionQuantity()))
            return MatchResult.changingMinExecQuantityWhileUpdating();
        if (!doseShareholderHaveEnoughPositions(order ,updateOrderRq ,order.getShareholder()))
            return MatchResult.notEnoughPositions();
        return null ;
    }

    protected boolean doesItLosePriority (Order order ,EnterOrderRq updateOrderRq ){
        return order.isQuantityIncreased(updateOrderRq.getQuantity()) || updateOrderRq.getPrice() != order.getPrice()
                || ((order instanceof IcebergOrder icebergOrder) &&
                (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()));
    }

    protected void updateActiveOrderCreditHandler(Order order ,EnterOrderRq updateOrderRq ){
        if (updateOrderRq.getSide() == Side.BUY) {
            order.getBroker().increaseCreditBy(order.getValue());
        }
    }

    protected MatchResult handlePriorityLoss (Order order ,EnterOrderRq updateOrderRq ,boolean loosesPriority ){
        if (!loosesPriority) {
            if (updateOrderRq.getSide() == Side.BUY) {
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            return MatchResult.executed(null, List.of());
        } else
            order.markAsNew();

        return null ;
    }

    protected MatchResult handleUpdateOrderExecution (EnterOrderRq updateOrderRq ,Matcher matcher ,Order order ,
                                                    Order originalOrder){
        orderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        MatchResult matchResult = matcher.execute(order);
        if (matchResult.outcome() != MatchingOutcome.EXECUTED) {
            orderBook.enqueue(originalOrder);
            if (updateOrderRq.getSide() == Side.BUY) {
                originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
            }
        }
        return matchResult ;
    }

    protected MatchResult updateActiveOrder(EnterOrderRq updateOrderRq , Matcher matcher )
            throws  InvalidRequestException{
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        MatchResult validationResult = validateUpdateRequest(order ,updateOrderRq );
        if(validationResult != null)
            return validationResult;

        boolean loosesPriority = doesItLosePriority(order ,updateOrderRq );

        Order originalOrder = updateOrderFromRequestWithCreditUpdate(order, updateOrderRq);

        MatchResult priorityLossResult = handlePriorityLoss(order ,updateOrderRq ,loosesPriority );
        if (priorityLossResult != null)
            return priorityLossResult;

        return handleUpdateOrderExecution(updateOrderRq ,matcher ,order ,originalOrder ) ;
    }

    private Order updateOrderFromRequestWithCreditUpdate(Order order, EnterOrderRq updateOrderRq) {
        updateActiveOrderCreditHandler(order ,updateOrderRq );
        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);

        return originalOrder;
    }

    protected MatchResult updateInActiveOrder(EnterOrderRq updateOrderRq) throws InvalidRequestException {
        StopLimitOrder inactiveOrder = findInactiveOrder(updateOrderRq);
        MatchResult validationResult = validateInactiveOrderUpdate(inactiveOrder, updateOrderRq);
        if (validationResult != null) {
            return validationResult;
        }
        removeInactiveOrder(inactiveOrder, updateOrderRq);
        updateOrder(inactiveOrder, updateOrderRq);
        requeueInactiveOrder(inactiveOrder);
        return MatchResult.stopLimitOrderUpdated();
    }

    private StopLimitOrder findInactiveOrder(EnterOrderRq updateOrderRq) {
        return stopLimitOrderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
    }

    private MatchResult validateInactiveOrderUpdate(StopLimitOrder inactiveOrder, EnterOrderRq updateOrderRq) throws InvalidRequestException {
        return validateUpdateRequest(inactiveOrder, updateOrderRq);
    }

    private void removeInactiveOrder(StopLimitOrder inactiveOrder, EnterOrderRq updateOrderRq) {
        stopLimitOrderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
    }

    private void updateOrder(StopLimitOrder inactiveOrder, EnterOrderRq updateOrderRq) {
        inactiveOrder.updateFromRequest(updateOrderRq);
    }

    private void requeueInactiveOrder(StopLimitOrder inactiveOrder) {
        stopLimitOrderBook.enqueue(inactiveOrder);
    }


    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        if (updateOrderRq.getStopPrice() == 0)
            return updateActiveOrder(updateOrderRq ,matcher );

        return updateInActiveOrder(updateOrderRq );
    }

    protected Order createNewOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder){
        if (isStopLimitOrder(enterOrderRq)) {
            return new StopLimitOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity(),
                    enterOrderRq.getStopPrice(), enterOrderRq.getRequestId());
        } else if (!isIceberg(enterOrderRq)) {
            return new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(),
                    enterOrderRq.getMinimumExecutionQuantity());
        } else {
            return new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(), enterOrderRq.getMinimumExecutionQuantity());
        }
    }

    protected boolean isStopLimitOrder(EnterOrderRq enterOrderRq){
        return enterOrderRq.getPeakSize() == 0  && enterOrderRq.getStopPrice() > 0;
    }

    protected boolean isIceberg(EnterOrderRq enterOrderRq){
        return enterOrderRq.getPeakSize() != 0;
    }

    protected boolean isActive(Order order){
        if(!(order instanceof StopLimitOrder))
            return true;
        return ((StopLimitOrder) order).isActivated(price);
    }

    protected boolean canGetEnqueued(StopLimitOrder order){
        if (order.getSide() == Side.BUY){
            return order.getBroker().reserveCredit(order.getPrice() * order.getQuantity());
        }
        return true;
    }

    public ArrayList<MatchResult> handleActivation(){
        LinkedList<StopLimitOrder> newActivatedOrders = stopLimitOrderBook.popActivatedOrders(price);
        ArrayList<MatchResult> activationResults = new ArrayList<>();
        activatedStopOrder.addAll(newActivatedOrders);
        for (StopLimitOrder newActivatedOrder : newActivatedOrders) {
            activationResults.add(MatchResult.stopLimitOrderActivated(newActivatedOrder));
        }
        return activationResults;
    }

    public MatchResult executeFirstActivatedOrder(Matcher matcher){
        if(!activatedStopOrder.isEmpty()){
            StopLimitOrder order = activatedStopOrder.pop();
            if(order.getSide() == Side.BUY)
                order.getBroker().releaseReservedCredit(order.getValue());
            return matcher.execute(order);
        }
        return null;
    }

    public ArrayList<MatchResult> executeActivatedStopOrders(Matcher matcher){
        ArrayList<MatchResult> executedResults = new ArrayList<>();
        MatchResult currentResult = executeFirstActivatedOrder(matcher);
        while (currentResult != null){
            executedResults.add(currentResult);
            ArrayList<MatchResult> newActivated = handleActivation();
            executedResults.addAll(newActivated);
            currentResult = executeFirstActivatedOrder(matcher);
        }
        return executedResults;
    }

    protected ChangeSecurityResult changeToAuction(){
        return ChangeSecurityResult.createRealSuccessFullChange(new AuctionSecurity(this));
    }

    protected ChangeSecurityResult changeToContinues(){
        return ChangeSecurityResult.createRealSuccessFullChange(this);
    }

    public ChangeSecurityResult changeTo (ChangeMatchingStateRq changeMatchingStateRq){
        if (changeMatchingStateRq.getTargetState() == MatchingState.AUCTION)
            return changeToAuction();

        return changeToContinues();
    }

    public void updatePrice(long price){
        this.price = price;
    }

}
