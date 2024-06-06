package ir.ramtung.tinyme.messaging.request;

import ir.ramtung.tinyme.domain.service.RequestPropertyFinder;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangeMatchingStateRq extends Request{

    private long requestId;
    private String securityIsin;
    private MatchingState targetState;

    public static ChangeMatchingStateRq createChangeMatchingState(long requestId, String securityIsin,
                                                                  MatchingState matchingState){
        return new ChangeMatchingStateRq(requestId, securityIsin, matchingState);
    }

    @Override
    public RequestPropertyFinder findProperties(SecurityRepository securityRepository,
                                                ShareholderRepository shareholderRepository,
                                                BrokerRepository brokerRepository) {
        return new RequestPropertyFinder(this, securityRepository, shareholderRepository, brokerRepository);
    }
}
