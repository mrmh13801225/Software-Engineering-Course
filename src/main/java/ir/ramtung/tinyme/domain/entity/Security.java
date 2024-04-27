package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.Message;
import lombok.Builder;
import lombok.Getter;

import javax.swing.text.html.HTMLDocument;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@Getter
@Builder
public class Security {
    private String isin;
    @Builder.Default
    private int tickSize = 1;
    @Builder.Default
    private int lotSize = 1;
    @Builder.Default
    private OrderBook orderBook = new OrderBook();
    @Builder.Default
    private long price = 0;
    @Builder.Default
    private StopLimitOrderBook stopLimitOrderBook = new StopLimitOrderBook();
    @Builder.Default
    private LinkedList<StopLimitOrder> activatedStopOrder = new LinkedList<>();

    private boolean doseShareholderHaveEnoughPositions (EnterOrderRq enterOrderRq, Shareholder shareholder){
        return !(enterOrderRq.getSide() == Side.SELL && !shareholder.hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity()));
    }

    private MatchResult handleOrderExecution (Order order ,Matcher matcher){
        if(order instanceof StopLimitOrder)
            return handleStopLimitOrder((StopLimitOrder) order);
        else
            return matcher.execute(order);
    }

    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        if (doseShareholderHaveEnoughPositions(enterOrderRq ,shareholder))
            return MatchResult.notEnoughPositions();
        Order order = createNewOrder(enterOrderRq, broker, shareholder);
        return handleOrderExecution(order ,matcher);
    }

    private Order findOrder(DeleteOrderRq deleteOrderRq){
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        StopLimitOrder inactiveOrder = stopLimitOrderBook.findByOrderId(deleteOrderRq.getSide(),
                deleteOrderRq.getOrderId());
        return (order != null) ? order : (inactiveOrder != null) ? inactiveOrder : null ;
    }

    private void removeOrder(Order order ,DeleteOrderRq deleteOrderRq){
        if (order instanceof StopLimitOrder)
            stopLimitOrderBook.removeByOrderId(deleteOrderRq.getSide(),deleteOrderRq.getOrderId());
        else
            orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
    }

    private void handleDeletedOrderCredit(Order order){
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

    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        MatchResult matchResult ;
        if (updateOrderRq.getStopPrice() == 0) {
            StopLimitOrder stopOrder = stopLimitOrderBook.findByOrderId(updateOrderRq.getSide(),
                    updateOrderRq.getOrderId());
            Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
            if (order == null && stopOrder == null)
                throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
            if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
                throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
            if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
                throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);

            if (order != null && !order.isYourMinExecQuantity(updateOrderRq.getMinimumExecutionQuantity()))
                return MatchResult.changingMinExecQuantityWhileUpdating();
            if (order != null && updateOrderRq.getSide() == Side.SELL &&
                    !order.getShareholder().hasEnoughPositionsOn(this,
                            orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity() + updateOrderRq.getQuantity()))
                return MatchResult.notEnoughPositions();
            if (order != null) {
                boolean losesPriority = order.isQuantityIncreased(updateOrderRq.getQuantity())
                        || updateOrderRq.getPrice() != order.getPrice()
                        || ((order instanceof IcebergOrder icebergOrder) && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()));

                if (updateOrderRq.getSide() == Side.BUY) {
                    order.getBroker().increaseCreditBy(order.getValue());
                }
                Order originalOrder = order.snapshot();
                order.updateFromRequest(updateOrderRq);
                if (!losesPriority) {
                    if (updateOrderRq.getSide() == Side.BUY) {
                        order.getBroker().decreaseCreditBy(order.getValue());
                    }
                    return MatchResult.executed(null, List.of());
                } else
                    order.markAsNew();

                orderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
                matchResult = matcher.execute(order);
                if (matchResult.outcome() != MatchingOutcome.EXECUTED) {
                    orderBook.enqueue(originalOrder);
                    if (updateOrderRq.getSide() == Side.BUY) {
                        originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
                    }
                }
            }
            else {
                stopLimitOrderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
                matchResult = matcher.execute(stopOrder.toOrder());
            }
        }
        else {
            StopLimitOrder inactiveOrder = stopLimitOrderBook.findByOrderId(updateOrderRq.getSide(),
                    updateOrderRq.getOrderId());
            if (inactiveOrder == null)
                throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);

            if (!inactiveOrder.isYourMinExecQuantity(updateOrderRq.getMinimumExecutionQuantity()))
                return MatchResult.changingMinExecQuantityWhileUpdating();
            if (updateOrderRq.getSide() == Side.SELL &&
                    !inactiveOrder.getShareholder().hasEnoughPositionsOn(this,
                            orderBook.totalSellQuantityByShareholder(inactiveOrder.getShareholder()) -
                                    inactiveOrder.getQuantity() + updateOrderRq.getQuantity()))
                return MatchResult.notEnoughPositions();

            stopLimitOrderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
            inactiveOrder.updateFromRequest(updateOrderRq);
            stopLimitOrderBook.enqueue(inactiveOrder);
            matchResult = MatchResult.stopLimitOrderUpdated();
        }
        return matchResult;
    }

    private Order createNewOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder){
        Order order;
        if (isStopLimitOrder(enterOrderRq))
            order = new StopLimitOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity(),
                    enterOrderRq.getStopPrice(), enterOrderRq.getRequestId());
        else if (!isIceberg(enterOrderRq))
            order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(),
                    enterOrderRq.getMinimumExecutionQuantity());
        else
            order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(), enterOrderRq.getMinimumExecutionQuantity());
        return order;
    }

    private boolean isStopLimitOrder(EnterOrderRq enterOrderRq){
        return enterOrderRq.getPeakSize() == 0  && enterOrderRq.getStopPrice() > 0;
    }

    private boolean isIceberg(EnterOrderRq enterOrderRq){
        return enterOrderRq.getPeakSize() != 0;
    }

    private boolean isActive(Order order){
        if(!(order instanceof StopLimitOrder))
            return true;
        else if(((StopLimitOrder) order).isActivated(price))
            return true;
        else
            return false;
    }

    private boolean canGetEnqueued(StopLimitOrder order){
        if (order.getSide() == Side.BUY){
            if (order.getBroker().reserveCredit(order.getPrice() * order.getQuantity()))
                return true;
            else
                return false;
        }
        return true;
    }

    private MatchResult handleStopLimitOrder(StopLimitOrder order) {
        if (canGetEnqueued(order)) {
            stopLimitOrderBook.enqueue(order);
            return MatchResult.stopLimitOrderQueued();
        }
        return MatchResult.notEnoughCredit();
    }


    public ArrayList<MatchResult> handleActivation(){
        LinkedList<StopLimitOrder> newActivatedOrders = stopLimitOrderBook.popActivatedOrders(price);
        ArrayList<MatchResult> activationResults = new ArrayList<>();
        activatedStopOrder.addAll(newActivatedOrders);
        Iterator<StopLimitOrder> iterator = newActivatedOrders.iterator();
        while (iterator.hasNext()){
            activationResults.add(MatchResult.stopLimitOrderActivated(iterator.next()));
        }
        return activationResults;
    }

    public MatchResult executeFirstActivatedOrder(Matcher matcher){
        if(!activatedStopOrder.isEmpty()){
            StopLimitOrder order = activatedStopOrder.pop();
            if(order.getSide() == Side.BUY)
                order.getBroker().releaseReservedCredit(order.getValue());
            return matcher.execute(order.toOrder());
        }
        return null;
    }

    public ArrayList<MatchResult> executeActivatedStopOrders(Matcher matcher){
        ArrayList<MatchResult> executedResults = new ArrayList<>();
        MatchResult temp = executeFirstActivatedOrder(matcher);
        while (temp != null){
            executedResults.add(temp);
            ArrayList<MatchResult> newActivated = handleActivation();
            executedResults.addAll(newActivated);
            temp = executeFirstActivatedOrder(matcher);
        }
        return executedResults;
    }

    public void updatePrice(long price){
        this.price = price;
    }

}
