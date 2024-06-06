package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.Request;
import ir.ramtung.tinyme.repository.SecurityRepository;

public interface RequestHandlingStrategy<T extends Request> {

    void validateRequest(T request, Security security, Broker broker,
                         Shareholder shareholder) throws InvalidRequestException ;

    RequestHandlingResult handleRequest(T request, Security security, Shareholder shareholder, Broker broker, Matcher matcher,
                                        SecurityRepository securityRepository)
            throws InvalidRequestException ;
}