package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.Request;
import ir.ramtung.tinyme.repository.SecurityRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Service
public class ChangeMatchingStateRequestStrategy implements RequestHandlingStrategy{

    @Override
    public void validateRequest(Request request, Security security, Broker broker, Shareholder shareholder)
            throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);

    }

    private ArrayList<MatchResult> handleSecurityReplacing (ChangeSecurityResult changeSecurityResult,
                                                            SecurityRepository securityRepository, Matcher matcher){
        if (changeSecurityResult.getChangingResult() == SecurityChangingResult.FAILED)
            return new ArrayList<>();
        ArrayList<MatchResult> temp = new ArrayList<>();
        securityRepository.replace(changeSecurityResult.getSecurity());
        if(!(changeSecurityResult.getSecurity() instanceof AuctionSecurity)){
            temp.addAll(changeSecurityResult.getSecurity().handleActivation());
            temp.addAll(changeSecurityResult.getSecurity().executeActivatedStopOrders(matcher));
        }
        return temp;
    }

    private ArrayList<MatchResult> handleOpening (ChangeSecurityResult changeSecurityResult,
                                                  SecurityRepository securityRepository){
        ArrayList<MatchResult> results = new ArrayList<>();
        if (changeSecurityResult.getChangingResult() == SecurityChangingResult.VIRTUAL){
            Security security = securityRepository.findSecurityByIsin(changeSecurityResult.getSecurity().getIsin());
            if (security instanceof AuctionSecurity auctionSecurity)  //TODO :add AuctionMatcher to class fields.
                results.addAll(auctionSecurity.open(new AuctionMatcher()));
            return results;
        }
        else
            return results;
    }

    @Override
    public RequestHandlingResult handleRequest(Request request, Security security, Shareholder shareholder, Broker broker,
                                               Matcher matcher, SecurityRepository securityRepository)
                                      throws InvalidRequestException {
        RequestHandlingResult results = new RequestHandlingResult();
        ChangeMatchingStateRq changeMatchingStateRq = (ChangeMatchingStateRq) request;

        ChangeSecurityResult changeSecurityResult = security.changeTo(changeMatchingStateRq);
        results.executionResult = changeSecurityResult;
        results.OpenningResults = handleOpening(changeSecurityResult, securityRepository);
        results.ActivationResults = handleSecurityReplacing(changeSecurityResult, securityRepository, matcher);

        return results;
    }
}
