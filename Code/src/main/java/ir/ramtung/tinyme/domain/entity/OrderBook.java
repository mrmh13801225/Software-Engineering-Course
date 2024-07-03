package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
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
    private int openingPrice ;

    public OrderBook() {
        buyQueue = new LinkedList<>();
        sellQueue = new LinkedList<>();
        tradableQuantity = 0;
    }

    public Order getFirstBuy (){
        if (!buyQueue.isEmpty() && buyQueue.getFirst().canGetExecuted(openingPrice))
            return buyQueue.pop();
        return null;
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

    protected int calculateOppositeSideTradableQuantity(Order order){

        LinkedList<Order> oppositeQueue = getQueue(order.side.opposite());

        int tradableQuantity = 0;
        for (int i = 0; i < oppositeQueue.size() ; i++ ){
            if (order.matches(oppositeQueue.get(i)))
                tradableQuantity += oppositeQueue.get(i).getWholeQuantity();
            else
                break;
        }
        return tradableQuantity;
    }

    protected boolean isLowerPriceCloser (int lowerPrice, int higherPrice, int targetPrice){
        return Math.abs(targetPrice - lowerPrice) <= Math.abs(higherPrice - targetPrice) ;
    }

    protected Pair<Integer, Integer> findOptimalOpeningPriceBySide (Side side){
        int currentTradableQuantity = 0;
        int bestTradableQuantity = 0 ;
        int bestprice = 0;

        LinkedList<Order> queue = getQueue(side);

        for (int currentIndex = 0; currentIndex < queue.size() ; currentIndex++ ){
            Order order = queue.get(currentIndex);
            currentTradableQuantity += order.getWholeQuantity();
            int tempTradableQuantity = Math.min(calculateOppositeSideTradableQuantity(order) , currentTradableQuantity);
            if (tempTradableQuantity < bestTradableQuantity)
                break;
            else if(tempTradableQuantity > bestTradableQuantity) {
                bestprice = order.getPrice();
                bestTradableQuantity = tempTradableQuantity;

            }
        }
        return Pair.of(bestprice, bestTradableQuantity);
    }

    protected int calculateExactOpeningPrice (int price, int lowerBound, int upperBound){

        if (price >= upperBound)
            return upperBound;
        else if (price <= lowerBound)
            return lowerBound;
        else
            return price;
    }

    public int calculateOpeningPrice (long price) {
        //<price, tradeable quantity>
        Pair<Integer, Integer> upperBoundResult = findOptimalOpeningPriceBySide(Side.BUY);
        Pair<Integer, Integer> lowerBoundResult = findOptimalOpeningPriceBySide(Side.SELL);

        int upperBoundPrice = upperBoundResult.getLeft();
        int lowerBoundPrice = lowerBoundResult.getLeft();

        tradableQuantity = lowerBoundResult.getRight();
        openingPrice = calculateExactOpeningPrice((int) price, lowerBoundPrice, upperBoundPrice);

        if(tradableQuantity == 0)
        {
            openingPrice = handleZeroTradableQuantity(price);
        }

        return openingPrice;
    }

    public int handleZeroTradableQuantity(long price){
        if(sellQueue.isEmpty() && !buyQueue.isEmpty())
            return (int)Math.min(buyQueue.getFirst().getPrice(), price);
        else if(buyQueue.isEmpty() && !sellQueue.isEmpty())
            return (int)Math.max(sellQueue.getFirst().getPrice(), price);
        else
            return 0;
    }

}
