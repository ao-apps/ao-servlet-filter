/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2017, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.hodgepodge.util.WildcardPatternMatcher;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Adds headers to the response of any filter-mapping that is sent to this filter.
 * Headers are added before the filter chain is called.
 * <p>
 * Due to the limitations of filter mapping URLs, patterns may be provided and
 * handled by this filter itself.  All patterns and regular expressions will be
 * evaluated and any match is considered a match overall.  There is currently no
 * support for negation.
 * </p>
 * <p>
 * Each header name that does not specify a pattern is taken directly from the filter init
 * parameters.
 * </p>
 * <p>
 * This should be used for the {@link DispatcherType#REQUEST} dispatcher only.
 * </p>
 * <pre>
 * Init Parameters:
 *    allowMultiple (Optional) Allow multiple headers of the same name?  Defaults to "true".
 *    Request filter:
 *        patterns  (Optional) Comma/space-separated list of patterns (default to *)
 *        regex     (Optional) zeroth regular expression to match
 *        regex.1   (Optional) first regular expression to match
 *        regex.2   (Optional) second regular expression to match
 *        regex.n   (Optional) nth regular expression to match
 *    TODO: Support Additional per-header filters?
 *        name.patterns  (Optional) Comma/space-separated list of patterns (default to *)
 *        name.regex     (Optional) zeroth regular expression to match
 *        name.regex.1   (Optional) first regular expression to match
 *        name.regex.2   (Optional) second regular expression to match
 *        name.regex.n   (Optional) nth regular expression to match
 *    Header:
 *        name=value
 * </pre>
 *
 * @see  WildcardPatternMatcher  for supported patterns
*/
public class AddResponseHeaderFilter implements Filter {

  private static final String ALLOW_MULTIPLE_PARAM_NAME = "allowMultiple";

  private static final String PATTERNS_PARAM_NAME = "patterns";

  private static final String REGEX_PARAM_NAME = "regex";

  private static final String REGEX_PARAM_PREFIX = REGEX_PARAM_NAME + '.';

  private boolean allowMultiples;

  private WildcardPatternMatcher patterns;

  private List<Pattern> regexs;

  private Map<String, String> headers;

  @Override
  public void init(FilterConfig config) {
    // Parse allowMultiple
    String allowMultiplesParam = config.getInitParameter(ALLOW_MULTIPLE_PARAM_NAME);
    if (allowMultiplesParam != null) {
      allowMultiplesParam = allowMultiplesParam.trim();
    }
    allowMultiples = !"false".equalsIgnoreCase(allowMultiplesParam);

    // Parse patterns
    String patternsParam = config.getInitParameter(PATTERNS_PARAM_NAME);
    if (patternsParam == null) {
      patterns = WildcardPatternMatcher.matchAll();
    } else {
      patterns = WildcardPatternMatcher.compile(patternsParam);
    }

    // Find and sort any regular expressions
    {
      SortedMap<Integer, Pattern> regexsByNum = new TreeMap<>();
      Enumeration<String> paramNames = config.getInitParameterNames();
      while (paramNames.hasMoreElements()) {
        String paramName = paramNames.nextElement();
        Integer number;
        if (REGEX_PARAM_NAME.equals(paramName)) {
          number = 0;
        } else if (paramName.startsWith(REGEX_PARAM_PREFIX)) {
          number = Integer.valueOf(paramName.substring(REGEX_PARAM_PREFIX.length()));
        } else {
          continue;
        }
        if (
          regexsByNum.put(
            number,
            Pattern.compile(config.getInitParameter(paramName))
          ) != null
        ) {
          throw new IllegalArgumentException("Duplicate " + REGEX_PARAM_NAME + " parameter number: " + paramName);
        }
      }
      if (regexsByNum.isEmpty()) {
        regexs = Collections.emptyList();
      } else if (regexsByNum.size() == 1) {
        regexs = Collections.singletonList(regexsByNum.values().iterator().next());
      } else {
        regexs = new ArrayList<>(regexsByNum.values());
      }
    }

    // Find all headers
    {
      Map<String, String> foundHeaders = new LinkedHashMap<>();
      Enumeration<String> paramNames = config.getInitParameterNames();
      while (paramNames.hasMoreElements()) {
        String paramName = paramNames.nextElement();
        if (
          !ALLOW_MULTIPLE_PARAM_NAME.equals(paramName)
          && !PATTERNS_PARAM_NAME.equals(paramName)
          && !REGEX_PARAM_NAME.equals(paramName)
          && !paramName.startsWith(REGEX_PARAM_PREFIX)
        ) {
          if (foundHeaders.put(paramName, config.getInitParameter(paramName)) != null) {
            throw new AssertionError("Duplicate init parameter: " + paramName);
          }
        }
      }
      if (foundHeaders.isEmpty()) {
        // Shortcut for empty headers
        headers = Collections.emptyMap();
      } else if (foundHeaders.size() == 1) {
        // Use singleton map for common case of single header
        Map.Entry<String, String> header = foundHeaders.entrySet().iterator().next();
        headers = Collections.singletonMap(header.getKey(), header.getValue());
      } else {
        headers = foundHeaders;
      }
    }
  }

  @Override
  public void doFilter(
    ServletRequest request,
    ServletResponse response,
    FilterChain chain
  ) throws IOException, ServletException {
    if (
      // Short-cut no headers
      !headers.isEmpty()
      // Must be HTTP request
      && (request instanceof HttpServletRequest)
      && (response instanceof HttpServletResponse)
    ) {
      HttpServletRequest httpRequest = (HttpServletRequest)request;
      // Fast patterns first
      String servletPath = httpRequest.getServletPath();
      boolean matched = patterns.isMatch(servletPath);
      if (!matched) {
        // Slow regular expressions second
        for (Pattern regex : regexs) {
          if (regex.matcher(servletPath).matches()) {
            matched = true;
            break;
          }
        }
      }
      if (matched) {
        HttpServletResponse httpResponse = (HttpServletResponse)response;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          String name = entry.getKey();
          if (allowMultiples || !httpResponse.containsHeader(name)) {
            httpResponse.addHeader(name, entry.getValue());
          }
        }
      }
    }
    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {
    // Do nothing
  }
}
