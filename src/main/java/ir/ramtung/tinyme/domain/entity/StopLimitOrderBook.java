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
        ListIterator<StopLimitOrder> it = queue.listIterator();
        while (it.hasNext()) {
            var temp = it.next();
            if (order.queuesBefore(temp)) {
                it.previous();
                break;
            }
        }
        order.queue();
        it.add(order);
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
        var it = queue.listIterator();
        while (it.hasNext()) {
            var temp = it.next();
            if (temp.getOrderId() == orderId) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public LinkedList<StopLimitOrder> popActivatedOrders(long price){
        LinkedList<StopLimitOrder> activatedOrders = new LinkedList<>();
        ListIterator<StopLimitOrder> itBuy = buyQueue.listIterator();
        ListIterator<StopLimitOrder> itSell = sellQueue.listIterator();
        while (itBuy.hasNext()) {
            StopLimitOrder temp = itBuy.next();
            if (temp.isActivated(price)) {
                activatedOrders.add(temp);
                itBuy.remove();
            }
            else
                break;
        }
        while (itSell.hasNext()) {
            StopLimitOrder temp = itSell.next();
            if (temp.isActivated(price)){
                activatedOrders.add(temp);
                itSell.remove();
            }
            else
                break;
        }

        return activatedOrders;
    }

}
