/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2016, 2021  AO Industries, Inc.
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

import com.aoapps.lang.concurrent.ThreadLocalsRunnable;

/**
 * Maintains current function context for the provided runnable.
 */
public class FunctionContextRunnable extends ThreadLocalsRunnable {

	public FunctionContextRunnable(Runnable task) {
		super(task, FunctionContextCallable.threadLocals);
	}
}
