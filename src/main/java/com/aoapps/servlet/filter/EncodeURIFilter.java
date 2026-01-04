/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2019, 2020, 2021, 2022, 2024, 2025, 2026  AO Industries, Inc.
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

import com.aoapps.encoding.Doctype;
import com.aoapps.encoding.servlet.DoctypeEE;
import com.aoapps.net.IRI;
import com.aoapps.net.URI;
import com.aoapps.net.URIParser;
import com.aoapps.servlet.attribute.AttributeEE;
import com.aoapps.servlet.attribute.ScopeEE;
import com.aoapps.servlet.http.Canonical;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Encodes the URL to either
 * <a href="https://datatracker.ietf.org/doc/html/rfc3986">RFC 3986 URI</a> US-ASCII format
 * or <a href="https://datatracker.ietf.org/doc/html/rfc3987">RFC 3987 IRI</a> Unicode format.
 * If the URL begins with <code>javascript:</code>, <code>cid:</code>, or <code>data:</code>,
 * (case-insensitive) it is not altered.
 * {@linkplain Canonical Canonical URLs} are always encoded to US-ASCII format.
 *
 * <p>IRI support is disabled by default, and only recommended for development or
 * internal systems.  IRI may result in slightly smaller output, and more readable
 * HTML source, but for interoperability should be off in production systems.</p>
 *
 * <p><a href="https://datatracker.ietf.org/doc/html/rfc7231#section-7.1.2">RFC 7231 - 7.1.2.  Location</a>
 * refers only to <a href="https://datatracker.ietf.org/doc/html/rfc3986">RFC 3986 URI</a> for
 * URI-reference, thus redirects are always formatted as
 * <a href="https://datatracker.ietf.org/doc/html/rfc3986">RFC 3986 URI</a>.</p>
 *
 * <p>This should be used for both the {@link DispatcherType#REQUEST} and {@link DispatcherType#ERROR} dispatchers.</p>
 */
public class EncodeURIFilter implements Filter {

  private static final ScopeEE.Request.Attribute<EncodeURIFilter> REQUEST_ATTRIBUTE =
      ScopeEE.REQUEST.attribute(EncodeURIFilter.class.getName());

  /**
   * Gets the filter active on the given request.
   *
   * @return  The currently active filter or {@code null} for none active.
   */
  public static EncodeURIFilter getActiveFilter(ServletRequest request) {
    return REQUEST_ATTRIBUTE.context(request).get();
  }

  private ServletContext servletContext;
  private boolean enableIRI;

  @Override
  public void init(FilterConfig config) {
    servletContext = config.getServletContext();
    enableIRI = Boolean.parseBoolean(servletContext.getInitParameter(EncodeURIFilter.class.getName() + ".enableIRI"));
  }

  private static String encode(String url, boolean enableIri, String characterEncoding) {
    if (
        // Do nothing on these types of URI
        URIParser.isScheme(url, "javascript")
            || URIParser.isScheme(url, "cid")
            || URIParser.isScheme(url, "data")
    ) {
      return url;
    } else {
      if (
          enableIri
              && !Canonical.get()
              && (
              characterEncoding.equalsIgnoreCase(StandardCharsets.UTF_8.name())
                  || Charset.forName(characterEncoding) == StandardCharsets.UTF_8
            )
      ) {
        return new IRI(url).toString();
      } else {
        return new URI(url).toASCIIString();
      }
    }
  }

  /**
   * Performs encoding on the given URL in the given response encoding.
   */
  public String encode(String url, Doctype doctype, String characterEncoding) {
    return encode(
        url,
        enableIRI && doctype.getSupportsIRI(),
        characterEncoding
    );
  }

  /**
   * Performs encoding on the given URL in the given response encoding.
   *
   * @deprecated  Please provide the current {@link Doctype} so can be enabled
   *              selectively via {@link Doctype#getSupportsIRI()}.
   */
  @Deprecated
  public String encode(String url, String characterEncoding) {
    return encode(url, enableIRI, characterEncoding);
  }

  @Override
  public void doFilter(
      ServletRequest request,
      ServletResponse response,
      FilterChain chain
  ) throws IOException, ServletException {
    // Makes sure only one filter is applied per request
    AttributeEE.Request<EncodeURIFilter> attribute = REQUEST_ATTRIBUTE.context(request);
    if (
        attribute.get() == null
            && (response instanceof HttpServletResponse)
    ) {
      attribute.set(this);
      try {
        chain.doFilter(
            request,
            new HttpServletResponseWrapper((HttpServletResponse) response) {
              @Override
              public String encodeRedirectURL(String url) {
                return encode(
                    super.encodeRedirectURL(url),
                    false,
                    null // characterEncoding not used with enableIri = false
                );
              }

              @Override
              public String encodeURL(String url) {
                boolean enableIri = EncodeURIFilter.this.enableIRI && DoctypeEE.get(servletContext, request).getSupportsIRI();
                return encode(
                    super.encodeURL(url),
                    enableIri,
                    enableIri ? getCharacterEncoding() : null
                );
              }
            }
        );
      } finally {
        attribute.remove();
      }
    } else {
      chain.doFilter(request, response);
    }
  }

  @Override
  public void destroy() {
    // Nothing to do
  }
}
