/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2015, 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-servlet-filter.
 *
 * ao-servlet-filter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-servlet-filter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-servlet-filter.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.servlet.filter;

import com.aoindustries.net.UrlUtils;
import com.aoindustries.servlet.http.Dispatcher;
import com.aoindustries.servlet.http.ServletUtil;
import com.aoindustries.util.WildcardPatternMatcher;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * <p>
 * A servlet filter that hides the .jsp extension from JSP-based sites.
 * It accomplishes this with the following steps:
 * </p>
 * <ol>
 * <li>Rewrite any URLs ending in "/path/index.jsp" to "/path/", maintaining any query string</li>
 * <li>Rewrite any URLs ending in "/path/file.jsp" to "/path/file", maintaining any query string</li>
 * <li>301 redirect any incoming request ending in "/path/index.jsp" to "/path/" (to not lose traffic after enabling the filter)</li>
 * <li>301 redirect any incoming request ending in "/path/file.jsp" to "/path/file" (to not lose traffic after enabling the filter)</li>
 * <li>Forward incoming request of "/path/" to "/path/index.jsp", if the resource exists.
 *     This is done by container with a welcome file list of index.jsp in web.xml.</li>
 * <li>Forward incoming request of "/path/file" to "/path/file.jsp", if the resource exists</li>
 * <li>Send any other request down the filter chain</li>
 * </ol>
 * <p>
 * This should be used for the REQUEST dispatcher only.
 * </p>
 * <p>
 * In the filter chain, it is important to consider the forwarding performed by this filter.  Subsequent filters
 * may need FILTER dispatcher in addition to REQUEST to see the forwarded requests.
 * </p>
 * <p>
 * Note: When testing in Tomcat 7, /WEB-INF/ protection was not violated by the forwarding.
 * Requests to /WEB-INF/ never hit the filter.
 * </p>
 */
public class HideJspExtensionFilter implements Filter {

    private static final String FILTER_APPLIED_KEY = HideJspExtensionFilter.class.getName()+".filterApplied";

	private static final String JSP_EXTENSION = ".jsp";
	private static final String INDEX_JSP = "index" + JSP_EXTENSION;
	private static final String SLASH_INDEX_JSP = "/" + INDEX_JSP;

	private ServletContext servletContext;
	private WildcardPatternMatcher noRewritePatterns;

	@Override
    public void init(FilterConfig config) {
        ServletContext configContext = config.getServletContext();
		this.servletContext = configContext;
		String param = config.getInitParameter("noRewritePatterns");
		if(param==null) noRewritePatterns = WildcardPatternMatcher.getMatchNone();
		else noRewritePatterns = WildcardPatternMatcher.getInstance(param);
	}

	/**
	 * Checks if the path represents a folder.
	 * A folder can be represented by empty string or ending slash.
	 */
	private static boolean isFolder(String path) {
		return path.isEmpty() || path.endsWith("/");
	}

