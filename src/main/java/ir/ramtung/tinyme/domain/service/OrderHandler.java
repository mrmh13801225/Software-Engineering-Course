package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderHandler {
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    EventPublisher eventPublisher;
    Matcher matcher;

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.eventPublisher = eventPublisher;
        this.matcher = matcher;
    }

    private List<String> stopLimitRequestErrors (EnterOrderRq enterOrderRq){
        List<String> errors = new LinkedList<>();

        if ((enterOrderRq.getMinimumExecutionQuantity() > 0) && (enterOrderRq.getStopPrice() > 0))
            errors.add(Message.STOP_LIMIT_ORDER_CANNOT_HAVE_MIN_EXEC);
        if ((enterOrderRq.getStopPrice() < 0))
            errors.add(Message.INVALID_STOP_PRICE);
        if ((enterOrderRq.getPeakSize() > 0) && (enterOrderRq.getStopPrice() > 0))
            errors.add(Message.STOP_LIMIT_ORDER_CANNOT_BE_ICEBERG);

        return errors;
    }

    private List<String> iceburgOrdersErrors(EnterOrderRq enterOrderRq) {
        List<String> errors = new LinkedList<>();

        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);

        return errors ;
    }

    private List<String> normalOrdersErrors(EnterOrderRq enterOrderRq){
        List<String> errors = new LinkedList<>();

        if (enterOrderRq.getMinimumExecutionQuantity() < 0)
            errors.add(Message.ORDER_MIN_EXEC_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getQuantity() < enterOrderRq.getMinimumExecutionQuantity())
            errors.add(Message.SO_BIG_ORDER_MIN_EXEC_QUANTITY);
        if (enterOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (enterOrderRq.getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);

        return  errors;
    }

    private List<String> elementsFindingErrors(EnterOrderRq enterOrderRq){
        List<String> errors = new LinkedList<>();

        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else {
            if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (enterOrderRq.getPrice() % security.getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
        }
        if (brokerRepository.findBrokerById(enterOrderRq.getBrokerId()) == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
        if (shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()) == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);

        return errors ;
    }

    private void    validateEnterOrderRq(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();

        errors.addAll(stopLimitRequestErrors(enterOrderRq));
        errors.addAll(iceburgOrdersErrors(enterOrderRq));
        errors.addAll(normalOrdersErrors(enterOrderRq));
        errors.addAll(elementsFindingErrors(enterOrderRq));

        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private MatchResult executeRequest(EnterOrderRq enterOrderRq ,Security security ,Broker broker ,
                                       Shareholder shareholder) throws InvalidRequestException {
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                return security.newOrder(enterOrderRq, broker, shareholder, matcher);
            else
                return security.updateOrder(enterOrderRq, matcher);
    }

    private boolean isRejectEventPublished(EnterOrderRq enterOrderRq ,MatchResult matchResult){
        if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) {
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

    private  void orderSituationPublisher(EnterOrderRq enterOrderRq, MatchResult matchResult){
        //TODO:check if orderAccepted event will be published for auction orders or just a OpeningPriceEvent will be published.
        if (matchResult.getOutcome() == MatchingOutcome.ORDER_ADDED_TO_AUCTION)
            eventPublisher.publish(new OpeningPriceEvent(enterOrderRq.getSecurityIsin(), matchResult.getAuctionPrice(),
                    matchResult.getTradableQuantity()));
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        else
            eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        if (!matchResult.trades().isEmpty()) {
            eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
        }
    }

    private void publishActivations(ArrayList<MatchResult> activationResults){
        Iterator<MatchResult> it = activationResults.iterator();
        while (it.hasNext()){
            StopLimitOrder temp = (StopLimitOrder) it.next().getRemainder();
            eventPublisher.publish(new OrderActivatedEvent(temp.getReqId(), temp.getOrderId()));
        }
    }

    public void publishActivatedOrdersExecution (ArrayList<MatchResult> activatedOrdersExecutionResults){
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

    private void resultsPublisher(MatchResult matchResult, EnterOrderRq enterOrderRq,
                                  ArrayList<MatchResult> activationResults,
                                  ArrayList<MatchResult> activatedOrdersExecutionResults){
        if (isRejectEventPublished(enterOrderRq ,matchResult))
            return;
        orderSituationPublisher(enterOrderRq, matchResult);
        publishActivations(activationResults);
        publishActivatedOrdersExecution(activatedOrdersExecutionResults);

    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        try {
            //TODO:validations must be updated
            validateEnterOrderRq(enterOrderRq);

            Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
            Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
            Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());

            MatchResult matchResult = executeRequest(enterOrderRq ,security ,broker ,shareholder);
            ArrayList<MatchResult> activationResults = security.handleActivation();
            ArrayList<MatchResult> activatedOrdersExecutionResults = security.executeActivatedStopOrders(matcher);

            resultsPublisher(matchResult, enterOrderRq, activationResults, activatedOrdersExecutionResults);
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    private void validateDeleteOrderRq(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (deleteOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin()) == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validateDeleteOrderRq(deleteOrderRq);
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            if (security instanceof AuctionSecurity auctionSecurity) {
                MatchResult deleteResult = auctionSecurity.deleteAuctionOrder(deleteOrderRq);
                eventPublisher.publish(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
                eventPublisher.publish(new OpeningPriceEvent(security.getIsin(), deleteResult.getAuctionPrice(), deleteResult.getTradableQuantity()));
            }
            else {
                security.deleteOrder(deleteOrderRq);
                eventPublisher.publish(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
            }
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    private void publishSecurityChangingResult(ChangeSecurityResult changeSecurityResult,
                                               ChangeMatchingStateRq changeMatchingStateRq){
        //TODO: must complete this section.
        eventPublisher.publish(new SecurityStateChangedEvent(changeSecurityResult.getSecurity().getIsin(),
                changeMatchingStateRq.getTargetState()));
    }

    private void handleSecurityReplacing (ChangeSecurityResult changeSecurityResult){
        if (changeSecurityResult.getChangingResult() == SecurityChangingResult.FAILED)
            return;

        securityRepository.replace(changeSecurityResult.getSecurity());
    }

    private ArrayList<MatchResult> handleOpening (ChangeSecurityResult changeSecurityResult){
        ArrayList<MatchResult> results = new ArrayList<>();
        if (changeSecurityResult.getChangingResult() == SecurityChangingResult.VIRTUAL){
            Security security = securityRepository.findSecurityByIsin(changeSecurityResult.getSecurity().getIsin());
            if (security instanceof AuctionSecurity auctionSecurity)  //TODO :add AuctionMatcher to class fields.
                results.addAll(auctionSecurity.open(new AuctionMatcher()));
            return results;
        }
        else
            return results;
    }

    public void handleChangeMatchingState(ChangeMatchingStateRq changeMatchingStateRq){
        //TODO:may need validations .
        Security security = securityRepository.findSecurityByIsin(changeMatchingStateRq.getSecurityIsin());
        ChangeSecurityResult changeSecurityResult = security.changeTo(changeMatchingStateRq);
        handleSecurityReplacing(changeSecurityResult);
        ArrayList<MatchResult> openingResult = handleOpening(changeSecurityResult);

        publishSecurityChangingResult(changeSecurityResult, changeMatchingStateRq);
    }

}
