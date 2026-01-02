/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2007, 2008, 2009, 2010, 2011, 2015, 2016, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;

/**
 * Sets the request encoding to {@link StandardCharsets#UTF_8} when encoding not provided by the client.
 *
 * @author  AO Industries, Inc.
 */
@WebListener("Sets request character encoding to UTF-8 when not provided by client")
public class Utf8RequestCharacterEncodingListener implements ServletRequestListener {

  @Override
  public void requestInitialized(ServletRequestEvent event) {
    ServletRequest request = event.getServletRequest();
    // Only override encoding when not provided by the client
    if (request.getCharacterEncoding() == null) {
      try {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
        throw new AssertionError("Standard encoding (" + StandardCharsets.UTF_8 + ") should always exist", e);
      }
    }
  }

  @Override
  public void requestDestroyed(ServletRequestEvent event) {
    // Do nothing
  }
}