	@Override
    public void doFilter(
        ServletRequest request,
        ServletResponse response,
        FilterChain chain
    ) throws IOException, ServletException {
        if(request.getAttribute(FILTER_APPLIED_KEY)==null) {
            try {
                request.setAttribute(FILTER_APPLIED_KEY, Boolean.TRUE);
                if(
                    (request instanceof HttpServletRequest)
                    && (response instanceof HttpServletResponse)
                ) {
                    final HttpServletRequest httpRequest = (HttpServletRequest)request;
                    final HttpServletResponse httpResponse = (HttpServletResponse)response;

					String servletPath = httpRequest.getServletPath();
					boolean requestRewrite = !noRewritePatterns.isMatch(servletPath);
					if(requestRewrite) {
						// 301 redirect any incoming request ending in "/path/index.jsp" to "/path/" (to not lose traffic after enabling the filter)
						if(servletPath.endsWith(SLASH_INDEX_JSP)) {
							// "index.jsp" is added to the servlet path for requests ending in /, this
							// uses the un-decoded requestUri to distinguish between the two
							if(httpRequest.getRequestURI().endsWith(SLASH_INDEX_JSP)) {
								String queryString = httpRequest.getQueryString();
								String path = servletPath.substring(0, servletPath.length() - INDEX_JSP.length());
								// Encode URL path elements (like Japanese filenames)
								path = UrlUtils.encodeUrlPath(path);
								// Add any query string
								if(queryString != null) {
									path = path + '?' + queryString;
								}
								// Perform URL rewriting
								path = httpResponse.encodeRedirectURL(path);
								// Convert to absolute URL
								String location = ServletUtil.getAbsoluteURL(httpRequest, path);
								ServletUtil.sendRedirect(httpResponse, location, HttpServletResponse.SC_MOVED_PERMANENTLY);
								return;
							}
						}

						// 301 redirect any incoming request ending in "/path/file.jsp" to "/path/file" (to not lose traffic after enabling the filter)
						if(
							servletPath.endsWith(JSP_EXTENSION)
							// Do not redirect the index.jsp
							&& !servletPath.endsWith(SLASH_INDEX_JSP)
						) {
							String queryString = httpRequest.getQueryString();
							String path = servletPath.substring(0, servletPath.length() - JSP_EXTENSION.length());
							if(!isFolder(path)) {
								// Encode URL path elements (like Japanese filenames)
								path = UrlUtils.encodeUrlPath(path);
								// Add any query string
								if(queryString != null) {
									path = path + '?' + queryString;
								}
								// Perform URL rewriting
								path = httpResponse.encodeRedirectURL(path);
								// Convert to absolute URL
								String location = ServletUtil.getAbsoluteURL(httpRequest, path);
								ServletUtil.sendRedirect(httpResponse, location, HttpServletResponse.SC_MOVED_PERMANENTLY);
								return;
							}
						}
					}
					HttpServletResponse rewritingResponse = new HttpServletResponseWrapper(httpResponse) {
						private String encode(final String url) {
							final int urlLen = url.length();
							final int pathEnd;
							{
								int questionPos = url.indexOf('?');
								if(questionPos != -1) {
									pathEnd = questionPos;
								} else {
									// Look for anchor
									int anchorPos = url.lastIndexOf('#');
									if(anchorPos != -1) {
										pathEnd = anchorPos;
									} else {
										pathEnd = urlLen;
									}
								}
							}
							String path = url.substring(0, pathEnd);
							if(!noRewritePatterns.isMatch(path)) {
								// Rewrite any URLs ending in "/path/index.jsp" to "/path/", maintaining any query string
								if(path.endsWith(SLASH_INDEX_JSP)) {
									String shortenedPath = path.substring(0, path.length() - INDEX_JSP.length());
									if(pathEnd == urlLen) {
										return shortenedPath;
									} else {
										return shortenedPath + url.substring(pathEnd);
									}
								}
								// Rewrite any URLs ending in "/path/file.jsp" to "/path/file", maintaining any query string
								if(path.endsWith(JSP_EXTENSION)) {
									String shortenedPath = path.substring(0, path.length() - JSP_EXTENSION.length());
									if(!isFolder(shortenedPath)) {
										if(pathEnd == urlLen) {
											return shortenedPath;
										} else {
											return shortenedPath + url.substring(pathEnd);
										}
									}
								}
							}
							// No rewriting
							return url;
						}

						@Deprecated
						@Override
						public String encodeUrl(String url) {
							return encode(url);
						}
						@Override
						public String encodeURL(String url) {
							return encode(url);
						}

						@Deprecated
						@Override
						public String encodeRedirectUrl(String url) {
							return encode(url);
						}
						@Override
						public String encodeRedirectURL(String url) {
							return encode(url);
						}
					};
					if(requestRewrite) {
						// Forward incoming request of "/path/" to "/path/index.jsp", if the resource exists
						// This is done by container with a welcome file list of index.jsp in web.xml.

						// Forward incoming request of "/path/file" to "/path/file.jsp", if the resource exists
						if(!isFolder(servletPath)) {
							String resourcePath = servletPath + JSP_EXTENSION;
							// Do not forward index to index.jsp
							if(!resourcePath.endsWith(SLASH_INDEX_JSP)) {
								URL resourceUrl;
								try {
									resourceUrl = servletContext.getResource(resourcePath);
								} catch(MalformedURLException e) {
									// Assume does not exist
									resourceUrl = null;
								}
								if(resourceUrl != null) {
									// Forward to JSP file
									Dispatcher.forward(
										servletContext,
										resourcePath,
										httpRequest,
										rewritingResponse
									);
									return;
								}
							}
						}
					}
					// Send any other request down the filter chain</li>
					chain.doFilter(httpRequest, rewritingResponse);
                } else {
					// Not HTTP protocol
                    chain.doFilter(request, response);
                }
            } finally {
                request.removeAttribute(FILTER_APPLIED_KEY);
            }
        } else {
            // Filter already applied
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
		servletContext = null;
    }
}
