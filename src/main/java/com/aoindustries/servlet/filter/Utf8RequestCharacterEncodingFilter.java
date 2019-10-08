/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright 2007, 2008, 2009, 2010, 2011, 2015, 2016, 2019 by AO Industries, Inc.,
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
import java.nio.charset.StandardCharsets;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * <p>
 * Sets the request encoding to {@link StandardCharsets#UTF_8} when encoding not provided by the client.
 * </p>
 * <p>
 * This should be first in the filter chain and used for the REQUEST dispatcher only.
 * </p>
 *
 * @author  AO Industries, Inc.
 */
// TODO: Change to ServletRequestListener
public class Utf8RequestCharacterEncodingFilter implements Filter {

	@Override
	public void init(FilterConfig config) {
	}

	@Override
	public void doFilter(
		ServletRequest request,
		ServletResponse response,
		FilterChain chain
	) throws IOException, ServletException {
		// Only override encoding when not provided by the client
		if(request.getCharacterEncoding() == null) {
			request.setCharacterEncoding(StandardCharsets.UTF_8.name());
		}
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
	}
}
