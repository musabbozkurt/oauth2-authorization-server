package mb.oauth2authorizationserver.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import mb.oauth2authorizationserver.model.enums.CustomerTier;

import java.math.BigDecimal;

public record DiscountRequest(@NotNull CustomerTier type, @NotNull @PositiveOrZero BigDecimal amount) {

}
