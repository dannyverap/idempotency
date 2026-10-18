package com.wrda.infrastructure.error;

import com.wrda.application.ApplicationException;
import com.wrda.domain.DomainException;
import com.wrda.infrastructure.orders.contract.model.AtlasError;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@WrdaErrorManagement
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 90)
public class WrdaErrorManagementInterceptor {

    private static final Logger LOG = Logger.getLogger(WrdaErrorManagementInterceptor.class);

    @AroundInvoke
    public Object mapErrors(InvocationContext context) throws Exception {
        try {
            return context.proceed();
        } catch (DomainException exception) {
            return error(422, exception.code(), exception.getMessage(), "DOMAIN");
        } catch (ApplicationException exception) {
            return error(500, exception.code(), exception.getMessage(), "APPLICATION");
        } catch (InfrastructureException exception) {
            LOG.error("Infrastructure request failure", exception);
            return error(500, exception.code(), "An infrastructure error occurred", "INFRASTRUCTURE");
        } catch (Exception exception) {
            LOG.error("Unexpected request failure", exception);
            return error(500, "UNEXPECTED_ERROR", "An unexpected error occurred", "UNEXPECTED");
        }
    }

    private Response error(int status, String code, String message, String category) {
        AtlasError error = new AtlasError();
        error.setCode(code);
        error.setMessage(message);
        error.setCategory(AtlasError.CategoryEnum.fromValue(category));
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(error).build();
    }
}
