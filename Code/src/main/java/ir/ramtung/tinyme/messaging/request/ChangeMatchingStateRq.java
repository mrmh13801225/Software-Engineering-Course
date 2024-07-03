package ir.ramtung.tinyme.messaging.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangeMatchingStateRq {

    private long requestId;
    private String securityIsin;
    private MatchingState targetState;

    public static ChangeMatchingStateRq createChangeMatchingState(long requestId, String securityIsin,
                                                                  MatchingState matchingState){
        return new ChangeMatchingStateRq(requestId, securityIsin, matchingState);
    }

}
