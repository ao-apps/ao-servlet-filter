/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2010, 2011, 2013, 2015, 2016, 2019, 2020, 2021, 2022, 2024  AO Industries, Inc.
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

import java.io.IOException;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Provides static access to the current request state via thread local variables.
 *
 * <p>This should be used for the {@link DispatcherType#REQUEST}, {@link DispatcherType#FORWARD}, {@link DispatcherType#INCLUDE} and {@link DispatcherType#ERROR} dispatchers.</p>
 */
public class FunctionContext implements Filter {

  private static final String INIT_ERROR_MESSAGE = "Function context not initialized.  Please install FunctionContext filter to web.xml";

  static final ThreadLocal<ServletContext> servletContextTL = new ThreadLocal<>();
  static final ThreadLocal<HttpServletRequest> requestTL = new ThreadLocal<>();
  static final ThreadLocal<HttpServletResponse> responseTL = new ThreadLocal<>();

  /**
   * Gets the {@link ServletContext} associated with the current thread.
   */
  public static ServletContext getServletContext() {
    ServletContext servletContext = servletContextTL.get();
    if (servletContext == null) {
      throw new IllegalStateException(INIT_ERROR_MESSAGE);
    }
    return servletContext;
  }

  /**
   * Gets the {@link HttpServletRequest} associated with the current thread.
   */
  public static HttpServletRequest getRequest() {
    HttpServletRequest request = requestTL.get();
    if (request == null) {
      throw new IllegalStateException(INIT_ERROR_MESSAGE);
    }
    return request;
  }

  /**
   * Gets the {@link HttpServletResponse} associated with the current thread.
   */
  public static HttpServletResponse getResponse() {
    HttpServletResponse response = responseTL.get();
    if (response == null) {
      throw new IllegalStateException(INIT_ERROR_MESSAGE);
    }
    return response;
  }

  private ServletContext filterServletContext;

  @Override
  public void init(FilterConfig config) throws ServletException {
    filterServletContext = config.getServletContext();
  }

  @Override
  public void doFilter(
      ServletRequest request,
      ServletResponse response,
      FilterChain chain
  ) throws IOException, ServletException {
    if (
        (request instanceof HttpServletRequest)
            && (response instanceof HttpServletResponse)
    ) {
      ServletContext newServletContext = filterServletContext;
      HttpServletRequest newRequest = (HttpServletRequest) request;
      HttpServletResponse newResponse = (HttpServletResponse) response;
      ServletContext oldServletContext = servletContextTL.get();
      HttpServletRequest oldRequest = requestTL.get();
      HttpServletResponse oldResponse = responseTL.get();
      try {
        if (newServletContext != oldServletContext) {
          servletContextTL.set(newServletContext);
        }
        if (newRequest != oldRequest) {
          requestTL.set(newRequest);
        }
        if (newResponse != oldResponse) {
          responseTL.set(newResponse);
        }
        chain.doFilter(request, response);
      } finally {
        if (newServletContext != oldServletContext) {
          if (oldServletContext == null) {
            servletContextTL.remove();
          } else {
            servletContextTL.set(oldServletContext);
          }
        }
        if (newRequest != oldRequest) {
          if (oldRequest == null) {
            requestTL.remove();
          } else {
            requestTL.set(oldRequest);
          }
        }
        if (newResponse != oldResponse) {
          if (oldResponse == null) {
            responseTL.remove();
          } else {
            responseTL.set(oldResponse);
          }
        }
      }
    } else {
      throw new ServletException("Not using HttpServletRequest and HttpServletResponse");
    }
  }

  @Override
  public void destroy() {
    filterServletContext = null;
  }
}
