package ir.ramtung.tinyme.domain.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Builder
@Getter
@EqualsAndHashCode
@ToString
public class Trade {
    Security security;
    private int price;
    private int quantity;
    private Order buy;
    private Order sell;

    public Trade(Security security, int price, int quantity, Order order1, Order order2) {
        this.security = security;
        this.price = price;
        this.quantity = quantity;

        if (order1.getSide() == Side.BUY) {
            this.buy = order1.snapshot();
            this.sell = order2.snapshot();
        } else {
            this.buy = order2.snapshot();
            this.sell = order1.snapshot();
        }
    }

    public long getTradedValue() {
        return (long) price * quantity;
    }

    public void increaseSellersCredit() {
        sell.getBroker().increaseCreditBy(getTradedValue());
    }

    public void decreaseBuyersCredit() {
        buy.getBroker().decreaseCreditBy(getTradedValue());
    }

    public boolean buyerHasEnoughCredit() {
        return buy.getBroker().hasEnoughCredit(getTradedValue());
    }

}
