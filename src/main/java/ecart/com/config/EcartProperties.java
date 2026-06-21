package ecart.com.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ecart")
public record EcartProperties(Discount discount, Admin admin) {
    public int defaultNthOrder() {
        return discount == null ? 3 : discount.defaultNthOrder();
    }

    public int defaultPercent() {
        return discount == null ? 10 : discount.defaultPercent();
    }

    public List<String> allowedAdminRoles() {
        return admin == null || admin.allowedRoles() == null ? List.of("ADMIN") : admin.allowedRoles();
    }

    public record Discount(int defaultNthOrder, int defaultPercent) {
    }

    public record Admin(List<String> allowedRoles) {
    }
}
