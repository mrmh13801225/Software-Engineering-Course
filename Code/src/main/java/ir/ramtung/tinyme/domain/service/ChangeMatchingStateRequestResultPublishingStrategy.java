package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.SecurityStateChangedEvent;
import ir.ramtung.tinyme.messaging.event.TradeEvent;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.Request;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChangeMatchingStateRequestResultPublishingStrategy extends BaseResultPublisher<ChangeMatchingStateRq>{

    private void publishAuctionTrades(List<MatchResult> auctionOpeningMatchResults, EventPublisher eventPublisher){

        for (MatchResult auctionOpeningMatchResult : auctionOpeningMatchResults)
            for (Trade trade : auctionOpeningMatchResult.getTrades())
                eventPublisher.publish(new TradeEvent(trade.getSecurity().getIsin(), trade.getPrice(),
                        trade.getQuantity(), trade.getBuy().getOrderId(), trade.getSell().getOrderId()));


    }

    @Override
    public void publishSuccess(ChangeMatchingStateRq request, RequestHandlingResult result, EventPublisher eventPublisher) {
        ChangeSecurityResult changeSecurityResult = (ChangeSecurityResult) result.executionResult;

        eventPublisher.publish(new SecurityStateChangedEvent(changeSecurityResult.getSecurity().getIsin(),
                request.getTargetState()));
        if(changeSecurityResult.getChangingResult() == SecurityChangingResult.VIRTUAL)
            publishAuctionTrades(result.OpenningResults, eventPublisher);
        publishActivatedOrdersExecution(result.ActivationResults, eventPublisher);
    }

    @Override
    public void publishFailure(ChangeMatchingStateRq request, List<String> errors, EventPublisher eventPublisher) {
        return;
    }
}
