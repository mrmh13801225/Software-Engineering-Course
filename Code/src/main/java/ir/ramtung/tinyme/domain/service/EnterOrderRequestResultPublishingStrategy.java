package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.MatchingOutcome;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import ir.ramtung.tinyme.messaging.request.Request;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EnterOrderRequestResultPublishingStrategy extends BaseResultPublisher<EnterOrderRq>{


    private boolean isRejectEventPublished(EnterOrderRq enterOrderRq , MatchResult matchResult,
                                           EventPublisher eventPublisher){
        if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) { //TODO: it can get refactored
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
            return true;
        }
        else if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
            return true;
        }
        else if (matchResult.outcome() == MatchingOutcome.MIN_EXEC_QUANTITY_HAVE_NOT_MET){
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.MIN_EXEC_QUANTITY_CONDITION_NOT_MET)));
            return true;
        }
        else if (matchResult.outcome() == MatchingOutcome.CHANGING_MIN_EXEC_QUANTITY_IN_UPDATE_REQUEST){
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.CANNOT_CHANGE_MIN_EXEC_QUANTITY_WHILE_UPDATING_REQUEST)));
            return true;
        }
        else if (matchResult.outcome() == MatchingOutcome.CANNOT_CHANGE_STOP_LIMIT_ORDER_FOR_AUCTION_SECURITY) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.CANNOT_CHANGE_STOP_LIMIT_ORDER_FOR_AUCTION_SECURITY)));
            return true;
        }
        return false;
    }

    private  void orderSituationPublisher(EnterOrderRq enterOrderRq, MatchResult matchResult,
                                          EventPublisher eventPublisher){
        //TODO:check if orderAccepted event will be published for auction orders or just a OpeningPriceEvent will be published.
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        else
            eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        if (!matchResult.trades().isEmpty()) {
            eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
        }
        if (matchResult.getOutcome() == MatchingOutcome.ORDER_ADDED_TO_AUCTION)
            eventPublisher.publish(new OpeningPriceEvent(enterOrderRq.getSecurityIsin(), matchResult.getAuctionPrice(),
                    matchResult.getTradableQuantity()));
        if (matchResult.getOutcome() == MatchingOutcome.AUCTION_ORDER_BOOK_CHANGED) {
            eventPublisher.publish(new OpeningPriceEvent(enterOrderRq.getSecurityIsin(), matchResult.getAuctionPrice(),
                    matchResult.getTradableQuantity()));
        }

    }

    @Override
    public void publishSuccess(EnterOrderRq request, RequestHandlingResult result, EventPublisher eventPublisher) {
        if (isRejectEventPublished(request , (MatchResult) result.executionResult, eventPublisher))
            return;
        orderSituationPublisher(request, (MatchResult) result.executionResult, eventPublisher);
        publishActivatedOrdersExecution(result.ActivationResults, eventPublisher);
    }

    @Override
    public void publishFailure(EnterOrderRq request, List<String> errors, EventPublisher eventPublisher) {
        eventPublisher.publish(new OrderRejectedEvent(request.getRequestId(), request.getOrderId(), errors));
    }
}
