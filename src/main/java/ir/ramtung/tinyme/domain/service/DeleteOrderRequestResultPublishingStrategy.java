package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.request.Request;

import java.util.ArrayList;

public class DeleteOrderRequestResultPublishingStrategy implements ResultPublishingStrategy{
    @Override
    public void publishSuccess(Request request, RequestHandlingResult result, EventPublisher eventPublisher) {

    }

    @Override
    public void publishFailure(Request request, ArrayList errors, EventPublisher eventPublisher) {

    }
}
