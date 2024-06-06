package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import ir.ramtung.tinyme.messaging.request.Request;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Service
public class EnterOrderRequestStrategy implements RequestHandlingStrategy{

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

    private List<String> securityValidationErrors (EnterOrderRq enterOrderRq, Security security){
        List<String> errors = new LinkedList<>();

        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else {
            if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (enterOrderRq.getPrice() % security.getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
            if ((security instanceof AuctionSecurity) && (enterOrderRq.getMinimumExecutionQuantity() != 0)) {
                errors.add(Message.AUCTION_ORDER_CANNOT_HAVE_MIN_EXEC_QUANTITY);
            }
            if ((security instanceof AuctionSecurity) && (enterOrderRq.getStopPrice() != 0)) {
                errors.add(Message.CANNOT_HAVE_STOP_LIMIT_ORDER_IN_AUCTION_SECURITY);
            }
        }

        return errors;
    }

    private List<String> elementsFindingErrors(EnterOrderRq enterOrderRq, Security security, Broker broker,
                                               Shareholder shareholder ){
        List<String> errors = new LinkedList<>();

        errors.addAll(securityValidationErrors(enterOrderRq, security));

        if (broker == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
        if (shareholder == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);


        return errors ;
    }

    @Override
    public void validateRequest(Request request, Security security, Broker broker,
                                Shareholder shareholder) throws InvalidRequestException {
        EnterOrderRq enterOrderRq = (EnterOrderRq) request;

        List<String> errors = new LinkedList<>();

        errors.addAll(stopLimitRequestErrors(enterOrderRq));
        errors.addAll(iceburgOrdersErrors(enterOrderRq));
        errors.addAll(normalOrdersErrors(enterOrderRq));
        errors.addAll(elementsFindingErrors(enterOrderRq, security, broker, shareholder));

        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private MatchResult executeRequest(EnterOrderRq enterOrderRq ,Security security ,Broker broker ,
                                       Shareholder shareholder, Matcher matcher) throws InvalidRequestException {
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            return security.newOrder(enterOrderRq, broker, shareholder, matcher);
        else
            return security.updateOrder(enterOrderRq, matcher);
    }

    @Override
    public RequestHandlingResult handleRequest(Request request, Security security, Shareholder shareholder, Broker broker,
                                               Matcher matcher, SecurityRepository securityRepository)
                                      throws InvalidRequestException {
        RequestHandlingResult results = new RequestHandlingResult();
        EnterOrderRq enterOrderRq = (EnterOrderRq) request;

        results.executionResult = executeRequest(enterOrderRq ,security ,broker ,shareholder, matcher);
        results.ActivationResults.addAll(security.handleActivation());
        results.ActivationResults.addAll(security.executeActivatedStopOrders(matcher));

        return results;
    }
}
