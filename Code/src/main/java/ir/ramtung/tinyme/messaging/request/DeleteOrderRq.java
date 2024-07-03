package ir.ramtung.tinyme.messaging.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import ir.ramtung.tinyme.domain.entity.Side;
import ir.ramtung.tinyme.domain.service.RequestPropertyFinder;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteOrderRq extends Request {
    private long requestId;
    private String securityIsin;
    private Side side;
    private long orderId;
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime entryTime;

    public DeleteOrderRq(long requestId, String securityIsin, Side side, long orderId) {
        this.requestId = requestId;
        this.securityIsin = securityIsin;
        this.side = side;
        this.orderId = orderId;
        this.entryTime = LocalDateTime.now();
    }

    @Override
    public RequestPropertyFinder findProperties(SecurityRepository securityRepository,
                                                ShareholderRepository shareholderRepository,
                                                BrokerRepository brokerRepository) {
        return new RequestPropertyFinder(this, securityRepository, shareholderRepository, brokerRepository);
    }
}
