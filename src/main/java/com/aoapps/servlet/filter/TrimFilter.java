/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2006, 2007, 2008, 2009, 2010, 2011, 2014, 2015, 2016, 2019, 2020, 2021, 2022, 2024  AO Industries, Inc.
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

import com.aoapps.servlet.attribute.AttributeEE;
import com.aoapps.servlet.attribute.ScopeEE;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

/**
 * Filters the output and removes extra white space at the beginning of lines and completely removes blank lines.
 * TEXTAREAs are automatically detected as long as they start with exact "&lt;textarea" and end with exactly "&lt;/textarea" (case insensitive).
 * PREs are automatically detected as long as they start with exact "&lt;pre" and end with exactly "&lt;/pre" (case insensitive).
 * The reason for the specific tag format is to simplify the implementation
 * for maximum performance.
 *
 * @author  AO Industries, Inc.
 */
public class TrimFilter implements Filter {

  private static final ScopeEE.Request.Attribute<Boolean> REQUEST_ATTRIBUTE =
      ScopeEE.REQUEST.attribute(TrimFilter.class.getName() + ".filter_applied");

  private boolean enabled;

  @Override
  public void init(FilterConfig config) {
    String enabledParam = config.getServletContext().getInitParameter("com.aoapps.servlet.filter.TrimFilter.enabled");
    if (enabledParam == null || (enabledParam = enabledParam.trim()).length() == 0) {
      enabledParam = "true";
    }
    enabled = Boolean.parseBoolean(enabledParam);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    // Makes sure only one filter is applied per request
    AttributeEE.Request<Boolean> attribute = REQUEST_ATTRIBUTE.context(request);
    if (
        enabled
            && attribute.get() == null
            && (response instanceof HttpServletResponse)
    ) {
      attribute.set(true);
      try {
        chain.doFilter(request, new TrimFilterResponse((HttpServletResponse) response));
      } finally {
        attribute.remove();
      }
    } else {
      chain.doFilter(request, response);
    }
  }

  @Override
  public void destroy() {
    enabled = false;
  }
}
