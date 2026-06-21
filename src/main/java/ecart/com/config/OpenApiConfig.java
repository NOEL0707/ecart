package ecart.com.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private static final String CORRELATION_ID = "CorrelationId";
    private static final String USER_ID = "UserId";
    private static final String IDEMPOTENCY_KEY = "IdempotencyKey";
    private static final String ADMIN_ID = "AdminId";
    private static final String ADMIN_ROLE = "AdminRole";

    @Bean
    public OpenAPI ecartOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ecart Backend API")
                        .version("v1")
                        .description("Cart, checkout, discount-code, and admin reporting APIs for the Ecart backend.")
                        .contact(new Contact().name("Ecart Engineering"))
                        .license(new License().name("Private")))
                .servers(List.of(new Server().url("/").description("Current server")))
                .components(new Components()
                        .addSecuritySchemes(CORRELATION_ID, apiKey("X-Correlation-Id", "Optional request correlation id. Generated when absent."))
                        .addSecuritySchemes(USER_ID, apiKey("X-User-Id", "Required for user cart and checkout APIs."))
                        .addSecuritySchemes(IDEMPOTENCY_KEY, apiKey("Idempotency-Key", "Required for checkout retry safety."))
                        .addSecuritySchemes(ADMIN_ID, apiKey("X-Admin-Id", "Required for admin APIs."))
                        .addSecuritySchemes(ADMIN_ROLE, apiKey("X-Admin-Role", "Required admin role, for example ADMIN.")))
                .addSecurityItem(new SecurityRequirement().addList(CORRELATION_ID));
    }

    private SecurityScheme apiKey(String headerName, String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName)
                .description(description);
    }
}
