/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2010, 2011, 2013, 2016  AO Industries, Inc.
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

import com.aoindustries.util.StringUtility;
import com.aoindustries.util.WrappedException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * Prevents sessions from being created.  Without sessions, minimal information
 * should be stored as cookies.  In the event cookies are disabled, this filter
 * also adds the cookie values during URL rewriting.  Any cookies added to the
 * URLs through rewriting will have a parameter name beginning with
 * <code>cookie:</code>
 * </p>
 * <p>
 * This should be used for both the REQUEST and ERROR dispatchers.
 * </p>
 * <p>
 * Only cookie names and values are encoded as URL parameters.  Comments, paths,
 * and other attributes are lost.
 * </p>
 * <p>
 * To ensure no namespace conflicts with cookies potentially rewritten as URL
 * parameters, any parameter in the request beginning with <code>cookie:</code>
 * is filtered, even if it doesn't currently match an allowed cookie name.
 * The result of <code>getQueryString</code>, however, is unaltered any may possibly
 * contain cookie parameters.
 * </p>
 * <p>
 * Any cookie name that is not in the configured list of cookies names is ignored
 * and not presented to the application, whether it came from HTTP headers or
 * URL parameters.
 * </p>
 * <p>
 * In the event a cookie value is available from both the headers and the URL
 * parameters, the headers take precedence.
 * </p>
 * <p>
 * Note: If using JSP, add <code>session="false"</code>, for example:
 * <pre>&lt;%@ page language="java" session="false" %&gt;</pre>
 * </p>
 */
public class NoSessionFilter implements Filter {

	private static final String FILTER_APPLIED_KEY = NoSessionFilter.class.getName()+".filterApplied";

	public static final String COOKIE_URL_PARAM_PREFIX = "cookie:";

	/**
	 * The maximum number of cookie names allowed.
	 */
	public static final int MAXIMUM_COOKIES = 20;

	private final SortedSet<String> cookieNames = new TreeSet<>();

	/**
	 * Adds the values for any new cookies to the URL.  This handles cookie-based
	 * session management through URL rewriting.
	 */
	private String addCookieValues(HttpServletRequest request, Map<String,Cookie> newCookies, String url) {
		// Split the anchor
		int poundPos = url.lastIndexOf('#');
		String anchor;
		if(poundPos==-1) anchor = null;
		else {
			anchor = url.substring(poundPos);
			url = url.substring(0, poundPos);
		}
		// Don't add for certains file types
		int questionPos = url.lastIndexOf('?');
		String lowerPath = (questionPos==-1 ? url : url.substring(0, questionPos)).toLowerCase(Locale.ENGLISH);
		if(
			!lowerPath.endsWith(".css")
			&& !lowerPath.endsWith(".gif")
			&& !lowerPath.endsWith(".ico")
			&& !lowerPath.endsWith(".jpeg")
			&& !lowerPath.endsWith(".jpg")
			&& !lowerPath.endsWith(".js")
			&& !lowerPath.endsWith(".png")
			&& !lowerPath.endsWith(".txt")
			&& !lowerPath.endsWith(".zip")
		) {
			try {
				Cookie[] oldCookies = null;
				boolean oldCookiesSet = false;
				StringBuilder urlSB = new StringBuilder(url);
				boolean hasParam = questionPos!=-1;
				for(String cookieName : cookieNames) {
					if(newCookies.containsKey(cookieName)) {
						Cookie newCookie = newCookies.get(cookieName);
						if(newCookie!=null) {
							if(hasParam) urlSB.append('&');
							else {
								urlSB.append('?');
								hasParam = true;
							}
							urlSB
								.append(URLEncoder.encode(COOKIE_URL_PARAM_PREFIX+cookieName, "UTF-8"))
								.append('=')
								.append(URLEncoder.encode(newCookie.getValue(), "UTF-8"))
							;
						} else {
							// Cookie was removed - do not add to URL
						}
					} else {
						// Add each of the cookie values that were passed-in on the URL, were not removed or added,
						// and were not included as a request cookie.
						String paramName = COOKIE_URL_PARAM_PREFIX+cookieName;
						String[] values = request.getParameterValues(paramName);
						if(values!=null && values.length>0) {
							boolean found = false;
							if(!oldCookiesSet) {
								oldCookies = request.getCookies();
								oldCookiesSet = true;
							}
							if(oldCookies!=null) {
								for(Cookie oldCookie : oldCookies) {
									if(oldCookie.getName().equals(cookieName)) {
										found = true;
										break;
									}
								}
							}
							if(!found) {
								if(hasParam) urlSB.append('&');
								else {
									urlSB.append('?');
									hasParam = true;
								}
								urlSB
									.append(URLEncoder.encode(paramName, "UTF-8"))
									.append('=')
									.append(URLEncoder.encode(values[values.length-1], "UTF-8"))
								;
							}
						}
					}
				}
				url = urlSB.toString();
			} catch(UnsupportedEncodingException err) {
				throw new WrappedException(err);
			}
		}
		if(anchor!=null) url += anchor;
		return url;
	}

