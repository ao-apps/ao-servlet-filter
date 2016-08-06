/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2016  AO Industries, Inc.
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

import com.aoindustries.net.ServletRequestParameters;
import com.aoindustries.servlet.http.ServletUtil;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>
 * Strips all invalid XML characters on incoming parameters.
 * GET requests will be 301-redirected to the same URL without the invalid XML characters.
 * All other methods, including POST requests, will have the invalid XML characters stripped.
 * </p>
 * <p>
 * This implementation supports UTF-16 surrogate pairs:
 * <a href="https://en.wikipedia.org/wiki/Universal_Character_Set_characters#Surrogates">https://en.wikipedia.org/wiki/Universal_Character_Set_characters#Surrogates</a>
 * </p>
 * <p>
 * The allowed characters defined by the specification at <a href="https://www.w3.org/TR/xml/#charsets">https://www.w3.org/TR/xml/#charsets</a>:
 * </p>
 * <pre>Char	   ::=   	#x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]</pre>
 * <p>
 * Only HTTP/HTTPS requests are filtered.
 * </p>
 */
public class StripInvalidXmlCharactersFilter implements Filter {

	/**
	 * <pre>Char	   ::=   	#x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]</pre>
	 */
	private static boolean isValidCharacter(int ch) {
		return
			(ch >= 0x20 && ch <= 0xD7FF) // Most common condition first
			|| ch == 0x9
			|| ch == 0xA
			|| ch == 0xD
			|| (ch >= 0xE000 && ch <= 0xFFFD)
			|| (ch >= 0x10000 && ch <= 0x10FFFF)
		;
	}

	private static boolean isValid(String s) {
		int len = s.length();
		int pos = 0;
		while(pos < len) {
			char ch1 = s.charAt(pos++);
			int ch;
			if(Character.isHighSurrogate(ch1)) {
				// Handle surrogates
				if(pos < len) {
					char ch2 = s.charAt(pos++);
					if(Character.isLowSurrogate(ch2)) {
						ch = Character.toCodePoint(ch1, ch2);
					} else {
						// High surrogate not followed by low surrogate, invalid
						return false;
					}
				} else {
					// High surrogate at end of string, invalid
					return false;
				}
			} else {
				// Not surrogates
				ch = ch1;
			}
			// Check is valid
			if(!isValidCharacter(ch)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Filters invalid XML characters.
	 */
	private static String filter(String s) {
		int len = s.length();
		StringBuilder filtered = new StringBuilder(len);
		int pos = 0;
		while(pos < len) {
			char ch1 = s.charAt(pos++);
			if(Character.isHighSurrogate(ch1)) {
				// Handle surrogates
				if(pos < len) {
					char ch2 = s.charAt(pos++);
					if(Character.isLowSurrogate(ch2)) {
						if(isValidCharacter(Character.toCodePoint(ch1, ch2))) {
							filtered.append(ch1).append(ch2);
						}
					} else {
						// High surrogate not followed by low surrogate, invalid
					}
				} else {
					// High surrogate at end of string, invalid
				}
			} else {
				// Not surrogates
				if(isValidCharacter(ch1)) {
					filtered.append(ch1);
				}
			}
		}
		assert filtered.length() <= len;
		return filtered.length() != len ? filtered.toString() : s;
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
		if(
			(request instanceof HttpServletRequest)
			&& (response instanceof HttpServletResponse)
		) {
			HttpServletRequest httpRequest = (HttpServletRequest)request;
			Map<String,List<String>> paramMap = new ServletRequestParameters(request).getParameterMap();
			boolean isValid = true;
			for(Map.Entry<String,List<String>> entry : paramMap.entrySet()) {
				for(String paramValue : entry.getValue()) {
					if(!isValid(paramValue)) {
						isValid = false;
						break;
					}
				}
			}
			if(!isValid) {
				HttpServletResponse httpResponse = (HttpServletResponse)response;
				String responseEncoding = response.getCharacterEncoding();
				if("GET".equals(httpRequest.getMethod())) {
					// Redirect to same request but with special characters removed
					StringBuilder url = new StringBuilder();
					ServletUtil.getAbsoluteURL(
						httpRequest,
						ServletUtil.getContextRequestUri(httpRequest),
						url
					);
					// Add any parameters
					boolean didOne = false;
					for(Map.Entry<String,List<String>> entry : paramMap.entrySet()) {
						String name = entry.getKey();
						for(String value : entry.getValue()) {
							if(didOne) {
								url.append('&');
							} else {
								url.append('?');
								didOne = true;
							}
							url
								.append(URLEncoder.encode(name, responseEncoding))
								.append('=')
								.append(URLEncoder.encode(filter(value), responseEncoding))
							;
						}
					}
					ServletUtil.sendRedirect(httpResponse, url.toString(), HttpServletResponse.SC_MOVED_PERMANENTLY);
				} else {
					// Filter invalid characters
					final Map<String,List<String>> filteredMap = new LinkedHashMap<>(paramMap.size()*4/3+1);
					for(Map.Entry<String,List<String>> entry : paramMap.entrySet()) {
						List<String> values = entry.getValue();
						List<String> filteredValues = new ArrayList<>(values.size());
						for(String value : values) {
							filteredValues.add(filter(value));
						}
						filteredMap.put(entry.getKey(), filteredValues);
					}
					// Dispatch with filtered values
					chain.doFilter(
						new HttpServletRequestWrapper(httpRequest) {
							@Override
							public String getParameter(String name) {
								List<String> values = filteredMap.get(name);
								if(values==null || values.isEmpty()) return null;
								return values.get(0);
							}

							@Override
							public Map<String, String[]> getParameterMap() {
								Map<String,String[]> newMap = new LinkedHashMap<>(filteredMap.size()*4/3+1);
								for(Map.Entry<String,List<String>> entry : filteredMap.entrySet()) {
									List<String> values = entry.getValue();
									newMap.put(entry.getKey(), values.toArray(new String[values.size()]));
								}
								return newMap;
							}

							@Override
							public Enumeration<String> getParameterNames() {
								return Collections.enumeration(filteredMap.keySet());
							}

							@Override
							public String[] getParameterValues(String name) {
								List<String> values = filteredMap.get(name);
								if(values==null) return null;
								return values.toArray(new String[values.size()]);
							}
						},
						httpResponse
					);
				}
			} else {
				chain.doFilter(request, response);
			}
		} else {
			// Not HTTP/HTTPS, do no filtering
			chain.doFilter(request, response);
		}
    }

    @Override
    public void destroy() {
    }
}
