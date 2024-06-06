package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.MatchingOutcome;
import ir.ramtung.tinyme.domain.entity.StopLimitOrder;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.OrderActivatedEvent;
import ir.ramtung.tinyme.messaging.event.OrderExecutedEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Collectors;

abstract public class BaseResultPublisher implements ResultPublishingStrategy{

    protected void publishActivations(ArrayList<MatchResult> activationResults, EventPublisher eventPublisher){
        Iterator<MatchResult> it = activationResults.iterator();
        while (it.hasNext()){
            StopLimitOrder temp = (StopLimitOrder) it.next().getRemainder();
            eventPublisher.publish(new OrderActivatedEvent(temp.getReqId(), temp.getOrderId()));
        }
    }

    protected void publishActivatedOrdersExecution (ArrayList<MatchResult> activatedOrdersExecutionResults,
                                                    EventPublisher eventPublisher){
        Iterator<MatchResult> it = activatedOrdersExecutionResults.iterator();
        while (it.hasNext()){
            MatchResult matchResult = it.next();
            StopLimitOrder temp = (StopLimitOrder) matchResult.getRemainder();
            if (matchResult.getOutcome() == MatchingOutcome.STOP_LIMIT_ORDER_QUEUED)
                eventPublisher.publish(new OrderActivatedEvent(temp.getReqId(), temp.getOrderId()));
            if (!matchResult.trades().isEmpty()) {
                eventPublisher.publish(new OrderExecutedEvent(temp.getReqId(), temp.getOrderId(), matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
            }
        }
    }

}
