package ir.ramtung.tinyme.domain.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
public class Broker {
    @Getter
    @EqualsAndHashCode.Include
    private long brokerId;
    @Getter
    private String name;
    @Getter
    private long credit;
    @Getter
    private long reservedCredit;

    public void increaseCreditBy(long amount) {
        assert amount >= 0;
        credit += amount;
    }

    public void decreaseCreditBy(long amount) {
        assert amount >= 0;
        credit -= amount;
    }

    public boolean hasEnoughCredit(long amount) {
        return credit - reservedCredit >= amount;
    }

    public boolean reserveCredit(long amount){
        if (credit - reservedCredit - amount > 0){
            reservedCredit += amount;
            return true;
        }
        return false;
    }
}
