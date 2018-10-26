package com.composum.sling.core.servlet;

import com.composum.sling.core.BeanContext;
import com.composum.sling.core.util.LinkUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.framework.BundleContext;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A base class for a general hook (servlet) for a console view.
 */
public abstract class AbstractConsoleServlet extends SlingSafeMethodsServlet {

    protected BundleContext bundleContext;

    @Activate
    protected void activate(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    protected abstract Pattern getPathPattern(BeanContext context);

    protected abstract String getResourceType(BeanContext context);

    /**
     * extension point to check access rights for a console feature by feature path
     * @return the path to the console feature (content); 'null' if no check supported or check switched off
     */
    protected String getConsolePath(BeanContext context) {
        return null;
    }

    protected String getRequestPath(SlingHttpServletRequest request) {
        RequestPathInfo reqPathInfo = request.getRequestPathInfo();
        String suffix = reqPathInfo.getSuffix();
        if (StringUtils.isBlank(suffix)) {
            suffix = "/";
        }
        return suffix;
    }

    protected BeanContext createContext(SlingHttpServletRequest request, SlingHttpServletResponse response) {
        return new BeanContext.Servlet(getServletContext(), bundleContext, request, response);
    }

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        BeanContext context = createContext(request, response);

        // the pattern matching is not necessary but it prevents from errors thrown during unwanted requests
        final String pathInfo = request.getPathInfo();
        final Matcher matcher = getPathPattern(context).matcher(pathInfo);

        if (matcher.matches()) {
            if (checkConsoleAccess(context)) {

                // the options for the delegation to the browser component implementation
                RequestDispatcherOptions options = new RequestDispatcherOptions();
                prepareForward(context, options);

                Resource resource = request.getResource();
                RequestDispatcher dispatcher = request.getRequestDispatcher(resource, options);
                dispatcher.forward(request, response);

            } else {
                // redirect to suffix path without console view if access not granted
                response.sendRedirect(LinkUtil.getUrl(request, getRequestPath(request)));
            }
        }
    }

    protected void prepareForward(BeanContext context, RequestDispatcherOptions options) {
        String path = getRequestPath(context.getRequest());
        if (StringUtils.isNotBlank(path)) {
            options.setReplaceSuffix(path);
        }
        // set the view component resource type for each request received by this servlet
        String resourceType = getResourceType(context);
        options.setForceResourceType(resourceType);
    }

    /**
     * Check access rights to the servlets path - is checking ACLs of the console path
     * @param context the current request
     * @return 'true' if access granted or access check switched off
     */
    protected boolean checkConsoleAccess(BeanContext context) {
        String consolePath = getConsolePath(context);
        if (StringUtils.isNotBlank(consolePath)) {
            return context.getResolver().getResource(consolePath) != null;
        }
        return true;
    }
}