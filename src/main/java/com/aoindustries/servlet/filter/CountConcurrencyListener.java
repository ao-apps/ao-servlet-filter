/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2016, 2017, 2019  AO Industries, Inc.
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

/**
 * Tracks the request concurrency, used to decide to use concurrent or sequential implementations.
 */
public class CountConcurrencyListener implements ServletRequestListener {

	private static final String REQUEST_ATTRIBUTE_NAME = CountConcurrencyListener.class.getName()+".concurrency";

	/**
	 * Gets the concurrency at the beginning of the request or <code>null</code> if filter not active.
	 */
	public static Integer getConcurrency(ServletRequest request) {
		return (Integer)request.getAttribute(REQUEST_ATTRIBUTE_NAME);
	}

	private final AtomicInteger concurrency = new AtomicInteger();

	@Override
	public void requestInitialized(ServletRequestEvent sre) {
		ServletRequest request = sre.getServletRequest();
		// Increase concurrency
		int newConcurrency = concurrency.incrementAndGet();
		assert newConcurrency >= 1;
		request.setAttribute(REQUEST_ATTRIBUTE_NAME, newConcurrency);
		onConcurrencySet(request, newConcurrency);
	}

	@Override
	public void requestDestroyed(ServletRequestEvent sre) {
		ServletRequest request = sre.getServletRequest();
		onConcurrencyRemove(request);
		request.removeAttribute(REQUEST_ATTRIBUTE_NAME);
		concurrency.getAndDecrement();
	}

	/**
	 * Called just after the concurrency of this request is set.
	 *
	 * Empty method, overriding methods do not need to call this method via {@code super}.
	 */
	protected void onConcurrencySet(ServletRequest request, int newConcurrency) {
		// Do nothing
	}

	/**
	 * Called just before the concurrency of this request is removed.
	 *
	 * Empty method, overriding methods do not need to call this method via {@code super}.
	 */
	protected void onConcurrencyRemove(ServletRequest request) {
		// Do nothing
	}
}
