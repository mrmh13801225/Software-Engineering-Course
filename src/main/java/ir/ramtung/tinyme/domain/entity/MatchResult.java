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

    public static MatchResult executed(Order remainder, List<Trade> trades) {
        return new MatchResult(MatchingOutcome.EXECUTED, remainder, new LinkedList<>(trades));
    }

    public static MatchResult notEnoughCredit() {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_CREDIT, null, new LinkedList<>());
    }
    public static MatchResult notEnoughPositions() {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_POSITIONS, null, new LinkedList<>());
    }
    public static MatchResult invalidMinExecQuantity() {
        return new MatchResult(MatchingOutcome.INVALID_MIN_EXEC_QUANTITY, null, new LinkedList<>());
    }
    public static MatchResult minExecQuantityHaveNotMet(){
        return new MatchResult(MatchingOutcome.MIN_EXEC_QUANTITY_HAVE_NOT_MET, null, new LinkedList<>());
    }
    public static MatchResult changingMinExecQuantityWhileUpdating(){
        return new MatchResult(MatchingOutcome.CHANGING_MIN_EXEC_QUANTITY_IN_UPDATE_REQUEST, null,
                new LinkedList<>());
    }
    public static MatchResult stopLimitOrderQueued(){
        return new MatchResult(MatchingOutcome.STOP_LIMIT_ORDER_QUEUED, null, new LinkedList<>());
    }
    public static MatchResult stopLimitOrderExecuted(){
        return new MatchResult(MatchingOutcome.STOP_LIMIT_ORDER_EXECUTED, null, new LinkedList<>());
    }
    public static MatchResult stopLimitOrderExecutedDirectly(Order remainder, List<Trade> trades){
        return new MatchResult(MatchingOutcome.STOP_LIMIT_ORDER_EXECUTED_DIRECTLY, remainder, new LinkedList<>(trades));
    }
    public static MatchResult invalidStopLimitOrder(){
        return new MatchResult(MatchingOutcome.INVALID_STOP_LIMIT_ORDER, null, new LinkedList<>());
    }

    public static MatchResult stopLimitOrderActivated(Order remainder){
        return new MatchResult(MatchingOutcome.STOP_LIMIT_ORDER_ACCEPTED, remainder, new LinkedList<>());
    }
    private MatchResult(MatchingOutcome outcome, Order remainder, LinkedList<Trade> trades) {
        this.outcome = outcome;
        this.remainder = remainder;
        this.trades = trades;
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
        return "MatchResult[" +
                "remainder=" + remainder + ", " +
                "trades=" + trades + ']';
    }


}
