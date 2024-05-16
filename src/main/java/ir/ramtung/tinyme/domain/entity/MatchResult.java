package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
@Getter
public final class MatchResult {
    private final MatchingOutcome outcome;
    private final Order remainder;
    private final LinkedList<Trade> trades;
    private final int auctionPrice ;
    private final int tradablePrice ;
    static final private String MATCH_RESULT_STRING = "MatchResult[";
    static final private String REMAINDER_STRING = "remainder=";
    static final private String TRADES_STRING = "trades=";

    public static MatchResult executed(Order remainder, List<Trade> trades) {
        return new MatchResult(MatchingOutcome.EXECUTED, remainder, new LinkedList<>(trades) , 0,
                0);
    }

    public static MatchResult notEnoughCredit() {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_CREDIT, null, new LinkedList<>(), 0,
                0);
    }
    public static MatchResult notEnoughPositions() {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_POSITIONS, null, new LinkedList<>(), 0,
                0);
    }
    public static MatchResult invalidMinExecQuantity() {
        return new MatchResult(MatchingOutcome.INVALID_MIN_EXEC_QUANTITY, null, new LinkedList<>(), 0,
                0);
    }
    public static MatchResult minExecQuantityHaveNotMet(){
        return new MatchResult(MatchingOutcome.MIN_EXEC_QUANTITY_HAVE_NOT_MET, null, new LinkedList<>(), 0,
                0);
    }
    public static MatchResult changingMinExecQuantityWhileUpdating(){
        return new MatchResult(MatchingOutcome.CHANGING_MIN_EXEC_QUANTITY_IN_UPDATE_REQUEST, null,
                new LinkedList<>(), 0, 0);
    }
    public static MatchResult stopLimitOrderQueued(){
        return new MatchResult(MatchingOutcome.STOP_LIMIT_ORDER_QUEUED, null, new LinkedList<>(), 0,
                0);
    }
    public static MatchResult stopLimitOrderExecuted(){
        return new MatchResult(MatchingOutcome.STOP_LIMIT_ORDER_EXECUTED, null, new LinkedList<>(), 0,
                0);
    }
    public static MatchResult stopLimitOrderExecutedDirectly(Order remainder, List<Trade> trades){
        return new MatchResult(MatchingOutcome.STOP_LIMIT_ORDER_EXECUTED_DIRECTLY, remainder, new LinkedList<>(trades),
                0, 0);
    }
    public static MatchResult invalidStopLimitOrder(){
        return new MatchResult(MatchingOutcome.INVALID_STOP_LIMIT_ORDER, null, new LinkedList<>(), 0,
                0);
    }

    public static MatchResult stopLimitOrderActivated(Order remainder){
        return new MatchResult(MatchingOutcome.STOP_LIMIT_ORDER_QUEUED, remainder, new LinkedList<>(), 0,
                0);
    }

    public static MatchResult stopLimitOrderUpdated(){
        return new MatchResult(MatchingOutcome.STOP_LIMIT_ORDER_UPDATED, null, new LinkedList<>(), 0,
                0);
    }

    public static MatchResult orderAddedToAuction(int price, int tradablePrice){
        return new MatchResult(MatchingOutcome.ORDER_ADDED_TO_AUCTION, null, new LinkedList<>(), price,
                tradablePrice);
    }

    public static MatchResult auctionOpened (int price, int tradablePrice){
        return new MatchResult(MatchingOutcome.OPENED, null, new LinkedList<>(), price, tradablePrice);
    }

    public static MatchResult updateAuctionStopLimitError() {
        return new MatchResult(MatchingOutcome.CANNOT_CHANGE_STOP_LIMIT_ORDER_FOR_AUCTION_SECURITY, null,
                                                                new LinkedList<>(), 0, 0);
    }

    public static MatchResult addMinExecToAuction() {

        return new MatchResult(MatchingOutcome.CANNOT_ADD_MIN_EXEC_QUANTITY_TO_AUCTION_ORDER, null,
                new LinkedList<>(), 0, 0);
    }

    public static MatchResult changeAuctionOrderBook(int openingPrice, int quantity) {
        return new MatchResult(MatchingOutcome.AUCTION_ORDER_BOOK_CHANGED, null,
                new LinkedList<>(), 0, 0);
    }

    public MatchResult(MatchingOutcome outcome, Order remainder, LinkedList<Trade> trades, int auctionPrice,
                       int tradablePrice) {
        this.outcome = outcome;
        this.remainder = remainder;
        this.trades = trades;
        this.auctionPrice = auctionPrice;
        this.tradablePrice = tradablePrice;
    }

    public MatchingOutcome outcome() {
        return outcome;
    }
    public Order remainder() {
        return remainder;
    }

    public LinkedList<Trade> trades() {
        return trades;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (MatchResult) obj;
        return Objects.equals(this.remainder, that.remainder) &&
                Objects.equals(this.trades, that.trades);
    }

    @Override
    public int hashCode() {
        return Objects.hash(remainder, trades);
    }

    @Override
    public String toString() {
        return MATCH_RESULT_STRING +
                REMAINDER_STRING + remainder + ", " +
                TRADES_STRING + trades + ']';
    }


}
