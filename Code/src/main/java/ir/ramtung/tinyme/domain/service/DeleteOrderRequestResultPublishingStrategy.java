package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.OpeningPriceEvent;
import ir.ramtung.tinyme.messaging.event.OrderDeletedEvent;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.Request;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DeleteOrderRequestResultPublishingStrategy extends BaseResultPublisher<DeleteOrderRq>{
    @Override
    public void publishSuccess(DeleteOrderRq request, RequestHandlingResult result, EventPublisher eventPublisher) {

        eventPublisher.publish(new OrderDeletedEvent(request.getRequestId(), request.getOrderId()));
        if (result.executionResult != null){
            MatchResult deleteResult = (MatchResult) result.executionResult;
            eventPublisher.publish(new OpeningPriceEvent(request.getSecurityIsin(), deleteResult.getAuctionPrice(),
                    deleteResult.getTradableQuantity()));
        }

    }

    @Override
    public void publishFailure(DeleteOrderRq request, List<String> errors, EventPublisher eventPublisher) {

        eventPublisher.publish(new OrderRejectedEvent(request.getRequestId(), request.getOrderId(), errors));
    }
}
