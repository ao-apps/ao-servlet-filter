/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2016, 2017, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.servlet.attribute.ScopeEE;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;

/**
 * Tracks the request concurrency, used to decide to use concurrent or sequential implementations.
 */
@WebListener
// TODO: Rename ConcurrencyCounter or RequestConcurrency?
public class CountConcurrencyListener implements ServletRequestListener {

  public static final ScopeEE.Request.Attribute<Integer> REQUEST_ATTRIBUTE =
      ScopeEE.REQUEST.attribute(CountConcurrencyListener.class.getName() + ".concurrency");

  /**
   * Gets the concurrency at the beginning of the request or {@code null} when listener not active.
   */
  public static Integer getConcurrency(ServletRequest request) {
    return REQUEST_ATTRIBUTE.context(request).get();
  }

  private final AtomicInteger concurrency = new AtomicInteger();

  @Override
  public void requestInitialized(ServletRequestEvent event) {
    // Increase concurrency
    int newConcurrency = concurrency.incrementAndGet();
    if (newConcurrency < 1) {
      throw new IllegalStateException("Concurrency < 1: " + newConcurrency);
    }
    REQUEST_ATTRIBUTE.context(event.getServletRequest()).set(newConcurrency);
  }

  @Override
  public void requestDestroyed(ServletRequestEvent event) {
    // Decrease concurrency
    REQUEST_ATTRIBUTE.context(event.getServletRequest()).remove();
    int oldConcurrency = concurrency.getAndDecrement();
    if (oldConcurrency < 1) {
      throw new IllegalStateException("Concurrency < 1: " + oldConcurrency);
    }
  }
}
