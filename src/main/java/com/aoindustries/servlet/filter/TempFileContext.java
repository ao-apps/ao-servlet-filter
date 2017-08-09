/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2013, 2015, 2016, 2017  AO Industries, Inc.
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

import com.aoindustries.io.TempFileList;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Automatically deletes temp files used during processing the request.
 * This should be used fairly early in the filter chain.  Since it has no other
 * affects on the request, it could be first.
 * <p>
 * Example web.xml contents:
 * </p>
 * <pre>
 * &lt;!-- Cleans-up temp files created during request processing. --&gt;
 * &lt;filter&gt;
 *     &lt;filter-name&gt;TempFileContext&lt;/filter-name&gt;
 *     &lt;filter-class&gt;com.aoindustries.servlet.filter.TempFileContext&lt;/filter-class&gt;
 * &lt;/filter&gt;
 * ...
 * &lt;filter-mapping&gt;
 *     &lt;filter-name&gt;TempFileContext&lt;/filter-name&gt;
 *     &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *     &lt;dispatcher&gt;REQUEST&lt;/dispatcher&gt;
 *     &lt;dispatcher&gt;ERROR&lt;/dispatcher&gt;
 * &lt;/filter-mapping&gt;
 * </pre>
 *
 * @deprecated  Please use <a href="https://aoindustries.com/ao-tempfiles/servlet/apidocs/com/aoindustries/tempfiles/servlet/ServletTempFileContext.html">ServletTempFileContext</a>
 *              that does not require <code>web.xml</code> configuration, supports additional scopes (application and session),
 *              and registers a shutdown hook to delete on JVM exit.
 */
@Deprecated
public class TempFileContext implements Filter {

	private static final Logger logger = Logger.getLogger(TempFileContext.class.getName());

	private static final String PREFIX = TempFileContext.class.getSimpleName();

	private static final String REQUEST_ATTRIBUTE_NAME = TempFileContext.class.getName()+".tempFileList";

	/**
	 * Gets the temp file list for the current request context or <code>null</code> if filter not active.
	 */
	public static TempFileList getTempFileList(ServletRequest request) {
		return (TempFileList)request.getAttribute(REQUEST_ATTRIBUTE_NAME);
	}

	// Java 1.8: @FunctionalInterface
	public static interface Wrapper<T> {
		T call(T original, TempFileList tempFileList);
	}

	private static class TempFileWarningLock {}
	private static final TempFileWarningLock tempFileWarningLock = new TempFileWarningLock();
	private static boolean tempFileWarned = false;

	/**
	 * If the TempFileContext is enabled, wraps the original object.
	 * When the context is inactive, the original object is returned unaltered.
	 * This is logged as a warning the first time not wrapped.
	 */
	public static <T> T wrapTempFileList(T original, TempFileList tempFileList, Wrapper<T> wrapper) {
		if(tempFileList != null) {
			return wrapper.call(original, tempFileList);
		} else {
			// Warn once
			synchronized(tempFileWarningLock) {
				if(!tempFileWarned) {
					if(logger.isLoggable(Level.WARNING)) {
						logger.log(
							Level.WARNING,
							"TempFileContext not initialized: refusing to automatically create temp files for large buffers.  "
							+ "Additional heap space may be used for large requests.  "
							+ "Please add the " + TempFileContext.class.getName() + " filter to your web.xml file.",
							new Throwable("Stack Trace")
						);
					}
					tempFileWarned = true;
				}
			}
			return original;
		}
	}

	/**
	 * @see  #getTempFileList(javax.servlet.ServletRequest)
	 * @see  #wrapTempFileList(java.lang.Object, com.aoindustries.io.TempFileList, com.aoindustries.servlet.filter.TempFileContext.Wrapper)
	 */
	public static <T> T wrapTempFileList(T original, ServletRequest request, Wrapper<T> wrapper) {
		return wrapTempFileList(original, getTempFileList(request), wrapper);
	}

	@Override
	public void init(FilterConfig config) {
	}

	@Override
	public void doFilter(
		ServletRequest request,
		ServletResponse response,
		FilterChain chain
	) throws IOException, ServletException {
		TempFileList list = (TempFileList)request.getAttribute(REQUEST_ATTRIBUTE_NAME);
		if(list==null) {
			// Make new list and delete when done
			list = new TempFileList(PREFIX);
			try {
				request.setAttribute(REQUEST_ATTRIBUTE_NAME, list);
				chain.doFilter(request, response);
			} finally {
				request.removeAttribute(REQUEST_ATTRIBUTE_NAME);
				list.delete();
			}
		} else {
			// List exists: nested call, no special action
			chain.doFilter(request, response);
		}
	}

	@Override
	public void destroy() {
	}
}
