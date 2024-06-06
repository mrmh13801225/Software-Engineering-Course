package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.Broker;
import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.domain.entity.Shareholder;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.Request;

import java.util.List;

public interface RequestHandlingStrategy<T extends Request> {
    List<MatchResult> handleRequest(T request, Security security, Shareholder shareholder, Broker broker, Matcher matcher)
            throws InvalidRequestException ;
}
