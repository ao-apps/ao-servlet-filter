/*
 * ao-servlet-filter - Reusable Java library of servlet filters.
 * Copyright (C) 2006, 2007, 2008, 2009, 2010, 2011, 2013, 2014, 2016, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.lang.io.ContentType;
import com.aoapps.lang.util.BufferManager;
import static com.aoapps.servlet.filter.TrimFilterWriter.PRE;
import static com.aoapps.servlet.filter.TrimFilterWriter.PRE_CLOSE;
import static com.aoapps.servlet.filter.TrimFilterWriter.TEXTAREA;
import static com.aoapps.servlet.filter.TrimFilterWriter.TEXTAREA_CLOSE;
import static com.aoapps.servlet.filter.TrimFilterWriter.pre;
import static com.aoapps.servlet.filter.TrimFilterWriter.pre_close;
import static com.aoapps.servlet.filter.TrimFilterWriter.textarea;
import static com.aoapps.servlet.filter.TrimFilterWriter.textarea_close;
import java.io.IOException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;

/**
 * Filters the output and removes extra white space at the beginning of lines and completely removes blank lines.
 * TEXTAREAs are automatically detected as long as they start with exact "&lt;textarea" and end with exactly "&lt;/textarea" (case insensitive).
 * PREs are automatically detected as long as they start with exact "&lt;pre" and end with exactly "&lt;/pre" (case insensitive).
 * The reason for the specific tag format is to simplify the implementation
 * for maximum performance.  Careful attention has been paid to minimize the internal buffering in this class.  As many write/print operations as possible
 * are passed directly to the wrapped <code>ServletOutputStream</code>.  Please note that these methods are not synchronized, as servlet output is normally written
 * by the thread allocated for the request.  If synchronization is required it should be provided externally.
 *
 * @author  AO Industries, Inc.
 */
public class TrimFilterOutputStream extends ServletOutputStream {

	private static final String lineSeparator = System.lineSeparator();

	private final ServletOutputStream wrapped;
	private final ServletResponse response;
	@SuppressWarnings("PackageVisibleField")
	boolean inTextArea = false;
	@SuppressWarnings("PackageVisibleField")
	boolean inPre = false;
	private boolean atLineStart = true;

	private int readCharMatchCount = 0;
	private int preReadCharMatchCount = 0;

	/**
	 * Only used within individual methods, released on close.
	 */
	private byte[] outputBuffer = BufferManager.getBytes();

	public TrimFilterOutputStream(ServletOutputStream wrapped, ServletResponse response) {
		this.wrapped = wrapped;
		this.response = response;
	}

	private String isTrimEnabledCacheContentType;
	private boolean isTrimEnabledCacheResult;

	/**
	 * Determines if trimming is enabled based on the output content type.
	 *
	 * @see  TrimFilterWriter#isTrimEnabled()  for same method implemented
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

	@Override
	public void flush() throws IOException {
		wrapped.flush();
	}

	@Override
	public void close() throws IOException {
		if(outputBuffer!=null) {
			BufferManager.release(outputBuffer, false);
			outputBuffer = null;
		}
		wrapped.close();
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		inTextArea = false;
		inPre = false;
		atLineStart = true;
	}

	/**
	 * Processes one character and returns true if the character should be outputted.
	 */
	private boolean processChar(char c) {
		if(inTextArea) {
			if(
				c == textarea_close[readCharMatchCount]
				|| c == TEXTAREA_CLOSE[readCharMatchCount]
			) {
				readCharMatchCount++;
				if(readCharMatchCount >= textarea_close.length) {
					inTextArea = false;
					readCharMatchCount = 0;
				}
			} else {
				readCharMatchCount = 0;
			}
			return true;
		} else if(inPre) {
			if(
				c == pre_close[preReadCharMatchCount]
				|| c == PRE_CLOSE[preReadCharMatchCount]
			) {
				preReadCharMatchCount++;
				if(preReadCharMatchCount >= pre_close.length) {
					inPre = false;
					preReadCharMatchCount = 0;
				}
			} else {
				preReadCharMatchCount = 0;
			}
			return true;
		} else {
			if(c == '\n') {
				readCharMatchCount = 0;
				preReadCharMatchCount = 0;
				// Newline only output when no longer at the beginning of the line
				if(!atLineStart) {
					atLineStart = true;
					return true;
				} else {
					return false;
				}
			} else if(c == ' ' || c == '\t' || c == '\r') {
				readCharMatchCount = 0;
				preReadCharMatchCount = 0;
				// Space, tab, and carriage return only output when no longer at the beginning of the line
				return !atLineStart;
			} else {
				atLineStart = false;
				if(
					c == textarea[readCharMatchCount]
					|| c == TEXTAREA[readCharMatchCount]
				) {
					readCharMatchCount++;
					if(readCharMatchCount >= textarea.length) {
						inTextArea = true;
						readCharMatchCount = 0;
					}
				} else {
					readCharMatchCount = 0;
				}
				if(
					c == pre[preReadCharMatchCount]
					|| c == PRE[preReadCharMatchCount]
				) {
					preReadCharMatchCount++;
					if(preReadCharMatchCount >= pre.length) {
						inPre = true;
						preReadCharMatchCount = 0;
					}
				} else {
					preReadCharMatchCount = 0;
				}
				return true;
			}
		}
	}

