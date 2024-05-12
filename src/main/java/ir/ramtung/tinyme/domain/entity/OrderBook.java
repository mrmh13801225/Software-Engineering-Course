package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Getter
public class OrderBook {
    private final LinkedList<Order> buyQueue;
    private final LinkedList<Order> sellQueue;
    private int tradableQuantity ;

    public OrderBook() {
        buyQueue = new LinkedList<>();
        sellQueue = new LinkedList<>();
        tradableQuantity = 0;
    }

    public void enqueue(Order order) {
        List<Order> queue = getQueue(order.getSide());
        ListIterator<Order> it = queue.listIterator();
        iterateThroughQueue(it, order);
        order.queue();
        it.add(order);
    }

    private LinkedList<Order> getQueue(Side side) {
        return side == Side.BUY ? buyQueue : sellQueue;
    }

    public Order findByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        for (Order order : queue) {
            if (order.getOrderId() == orderId)
                return order;
        }
        return null;
    }

    public void removeByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        var it = queue.listIterator();
        while (it.hasNext()) {
            if (it.next().getOrderId() == orderId) {
                it.remove();
                return;
            }
        }
    }

    public Order matchWithFirst(Order newOrder) {
        var queue = getQueue(newOrder.getSide().opposite());
        if (newOrder.matches(queue.getFirst()))
            return queue.getFirst();
        else
            return null;
    }

    public void putBack(Order order) {
        LinkedList<Order> queue = getQueue(order.getSide());
        order.queue();
        queue.addFirst(order);
    }

    public void restoreOrder(Order order) {
        removeByOrderId(order.getSide(), order.getOrderId());
        putBack(order);
    }

    public boolean hasOrderOfType(Side side) {
        return !getQueue(side).isEmpty();
    }

    public void removeFirst(Side side) {
        getQueue(side).removeFirst();
    }

    public int totalSellQuantityByShareholder(Shareholder shareholder) {
        return sellQueue.stream()
                .filter(order -> order.getShareholder().equals(shareholder))
                .mapToInt(Order::getTotalQuantity)
                .sum();
    }

    public void iterateThroughQueue(ListIterator<Order> it, Order order){
        while (it.hasNext()) {
            if (order.queuesBefore(it.next())) {
                it.previous();
                break;
            }
        }
    }

    protected int calculateBuyTradableQuantity (Order order){
        int buyTradableQuantity = 0;
        for (int i = 0; i < buyQueue.size() ; i++ ){
            if (order.matches(buyQueue.get(i)))
                buyTradableQuantity += buyQueue.get(i).getWholeQuantity();
            else
                break;
        }
        return buyTradableQuantity;
    }

    protected boolean isLowerPriceCloser (int lowerPrice, int higherPrice, int targetPrice){
        return Math.abs(targetPrice - lowerPrice) <= Math.abs(higherPrice - targetPrice) ;
    }

    protected Pair<Integer, Integer> calculateLowerBound (long price){
        int currentSellTradableQuantity = 0;
        int bestTradableQuantity = 0 ;
        int bestprice = 0;
        for (int currentIndex = sellQueue.size() - 1; currentIndex >= 0 ; currentIndex-- ){
            Order sell = sellQueue.get(currentIndex);
            currentSellTradableQuantity += sell.getWholeQuantity();
            int tempTradableQuantity = Math.min(calculateBuyTradableQuantity(sell) , currentSellTradableQuantity);
            if (tempTradableQuantity < bestTradableQuantity)
                break;
            else if (tempTradableQuantity == bestTradableQuantity){
                if (isLowerPriceCloser(sell.getPrice(), bestprice, (int) price))
                    bestprice = sell.getPrice();
                else
                    break;
            }
            else {
                bestprice = sell.getPrice();
                bestTradableQuantity = tempTradableQuantity ;
            }
        }
        return Pair.of(bestprice, bestTradableQuantity);
    }

    protected int findFirstHigherBuyPrice(int lowerBound){
        int bestPrice = Integer.MAX_VALUE;
        for (int i = buyQueue.size()-1 ; i >= 0 ; i--){
            Order buy = buyQueue.get(i);
            if (buy.getPrice() >= lowerBound)
                bestPrice = buy.getPrice();
            else
                break;
        }
        return bestPrice;
    }

    protected int calculateExactOpeningPrice (int price, int lowerBound){

        int upperBound = findFirstHigherBuyPrice(lowerBound);

        if (price >= upperBound)
            return upperBound;
        else if (price <= lowerBound)
            return lowerBound;
        else
            return price;
    }

    public int calculateOpeningPrice (long price){

        Pair<Integer, Integer> lowerBoundResult = calculateLowerBound(price);
        tradableQuantity = lowerBoundResult.getRight();

        return calculateExactOpeningPrice((int) price, lowerBoundResult.getLeft());
    }

}
