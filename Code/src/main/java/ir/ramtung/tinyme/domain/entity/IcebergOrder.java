package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IcebergOrder extends Order {
    int peakSize;
    int displayedQuantity;

    public IcebergOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                        Shareholder shareholder, LocalDateTime entryTime, int peakSize, int displayedQuantity,
                        OrderStatus status, long minimumExecutionQuantity) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, minimumExecutionQuantity);
        this.peakSize = peakSize;
        this.displayedQuantity = displayedQuantity;
    }

    public IcebergOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                        Shareholder shareholder, LocalDateTime entryTime, int peakSize, int displayedQuantity,
                        OrderStatus status) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, peakSize, displayedQuantity,
                     status, 0L);
    }

    public IcebergOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                        Shareholder shareholder, LocalDateTime entryTime, int peakSize, OrderStatus status,
                        long minimumExecutionQuantity) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, peakSize,
                Math.min(peakSize, quantity), status, minimumExecutionQuantity);
    }

    public IcebergOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                        Shareholder shareholder, LocalDateTime entryTime, int peakSize, OrderStatus status) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, peakSize,
                Math.min(peakSize, quantity), status, 0);
    }

    public IcebergOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                        Shareholder shareholder, LocalDateTime entryTime, int peakSize, long minimumExecutionQuantity) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, peakSize, OrderStatus.NEW,
                minimumExecutionQuantity);
    }

    public IcebergOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                        Shareholder shareholder, LocalDateTime entryTime, int peakSize) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, peakSize, OrderStatus.NEW,
                0L);
    }

    public IcebergOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                        Shareholder shareholder, int peakSize, long minimumExecutionQuantity) {
        super(orderId, security, side, quantity, price, broker, shareholder, minimumExecutionQuantity);
        this.peakSize = peakSize;
        this.displayedQuantity = Math.min(peakSize, quantity);
    }

    public IcebergOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                        Shareholder shareholder, int peakSize) {
        this(orderId, security, side, quantity, price, broker, shareholder, peakSize, 0L);
    }

    @Override
    public Order snapshot() {
        return new IcebergOrder(orderId, security, side, quantity, price, broker, shareholder, entryTime, peakSize,
                OrderStatus.SNAPSHOT, minimumExecutionQuantity);
    }

    @Override
    public Order snapshotWithQuantity(int newQuantity) {
        return new IcebergOrder(orderId, security, side, newQuantity, price, broker, shareholder, entryTime, peakSize,
                OrderStatus.SNAPSHOT, minimumExecutionQuantity);
    }

    @Override
    public int getQuantity() {
        if (isOrderNew())
            return super.getQuantity();
        return displayedQuantity;
    }

    @Override
    public void decreaseQuantity(int amount) {
        if (isOrderNew()) {
            super.decreaseQuantity(amount);
            return;
        }
        handleIcebergDecreaseQuantity(amount);
    }

    public void replenish() {
        displayedQuantity = Math.min(quantity, peakSize);
    }

    @Override
    public void queue() {
        displayedQuantity = Math.min(quantity, peakSize);
        super.queue();
    }

    @Override
    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        super.updateFromRequest(updateOrderRq);
        if (peakSize < updateOrderRq.getPeakSize()) {
            displayedQuantity = Math.min(quantity, updateOrderRq.getPeakSize());
        }
        else if (peakSize > updateOrderRq.getPeakSize()) {
            displayedQuantity = Math.min(displayedQuantity, updateOrderRq.getPeakSize());
        }
        peakSize = updateOrderRq.getPeakSize();
    }

    public boolean isOrderNew(){
        return OrderStatus.NEW == status;
    }

    public void handleIcebergDecreaseQuantity(int amount){
        if (amount > displayedQuantity)
            throw new IllegalArgumentException();
        quantity -= amount;
        displayedQuantity -= amount;
    }
}
