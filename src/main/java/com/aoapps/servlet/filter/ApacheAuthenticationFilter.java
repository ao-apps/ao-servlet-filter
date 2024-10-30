/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2020, 2021, 2022, 2024  AO Industries, Inc.
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
import com.aoapps.lang.Strings;
import com.aoapps.lang.attribute.Attribute;
import com.aoapps.servlet.ServletContextCache;
import com.aoapps.servlet.attribute.ScopeEE;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * Parses Apache group file for role information while under Apache authentication.
 *
 * <p>When <a href="https://tomcat.apache.org/tomcat-9.0-doc/config/ajp.html"><code>tomcatAuthentication</code></a>
 * is set to <code>false</code>, user information is passed to Tomcat while group membership is not.
 * The filter parses the Apache group file and grants access to the roles corresponding to group names.</p>
 *
 * <p>This should be used for both the {@link DispatcherType#REQUEST} and {@link DispatcherType#ERROR} dispatchers.</p>
 */
public class ApacheAuthenticationFilter implements Filter {

  private ServletContext servletContext;

  private String groupFile;
  private ScopeEE.Request.Attribute<Set<String>> groupsRequestAttribute;

  @Override
  public void init(FilterConfig config) {
    servletContext = config.getServletContext();

    groupFile = Strings.trimNullIfEmpty(config.getInitParameter("groupFile"));
    if (groupFile == null) {
      groupFile = "/WEB-INF/group";
    }
    String groupsRequestAttributeName = Strings.trimNullIfEmpty(config.getInitParameter("groupsRequestAttribute"));
    if (groupsRequestAttributeName == null) {
      groupsRequestAttributeName = ApacheAuthenticationFilter.class.getName() + ".groups";
    }
    groupsRequestAttribute = ScopeEE.REQUEST.attribute(groupsRequestAttributeName);
  }

  private static class CacheLock {
    // Empty lock class to help heap profile
  }

  private final CacheLock cacheLock = new CacheLock();

  private long cacheLastModified;
  private Map<String, Set<String>> cache;

  /**
   * Parses the Apache group file.
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  private Map<String, Set<String>> getUserGroups() throws IOException {
    synchronized (cacheLock) {
      long lastModified = ServletContextCache.getInstance(servletContext).getLastModified(groupFile);
      if (cache == null || lastModified != cacheLastModified) {
        try (InputStream in = servletContext.getResourceAsStream(groupFile)) {
          if (in == null) {
            throw new FileNotFoundException("Resource not found: " + groupFile);
          }
          try (
              BufferedReader reader = new BufferedReader(
                  new InputStreamReader(in, StandardCharsets.UTF_8)
              )
              ) {
            Map<String, Set<String>> parsed = new LinkedHashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
              line = line.trim();
              if (!line.isEmpty() && line.charAt(0) != '#') {
                int pos = line.indexOf(':');
                if (pos != -1) {
                  String group = line.substring(0, pos).trim();
                  Set<String> usernames = new LinkedHashSet<>();
                  for (String username : Strings.split(line.substring(pos + 1).trim(), ' ')) {
                    if (!username.isEmpty()) {
                      if (!usernames.add(username)) {
                        throw new IOException("Duplicate user \"" + username + "\" in group \"" + group + '"');
                      }
                    }
                  }
                  if (parsed.put(group, usernames) != null) {
                    throw new IOException("Duplicate group \"" + group + '"');
                  }
                }
              }
            }
            // Invert
            Map<String, Set<String>> userGroups = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> entry : parsed.entrySet()) {
              String group = entry.getKey();
              for (String username : entry.getValue()) {
                Set<String> groups = userGroups.get(username);
                if (groups == null) {
                  userGroups.put(username, groups = new LinkedHashSet<>());
                }
                groups.add(group);
              }
            }
            // Make unmodifiable
            for (Map.Entry<String, Set<String>> entry : userGroups.entrySet()) {
              entry.setValue(AoCollections.optimalUnmodifiableSet(entry.getValue()));
            }
            cache = AoCollections.optimalUnmodifiableMap(userGroups);
            cacheLastModified = lastModified;
          }
        }
      }
      return cache;
    }
  }

  @Override
  public void doFilter(
      ServletRequest request,
      ServletResponse response,
      FilterChain chain
  ) throws IOException, ServletException {
    if (request instanceof HttpServletRequest) {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      Principal userPrincipal = httpRequest.getUserPrincipal();
      final Set<String> groups;
      if (userPrincipal != null) {
        String user = userPrincipal.getName();
        // servletContext.log("ApacheAuthenticationFilter: user = " + user);
        Set<String> parsed = getUserGroups().get(user);
        // servletContext.log("ApacheAuthenticationFilter: parsed = " + parsed);
        if (parsed != null) {
          groups = parsed;
        } else {
          // No groups
          groups = Collections.emptySet();
        }
      } else {
        // Not logged-in
        // servletContext.log("ApacheAuthenticationFilter: No user principal");
        groups = Collections.emptySet();
      }
      try (Attribute.OldValue oldAttribute = groupsRequestAttribute.context(request).init(groups)) {
        chain.doFilter(
            new HttpServletRequestWrapper(httpRequest) {
              @Override
              public boolean isUserInRole(String role) {
                return groups.contains(role);
              }
            },
            response
        );
      }
    } else {
      // Non-HTTP, no user information available
      try (Attribute.OldValue oldAttribute = groupsRequestAttribute.context(request).init(Collections.emptySet())) {
        chain.doFilter(request, response);
      }
    }
  }

  @Override
  public void destroy() {
    servletContext = null;
    groupFile = null;
    groupsRequestAttribute = null;
    cacheLastModified = 0;
    cache = null;
  }
}
