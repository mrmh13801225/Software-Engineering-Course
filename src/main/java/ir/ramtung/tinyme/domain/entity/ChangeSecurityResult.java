package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

@Getter
public class ChangeSecurityResult extends Result{

    SecurityChangingResult changingResult ;
    Security security ;

    public ChangeSecurityResult(SecurityChangingResult changingResult, Security security) {
        this.changingResult = changingResult;
        this.security = security;
    }

    public static ChangeSecurityResult createVirtualAuctionSuccessFullChange(Security security){
        return new ChangeSecurityResult(SecurityChangingResult.VIRTUAL, security);
    }

    public static ChangeSecurityResult createRealSuccessFullChange(Security security){
        return new ChangeSecurityResult(SecurityChangingResult.CHANGED, security);
    }

    public static ChangeSecurityResult failedChange(){
        return new ChangeSecurityResult(SecurityChangingResult.FAILED, null);
    }
}