	@Override
	public void write(int b) throws IOException {
		if(
			!isTrimEnabled()
			|| processChar((char)b)
		) wrapped.write(b);
	}

	@Override
	public void write(byte[] buf, int off, int len) throws IOException {
		if(isTrimEnabled()) {
			final byte[] buff = outputBuffer;
			// If len > OUPUT_BUFFER_SIZE, process in blocks
			int buffUsed = 0;
			while(len>0) {
				int blockLen = len<=BufferManager.BUFFER_SIZE ? len : BufferManager.BUFFER_SIZE;
				for(
					int index = off, blockEnd = off + blockLen;
					index<blockEnd;
					index++
				) {
					byte b = buf[index];
					if(processChar((char)b)) {
						buff[buffUsed++] = b;
						if(buffUsed>=BufferManager.BUFFER_SIZE) {
							assert buffUsed==BufferManager.BUFFER_SIZE;
							wrapped.write(buff, 0, buffUsed);
							buffUsed = 0;
						}
					}
				}
				off+=blockLen;
				len-=blockLen;
			}
			if(buffUsed>0) wrapped.write(buff, 0, buffUsed);
		} else {
			wrapped.write(buf, off, len);
		}
	}

	@Override
	public void write(byte[] b) throws IOException {
		if(isTrimEnabled()) write(b, 0, b.length);
		else wrapped.write(b);
	}

	@Override
	public void print(boolean b) throws IOException {
		atLineStart = false;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		wrapped.print(b);
	}

	@Override
	public void print(char c) throws IOException {
		if(
			!isTrimEnabled()
			|| processChar(c)
		) wrapped.print(c);
	}

	@Override
	public void print(double d) throws IOException {
		atLineStart = false;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		wrapped.print(d);
	}

	@Override
	public void print(float f) throws IOException {
		atLineStart = false;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		wrapped.print(f);
	}

	@Override
	public void print(int i) throws IOException {
		atLineStart = false;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		wrapped.print(i);
	}

	@Override
	public void print(long l) throws IOException {
		atLineStart = false;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		wrapped.print(l);
	}

	@Override
	public void print(String s) throws IOException {
		if(isTrimEnabled()) {
			byte[] buff = outputBuffer;
			// If len > OUPUT_BUFFER_SIZE, process in blocks
			int off = 0;
			int len = s.length();
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
						buff[buffUsed++] = (byte)c;
						if(buffUsed>=BufferManager.BUFFER_SIZE) {
							assert buffUsed==BufferManager.BUFFER_SIZE;
							wrapped.write(buff, 0, buffUsed);
							buffUsed = 0;
						}
					}
				}
				off+=blockLen;
				len-=blockLen;
			}
			if(buffUsed>0) wrapped.write(buff, 0, buffUsed);
		} else {
			wrapped.print(s);
		}
	}

	@Override
	public void println() throws IOException {
		if(isTrimEnabled()) print(lineSeparator);
		else wrapped.println();
	}

	@Override
	public void println(boolean b) throws IOException {
		atLineStart = true;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		wrapped.println(b);
	}

	@Override
	public void println(char c) throws IOException {
		if(isTrimEnabled()) {
			if(processChar(c)) wrapped.print(c);
			print(lineSeparator);
		} else {
			wrapped.println(c);
		}
	}

	@Override
	public void println(double d) throws IOException {
		atLineStart = true;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		wrapped.println(d);
	}

	@Override
	public void println(float f) throws IOException {
		atLineStart = true;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		wrapped.println(f);
	}

	@Override
	public void println(int i) throws IOException {
		atLineStart = true;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		wrapped.println(i);
	}

	@Override
	public void println(long l) throws IOException {
		atLineStart = true;
		readCharMatchCount = 0;
		preReadCharMatchCount = 0;
		wrapped.println(l);
	}

	@Override
	public void println(String s) throws IOException {
		if(isTrimEnabled()) {
			print(s);
			print(lineSeparator);
		} else {
			wrapped.println(s);
		}
	}

	@Override
	public boolean isReady() {
		return wrapped.isReady();
	}

	@Override
	public void setWriteListener(WriteListener wl) {
		wrapped.setWriteListener(wl);
	}
}
