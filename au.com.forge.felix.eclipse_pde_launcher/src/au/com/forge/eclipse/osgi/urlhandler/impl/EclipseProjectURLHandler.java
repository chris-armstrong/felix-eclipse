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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.osgi.service.url.AbstractURLStreamHandlerService;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

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

	public class EclipseProjectURLConnection extends URLConnection {
		private static final int MAX_RECURSE_DEPTH = 256;
		byte[] readBuffer = new byte[100 * 1024];
		private Set<String> addedEntries = new HashSet<String>();

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
			ZipOutputStream outputStream = new ZipOutputStream(bundleBuffer);

			handleBuildProperties(projectDirectory, outputStream);
			handleClasspath(projectDirectory, outputStream);

			outputStream.close();
			return new ByteArrayInputStream(bundleBuffer.toByteArray());
		}

		/**
		 * Handles the entries in the .classpath file.
		 * 
		 * @param projectDirectory
		 * @param outputStream
		 * @throws IOException
		 */
		private void handleClasspath(File projectDirectory,
				ZipOutputStream outputStream) throws IOException {
			File classpathFile = new File(projectDirectory, ".classpath");
			if (!classpathFile.exists() || !classpathFile.isFile())
				throw new IOException(
						"The .classpath file does not exist in the project directory or is not a file.");
			SAXParser parser;
			final Set<String> classpathSet = new TreeSet<String>();
			try {
				parser = saxParserFactory.newSAXParser();
				parser.parse(classpathFile, new DefaultHandler() {
					public void startElement(String uri, String localName,
							String name, Attributes attributes)
							throws SAXException {
						if (name.equals("classpathentry")) {
							String kind = attributes.getValue("", "kind");
							if (kind == null || kind.equals(""))
								throw new SAXException(
										"Missing kind attribute on classpathentry.");
							if (kind.equals("src")) {
								String output = attributes.getValue("output");
								if (output != null && !output.equals(""))
									classpathSet.add(output);
							} else if (kind.equals("output")) {
								String path = attributes.getValue("path");
								if (path != null && !path.equals(""))
									classpathSet.add(path);
							}
						}
					}
				});
			} catch (ParserConfigurationException e) {
				throw new IOException(
						"Unable to configure XML parser to parse '.classpath' file ("
								+ e.getMessage() + ")");
			} catch (SAXException e) {
				throw new IOException("Unable to parse the '.classpath' file: "
						+ e.getMessage());
			}
			Iterator<String> it = classpathSet.iterator();
			while (it.hasNext()) {
				String path = (String) it.next();
				File inputPath = new File(projectDirectory, path);
				// We have to ignore invalid classpath locations, because if
				// nothing is compiled,
				// the directory might not be created. (At least, thats the case
				// with maven, and
				// Eclipse seems not to care).
				if (inputPath.exists() && inputPath.isDirectory())
					writeResourcesFromDirectory(outputStream, ".", inputPath, 0);
			}
		}

		private void handleBuildProperties(File projectDirectory,
				ZipOutputStream outputStream) throws IOException,
				FileNotFoundException {
			Properties buildProperties = new Properties();
			File buildPropertiesFile = new File(projectDirectory,
					"build.properties");
			if (!buildPropertiesFile.exists() || !buildPropertiesFile.isFile()) {
				// The build.properties file is missing. We probably ended up
				// here because there is a MANIFEST.MF file. Lets just cheat and
				// include META-INF/ directory as if the build.properties
				// file was just generated by the
				// "Configure -> Convert to Plugin Project" menu
				buildProperties.put("bin.includes", "META-INF,.");
			} else
				buildProperties.load(new FileInputStream(buildPropertiesFile));

			outputStream.setLevel(ZipOutputStream.STORED);

			String binIncludes = buildProperties.getProperty("bin.includes");
			if (binIncludes != null) {
				String[] binFiles = binIncludes.split(",");

				for (int i = 0; i < binFiles.length; ++i) {
					String binFile = binFiles[i].trim();

					// Could be nasty and throw an exception, but we know it
					// just means the user has left a blank line
					// in the file after a "\"
					if (binFile.equals(""))
						continue;

					// We deliberately ignore this one because it is
					// handled by the classpath. I'm not sure, but I think
					// output. entries are intended for the exporter
					// compiler stage, not the packaging stage (which is
					// what we're imitating). We still handle them for
					// .jar files because the default Eclipse builder
					// doesn't always build them (so we simulate their
					// "building").
					// (If we were building a source bundle, we would
					// include it, as the source.. entry has the source
					// folders, although the .classpath file does too :-?)
					if (binFile.equals("."))
						continue;

					String binOutput = buildProperties.getProperty("output."
							+ binFiles[i]);
					if (binOutput != null) {

						// This specifies a library, with an output directory
						File outputDirectory = new File(projectDirectory,
								binOutput);
						writeResourcesFromDirectory(outputStream, binFiles[i],
								outputDirectory, 0);
					} else {
						// This specifies a direct resource include in the
						// binary build
						File resource = new File(projectDirectory, binFiles[i]);
						if (!resource.exists())
							throw new IOException(
									"Invalid binary build include in build.properties: "
											+ binFiles[i]);
						if (resource.isFile())
							writeResourceFromFile(outputStream, new File(
									binFiles[i]).getPath(), resource);
						else
							writeResourcesFromDirectory(outputStream, new File(
									binFiles[i]).getPath(), resource, 0);
					}
				}
			}
		}

		private void writeResourcesFromDirectory(ZipOutputStream jarFile,
				String outputResource, File inputDirectory, int recurseDepth)
				throws IOException {
			String prefix;
			if (recurseDepth > MAX_RECURSE_DEPTH)
				throw new IOException(
						"Unwanted recursion building resource directory: "
								+ inputDirectory + " for outputResource="
								+ outputResource);
			if (outputResource.equals(".")) {
				prefix = ""; // project root directory -> no prefix
			} else if (outputResource.endsWith(".jar")) {
				// Generate internal jar
				ByteArrayOutputStream internalJarByteBuffer = new ByteArrayOutputStream();
				JarOutputStream internalJar = new JarOutputStream(
						internalJarByteBuffer);
				internalJar.setLevel(ZipOutputStream.STORED);
				writeResourcesFromDirectory(internalJar, ".", inputDirectory, 0);
				internalJarByteBuffer.close();

				// Write its output entry
				ZipEntry outputEntry = new ZipEntry(outputResource);
				jarFile.putNextEntry(outputEntry);
				jarFile.write(internalJarByteBuffer.toByteArray());
				jarFile.closeEntry();
				return;
			} else
				prefix = outputResource;
			if (inputDirectory.exists() == false)
				return; // Don't think we should throw an exception here, as its
			// possible that nothing is generated for the build.
			File[] files = inputDirectory.listFiles();
			for (int i = 0; i < files.length; i++) {
				File inputFile = files[i];
				String fileOutputPath;
				if (prefix.equals(""))
					fileOutputPath = inputFile.getName();
				else
					// Use '/' as ZIP files are always constructed with forward slash
					fileOutputPath = prefix + "/"
							+ inputFile.getName();

				if (inputFile.isFile()) {
					writeResourceFromFile(jarFile, fileOutputPath, inputFile);
				} else if (inputFile.isDirectory()) {
					writeResourcesFromDirectory(jarFile, fileOutputPath,
							inputFile, recurseDepth + 1);
				}
			}
		}

		private void writeResourceFromFile(ZipOutputStream jarFile,
				String outputResource, File inputFile) throws IOException,
				FileNotFoundException {			
			// Normalise path on outputResource when running on Windows
			if (File.separatorChar == '\\')
			{
				outputResource = outputResource.replace("\\", "/");
			}
			
			if (addedEntries.contains(outputResource))
				return; // Ignore it, it has already been found and added
			
			ZipEntry entry = new ZipEntry(outputResource);
			addedEntries.add(outputResource);
			jarFile.putNextEntry(entry);
			InputStream inputFileStream = new FileInputStream(inputFile);

			int read = 0;
			while (read != -1) {
				read = inputFileStream.read(readBuffer);
				if (read > 0)
					jarFile.write(readBuffer, 0, read);
			}
			inputFileStream.close();
			jarFile.closeEntry();
		}

	}

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

}
