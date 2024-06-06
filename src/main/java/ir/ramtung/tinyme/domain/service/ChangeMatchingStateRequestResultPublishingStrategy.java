package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.ChangeSecurityResult;
import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.SecurityChangingResult;
import ir.ramtung.tinyme.domain.entity.Trade;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.SecurityStateChangedEvent;
import ir.ramtung.tinyme.messaging.event.TradeEvent;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.Request;

import java.util.ArrayList;
import java.util.List;

public class ChangeMatchingStateRequestResultPublishingStrategy extends BaseResultPublisher{

    private void publishAuctionTrades(List<MatchResult> auctionOpeningMatchResults, EventPublisher eventPublisher){

        for (MatchResult auctionOpeningMatchResult : auctionOpeningMatchResults)
            for (Trade trade : auctionOpeningMatchResult.getTrades())
                eventPublisher.publish(new TradeEvent(trade.getSecurity().getIsin(), trade.getPrice(),
                        trade.getQuantity(), trade.getBuy().getOrderId(), trade.getSell().getOrderId()));


    }

    @Override
    public void publishSuccess(Request request, RequestHandlingResult result, EventPublisher eventPublisher) {
        ChangeMatchingStateRq changeMatchingStateRq = (ChangeMatchingStateRq) request;
        ChangeSecurityResult changeSecurityResult = (ChangeSecurityResult) result.executionResult;

        eventPublisher.publish(new SecurityStateChangedEvent(changeSecurityResult.getSecurity().getIsin(),
                changeMatchingStateRq.getTargetState()));
        if(changeSecurityResult.getChangingResult() == SecurityChangingResult.VIRTUAL)
            publishAuctionTrades(result.OpenningResults, eventPublisher);
        publishActivatedOrdersExecution(result.ActivationResults, eventPublisher);
    }

    @Override
    public void publishFailure(Request request, ArrayList errors, EventPublisher eventPublisher) {}
}
