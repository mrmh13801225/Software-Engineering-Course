package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.Request;
import ir.ramtung.tinyme.repository.SecurityRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Service
public class DeleteOrderRequestStrategy implements RequestHandlingStrategy{
    @Override
    public void validateRequest(Request request, Security security, Broker broker, Shareholder shareholder)
            throws InvalidRequestException {
        DeleteOrderRq deleteOrderRq = (DeleteOrderRq) request;
        List<String> errors = new LinkedList<>();
        if (deleteOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    @Override
    public RequestHandlingResult handleRequest(Request request, Security security, Shareholder shareholder, Broker broker,
                                               Matcher matcher, SecurityRepository securityRepository)
                                      throws InvalidRequestException {

        RequestHandlingResult results = new RequestHandlingResult();
        DeleteOrderRq deleteOrderRq = (DeleteOrderRq) request;

        if (security instanceof AuctionSecurity auctionSecurity)
            results.executionResult = auctionSecurity.deleteAuctionOrder(deleteOrderRq);
        else
            security.deleteOrder(deleteOrderRq);


        return results;
    }
}
