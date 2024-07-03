package ir.ramtung.tinyme.messaging.request;

import ir.ramtung.tinyme.domain.service.RequestPropertyFinder;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;

abstract public class Request {
    public abstract RequestPropertyFinder findProperties (SecurityRepository securityRepository,
                                                          ShareholderRepository shareholderRepository,
                                                          BrokerRepository brokerRepository);

}
