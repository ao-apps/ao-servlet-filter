/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2006, 2007, 2008, 2009, 2010, 2011, 2013, 2016, 2020, 2021  AO Industries, Inc.
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
package com.aoapps.servlet.filter;

import com.aoapps.lang.io.ContentType;
import com.aoapps.lang.util.BufferManager;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Locale;
import javax.servlet.ServletResponse;

/**
 * Filters the output and removes extra white space at the beginning of lines and completely removes blank lines.
 * TEXTAREAs are automatically detected as long as they start with exact "&lt;textarea" and end with exactly "&lt;/textarea" (case insensitive).
 * The reason for the specific tag format is to simplify the implementation
 * for maximum performance.  Careful attention has been paid to minimize the internal buffering in this class.
 *
 * @author  AO Industries, Inc.
 */
public class TrimFilterWriter extends PrintWriter {

	private static final String lineSeparator = System.lineSeparator();

	private final ServletResponse response;
	@SuppressWarnings("PackageVisibleField")
	boolean inTextArea = false;
	@SuppressWarnings("PackageVisibleField")
	boolean inPre = false;
	private boolean atLineStart = true;

	private int readCharMatchCount = 0;
	private int preReadCharMatchCount = 0;

	/**
	 * Used within individual methods only and released on close.
	 */
	private char[] outputBuffer = BufferManager.getChars();

	public TrimFilterWriter(Writer out, ServletResponse response) {
		super(out);
		this.response = response;
	}

	private String isTrimEnabledCacheContentType;
	private boolean isTrimEnabledCacheResult;

	/**
	 * Determines if trimming is enabled based on the output content type.
	 *
	 * @see  TrimFilterOutputStream#isTrimEnabled()  for same method implemented
	 */
	@SuppressWarnings({"deprecation", "StringEquality"})
	private boolean isTrimEnabled() {
		String contentType = response.getContentType();
		// Fast-path: If the contentType is the same string (by identity), return the previously determined value.
		if(contentType != isTrimEnabledCacheContentType) {
			isTrimEnabledCacheResult =
				contentType==null
				|| contentType.equals(ContentType.XHTML)
				|| contentType.startsWith(ContentType.XHTML + ";")
				|| contentType.equals(ContentType.HTML)
				|| contentType.startsWith(ContentType.HTML + ";")
				|| contentType.equals(ContentType.XML)
				|| contentType.startsWith(ContentType.XML + ";")
				|| contentType.equals(ContentType.XML_OLD)
				|| contentType.startsWith(ContentType.XML_OLD + ";")
			;
			isTrimEnabledCacheContentType = contentType;
		}
		return isTrimEnabledCacheResult;
	}

	/*
	@Override
	public void flush() {
		out.flush();
	}*/

	@Override
	public void close() {
		if(outputBuffer!=null) {
			BufferManager.release(outputBuffer, false);
			outputBuffer = null;
		}
		super.close();
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		inTextArea = false;
		inPre = false;
		atLineStart = true;
	}

	static final char[] textarea = {'<', 't', 'e', 'x', 't', 'a', 'r', 'e', 'a'};
	static final char[] TEXTAREA = {'<', 'T', 'E', 'X', 'T', 'A', 'R', 'E', 'A'};

	static final char[] textarea_close = {'<', '/', 't', 'e', 'x', 't', 'a', 'r', 'e', 'a'};
	static final char[] TEXTAREA_CLOSE = {'<', '/', 'T', 'E', 'X', 'T', 'A', 'R', 'E', 'A'};

	static final char[] pre = {'<', 'p', 'r', 'e'};
	static final char[] PRE = {'<', 'P', 'R', 'E'};

	static final char[] pre_close = {'<', '/', 'p', 'r', 'e'};
	static final char[] PRE_CLOSE = {'<', '/', 'P', 'R', 'E'};

	/**
	 * Processes one character and returns true if the character should be outputted.
	 */
	private boolean processChar(char c) {
		if(inTextArea) {
			if(
				c==textarea_close[readCharMatchCount]
				|| c==TEXTAREA_CLOSE[readCharMatchCount]
			) {
				readCharMatchCount++;
				if(readCharMatchCount>=textarea_close.length) {
					inTextArea=false;
					readCharMatchCount=0;
				}
			} else {
				readCharMatchCount=0;
			}
			return true;
		} else if(inPre) {
			if(
				c==pre_close[preReadCharMatchCount]
				|| c==PRE_CLOSE[preReadCharMatchCount]
			) {
				preReadCharMatchCount++;
				if(preReadCharMatchCount>=pre_close.length) {
					inPre=false;
					preReadCharMatchCount=0;
				}
			} else {
				preReadCharMatchCount=0;
			}
			return true;
		} else {
			if(c=='\r') {
				readCharMatchCount = 0;
				preReadCharMatchCount = 0;
				// Carriage return only output when no longer at the beginning of the line
				return !atLineStart;
			} else if(c=='\n') {
				readCharMatchCount = 0;
				preReadCharMatchCount = 0;
				// Newline only output when no longer at the beginning of the line
				if(!atLineStart) {
					atLineStart = true;
					return true;
				} else {
					return false;
				}
			} else if(c==' ' || c=='\t') {
				readCharMatchCount = 0;
				preReadCharMatchCount = 0;
				// Space and tab only output when no longer at the beginning of the line
				return !atLineStart;
			} else {
				atLineStart = false;
				if(
					c==textarea[readCharMatchCount]
					|| c==TEXTAREA[readCharMatchCount]
				) {
					readCharMatchCount++;
					if(readCharMatchCount>=textarea.length) {
						inTextArea=true;
						readCharMatchCount=0;
					}
				} else {
					readCharMatchCount=0;
				}
				if(
					c==pre[preReadCharMatchCount]
					|| c==PRE[preReadCharMatchCount]
				) {
					preReadCharMatchCount++;
					if(preReadCharMatchCount>=pre.length) {
						inPre=true;
						preReadCharMatchCount=0;
					}
				} else {
					preReadCharMatchCount=0;
				}
				return true;
			}
		}
	}

