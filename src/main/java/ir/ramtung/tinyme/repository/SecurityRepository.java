package ir.ramtung.tinyme.repository;

import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.domain.entity.SecurityChangingResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class SecurityRepository {
    private final HashMap<String, Security> securityByIsin = new HashMap<>();
    public Security findSecurityByIsin(String isin) {
        return securityByIsin.get(isin);
    }

    public void addSecurity(Security security) {
        securityByIsin.put(security.getIsin(), security);
    }

    public void clear() {
        securityByIsin.clear();
    }

    public void replace(Security security) {
        String isin = security.getIsin();
        if (securityByIsin.containsKey(isin))
            securityByIsin.put(isin, security);

    }


    Iterable<? extends Security> allSecurities() {
        return securityByIsin.values();
    }
}
