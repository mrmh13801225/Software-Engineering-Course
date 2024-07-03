package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.domain.entity.*;
import lombok.Getter;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Getter
public class StopLimitOrderBook{

    private final LinkedList<StopLimitOrder> buyQueue;
    private final LinkedList<StopLimitOrder> sellQueue;
    public StopLimitOrderBook() {
        buyQueue = new LinkedList<>();
        sellQueue = new LinkedList<>();
    }

    public void enqueue(StopLimitOrder order) {
        List<StopLimitOrder> queue = getQueue(order.getSide());
        ListIterator<StopLimitOrder> iterator = queue.listIterator();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (order.queuesBefore(next)) {
                iterator.previous();
                break;
            }
        }
        order.queue();
        iterator.add(order);
    }

    private LinkedList<StopLimitOrder> getQueue(Side side) {
        return side == Side.BUY ? buyQueue : sellQueue;
    }

    public StopLimitOrder findByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        for (StopLimitOrder order : queue) {
            if (order.getOrderId() == orderId)
                return order;
        }
        return null;
    }

    public boolean removeByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        var iterator = queue.listIterator();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (next.getOrderId() == orderId) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public LinkedList<StopLimitOrder> handleActivatedOrders(ListIterator<StopLimitOrder> iterator, LinkedList<StopLimitOrder> activatedOrders, long price) {
        while (iterator.hasNext()) {
            StopLimitOrder temp = iterator.next();
            if (temp.isActivated(price)) {
                activatedOrders.add(temp);
                iterator.remove();
            }
            else
                break;
        }

        return activatedOrders;
    }

    public LinkedList<StopLimitOrder> popActivatedOrders(long price){
        LinkedList<StopLimitOrder> activatedOrders = new LinkedList<>();
        ListIterator<StopLimitOrder> buyIterator = buyQueue.listIterator();
        ListIterator<StopLimitOrder> sellIterator = sellQueue.listIterator();

        activatedOrders = handleActivatedOrders(buyIterator, activatedOrders, price);
        activatedOrders = handleActivatedOrders(sellIterator, activatedOrders, price);

        return activatedOrders;
    }

}
