/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2013, 2015, 2016, 2017, 2020, 2021, 2022, 2024  AO Industries, Inc.
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
 * along with ao-servlet-filter.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoapps.servlet.filter;

import com.aoapps.hodgepodge.util.WildcardPatternMatcher;
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
 * Returns not found for any filter-mapping that is sent to this filter.
 *
 * <p>Due to the limitations of filter mapping URLs, patterns may be provided and
 * handled by this filter itself.</p>
 *
 * <p>This should be used for the {@link DispatcherType#REQUEST} dispatcher only.</p>
 *
 * <pre>Init Parameters:
 *    patterns  Comma/space-separated list of patterns (default to *)</pre>
 *
 * @see  WildcardPatternMatcher  for supported patterns
 */
public class NotFoundFilter implements Filter {

  private WildcardPatternMatcher patterns;

  @Override
  public void init(FilterConfig config) {
    String param = config.getInitParameter("patterns");
    if (param == null) {
      patterns = WildcardPatternMatcher.matchAll();
    } else {
      patterns = WildcardPatternMatcher.compile(param);
    }
  }

  @Override
  public void doFilter(
      ServletRequest request,
      ServletResponse response,
      FilterChain chain
  ) throws IOException, ServletException {
    final String message = "404 Not Found";
    if (
        (request instanceof HttpServletRequest)
            && (response instanceof HttpServletResponse)
    ) {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      if (patterns.isMatch(httpRequest.getServletPath())) {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, message);
      } else {
        chain.doFilter(request, response);
      }
    } else {
      throw new ServletException(message);
    }
  }

  @Override
  public void destroy() {
    // Do nothing
  }
}
