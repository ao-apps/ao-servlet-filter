/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2019, 2020  AO Industries, Inc.
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

import com.aoindustries.servlet.http.LastModifiedServlet;
import com.aoindustries.util.StringUtility;
import com.aoindustries.util.WildcardPatternMatcher;
import java.io.IOException;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Adds a <code><a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control">cache-control</a></code>
 * header to any request with a {@link LastModifiedServlet#LAST_MODIFIED_PARAMETER_NAME} parameter.
 * The header is added before the filter chain is called.
 * <p>
 * This should be used for the {@link DispatcherType#REQUEST} dispatcher only.
 * </p>
 * <pre>
 * Init Parameters:
 *    cache-control: The content of the <code>cache-control</code> header, defaults to 
 * </pre>
 * <p>
 * See also:
 * </p>
 * <ol>
 *   <li><a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control">Cache-Control - HTTP | MDN</a></li>
 *   <li><a href="https://web.dev/stale-while-revalidate">Keeping things fresh with stale-while-revalidate</a></li>
 *   <li><a href="https://ashton.codes/set-cache-control-max-age-1-year/">Why we set a `Cache-Control: Max-Age` of 1 year</a></li>
 *   <li><a href="https://developers.google.com/web/tools/lighthouse/audits/cache-policy?utm_source=lighthouse&utm_medium=devtools">Uses inefficient cache policy on static assets</a></li>
 * </ol>
 * 
 * @see  WildcardPatternMatcher  for supported patterns
 */
public class LastModifiedCacheControlFilter implements Filter {

	/**
	 * The default, very aggressive, <code>cache-control</code> header value.
	 */
	// In order documented at https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control
	public static final String DEFAULT_CACHE_CONTROL =
		// Cacheability
		"public"
		// Expiration (1 year = 365.25 days)
		+ ",max-age=31557600"
		//+ ",s-maxage=31557600" // Use same value for proxies
		+ ",max-stale=31557600"
		+ ",stale-while-revalidate=31557600"
		+ ",stale-if-error=31557600"
		// Revalidation and reloading
		+ ",immutable";

	private String cacheControl;

	@Override
	public void init(FilterConfig config) {
		String cacheControlParam = StringUtility.trimNullIfEmpty(config.getInitParameter("cache-control"));
		if(cacheControlParam == null) {
			// Compatibility with old parameter case
			cacheControlParam = StringUtility.trimNullIfEmpty(config.getInitParameter("Cache-Control"));
		}
		this.cacheControl = (cacheControlParam == null) ? DEFAULT_CACHE_CONTROL : cacheControlParam;
	}

	@Override
	public void doFilter(
		ServletRequest request,
		ServletResponse response,
		FilterChain chain
	) throws IOException, ServletException {
		if(
			// Must be HTTP request
			(request instanceof HttpServletRequest)
			&& (response instanceof HttpServletResponse)
		) {
			HttpServletRequest httpRequest = (HttpServletRequest)request;
			String lastModified = httpRequest.getParameter(LastModifiedServlet.LAST_MODIFIED_PARAMETER_NAME);
			if(lastModified != null && !lastModified.isEmpty()) {
				HttpServletResponse httpResponse = (HttpServletResponse)response;
				httpResponse.setHeader("cache-control", cacheControl);
			}
		}
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
	}
}
