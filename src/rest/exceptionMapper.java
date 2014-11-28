package rest;

import bcidExceptions.BCIDRuntimeException;
import com.sun.jersey.api.core.ExtendedUriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.errorInfo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * class to catch an exception thrown from a rest service and map the necessary information to a request
 */
@Provider
public class exceptionMapper implements ExceptionMapper<Exception> {
    @Context
    static HttpServletRequest request;
    @Context
    static ExtendedUriInfo uriInfo;
    private Logger logger = LoggerFactory.getLogger(exceptionMapper.class);

    @Override
    public Response toResponse(Exception e) {
        logger.warn("{} thrown.", e.getClass().toString(), e);
        errorInfo errorInfo = getErrorInfo(e);

        // check if the called service is expected to return HTML of JSON
        String mediaType = uriInfo.getMatchedMethod().getSupportedOutputTypes().get(0).toString();

        if (mediaType.equalsIgnoreCase( MediaType.APPLICATION_JSON )) {
            return Response.status(errorInfo.getHttpStatusCode())
                    .entity(errorInfo.toJSON())
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } else {
            try {
//              send the user to error.jsp to display info about the exception/error
                URI url = new URI("error.jsp");
                HttpSession session = request.getSession();
                session.setAttribute("errorInfo", errorInfo);

                return Response.status(errorInfo.getHttpStatusCode())
                        .location(url)
                        .build();
            } catch (URISyntaxException ex) {
                logger.warn("URISyntaxException forming url for bcid error page.", ex);
                return Response.status(errorInfo.getHttpStatusCode())
                        .entity(errorInfo.toJSON())
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }

        }
    }

    // method to set the relevent information in errorInfo
    //TODO pull information out of BCID Exceptions
    private errorInfo getErrorInfo(Exception e) {
        String usrMessage = "Server Error";
        String developerMessage = "Server Error";
        Integer httpStatusCode = getHttpStatus(e);

        return new errorInfo(usrMessage, developerMessage, httpStatusCode, e);

    }

    private Integer getHttpStatus(Exception e) {
        // if the throwable is an instance of WebApplicationException, get the status code
        if (e instanceof WebApplicationException) {
            return ((WebApplicationException) e).getResponse().getStatus();
        } else if (e instanceof BCIDRuntimeException) {
            return ((BCIDRuntimeException) e).getHttpStatusCode();
        } else {
            return Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        }
    }
}
