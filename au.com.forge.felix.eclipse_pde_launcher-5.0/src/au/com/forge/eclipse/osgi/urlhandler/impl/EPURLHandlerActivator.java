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

import java.net.URLStreamHandler;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.xml.parsers.SAXParserFactory;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;

/**
 * A {@link BundleActivator} for installing the Eclipse PDE
 * project directory {@link URLStreamHandler} into the framework.
 * 
 * This activator MUST be launched from the Felix launcher (or OSGi 
 * framework) launcher in order to start up the framework with
 * Eclipse Project directories as startup bundles.
 * 
 * @author Christopher Armstrong
 *
 */
public class EPURLHandlerActivator implements BundleActivator {

	private ServiceRegistration<URLStreamHandlerService> eclipseHandlerReg;
	private EclipseProjectURLHandler eclipseHandler;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	public void start(BundleContext context) throws Exception {

		SAXParserFactory factory = SAXParserFactory.newInstance();
		Dictionary<String, String> serviceProps = new Hashtable<String, String>();
		serviceProps.put(URLConstants.URL_HANDLER_PROTOCOL,
				EclipseProjectURLHandler.URL_PROTOCOL);
		eclipseHandler = new EclipseProjectURLHandler(factory);
		eclipseHandlerReg = context.registerService(
				URLStreamHandlerService.class, eclipseHandler,
				serviceProps);

	}

	public void stop(BundleContext context) {
		eclipseHandlerReg.unregister();
	}
}
