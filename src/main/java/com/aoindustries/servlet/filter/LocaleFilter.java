/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2014, 2015, 2016  AO Industries, Inc.
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

import com.aoindustries.net.ServletRequestParameters;
import com.aoindustries.net.UrlUtils;
import com.aoindustries.servlet.http.ServletUtil;
import com.aoindustries.util.StringUtility;
import com.aoindustries.util.WrappedException;
import com.aoindustries.util.i18n.ThreadLocale;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Maintains the user Locale through URL rewriting.
 * The URL rewriting is preferred since it will allow search engines to properly
 * crawl the site per-locale and does not depend on cookies.
 * </p>
 * <p>
 * Each URL (except javascript:, mailto:, telnet:, tel:, and cid: protocols) may be rewritten
 * using encodeURL to include a <code>paramName</code> parameter.  URLs to
 * non-localized resources are not rewritten.
 * </p>
 * <p>
 * During each request, the locale will be set according to:
 * <ol>
 *   <li>If <code>paramName</code> parameter exists and is a supported locale, then set response locale to the exact match.</li>
 *   <li>If <code>paramName</code> parameter exists and can match a supported locale, then set response locale to the matched locale.</li>
 *   <li>Otherwise, select the best locale from the Accept headers.</li>
 * </ol>
 * </p>
 * <p>
 * Also sets the JSTL fmt tag locale as the <code>javax.servlet.jsp.jstl.fmt.locale.request (Config.FMT_LOCALE+Config.REQUEST_SCOPE_SUFFIX)</code> request attribute
 * to the current locale.
 * </p>
 * <p>
 * Also sets the ThreadLocale.
 * </p>
 * <p>
 * If the request is a GET request and the request parameter is missing, invalid, or does not match the
 * final resolved locale, the client is 301 redirected to the URL including the <code>paramName</code>
 * parameter.  This is to help avoid possible duplicate content penalties for search engines.
 * </p>
 *
 * @see ThreadLocale
 */
abstract public class LocaleFilter implements Filter {

	private static final boolean DEBUG = false;

	/**
	 * The default parameter name if not overridden.
	 * 
	 * @see  #getParamName() 
	 */
	private static final String DEFAULT_PARAM_NAME = "hl";

    private static final String ENABLED_LOCALES_REQUEST_ATTRIBUTE_KEY = LocaleFilter.class.getName()+".enabledLocales";

    /**
     * Adds the current locale as a parameter to the URL.
     */
    private String addLocale(Locale locale, String url, String encodedParamName, String encoding) {
        // Split the anchor
        int poundPos = url.lastIndexOf('#');
		String beforeAnchor;
        String anchor;
        if(poundPos==-1) {
			beforeAnchor = url;
			anchor = null;
		} else {
            anchor = url.substring(poundPos);
            beforeAnchor = url.substring(0, poundPos);
        }
        // Only add for non-excluded file types
        if(isLocalizedPath(beforeAnchor)) {
            int questionPos = beforeAnchor.lastIndexOf('?');
            // Only rewrite a URL that does not already contain a paramName parameter.
            if(
                questionPos == -1
                || (
                    !beforeAnchor.startsWith("?"+encodedParamName+"=", questionPos)
                    && beforeAnchor.indexOf("&"+encodedParamName+"=", questionPos + 1) == -1
                )
            ) {
				try {
	                beforeAnchor += (questionPos == -1 ? '?' : '&') + encodedParamName + '=' + URLEncoder.encode(toLocaleString(locale), encoding);
				} catch(UnsupportedEncodingException e) {
					// Should never happen with standard supported encoding
					throw new WrappedException(e);
				}
            }
			return
				(anchor != null)
				? (beforeAnchor + anchor)
				: beforeAnchor
			;
        } else {
			// Unmodified
			return url;
		}
    }

	private ServletContext servletContext;

    @Override
    public void init(FilterConfig config) throws ServletException {
		this.servletContext = config.getServletContext();
    }

    /**
     * Gets the set of enabled locales for the provided request.  This must be called
     * from a request that has already been filtered through LocaleFilter.
	 * When container's default locale is used, will return an empty map.
	 * 
	 * @return  The mapping from localeString to locale
     */
    public static Map<String,Locale> getEnabledLocales(ServletRequest request) {
        @SuppressWarnings("unchecked")
        Map<String,Locale> enabledLocales = (Map<String,Locale>)request.getAttribute(ENABLED_LOCALES_REQUEST_ATTRIBUTE_KEY);
        if(enabledLocales==null) throw new IllegalStateException("Not in request filtered by LocaleFilter, unable to get enabled locales.");
        return enabledLocales;
    }

