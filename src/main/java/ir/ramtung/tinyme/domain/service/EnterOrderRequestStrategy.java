package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.Broker;
import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.domain.entity.Shareholder;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import ir.ramtung.tinyme.messaging.request.Request;

import java.util.ArrayList;
import java.util.List;

public class EnterOrderRequestStrategy implements RequestHandlingStrategy{

    private MatchResult executeRequest(EnterOrderRq enterOrderRq ,Security security ,Broker broker ,
                                       Shareholder shareholder, Matcher matcher) throws InvalidRequestException {
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            return security.newOrder(enterOrderRq, broker, shareholder, matcher);
        else
            return security.updateOrder(enterOrderRq, matcher);
    }

    @Override
    public List<MatchResult> handleRequest(Request request, Security security, Shareholder shareholder, Broker broker,
                                           Matcher matcher) throws InvalidRequestException {
        List<MatchResult> results = new ArrayList<>();
        EnterOrderRq enterOrderRq = (EnterOrderRq) request;

        results.add(executeRequest(enterOrderRq ,security ,broker ,shareholder, matcher));
        results.addAll(security.handleActivation());
        results.addAll(security.executeActivatedStopOrders(matcher));

        return results;
    }
}
