/**
 *  Eclipse PDE Felix Launcher - launches Apache Felix with support for Eclipse PDE project 
 *  directories as bundles
 *  
 *  Copyright (C) 2010 Forge Research
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package au.com.forge.eclipse.osgi.urlhandler.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import javax.xml.parsers.SAXParserFactory;

import org.osgi.service.url.AbstractURLStreamHandlerService;

/**
 * A {@link URLStreamHandler} that accepts file-system path
 * URLs to Eclipse project directories and builds a bundle
 * out of them. The project must be an OSGi bundle project or
 * plugin project, as the meta-data from .classpath and 
 * MANIFEST.MF is used to build the pseudo-bundle.
 * 
 * Don't use this handler during deployment because it builds
 * the pseudo-bundle in memory.
 * 
 * @author Christopher Armstrong
 * 
 */
public class EclipseProjectURLHandler extends AbstractURLStreamHandlerService {
	/**
	 * Invented URL protocol for loading Eclipse projects. The
	 * handler expects
	 */
	public static final String URL_PROTOCOL = "eclipse-project";
	private SAXParserFactory saxParserFactory;

	/**
	 * @param factory
	 * 
	 */
	public EclipseProjectURLHandler(SAXParserFactory factory) {
		this.saxParserFactory = factory;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.service.url.AbstractURLStreamHandlerService#openConnection(java
	 * .net.URL)
	 */
	public URLConnection openConnection(final URL url) throws IOException {
		if (!url.getProtocol().equals(URL_PROTOCOL))
			throw new IOException("Unable to handle the protocol "
					+ url.getProtocol());
		if (url.getHost() != null && !url.getHost().equals(""))
			throw new IOException(
					"Eclipse Project directory URLs do not contain a host component.");
		if (url.getPort() != -1)
			throw new IOException(
					"Eclipse Project directory URLs do not contain a port.");
		if (url.getQuery() != null)
			throw new IOException(
					"Eclipse Project directory URLs do not support query strings.");
		return new EclipseProjectURLConnection(url);
	}

	public void setSAXParserFactory(SAXParserFactory arg1) {
		this.saxParserFactory = arg1;
	}
	
	public class EclipseProjectURLConnection extends URLConnection {

		protected EclipseProjectURLConnection(URL url) {
			super(url);
		}

		public void connect() throws IOException {
		}

		public InputStream getInputStream() throws IOException {
			// Technically we can get a race condition this way (i.e. checking
			// if the file
			// exists, then someone deletes or relinks the file, and then we
			// try open it).
			// But we're not doing anything security related, and an IOException
			// will be
			// thrown anyway if it does not exist (the only problem being a more
			// cryptic exception)
			File projectDirectory = new File(url.getPath());
			if (!projectDirectory.exists() || !projectDirectory.isDirectory())
				throw new IOException("The path " + url.getPath()
						+ " does not exist or is not a directory.");
			ByteArrayOutputStream bundleBuffer = new ByteArrayOutputStream();
			EclipseProjectToOSGiBundleBuilder builder = new EclipseProjectToOSGiBundleBuilder(projectDirectory, bundleBuffer, saxParserFactory);
			builder.build();

			return new ByteArrayInputStream(bundleBuffer.toByteArray());
		}
		
		
	}
}
