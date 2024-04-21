package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StopLimitOrder extends Order{
    long stopPrice;
    long reqId;

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                          Shareholder shareholder, LocalDateTime entryTime, OrderStatus status,
                          long minimumExecutionQuantity, long stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, minimumExecutionQuantity);
        this.stopPrice = stopPrice;
    }

    public StopLimitOrder(long orderId, Security security, Side side, int initialQuantity, int quantity,
                          int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime,
                          OrderStatus status, long minimumExecutionQuantity, boolean isUpdated, long stopPrice,
                          long reqId) {
        super(orderId, security, side, initialQuantity, quantity, price, broker, shareholder, entryTime, status, minimumExecutionQuantity, isUpdated);
        this.stopPrice = stopPrice;
        this.reqId = reqId;
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, long stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status);
        this.stopPrice = stopPrice;
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                          Shareholder shareholder, LocalDateTime entryTime, long minimumExecutionQuantity,
                          long stopPrice, long reqId) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, minimumExecutionQuantity);
        this.stopPrice = stopPrice;
        this.reqId = reqId;
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, long stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime);
        this.stopPrice = stopPrice;
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, long minimumExecutionQuantity, long stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder, minimumExecutionQuantity);
        this.stopPrice = stopPrice;
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, long stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder);
        this.stopPrice = stopPrice;
    }

    @Override
    public Order snapshot() {
        return new StopLimitOrder(orderId, security, side, initialQuantity, quantity, price, broker, shareholder, entryTime, status, minimumExecutionQuantity, isUpdated, stopPrice, reqId);
    }

    @Override
    public Order snapshotWithQuantity(int newQuantity){
        return new StopLimitOrder(orderId, security, side, initialQuantity, newQuantity, price, broker, shareholder, entryTime, status, minimumExecutionQuantity, isUpdated, stopPrice, reqId);
    }

    @Override
    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        super.updateFromRequest(updateOrderRq);
        stopPrice = updateOrderRq.getStopPrice();
    }

    public boolean queuesBefore(StopLimitOrder order) {
        if (order.getSide() == Side.BUY) {
            return stopPrice > order.stopPrice;
        } else {
            return stopPrice < order.stopPrice;
        }
    }

    public boolean isActivated(long price){
        if (this.side == Side.BUY) {
            return price > this.stopPrice;
        } else {
            return price < this.stopPrice;
        }
    }

    public Order toOrder(){
        return super.snapshot();
    }
}
