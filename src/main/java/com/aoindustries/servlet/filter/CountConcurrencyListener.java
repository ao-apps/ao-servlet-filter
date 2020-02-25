/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2016, 2017, 2019, 2020  AO Industries, Inc.
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

import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;

/**
 * Tracks the request concurrency, used to decide to use concurrent or sequential implementations.
 */
@WebListener
public class CountConcurrencyListener implements ServletRequestListener {

	public static final String REQUEST_ATTRIBUTE_NAME = CountConcurrencyListener.class.getName() + ".concurrency";

	/**
	 * Gets the concurrency at the beginning of the request or {@code null} when listener not active.
	 */
	public static Integer getConcurrency(ServletRequest request) {
		return (Integer)request.getAttribute(REQUEST_ATTRIBUTE_NAME);
	}

	private final AtomicInteger concurrency = new AtomicInteger();

	@Override
	public void requestInitialized(ServletRequestEvent event) {
		// Increase concurrency
		int newConcurrency = concurrency.incrementAndGet();
		if(newConcurrency < 1) throw new IllegalStateException("Concurrency < 1: " + newConcurrency);
		event.getServletRequest().setAttribute(REQUEST_ATTRIBUTE_NAME, newConcurrency);
	}

	@Override
	public void requestDestroyed(ServletRequestEvent event) {
		// Decrease concurrency
		event.getServletRequest().removeAttribute(REQUEST_ATTRIBUTE_NAME);
		int oldConcurrency = concurrency.getAndDecrement();
		if(oldConcurrency < 1) throw new IllegalStateException("Concurrency < 1: " + oldConcurrency);
	}
}