	@Override
	public void init(FilterConfig config) {
		cookieNames.clear();
		String cookieNamesInitParam = config.getInitParameter("cookieNames");
		if(cookieNamesInitParam != null) cookieNames.addAll(StringUtility.splitStringCommaSpace(cookieNamesInitParam));
		if(cookieNames.size()>MAXIMUM_COOKIES) throw new IllegalArgumentException("cookieNames.size()>"+MAXIMUM_COOKIES);
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
					final HttpServletRequest originalRequest = (HttpServletRequest)request;
					final HttpServletResponse originalResponse = (HttpServletResponse)response;
					final Map<String,Cookie> newCookies = new HashMap<>(cookieNames.size()*4/3+1);
					chain.doFilter(
						new HttpServletRequestWrapper(originalRequest) {
							@Override
							public HttpSession getSession() {
								throw new RuntimeException("Sessions are disabled by NoSessionFilter");
							}
							@Override
							public HttpSession getSession(boolean create) {
								if(create) throw new RuntimeException("Sessions are disabled by NoSessionFilter");
								return null;
							}
							/** Filter cookie parameters */
							@Override
							public String getParameter(String name) {
								if(name.startsWith(COOKIE_URL_PARAM_PREFIX)) return null;
								return super.getParameter(name);
							}
							/** Filter cookie parameters */
							@Override
							public Map<String,String[]> getParameterMap() {
								// Only create new map if at least one parameter is filtered
								Map<String,String[]> completeMap = super.getParameterMap();
								boolean needsFilter = false;
								for(String paramName : completeMap.keySet()) {
									if(paramName.startsWith(COOKIE_URL_PARAM_PREFIX)) {
										needsFilter = true;
										break;
									}
								}
								if(!needsFilter) return completeMap;
								Map<String,String[]> filteredMap = new LinkedHashMap<>(completeMap.size()*4/3); // No +1 on size since we will filter at least one - guaranteed no rehash
								for(Map.Entry<String,String[]> entry : completeMap.entrySet()) {
									String paramName = entry.getKey();
									if(!paramName.startsWith(COOKIE_URL_PARAM_PREFIX)) filteredMap.put(paramName, entry.getValue());
								}
								return Collections.unmodifiableMap(filteredMap);
							}
							/** Filter cookie parameters */
							@Override
							public Enumeration<String> getParameterNames() {
								final Enumeration<String> completeNames = super.getParameterNames();
								return new Enumeration<String>() {
									// Need to look one ahead
									private String nextName = null;
									@Override
									public boolean hasMoreElements() {
										if(nextName!=null) return true;
										while(completeNames.hasMoreElements()) {
											String name = completeNames.nextElement();
											if(!name.startsWith(COOKIE_URL_PARAM_PREFIX)) {
												nextName = name;
												return true;
											}
										}
										return false;
									}
									@Override
									public String nextElement() {
										String name = nextName;
										if(name!=null) {
											nextName = null;
											return name;
										}
										while(true) {
											name = completeNames.nextElement();
											if(!name.startsWith(COOKIE_URL_PARAM_PREFIX)) return name;
										}
									}
								};
							}
							/** Filter cookie parameters */
							@Override
							public String[] getParameterValues(String name) {
								if(name.startsWith(COOKIE_URL_PARAM_PREFIX)) return null;
								return super.getParameterValues(name);
							}

							@Override
							public Cookie[] getCookies() {
								Cookie[] headerCookies = originalRequest.getCookies();
								Enumeration<String> parameterNames = originalRequest.getParameterNames();
								if(headerCookies==null && !parameterNames.hasMoreElements()) return null; // Not possibly any cookies
								// Add header cookies
								Map<String,Cookie> allCookies = new LinkedHashMap<>(cookieNames.size()*4/3+1); // Worst-case map size is cookieNames
								if(headerCookies!=null) {
									for(Cookie cookie : headerCookies) {
										String cookieName = cookie.getName();
										if(cookieNames.contains(cookieName)) { // Only add expected cookie names
											allCookies.put(cookieName, cookie);
										}
									}
								}
								// Add parameter cookies
								while(parameterNames.hasMoreElements()) {
									String paramName = parameterNames.nextElement();
									if(paramName.startsWith(COOKIE_URL_PARAM_PREFIX)) {
										String cookieName = paramName.substring(COOKIE_URL_PARAM_PREFIX.length());
										if(
											!allCookies.containsKey(cookieName) // Header cookies have priority over parameter cookies
											&& cookieNames.contains(cookieName) // Only add expected cookie names
										) {
											String value = originalRequest.getParameter(paramName);
											assert value!=null;
											Cookie newCookie = new Cookie(cookieName, value);
											newCookie.setPath(originalRequest.getContextPath()+"/");
											allCookies.put(cookieName, newCookie);
										}
									}
								}
								return allCookies.values().toArray(new Cookie[allCookies.size()]);
							}
						},
						new HttpServletResponseWrapper(originalResponse) {
							@Override
							@Deprecated
							public String encodeRedirectUrl(String url) {
								return encodeRedirectURL(url);
							}

							/**
							 * TODO: Only add cookies if their domain and path would make the available to the given url.
							 */
							@Override
							public String encodeRedirectURL(String url) {
								// Don't rewrite anchor-only URLs
								if(url.length()>0 && url.charAt(0)=='#') return url;
								// If starts with http:// or https:// parse out the first part of the URL, encode the path, and reassemble.
								String protocol;
								String remaining;
								if(url.length()>7 && (protocol=url.substring(0, 7)).equalsIgnoreCase("http://")) {
									remaining = url.substring(7);
								} else if(url.length()>8 && (protocol=url.substring(0, 8)).equalsIgnoreCase("https://")) {
									remaining = url.substring(8);
								} else if(url.startsWith("javascript:")) {
									return url;
								} else if(url.startsWith("mailto:")) {
									return url;
								} else if(url.startsWith("telnet:")) {
									return url;
								} else if(url.startsWith("tel:")) {
									return url;
								} else if(url.startsWith("cid:")) {
									return url;
								} else {
									return addCookieValues(originalRequest, newCookies, url);
								}
								int slashPos = remaining.indexOf('/');
								if(slashPos==-1) {
									return addCookieValues(originalRequest, newCookies, url);
								}
								String hostPort = remaining.substring(0, slashPos);
								int colonPos = hostPort.indexOf(':');
								String host = colonPos==-1 ? hostPort : hostPort.substring(0, colonPos);
								String encoded;
								if(host.equalsIgnoreCase(originalRequest.getServerName())) {
									encoded = protocol + hostPort + addCookieValues(originalRequest, newCookies, remaining.substring(slashPos));
								} else {
									// Going to an different hostname, do not add request parameters
									encoded = url;
								}
								return encoded;
							}

							@Override
							@Deprecated
							public String encodeUrl(String url) {
								return encodeURL(url);
							}

							/**
							 * TODO: Only add cookies if their domain and path would make the available to the given url.
							 */
							@Override
							public String encodeURL(String url) {
								// Don't rewrite anchor-only URLs
								if(url.length()>0 && url.charAt(0)=='#') return url;
								// If starts with http:// or https:// parse out the first part of the URL, encode the path, and reassemble.
								String protocol;
								String remaining;
								if(url.length()>7 && (protocol=url.substring(0, 7)).equalsIgnoreCase("http://")) {
									remaining = url.substring(7);
								} else if(url.length()>8 && (protocol=url.substring(0, 8)).equalsIgnoreCase("https://")) {
									remaining = url.substring(8);
								} else if(url.startsWith("javascript:")) {
									return url;
								} else if(url.startsWith("tel:")) {
									return url;
								} else if(url.startsWith("cid:")) {
									return url;
								} else {
									return addCookieValues(originalRequest, newCookies, url);
								}
								int slashPos = remaining.indexOf('/');
								if(slashPos==-1) {
									return addCookieValues(originalRequest, newCookies, url);
								}
								String hostPort = remaining.substring(0, slashPos);
								int colonPos = hostPort.indexOf(':');
								String host = colonPos==-1 ? hostPort : hostPort.substring(0, colonPos);
								String encoded;
								if(host.equalsIgnoreCase(originalRequest.getServerName())) {
									encoded = protocol + hostPort + addCookieValues(originalRequest, newCookies, remaining.substring(slashPos));
								} else {
									// Going to an different hostname, do not add request parameters
									encoded = url;
								}
								return encoded;
							}

							@Override
							public void addCookie(Cookie newCookie) {
								String cookieName = newCookie.getName();
								if(!cookieNames.contains(cookieName)) throw new IllegalArgumentException("Unexpected cookie name, add to cookieNames init parameter: "+cookieName);
								super.addCookie(newCookie);
								if(newCookie.getMaxAge()==0) {
									// Cookie deleted
									newCookies.put(cookieName, null);
								} else {
									boolean found = false;
									Cookie[] oldCookies = originalRequest.getCookies();
									if(oldCookies!=null) {
										for(Cookie oldCookie : oldCookies) {
											if(oldCookie.getName().equals(cookieName)) {
												found = true;
												break;
											}
										}
									}
									if(!found) newCookies.put(cookieName, newCookie);
								}
							}
						}
					);
				} else {
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
		cookieNames.clear();
	}
}
