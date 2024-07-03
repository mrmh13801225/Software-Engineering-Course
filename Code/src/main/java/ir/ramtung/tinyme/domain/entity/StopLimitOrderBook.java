package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.domain.entity.*;
import lombok.Getter;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

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
            if (order.queuesBefore(iterator.next())) {
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
        LinkedList<StopLimitOrder> queue = getQueue(side);
        return removeOrderFromQueue(queue, orderId);
    }

    private boolean removeOrderFromQueue(LinkedList<StopLimitOrder> queue, long orderId) {
        ListIterator<StopLimitOrder> iterator = queue.listIterator();
        while (iterator.hasNext()) {
            if (iterator.next().getOrderId() == orderId) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public LinkedList<StopLimitOrder> popActivatedOrders(long price) {
        LinkedList<StopLimitOrder> activatedOrders = new LinkedList<>();
        processActivatedOrders(buyQueue.listIterator(), activatedOrders, price);
        processActivatedOrders(sellQueue.listIterator(), activatedOrders, price);
        return activatedOrders;
    }

    private void processActivatedOrders(ListIterator<StopLimitOrder> iterator, LinkedList<StopLimitOrder> activatedOrders, long price) {
        while (iterator.hasNext()) {
            StopLimitOrder order = iterator.next();
            if (order.isActivated(price)) {
                activatedOrders.add(order);
                iterator.remove();
            }
        }
    }


}
