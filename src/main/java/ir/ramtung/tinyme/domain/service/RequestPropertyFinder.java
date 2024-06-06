package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.Broker;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.domain.entity.Shareholder;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import lombok.Getter;

@Getter
public class RequestPropertyFinder {
    private final Security security;
    private final Shareholder shareholder;
    private final Broker broker;

    public RequestPropertyFinder (EnterOrderRq enterOrderRq, SecurityRepository securityRepository,
                                  ShareholderRepository shareholderRepository, BrokerRepository brokerRepository){
        this.security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        this.shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());
        this.broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
    }

    public RequestPropertyFinder (DeleteOrderRq deleteOrderRq, SecurityRepository securityRepository,
                                  ShareholderRepository shareholderRepository, BrokerRepository brokerRepository) {
        this.security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
        this.broker = null;
        this.shareholder = null;
    }

    public RequestPropertyFinder (ChangeMatchingStateRq changeMatchingStateRq, SecurityRepository securityRepository,
                                  ShareholderRepository shareholderRepository, BrokerRepository brokerRepository){
        this.security = securityRepository.findSecurityByIsin(changeMatchingStateRq.getSecurityIsin());
        this.broker = null;
        this.shareholder = null;
    }
}
