package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.event.OpeningPriceEvent;
import ir.ramtung.tinyme.messaging.event.OrderDeletedEvent;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.Request;

import java.util.ArrayList;
import java.util.List;

public class DeleteOrderRequestStrategy implements RequestHandlingStrategy{
    @Override
    public List<Result> handleRequest(Request request, Security security, Shareholder shareholder, Broker broker,
                                           Matcher matcher) throws InvalidRequestException {

        List<Result> results = new ArrayList<>();
        DeleteOrderRq deleteOrderRq = (DeleteOrderRq) request;

        if (security instanceof AuctionSecurity auctionSecurity)
            results.add(auctionSecurity.deleteAuctionOrder(deleteOrderRq));
        else
            security.deleteOrder(deleteOrderRq);


        return results;
    }
}
