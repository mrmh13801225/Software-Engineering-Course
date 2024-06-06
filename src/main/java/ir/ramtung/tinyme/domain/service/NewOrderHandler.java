package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.request.*;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class NewOrderHandler {
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    EventPublisher eventPublisher;
    Matcher matcher;
    private final Map<Class<? extends Request>, RequestHandlingStrategy> requestHandlingStrategies;
    private final Map<Class<? extends Request>, ResultPublishingStrategy> resultPublishingStrategies;

    public NewOrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository,
                           ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher,
                           Map<Class<? extends Request>, RequestHandlingStrategy> requestHandlingStrategies,
                           Map<Class<? extends Request>, ResultPublishingStrategy> resultPublishingStrategies) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.eventPublisher = eventPublisher;
        this.matcher = matcher;
        this.requestHandlingStrategies = requestHandlingStrategies;
        this.resultPublishingStrategies = resultPublishingStrategies;
    }

    public void handleRequest(Request request) {
        RequestHandlingStrategy requestHandlingStrategy = requestHandlingStrategies.get(request.getClass());
        ResultPublishingStrategy resultPublishingStrategy = resultPublishingStrategies.get(request.getClass());
        RequestPropertyFinder properties = request.findProperties(securityRepository, shareholderRepository, brokerRepository);
        try {
            requestHandlingStrategy.validateRequest(request, properties.getSecurity(), properties.getBroker(), properties.getShareholder());
            RequestHandlingResult requestHandlingResult = requestHandlingStrategy.handleRequest(request, properties.getSecurity(),
                    properties.getShareholder(), properties.getBroker(), matcher, securityRepository);
            resultPublishingStrategy.publishSuccess(request, requestHandlingResult, eventPublisher);
        } catch (InvalidRequestException e) {
            resultPublishingStrategy.publishFailure(request, e.getReasons(), eventPublisher);
        }
    }


}
