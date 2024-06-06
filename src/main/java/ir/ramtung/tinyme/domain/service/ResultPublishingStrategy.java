package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.request.Request;

import java.util.List;

public interface ResultPublishingStrategy <T extends Request>{
    public void publishSuccess(T request, RequestHandlingResult result, EventPublisher eventPublisher);
    public void publishFailure(T request, List<String> errors, EventPublisher eventPublisher);
}
