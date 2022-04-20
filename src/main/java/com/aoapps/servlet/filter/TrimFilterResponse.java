/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2006, 2007, 2008, 2009, 2010, 2011, 2013, 2016, 2021, 2022  AO Industries, Inc.
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

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Filters the output and removes extra white space at the beginning of lines and completely removes blank lines.
 * TEXTAREAs are automatically detected as long as they start with exact "&lt;textarea" and end with exactly "&lt;/textarea" (case insensitive).
 * PREs are automatically detected as long as they start with exact "&lt;pre" and end with exactly "&lt;/pre" (case insensitive).
 * The reason for the specific tag format is to simplify the implementation
 * for maximum performance.
 * 
 * @author  AO Industries, Inc.
 */
public class TrimFilterResponse extends HttpServletResponseWrapper {

  private TrimFilterWriter writer;
  private TrimFilterOutputStream outputStream;

  public TrimFilterResponse(HttpServletResponse response) {
    super(response);
  }

  @Override
  public void reset() {
    getResponse().reset();
    if (writer != null) {
      writer.inTextArea = false;
      writer.inPre = false;
    }
    if (outputStream != null) {
      outputStream.inTextArea = false;
      outputStream.inPre = false;
    }
  }

  @Override
  public void resetBuffer() {
    getResponse().resetBuffer();
    if (writer != null) {
      writer.inTextArea = false;
      writer.inPre = false;
    }
    if (outputStream != null) {
      outputStream.inTextArea = false;
      outputStream.inPre = false;
    }
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    if (writer == null) {
      ServletResponse response = getResponse();
      writer = new TrimFilterWriter(response.getWriter(), response);
    }
    return writer;
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    if (outputStream == null) {
      ServletResponse response = getResponse();
      outputStream = new TrimFilterOutputStream(response.getOutputStream(), response);
    }
    return outputStream;
  }
}