    @Override
    public void doFilter(
        ServletRequest request,
        ServletResponse response,
        FilterChain chain
    ) throws IOException, ServletException {
        if(
	        // Makes sure only one locale filter is applied per request
            request.getAttribute(ENABLED_LOCALES_REQUEST_ATTRIBUTE_KEY) == null
			// Must be HTTP protocol
            && (request instanceof HttpServletRequest)
            && (response instanceof HttpServletResponse)
        ) {
			final Map<String,Locale> supportedLocales = getSupportedLocales(request);
            request.setAttribute(ENABLED_LOCALES_REQUEST_ATTRIBUTE_KEY, supportedLocales);
            try {
                final HttpServletRequest httpRequest = (HttpServletRequest)request;
                final HttpServletResponse httpResponse = (HttpServletResponse)response;

				final String requestEncoding = ServletUtil.getRequestEncoding(request);
				final String responseEncoding = response.getCharacterEncoding();

                final String requestUri = ServletUtil.getContextRequestUri(httpRequest);
				final boolean isLocalized = isLocalizedPath(requestUri);

				final String paramName = getParamName();
				final String encodedParamName = URLEncoder.encode(paramName, responseEncoding);
				final String paramValue = httpRequest.getParameter(paramName);

				if(
					// 301 redirect if paramName should not be on request, stripping paramName
					paramValue != null
					&& httpRequest.getMethod().equals("GET")
					&& (
						// Never allow paramName on non-localized paths
						!isLocalized
						// Never allow paramName when no choice in locale
						|| supportedLocales.size() < 2
					)
				) {
					if(DEBUG) servletContext.log("DEBUG: Redirecting to remove \"" + paramName + "\" parameter.");
					StringBuilder url = new StringBuilder();
					url.append(UrlUtils.decodeUrlPath(requestUri, requestEncoding));
					boolean didOne = false;
					for(Map.Entry<String,List<String>> entry : new ServletRequestParameters(request).getParameterMap().entrySet()) {
						String name = entry.getKey();
						if(!paramName.equals(name)) {
							for(String value : entry.getValue()) {
								if(didOne) {
									url.append('&');
								} else {
									url.append('?');
									didOne = true;
								}
								url.append(URLEncoder.encode(name, responseEncoding)).append('=').append(URLEncoder.encode(value, responseEncoding));
							}
						}
					}
					ServletUtil.sendRedirect(httpRequest, httpResponse, url.toString(), HttpServletResponse.SC_MOVED_PERMANENTLY);
					return;
				}
				if(
					// Set no response locale and perform no URL rewriting when no locales supported
					!isLocalized
				) {
					if(DEBUG) servletContext.log("DEBUG: Resource not localized, using container's default locale and performing no URL rewriting.");
		            chain.doFilter(request, response);
					return;
				}
				if(
					// Set no response locale and perform no URL rewriting when no locales supported
					supportedLocales.isEmpty()
				) {
					if(DEBUG) servletContext.log("DEBUG: No supported locales, using container's default locale and performing no URL rewriting.");
		            chain.doFilter(request, response);
					return;
				}
				final Locale responseLocale;
				final boolean rewriteUrls;
				if(
					// Only one choice of locale, use it in the response - no language negotiation
					supportedLocales.size() < 2
				) {
					assert supportedLocales.size() == 1;
					if(DEBUG) servletContext.log("DEBUG: Only one supported locale, using the locale and performing no URL rewriting.");
					responseLocale = supportedLocales.values().iterator().next();
					rewriteUrls = false;
				} else {
					assert supportedLocales.size() >= 2;
					Locale locale = null;
					if(paramValue != null) {
						locale = supportedLocales.get(paramValue);
						if(locale != null) {
							if(DEBUG) servletContext.log("DEBUG: Exact match on locale parameter: " + toLocaleString(locale));
						} else {
							// Use best match here to have parameter like "en-GB" redirect to just "en".
							// In this regard, a parameter behaves similarly to, and takes precedence over, the Accept-Language header.
							// Note: this assumes that parameter values are compatible with header values, which is
							//       true in the current implementation (all separated by hyphen '-').
							MatchedLocale matched = getBestMatch(supportedLocales, paramValue);
							if(matched != null) {
								locale = matched.locale;
								if(DEBUG) servletContext.log("DEBUG: Found best match on locale parameter: " + toLocaleString(locale));
							} else {
								if(DEBUG) servletContext.log("DEBUG: Found no match on locale parameter.");
							}
						}
					}
					if(locale == null) {
						// Determine language from Accept-Language header, if present
						locale = getBestLocale(httpRequest, supportedLocales);
						assert locale != null;
						if(DEBUG) servletContext.log("DEBUG: Found best locale from headers: " + toLocaleString(locale));
					}
					final String localeString = toLocaleString(locale);
					if(
						// 301 redirect if paramName not on GET request
						// or if the parameter value doesn't match the resolved locale
						httpRequest.getMethod().equals("GET")
						&& !localeString.equals(paramValue)
					) {
						if(DEBUG) servletContext.log("DEBUG: Redirecting for missing or mismatched locale parameter: " + localeString);
						StringBuilder url = new StringBuilder();
						url.append(UrlUtils.decodeUrlPath(requestUri, requestEncoding));
						boolean didOne = false;
						for(Map.Entry<String,List<String>> entry : new ServletRequestParameters(request).getParameterMap().entrySet()) {
							String name = entry.getKey();
							if(!paramName.equals(name)) {
								for(String value : entry.getValue()) {
									if(didOne) url.append('&');
									else {
										url.append('?');
										didOne = true;
									}
									url.append(URLEncoder.encode(name, responseEncoding)).append('=').append(URLEncoder.encode(value, responseEncoding));
								}
							}
						}
						if(didOne) {
							url.append('&');
						} else {
							url.append('?');
							// Not used: didOne = true;
						}
						url.append(encodedParamName).append('=').append(URLEncoder.encode(localeString, responseEncoding));
						ServletUtil.sendRedirect(httpRequest, httpResponse, url.toString(), HttpServletResponse.SC_MOVED_PERMANENTLY);
						return;
					}
					responseLocale = locale;
					rewriteUrls = true;
				}
				if(DEBUG) servletContext.log("DEBUG: Setting response locale: " + toLocaleString(responseLocale));

				// Set the response locale.
				httpResponse.setLocale(responseLocale);

				// Set the locale for JSTL fmt tags
				httpRequest.setAttribute("javax.servlet.jsp.jstl.fmt.locale.request", responseLocale);

				// Set and restore ThreadLocale
				Locale oldThreadLocale = ThreadLocale.get();
				try {
					ThreadLocale.set(responseLocale);
					if(rewriteUrls) {
						// Perform URL rewriting to maintain locale
						if(DEBUG) servletContext.log("DEBUG: Performing URL rewriting.");
						chain.doFilter(
							httpRequest,
							new HttpServletResponseWrapper(httpResponse) {
								@Override
								@Deprecated
								public String encodeRedirectUrl(String url) {
									return encodeRedirectURL(url);
								}

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
										return httpResponse.encodeRedirectURL(url);
									} else if(url.startsWith("mailto:")) {
										return httpResponse.encodeRedirectURL(url);
									} else if(url.startsWith("telnet:")) {
										return httpResponse.encodeRedirectURL(url);
									} else if(url.startsWith("tel:")) {
										return httpResponse.encodeRedirectURL(url);
									} else if(url.startsWith("cid:")) {
										return httpResponse.encodeRedirectURL(url);
									} else {
										String newUrl = addLocale(httpResponse.getLocale(), url, encodedParamName, httpResponse.getCharacterEncoding());
										return httpResponse.encodeRedirectURL(newUrl);
									}
									int slashPos = remaining.indexOf('/');
									if(slashPos==-1) {
										String newUrl = addLocale(httpResponse.getLocale(), url, encodedParamName, httpResponse.getCharacterEncoding());
										return httpResponse.encodeRedirectURL(newUrl);
									}
									String hostPort = remaining.substring(0, slashPos);
									int colonPos = hostPort.indexOf(':');
									String host = colonPos==-1 ? hostPort : hostPort.substring(0, colonPos);
									String encoded;
									if(host.equalsIgnoreCase(httpRequest.getServerName())) {
										encoded = protocol + hostPort + addLocale(httpResponse.getLocale(), remaining.substring(slashPos), encodedParamName, httpResponse.getCharacterEncoding());
									} else {
										// Going to an different hostname, do not add request parameters
										encoded = url;
									}
									return httpResponse.encodeRedirectURL(encoded);
								}

								@Override
								@Deprecated
								public String encodeUrl(String url) {
									return encodeURL(url);
								}

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
										return httpResponse.encodeURL(url);
									} else if(url.startsWith("mailto:")) {
										return httpResponse.encodeURL(url);
									} else if(url.startsWith("telnet:")) {
										return httpResponse.encodeURL(url);
									} else if(url.startsWith("tel:")) {
										return httpResponse.encodeURL(url);
									} else if(url.startsWith("cid:")) {
										return httpResponse.encodeURL(url);
									} else {
										String newUrl = addLocale(httpResponse.getLocale(), url, encodedParamName, httpResponse.getCharacterEncoding());
										return httpResponse.encodeURL(newUrl);
									}
									int slashPos = remaining.indexOf('/');
									if(slashPos==-1) {
										String newUrl = addLocale(httpResponse.getLocale(), url, encodedParamName, httpResponse.getCharacterEncoding());
										return httpResponse.encodeURL(newUrl);
									}
									String hostPort = remaining.substring(0, slashPos);
									int colonPos = hostPort.indexOf(':');
									String host = colonPos==-1 ? hostPort : hostPort.substring(0, colonPos);
									String encoded;
									if(host.equalsIgnoreCase(httpRequest.getServerName())) {
										encoded = protocol + hostPort + addLocale(httpResponse.getLocale(), remaining.substring(slashPos), encodedParamName, httpResponse.getCharacterEncoding());
									} else {
										// Going to an different hostname, do not add request parameters
										encoded = url;
									}
									return httpResponse.encodeURL(encoded);
								}
							}
						);
					} else {
						// No URL rewriting when no choice in language
						if(DEBUG) servletContext.log("DEBUG: Performing no URL rewriting.");
						chain.doFilter(request, response);
					}
				} finally {
					ThreadLocale.set(oldThreadLocale);
				}
            } finally {
                request.removeAttribute(ENABLED_LOCALES_REQUEST_ATTRIBUTE_KEY);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
		// Do nothing
    }

	/**
	 * Checks if the locale parameter should be added to the given URL.
	 * 
	 * This default implementation will cause the parameter to be added to any
	 * URL that is not one of the following (case-insensitive):
	 * <ul>
	 *   <li>*.css[?params]</li>
	 *   <li>*.exe[?params]</li>
	 *   <li>*.gif[?params]</li>
	 *   <li>*.ico[?params]</li>
	 *   <li>*.jpeg[?params]</li>
	 *   <li>*.jpg[?params]</li>
	 *   <li>*.js[?params]</li>
	 *   <li>*.png[?params]</li>
	 *   <li>*.txt[?params]</li>
	 *   <li>*.zip[?params]</li>
	 * </ul>
	 */
    protected boolean isLocalizedPath(String url) {
        int questionPos = url.lastIndexOf('?');
        String lowerPath = (questionPos==-1 ? url : url.substring(0, questionPos)).toLowerCase(Locale.ROOT);
        return
            !lowerPath.endsWith(".css")
            && !lowerPath.endsWith(".exe")
            && !lowerPath.endsWith(".gif")
            && !lowerPath.endsWith(".ico")
            && !lowerPath.endsWith(".jpeg")
            && !lowerPath.endsWith(".jpg")
            && !lowerPath.endsWith(".js")
            && !lowerPath.endsWith(".png")
            && !lowerPath.endsWith(".txt")
            && !lowerPath.endsWith(".zip")
        ;
	}

	/**
	 * Gets a string representation of the given locale.
	 * This default implementation only supports language, country, and variant.
	 * Country will only be added when language present.
	 * Variant will only be added when both language and country are present.
	 */
	protected String toLocaleString(Locale locale) {
		String language = locale.getLanguage();
		if(language.isEmpty()) return "";
		
		String country = locale.getCountry();
		if(country.isEmpty()) return language;

		String variant = locale.getVariant();
		if(variant.isEmpty()) {
			return language + '-' + country;
		} else {
			return language + '-' + country + '-' + variant;
		}
	}

	/**
	 * Performs the language negotiation based on the Accept-Language header(s).
	 */
	protected Locale getBestLocale(HttpServletRequest request, Map<String,Locale> supportedLocales) throws ServletException {
		@SuppressWarnings("unchecked")
		Enumeration<String> acceptLanguages = request.getHeaders("accept-language");
		if(acceptLanguages == null) {
			// No header, use default
			return getDefaultLocale(request, supportedLocales);
		}
		// Select the best locale from the Accept-Language header(s).
		Locale bestExact = null;
		float bestExactQ = Float.NaN;
		Locale bestApprox = null;
		float bestApproxQ = Float.NaN;
		while(acceptLanguages.hasMoreElements()) {
			for(String pair : StringUtility.splitString(acceptLanguages.nextElement(), ',')) {
				String acceptLanguage;
				float q;
				int semiPos = pair.indexOf(';');
				if(semiPos == -1) {
					acceptLanguage = pair.trim();
					q = 1;
				} else {
					acceptLanguage = pair.substring(0, semiPos).trim();
					try {
						q = Float.parseFloat(pair.substring(semiPos + 1).trim());
					} catch(NumberFormatException e) {
						// Invalid value, use q=0 to ignore the language
						q = 0;
					}
				}
				if(
					q > 0
					&& (Float.isNaN(bestExactQ) || q > bestExactQ)
					&& (Float.isNaN(bestApproxQ) || q > bestApproxQ)
				) {
					MatchedLocale match = getBestMatch(supportedLocales, acceptLanguage);
					if(match != null) {
						if(match.exact) {
							if(Float.isNaN(bestExactQ) || q > bestExactQ) {
								bestExact = match.locale;
								bestExactQ = q;
							}
						} else {
							if(Float.isNaN(bestApproxQ) || q > bestApproxQ) {
								bestApprox = match.locale;
								bestApproxQ = q;
							}
						}
					}
				}
			}
		}
		// Select best exact, best approximate, then default
		Locale selected;
		if(bestExact != null) {
			selected = bestExact;
		} else if(bestApprox != null) {
			selected = bestApprox;
		} else {
			selected = getDefaultLocale(request, supportedLocales);
		}
		return selected;
	}

	protected static class MatchedLocale {
		private final Locale locale;
		private final boolean exact;
		protected MatchedLocale(Locale locale, boolean exact) {
			this.locale = locale;
			this.exact = exact;
		}
	}

	/**
	 * Resolves the best supported language for the given language, country, and variant.
	 * <ol>
	 *   <li>Exact match on language, country, and variant</li>
	 *   <li>Match on language and country</li>
	 *   <li>Match language</li>
	 *   <li>null</li>
	 * </ol>
	 */
	protected MatchedLocale getBestMatch(Map<String,Locale> supportedLocales, String acceptLanguage) {
		// Parse into language, country, and variant
		String language;
		String country;
		String variant;
		int pos1 = acceptLanguage.indexOf('-');
		if(pos1 == -1) {
			language = acceptLanguage;
			country = "";
			variant = "";
		} else {
			language = acceptLanguage.substring(0, pos1);
			int pos2 = acceptLanguage.indexOf('-', pos1 + 1);
			if(pos2 == -1) {
				country = acceptLanguage.substring(pos1 + 1);
				variant = "";
			} else {
				country = acceptLanguage.substring(pos1 + 1, pos2);
				variant = acceptLanguage.substring(pos2 + 1);
			}
		}
		if(!language.isEmpty()) {
			language = language.toLowerCase(Locale.ROOT);
			if(!country.isEmpty()) {
				country = country.toUpperCase(Locale.ROOT);
				if(!variant.isEmpty()) {
					// Exact match on language, country, and variant
					Locale match = supportedLocales.get(
						toLocaleString(
							new Locale(language, country, variant)
						)
					);
					if(match != null) {
						return new MatchedLocale(
							match,
							true
						);
					}
				}
				// Match on language and country
				Locale match = supportedLocales.get(
					toLocaleString(
						new Locale(language, country)
					)
				);
				if(match != null) {
					return new MatchedLocale(
						match,
						variant.isEmpty()
					);
				}
			}
			// Match language
			Locale match = supportedLocales.get(
				toLocaleString(
					new Locale(language)
				)
			);
			if(match != null) {
				return new MatchedLocale(
					match,
					country.isEmpty() && variant.isEmpty()
				);
			}
		}
		// No match
		return null;
	}

	/**
	 * Gets the name of the parameter that will contain the locale.
	 * 
	 * @see  #DEFAULT_PARAM_NAME
	 */
	protected String getParamName() {
		return DEFAULT_PARAM_NAME;
	}

	/**
	 * <p>
	 * Gets the supported locales as a mapping of localeString to locale.
	 * The map key must be consistent with <code>toLocaleString</code> for
	 * each supported locale.
	 * </p>
	 * <p>
	 * If no specific locales are supported, and the responses should remain in
	 * the container's default locale, may return an empty map.
	 * </p>
	 * <p>
	 * When less than two locales are supported, the URLs will not be rewritten and
	 * any paramName parameter will be stripped from incoming requests.
	 * </p>
	 *
	 * @see  #toLocaleString(java.util.Locale) 
	 */
	abstract protected Map<String,Locale> getSupportedLocales(ServletRequest request) throws ServletException;

	/**
	 * Gets the default locale to be used when a best locale cannot be resolved.
	 * This will never be called when <code>getSupportedLocales</code> returns an
	 * empty map.
	 *
	 * This must be one of the supported locales.
	 * 
	 * @see  #getSupportedLocales(javax.servlet.ServletRequest)
	 */
	abstract protected Locale getDefaultLocale(ServletRequest request, Map<String,Locale> supportedLocales) throws ServletException;
}
