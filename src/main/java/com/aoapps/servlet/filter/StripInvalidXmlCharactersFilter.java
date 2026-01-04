/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2016, 2019, 2020, 2021, 2022, 2024, 2025, 2026  AO Industries, Inc.
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

import com.aoapps.collections.AoCollections;
import com.aoapps.net.URIEncoder;
import com.aoapps.servlet.ServletRequestParameters;
import com.aoapps.servlet.http.HttpServletUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * Strips all invalid XML characters on incoming parameters.
 * GET requests on the {@link DispatcherType#REQUEST} dispatcher will be 301-redirected to the same URL without the invalid XML characters.
 * All other methods and all other dispatchers, including POST requests, will have the invalid XML characters stripped.
 *
 * <p>Parameters with invalid names are removed.</p>
 *
 * <p>This implementation supports UTF-16 surrogate pairs:
 * <a href="https://wikipedia.org/wiki/Universal_Character_Set_characters#Surrogates">https://wikipedia.org/wiki/Universal_Character_Set_characters#Surrogates</a></p>
 *
 * <p>The allowed characters defined by the specification at <a href="https://www.w3.org/TR/xml/#charsets">https://www.w3.org/TR/xml/#charsets</a>:</p>
 *
 * <pre>Char   ::=   #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]</pre>
 *
 * <p>Only HTTP/HTTPS requests are filtered.</p>
 *
 * <p>This should be used for both the {@link DispatcherType#REQUEST} and {@link DispatcherType#ERROR} dispatchers.</p>
 */
public class StripInvalidXmlCharactersFilter implements Filter {

  /**
   * <pre>Char   ::=   #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]</pre>.
   */
  private static boolean isValidCharacter(int codePoint) {
    return
        (codePoint >= 0x20 && codePoint <= 0xD7FF) // Most common condition first
            || codePoint == 0x9
            || codePoint == 0xA
            || codePoint == 0xD
            || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
            || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
  }

  private static boolean isValid(String s) {
    for (int i = 0, len = s.length(), codePoint; i < len; i += Character.charCount(codePoint)) {
      codePoint = s.codePointAt(i);
      // Check is valid
      if (!isValidCharacter(codePoint)) {
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
    for (int i = 0, codePoint; i < len; i += Character.charCount(codePoint)) {
      codePoint = s.codePointAt(i);
      // Check is valid
      if (isValidCharacter(codePoint)) {
        filtered.appendCodePoint(codePoint);
      }
    }
    assert filtered.length() <= len;
    if (filtered.length() == len) {
      assert filtered.toString().equals(s);
      return s;
    } else {
      return filtered.toString();
    }
  }

  @Override
  public void init(FilterConfig config) {
    // Do nothing
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
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      Map<String, List<String>> paramMap = new ServletRequestParameters(request).getParameterMap();
      boolean isValid = true;
      for (Map.Entry<String, List<String>> entry : paramMap.entrySet()) {
        if (!isValid(entry.getKey())) {
          isValid = false;
          break;
        }
        for (String paramValue : entry.getValue()) {
          if (!isValid(paramValue)) {
            isValid = false;
            break;
          }
        }
      }
      if (!isValid) {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        if (
            httpRequest.getDispatcherType() == DispatcherType.REQUEST
                && HttpServletUtil.METHOD_GET.equals(httpRequest.getMethod())
        ) {
          // Redirect to same request but with invalid parameters and special characters removed
          StringBuilder url = new StringBuilder();
          HttpServletUtil.getAbsoluteURL(
              httpRequest,
              false,
              httpRequest.getRequestURI(),
              url
          );
          // Add any parameters
          boolean didOne = false;
          for (Map.Entry<String, List<String>> entry : paramMap.entrySet()) {
            String name = entry.getKey();
            if (isValid(name)) {
              for (String value : entry.getValue()) {
                if (didOne) {
                  url.append('&');
                } else {
                  url.append('?');
                  didOne = true;
                }
                URIEncoder.encodeURIComponent(name, url);
                url.append('=');
                URIEncoder.encodeURIComponent(filter(value), url);
              }
            }
          }
          HttpServletUtil.sendRedirect(HttpServletResponse.SC_MOVED_PERMANENTLY, httpResponse, url.toString());
        } else {
          // Filter invalid parameters and characters
          final Map<String, List<String>> filteredMap = AoCollections.newLinkedHashMap(paramMap.size());
          for (Map.Entry<String, List<String>> entry : paramMap.entrySet()) {
            String name = entry.getKey();
            if (isValid(name)) {
              List<String> values = entry.getValue();
              List<String> filteredValues = new ArrayList<>(values.size());
              for (String value : values) {
                filteredValues.add(filter(value));
              }
              filteredMap.put(name, filteredValues);
            }
          }
          // Dispatch with filtered values
          chain.doFilter(
              new HttpServletRequestWrapper(httpRequest) {
                @Override
                public String getParameter(String name) {
                  List<String> values = filteredMap.get(name);
                  if (values == null || values.isEmpty()) {
                    return null;
                  }
                  return values.get(0);
                }

                @Override
                public Map<String, String[]> getParameterMap() {
                  Map<String, String[]> newMap = AoCollections.newLinkedHashMap(filteredMap.size());
                  for (Map.Entry<String, List<String>> entry : filteredMap.entrySet()) {
                    List<String> values = entry.getValue();
                    newMap.put(entry.getKey(), values.toArray(String[]::new));
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
                  if (values == null) {
                    return null;
                  }
                  return values.toArray(String[]::new);
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
    // Do nothing
  }
}
