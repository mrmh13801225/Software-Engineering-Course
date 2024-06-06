package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import ir.ramtung.tinyme.messaging.request.Request;
import ir.ramtung.tinyme.repository.SecurityRepository;

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
    public RequestHandlingResult handleRequest(Request request, Security security, Shareholder shareholder, Broker broker,
                                               Matcher matcher, SecurityRepository securityRepository)
                                      throws InvalidRequestException {
        RequestHandlingResult results = new RequestHandlingResult();
        EnterOrderRq enterOrderRq = (EnterOrderRq) request;

        results.executionResult = executeRequest(enterOrderRq ,security ,broker ,shareholder, matcher);
        results.ActivationResults.addAll(security.handleActivation());
        results.ActivationResults.addAll(security.executeActivatedStopOrders(matcher));

        return results;
    }
}
