/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2014, 2015, 2016, 2019  AO Industries, Inc.
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

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * <p>
 * Prevents any URL rewriting performed on the response encodeURL or encodeRedirectURL
 * methods.
 * </p>
 * <p>
 * This should be used for both the REQUEST and ERROR dispatchers.
 * </p>
 * <p>
 * <strong>This must be first in the filter chain</strong>, or at least before all filters that
 * perform any URL rewriting.  This filter specifically does not pass the URL
 * rewriting up the filter chain, as it is intended to block default session
 * URL rewriting provided by servlet contains.
 * </p>
 */
public class NoEncodeUrlFilter implements Filter {

	private static final String REQUEST_ATTRIBUTE_KEY = NoEncodeUrlFilter.class.getName() + ".filter_applied";

	@Override
	public void init(FilterConfig config) {
		// Nothing to do
	}

	@Override
	public void doFilter(
		ServletRequest request,
		ServletResponse response,
		FilterChain chain
	) throws IOException, ServletException {
		// Makes sure only one filter is applied per request
		if(
			request.getAttribute(REQUEST_ATTRIBUTE_KEY)==null
			&& (response instanceof HttpServletResponse)
		) {
			request.setAttribute(REQUEST_ATTRIBUTE_KEY, Boolean.TRUE);
			try {
				chain.doFilter(
					request,
					new HttpServletResponseWrapper((HttpServletResponse)response) {
						@Override
						@Deprecated
						public String encodeRedirectUrl(String url) {
							return url;
						}

						@Override
						public String encodeRedirectURL(String url) {
							return url;
						}

						@Override
						@Deprecated
						public String encodeUrl(String url) {
							return url;
						}

						@Override
						public String encodeURL(String url) {
							return url;
						}
					}
				);
			} finally {
				request.removeAttribute(REQUEST_ATTRIBUTE_KEY);
			}
		} else {
			chain.doFilter(request, response);
		}
	}

	@Override
	public void destroy() {
		// Nothing to do
	}
}
