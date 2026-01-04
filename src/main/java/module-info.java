/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2021, 2022, 2023, 2025, 2026  AO Industries, Inc.
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
module com.aoapps.servlet.filter {
  exports com.aoapps.servlet.filter;
  // Direct
  requires com.aoapps.collections; // <groupId>com.aoapps</groupId><artifactId>ao-collections</artifactId>
  requires com.aoapps.encoding; // <groupId>com.aoapps</groupId><artifactId>ao-encoding</artifactId>
  requires com.aoapps.encoding.servlet; // <groupId>com.aoapps</groupId><artifactId>ao-encoding-servlet</artifactId>
  requires com.aoapps.hodgepodge; // <groupId>com.aoapps</groupId><artifactId>ao-hodgepodge</artifactId>
  requires com.aoapps.lang; // <groupId>com.aoapps</groupId><artifactId>ao-lang</artifactId>
  requires com.aoapps.net.types; // <groupId>com.aoapps</groupId><artifactId>ao-net-types</artifactId>
  requires com.aoapps.servlet.util; // <groupId>com.aoapps</groupId><artifactId>ao-servlet-util</artifactId>
  requires jakarta.servlet; // <groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId>
  // Java SE
  requires java.logging;
}
