package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Builder
@EqualsAndHashCode
@ToString
@Getter
public class Order {
    protected long orderId;
    protected Security security;
    protected Side side;
    protected final int initialQuantity;
    protected int quantity;
    protected int price;
    protected Broker broker;
    protected Shareholder shareholder;
    @Builder.Default
    protected LocalDateTime entryTime = LocalDateTime.now();
    @Builder.Default
    protected OrderStatus status = OrderStatus.NEW;
    protected long minimumExecutionQuantity ;
    protected boolean isUpdated;

    public Order(long orderId, Security security, Side side, int initialQuantity, int quantity, int price, Broker broker, Shareholder shareholder,
                 LocalDateTime entryTime, OrderStatus status, long minimumExecutionQuantity, boolean isUpdated){
//        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, 0L);
        this.orderId = orderId;
        this.security = security;
        this.side = side;
        this.initialQuantity = initialQuantity;
        this.quantity = quantity;
        this.price = price;
        this.entryTime = entryTime;
        this.broker = broker;
        this.shareholder = shareholder;
        this.status = status;
        this.minimumExecutionQuantity = minimumExecutionQuantity;
        this.isUpdated = isUpdated;
    }

    public Order(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder,
                 LocalDateTime entryTime, OrderStatus status, long minimumExecutionQuantity) {
        this(orderId, security, side, quantity ,quantity, price, broker, shareholder, entryTime, status, 0L, false);
    }

    public Order(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder,
                 LocalDateTime entryTime, OrderStatus status) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, 0L);
    }

    public Order(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder,
                 LocalDateTime entryTime, long minimumExecutionQuantity) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, OrderStatus.NEW, minimumExecutionQuantity);
    }

    public Order(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder,
                 LocalDateTime entryTime) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, 0L);
    }

    public Order(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder,
                 long minimumExecutionQuantity) {
        this(orderId, security, side, quantity, price, broker, shareholder, LocalDateTime.now(), minimumExecutionQuantity);
    }

    public Order(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder) {
        this(orderId, security, side, quantity, price, broker, shareholder, LocalDateTime.now(), 0L);
    }

    public Order snapshot() {
        return new Order(orderId, security, side, quantity, price, broker, shareholder, entryTime, OrderStatus.SNAPSHOT,
                         minimumExecutionQuantity);
    }

    public Order snapshotWithQuantity(int newQuantity) {
        return new Order(orderId, security, side, newQuantity, price, broker, shareholder, entryTime, OrderStatus.SNAPSHOT,
                         minimumExecutionQuantity);
    }

    public boolean matches(Order other) {
        if (side == Side.BUY)
            return price >= other.price;
        else
            return price <= other.price;
    }

    public void decreaseQuantity(int amount) {
        if (amount > quantity)
            throw new IllegalArgumentException();
        quantity -= amount;
    }

    public void makeQuantityZero() {
        quantity = 0;
    }

    public boolean queuesBefore(Order order) {
        if (order.getSide() == Side.BUY) {
            return price > order.getPrice();
        } else {
            return price < order.getPrice();
        }
    }

    public void queue() {
        status = OrderStatus.QUEUED;
    }

    public void markAsNew(){
        status = OrderStatus.NEW;
        this.isUpdated = true;
    }
    public boolean isQuantityIncreased(int newQuantity) {
        return newQuantity > quantity;
    }

    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        quantity = updateOrderRq.getQuantity();
        price = updateOrderRq.getPrice();
    }

    public long getValue() {
        return (long)price * quantity;
    }

    public int getTotalQuantity() { return quantity; }
    public boolean isMinExecQuantityConditionMet(){
        return isUpdated || (initialQuantity - quantity >= minimumExecutionQuantity);
    }

    public boolean isYourMinExecQuantity(long reqMinExecQuantity){ return reqMinExecQuantity == minimumExecutionQuantity;}
}
