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
 * A servlet filter that hides the .jspx or .jsp extension from JSP-based sites.
 * It accomplishes this with the following steps:
 * </p>
 * <ol>
 * <li>Rewrite any URLs ending in "/path/index.jsp(x)" to "/path/", maintaining any query string</li>
 * <li>Rewrite any URLs ending in "/path/file.jsp(x)" to "/path/file", maintaining any query string</li>
 * <li>301 redirect any incoming GET request ending in "/path/index.jsp(x)" to "/path/" (to not lose traffic after enabling the filter)</li>
 * <li>301 redirect any incoming GET request ending in "/path/file.jsp(x)" to "/path/file" (to not lose traffic after enabling the filter)</li>
 * <li>Forward incoming request of "/path/" to "/path/index.jsp(x)", if the resource exists.
 *     This is done by container with a welcome file list of index.jsp(x) in web.xml.</li>
 * <li>Forward incoming request of "/path/file" to "/path/file.jsp(x)", if the resource exists</li>
 * <li>Send any other request down the filter chain</li>
 * </ol>
 * <p>
 * When both *.jspx and *.jsp resources exist, the *.jspx is used.
 * </p>
 * <p>
 * This should be used for the REQUEST dispatcher only.
 * </p>
 * <p>
 * In the filter chain, it is important to consider the forwarding performed by this filter.  Subsequent filters
 * may need FORWARD dispatcher in addition to REQUEST to see the forwarded requests.
 * </p>
 * <p>
 * Note: When testing in Tomcat 7, /WEB-INF/ protection was not compromised by the forwarding.
 * Requests to /WEB-INF/ never even hit the filter.
 * </p>
 */
public class HideJspExtensionFilter implements Filter {

	private static final String FILTER_APPLIED_KEY = HideJspExtensionFilter.class.getName()+".filterApplied";

	/**
	 * The file extensions to rewrite, in priority order.
	 */
	private static final String[] EXTENSIONS = {
		".jspx",
		".jsp"
	};

	/**
	 * The index.ext resource names.
	 */
	private static final String[] INDEXES = new String[EXTENSIONS.length];

	/**
	 * The /index.ext paths.
	 */
	private static final String[] SLASH_INDEXES = new String[EXTENSIONS.length];

	static {
		for(int i=0; i<EXTENSIONS.length; i++) {
			String extension = EXTENSIONS[i];
			String index = "index" + extension;
			INDEXES[i] = index;
			SLASH_INDEXES[i] = "/" + index;
		}
	}

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
					final String responseEncoding = response.getCharacterEncoding();

					String servletPath = httpRequest.getServletPath();
					boolean requestRewrite = !noRewritePatterns.isMatch(servletPath);
					if(
						requestRewrite
						// Only redirect GET requests
						&& ServletUtil.METHOD_GET.equals(httpRequest.getMethod())
					) {
						for(int i=0; i<EXTENSIONS.length; i++) {
							String slashIndex = SLASH_INDEXES[i];
							// 301 redirect any incoming GET request ending in "/path/index.jsp(x)" to "/path/" (to not lose traffic after enabling the filter)
							if(servletPath.endsWith(slashIndex)) {
								// "index.jsp(x)" is added to the servlet path for requests ending in /, this
								// uses the un-decoded requestUri to distinguish between the two
								if(httpRequest.getRequestURI().endsWith(slashIndex)) {
									String queryString = httpRequest.getQueryString();
									String path = servletPath.substring(0, servletPath.length() - INDEXES[i].length());
									// Encode URL path elements (like Japanese filenames)
									path = UrlUtils.encodeUrlPath(path, responseEncoding);
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

						for(int i=0; i<EXTENSIONS.length; i++) {
							String extension = EXTENSIONS[i];
							// 301 redirect any incoming GET request ending in "/path/file.jsp(x)" to "/path/file" (to not lose traffic after enabling the filter)
							if(servletPath.endsWith(extension)) {
								// Do not redirect the index.jsp(x)
								if(!servletPath.endsWith(SLASH_INDEXES[i])) {
									String queryString = httpRequest.getQueryString();
									String path = servletPath.substring(0, servletPath.length() - extension.length());
									if(!isFolder(path)) {
										// Encode URL path elements (like Japanese filenames)
										path = UrlUtils.encodeUrlPath(path, responseEncoding);
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
								for(int i=0; i<EXTENSIONS.length; i++) {
									// Rewrite any URLs ending in "/path/index.jsp(x)" to "/path/", maintaining any query string
									if(path.endsWith(SLASH_INDEXES[i])) {
										String shortenedPath = path.substring(0, path.length() - INDEXES[i].length());
										if(pathEnd == urlLen) {
											return shortenedPath;
										} else {
											return shortenedPath + url.substring(pathEnd);
										}
									}
								}
								for(int i=0; i<EXTENSIONS.length; i++) {
									String extension = EXTENSIONS[i];
									// Rewrite any URLs ending in "/path/file.jsp(x)" to "/path/file", maintaining any query string
									if(path.endsWith(extension)) {
										String shortenedPath = path.substring(0, path.length() - extension.length());
										if(!isFolder(shortenedPath)) {
											if(pathEnd == urlLen) {
												return shortenedPath;
											} else {
												return shortenedPath + url.substring(pathEnd);
											}
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
						// Forward incoming request of "/path/" to "/path/index.jsp(x)", if the resource exists.
						// This is done by container with a welcome file list of index.jsp(x) in web.xml.

						if(!isFolder(servletPath)) {
							for(int i=0; i<EXTENSIONS.length; i++) {
								// Forward incoming request of "/path/file" to "/path/file.jsp(x)", if the resource exists
								String resourcePath = servletPath + EXTENSIONS[i];
								// Do not forward index to index.jsp(x)
								if(!resourcePath.endsWith(SLASH_INDEXES[i])) {
									URL resourceUrl;
									try {
										resourceUrl = servletContext.getResource(resourcePath);
									} catch(MalformedURLException e) {
										// Assume does not exist
										resourceUrl = null;
									}
									if(resourceUrl != null) {
										// Forward to .jsp(x) file
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
