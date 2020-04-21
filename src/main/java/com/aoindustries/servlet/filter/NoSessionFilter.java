/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2010, 2011, 2013, 2016, 2019, 2020  AO Industries, Inc.
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

import com.aoindustries.lang.Strings;
import com.aoindustries.net.IRI;
import com.aoindustries.net.MutableURIParameters;
import com.aoindustries.net.URIParametersMap;
import com.aoindustries.net.URIParser;
import com.aoindustries.servlet.http.Canonical;
import com.aoindustries.servlet.http.Cookies;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.servlet.DispatcherType;
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
 * <code>cookie:</code> (by default).
 * </p>
 * <p>
 * <strong>Security implications!</strong>  Since cookies may now come from the URL, they
 * may be added on links from other sites.  Thus, one cannot use cookies in
 * any Cross-Site Request Forgery (CSRF) detection or for any other purpose
 * that assumes the cookie may only be provided by the browser.
 * </p>
 * <p>
 * This should be used for both the {@link DispatcherType#REQUEST} and {@link DispatcherType#ERROR} dispatchers.
 * </p>
 * <p>
 * Only cookie names and values are encoded as URL parameters.  Comments, paths,
 * and other attributes are lost.
 * </p>
 * <p>
 * To ensure no namespace conflicts with cookies potentially rewritten as URL
 * parameters, any parameter in the request beginning with <code>cookie:</code> (by default)
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
 * </p>
 * <pre>&lt;%@ page language="java" session="false" %&gt;</pre>
 * <p>
 * Consider using in conjunction with <code>session-config</code> to ensure that
 * <code>;jsessionid</code> is never added to the URLs.
 * </p>
 * <p>
 * TODO: Idea:
 * When only one cookie name is allowed, convert it to be just the cookie symbol itself?
 * This would means cookies would be lost when a second cookie added, but would be a cool short URL otherwise.
 * Or a second init parameter that specifies which cookie name is the "shortCookie"?
 * Or do we allow each cookie to mapped to a custom name instead of prefix + name?
 * </p>
 * <p>
 * TODO: Support empty cookieUrlParamPrefix?  This would make it more difficult
 * to separate cookies and parameters.  It would make it where any cookie name
 * allowed here would effectively never be able to be used as a parameter.
 * </p>
 */
public class NoSessionFilter implements Filter {

	private static final String FILTER_APPLIED_KEY = NoSessionFilter.class.getName() + ".filterApplied";

	/**
	 * The default symbol is used as prefix.
	 */
	private static final String DEFAULT_COOKIE_URL_PARAM_PREFIX = "cookie:";

	/**
	 * The maximum number of cookie names allowed.
	 */
	public static final int MAXIMUM_COOKIES = 20;

	private String cookieUrlParamPrefix;

	private final SortedSet<String> cookieNames = new TreeSet<>();

	@Override
	public void init(FilterConfig config) {
		String cookieUrlParamPrefixInitParam = config.getInitParameter("cookieUrlParamPrefix");
		cookieUrlParamPrefix = (cookieUrlParamPrefixInitParam != null && !cookieUrlParamPrefixInitParam.isEmpty()) ? cookieUrlParamPrefixInitParam : DEFAULT_COOKIE_URL_PARAM_PREFIX;
		cookieNames.clear();
		String cookieNamesInitParam = config.getInitParameter("cookieNames");
		if(cookieNamesInitParam != null) cookieNames.addAll(Strings.splitCommaSpace(cookieNamesInitParam));
		if(cookieNames.size() > MAXIMUM_COOKIES) throw new IllegalArgumentException("cookieNames.size() > " + MAXIMUM_COOKIES);
	}

	/**
	 * Adds the values for any new cookies to the URL.  This handles cookie-based
	 * session management through URL rewriting.
	 */
	private IRI addCookieValues(HttpServletRequest request, Map<String,Cookie> newCookies, IRI iri) {
		// Don't add for certains file types
		if(
			// Matches SessionResponseWrapper
			// Matches LocaleFilter
			!iri.pathEndsWithIgnoreCase(".bmp")
			&& !iri.pathEndsWithIgnoreCase(".css")
			&& !iri.pathEndsWithIgnoreCase(".dia")
			&& !iri.pathEndsWithIgnoreCase(".exe")
			&& !iri.pathEndsWithIgnoreCase(".gif")
			&& !iri.pathEndsWithIgnoreCase(".ico")
			&& !iri.pathEndsWithIgnoreCase(".jpeg")
			&& !iri.pathEndsWithIgnoreCase(".jpg")
			&& !iri.pathEndsWithIgnoreCase(".js")
			&& !iri.pathEndsWithIgnoreCase(".png")
			&& !iri.pathEndsWithIgnoreCase(".svg")
			&& !iri.pathEndsWithIgnoreCase(".txt")
			&& !iri.pathEndsWithIgnoreCase(".webp")
			&& !iri.pathEndsWithIgnoreCase(".zip")
		) {
			Cookie[] oldCookies = null;
			boolean oldCookiesSet = false;
			MutableURIParameters cookieParams = null;
			for(String cookieName : cookieNames) {
				if(newCookies.containsKey(cookieName)) {
					Cookie newCookie = newCookies.get(cookieName);
					if(newCookie != null) {
						if(cookieParams == null) cookieParams = new URIParametersMap();
						cookieParams.addParameter(cookieUrlParamPrefix + cookieName, Cookies.getValue(newCookie));
					} else {
						// Cookie was removed - do not add to URL
					}
				} else {
					// Add each of the cookie values that were passed-in on the URL, were not removed or added,
					// and were not included as a request cookie.
					String paramName = cookieUrlParamPrefix + cookieName;
					String[] values = request.getParameterValues(paramName);
					if(values != null && values.length > 0) {
						boolean found = false;
						if(!oldCookiesSet) {
							oldCookies = request.getCookies();
							oldCookiesSet = true;
						}
						if(oldCookies != null) {
							String encodedName = Cookies.encodeName(cookieName);
							for(Cookie oldCookie : oldCookies) {
								if(oldCookie.getName().equals(encodedName)) {
									found = true;
									break;
								}
							}
						}
						if(!found) {
							if(cookieParams == null) cookieParams = new URIParametersMap();
							cookieParams.addParameter(paramName, values[values.length - 1]);
						}
					}
				}
			}
			iri = iri.addParameters(cookieParams);
		}
		return iri;
	}

	private String addCookieValues(HttpServletRequest request, Map<String,Cookie> newCookies, String url) {
		return addCookieValues(request, newCookies, new IRI(url)).toASCIIString();
	}

	@Override
	public void doFilter(
		ServletRequest request,
		ServletResponse response,
		FilterChain chain
	) throws IOException, ServletException {
		if(request.getAttribute(FILTER_APPLIED_KEY) == null) {
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
								if(name.startsWith(cookieUrlParamPrefix)) return null;
								return super.getParameter(name);
							}
							/** Filter cookie parameters */
							@Override
							public Map<String,String[]> getParameterMap() {
								// Only create new map if at least one parameter is filtered
								Map<String,String[]> completeMap = super.getParameterMap();
								boolean needsFilter = false;
								for(String paramName : completeMap.keySet()) {
									if(paramName.startsWith(cookieUrlParamPrefix)) {
										needsFilter = true;
										break;
									}
								}
								if(!needsFilter) return completeMap;
								Map<String,String[]> filteredMap = new LinkedHashMap<>(completeMap.size()*4/3); // No +1 on size since we will filter at least one - guaranteed no rehash
								for(Map.Entry<String,String[]> entry : completeMap.entrySet()) {
									String paramName = entry.getKey();
									if(!paramName.startsWith(cookieUrlParamPrefix)) filteredMap.put(paramName, entry.getValue());
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
										if(nextName != null) return true;
										while(completeNames.hasMoreElements()) {
											String name = completeNames.nextElement();
											if(!name.startsWith(cookieUrlParamPrefix)) {
												nextName = name;
												return true;
											}
										}
										return false;
									}
									@Override
									public String nextElement() {
										String name = nextName;
										if(name != null) {
											nextName = null;
											return name;
										}
										while(true) {
											name = completeNames.nextElement();
											if(!name.startsWith(cookieUrlParamPrefix)) return name;
										}
									}
								};
							}
							/** Filter cookie parameters */
							@Override
							public String[] getParameterValues(String name) {
								if(name.startsWith(cookieUrlParamPrefix)) return null;
								return super.getParameterValues(name);
							}

							@Override
							public Cookie[] getCookies() {
								Cookie[] headerCookies = originalRequest.getCookies();
								Enumeration<String> parameterNames = originalRequest.getParameterNames();
								if(headerCookies == null && !parameterNames.hasMoreElements()) return null; // Not possibly any cookies
								// Add header cookies
								Map<String,Cookie> allCookies = new LinkedHashMap<>(cookieNames.size()*4/3+1); // Worst-case map size is cookieNames
								if(headerCookies != null) {
									for(Cookie cookie : headerCookies) {
										String cookieName = Cookies.getName(cookie);
										if(cookieNames.contains(cookieName)) { // Only add expected cookie names
											allCookies.put(cookieName, cookie);
										}
									}
								}
								// Add parameter cookies
								while(parameterNames.hasMoreElements()) {
									String paramName = parameterNames.nextElement();
									if(paramName.startsWith(cookieUrlParamPrefix)) {
										String cookieName = paramName.substring(cookieUrlParamPrefix.length());
										if(
											!allCookies.containsKey(cookieName) // Header cookies have priority over parameter cookies
											&& cookieNames.contains(cookieName) // Only add expected cookie names
										) {
											String value = originalRequest.getParameter(paramName);
											assert value != null;
											Cookie newCookie = Cookies.newCookie(cookieName, value);
											Cookies.setPath(newCookie, originalRequest.getContextPath() + "/");
											allCookies.put(cookieName, newCookie);
										}
									}
								}
								return allCookies.values().toArray(new Cookie[allCookies.size()]);
							}
						},
						new HttpServletResponseWrapper(originalResponse) {
							/**
							 * TODO: Only add cookies if their domain and path would make the available to the given url.
							 */
							private String encode(String url) {
								// Don't rewrite canonical URLs
								if(Canonical.get()) return url;
								// Don't rewrite empty or anchor-only URLs
								if(url.isEmpty() || url.charAt(0) == '#') return url;
								// If starts with http:// or https:// parse out the first part of the URL, encode the path, and reassemble.
								String protocol;
								String remaining;
								if(
									// 7: "http://".length()
									url.length() > 7
									&& url.charAt(5) == '/'
									&& url.charAt(6) == '/'
									&& URIParser.isScheme(url, "http")
								) {
									protocol = url.substring(0, 7);
									remaining = url.substring(7);
								} else if(
									// 8: "https://".length()
									url.length() > 8
									&& url.charAt(6) == '/'
									&& url.charAt(7) == '/'
									&& URIParser.isScheme(url, "https")
								) {
									protocol = url.substring(0, 8);
									remaining = url.substring(8);
								} else if(
									URIParser.isScheme(url, "javascript")
									|| URIParser.isScheme(url, "mailto")
									|| URIParser.isScheme(url, "telnet")
									|| URIParser.isScheme(url, "tel")
									|| URIParser.isScheme(url, "cid")
									|| URIParser.isScheme(url, "file")
									|| URIParser.isScheme(url, "data")
								) {
									return url;
								} else {
									return addCookieValues(originalRequest, newCookies, url);
								}
								int slashPos = remaining.indexOf('/');
								if(slashPos == -1) slashPos = remaining.length();
								String hostPort = remaining.substring(0, slashPos);
								int colonPos = hostPort.indexOf(':');
								String host = colonPos == -1 ? hostPort : hostPort.substring(0, colonPos);
								String encoded;
								if(
									// TODO: What about [...] IPv6 addresses?
									host.equalsIgnoreCase(originalRequest.getServerName())
								) {
									String withCookies = addCookieValues(originalRequest, newCookies, remaining.substring(slashPos));
									int newUrlLen = protocol.length() + hostPort.length() + withCookies.length();
									if(newUrlLen == url.length()) {
										assert url.equals(protocol + hostPort + withCookies);
										encoded = url;
									} else {
										StringBuilder newUrl = new StringBuilder(newUrlLen);
										newUrl.append(protocol).append(hostPort).append(withCookies);
										assert newUrl.length() == newUrlLen;
										encoded = newUrl.toString();
									}
								} else {
									// Going to an different hostname, do not add request parameters
									encoded = url;
								}
								return encoded;
							}

							@Override
							@Deprecated
							public String encodeRedirectUrl(String url) {
								return super.encodeRedirectUrl(encode(url));
							}

							@Override
							public String encodeRedirectURL(String url) {
								return super.encodeRedirectURL(encode(url));
							}

							@Override
							@Deprecated
							public String encodeUrl(String url) {
								return super.encodeUrl(encode(url));
							}

							@Override
							public String encodeURL(String url) {
								return super.encodeURL(encode(url));
							}

							@Override
							public void addCookie(Cookie newCookie) {
								String encodedName = newCookie.getName();
								String cookieName = Cookies.decodeName(encodedName);
								if(!cookieNames.contains(cookieName)) throw new IllegalArgumentException("Unexpected cookie name, add to cookieNames init parameter: " + cookieName);
								super.addCookie(newCookie);
								if(newCookie.getMaxAge()==0) {
									// Cookie deleted
									newCookies.put(cookieName, null);
								} else {
									boolean found = false;
									Cookie[] oldCookies = originalRequest.getCookies();
									if(oldCookies != null) {
										for(Cookie oldCookie : oldCookies) {
											if(oldCookie.getName().equals(encodedName)) {
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
		cookieUrlParamPrefix = null;
		cookieNames.clear();
	}
}