	@Override
	public void write(int c) {
		if(
			!isTrimEnabled()
			|| processChar((char)c)
		) super.write(c);
	}

	@Override
	public void write(char[] buf, int off, int len) {
		if(isTrimEnabled()) {
			char[] buff = outputBuffer;
			// If len > OUPUT_BUFFER_SIZE, process in blocks
			int buffUsed = 0;
			while(len>0) {
				int blockLen = len<=BufferManager.BUFFER_SIZE ? len : BufferManager.BUFFER_SIZE;
				for(
					int index = off, blockEnd = off + blockLen;
					index<blockEnd;
					index++
				) {
					char c = buf[index];
					if(processChar(c)) {
						buff[buffUsed++] = c;
						if(buffUsed>=BufferManager.BUFFER_SIZE) {
							assert buffUsed==BufferManager.BUFFER_SIZE;
							super.write(buff, 0, buffUsed);
							buffUsed = 0;
						}
					}
				}
				off+=blockLen;
				len-=blockLen;
			}
			if(buffUsed>0) super.write(buff, 0, buffUsed);
		} else {
			super.write(buf, off, len);
		}
	}

	@Override
	public void write(char[] buf) {
		if(isTrimEnabled()) write(buf, 0, buf.length);
		else super.write(buf);
	}

	@Override
	public void write(String s, int off, int len) {
		if(isTrimEnabled()) {
			char[] buff = outputBuffer;
			// If len > OUPUT_BUFFER_SIZE, process in blocks
			int buffUsed = 0;
			while(len>0) {
				int blockLen = len<=BufferManager.BUFFER_SIZE ? len : BufferManager.BUFFER_SIZE;
				for(
					int index = off, blockEnd = off + blockLen;
					index<blockEnd;
					index++
				) {
					char c = s.charAt(index);
					if(processChar(c)) {
						buff[buffUsed++] = c;
						if(buffUsed>=BufferManager.BUFFER_SIZE) {
							assert buffUsed==BufferManager.BUFFER_SIZE;
							super.write(buff, 0, buffUsed);
							buffUsed = 0;
						}
					}
				}
				off+=blockLen;
				len-=blockLen;
			}
			if(buffUsed>0) super.write(buff, 0, buffUsed);
		} else {
			super.write(s, off, len);
		}
	}

	@Override
	public void print(boolean b) {
		atLineStart = false;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		super.print(b);
	}

	@Override
	public void print(char c) {
		if(
			!isTrimEnabled()
			|| processChar(c)
		) super.print(c);
	}

	@Override
	public void print(int i) {
		atLineStart = false;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		super.print(i);
	}

	@Override
	public void print(long l) {
		atLineStart = false;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		super.print(l);
	}

	@Override
	public void print(float f) {
		atLineStart = false;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		super.print(f);
	}

	@Override
	public void print(double d) {
		atLineStart = false;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		super.print(d);
	}

	@Override
	public void println() {
		if(isTrimEnabled()) write(lineSeparator);
		else super.println();
	}

	@Override
	public void println(boolean b) {
		atLineStart = true;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		super.println(b);
	}

	@Override
	public void println(char x) {
		if(isTrimEnabled()) {
			if(processChar(x)) super.print(x);
			write(lineSeparator);
		} else super.println(x);
	}

	@Override
	public void println(int i) {
		atLineStart = true;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		super.println(i);
	}

	@Override
	public void println(long l) {
		atLineStart = true;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		super.println(l);
	}

	@Override
	public void println(float f) {
		atLineStart = true;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		super.println(f);
	}

	@Override
	public void println(double d) {
		atLineStart = true;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		super.println(d);
	}

	@Override
	public void println(char[] x) {
		if(isTrimEnabled()) {
			print(x);
			write(lineSeparator);
		} else {
			super.println(x);
		}
	}

	@Override
	public void println(String x) {
		if(isTrimEnabled()) {
			print(x);
			write(lineSeparator);
		} else super.println(x);
	}

	@Override
	public void println(Object x) {
		if(isTrimEnabled()) {
			print(x);
			write(lineSeparator);
		} else super.println(x);
	}

	@Override
	public TrimFilterWriter format(String format, Object ... args) {
		atLineStart = false;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		super.format(format, args);
		return this;
	}

	@Override
	public TrimFilterWriter format(Locale l, String format, Object ... args) {
		atLineStart = false;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		super.format(l, format, args);
		return this;
	}
}
