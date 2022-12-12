/*******************************************************************************
 * Copyright (c) 2009, 2019 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    https://www.eclipse.org/legal/epl-2.0
 * and the Eclipse Distribution License is available at 
 *   https://www.eclipse.org/org/documents/edl-v10.php
 *
 * Contributors:
 *    Dave Locke - initial API and implementation and/or initial documentation
 */
package org.eclipse.paho.mqttv5.client.internal;

import org.eclipse.paho.mqttv5.client.logging.Logger;
import org.eclipse.paho.mqttv5.client.logging.LoggerFactory;
import org.eclipse.paho.mqttv5.common.MqttException;

import javax.net.ssl.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A network module for connecting over SSL.
 */
public class SSLNetworkModule extends TCPNetworkModule {
	private static final String CLASS_NAME = SSLNetworkModule.class.getName();
	private Logger log = LoggerFactory.getLogger(LoggerFactory.MQTT_CLIENT_MSG_CAT, CLASS_NAME);

	private String[] enabledCiphers;
	private int handshakeTimeoutSecs;
	private HostnameVerifier hostnameVerifier;
	private boolean httpsHostnameVerificationEnabled = true;

	private String host;
	private int port;

	/**
	 * Constructs a new SSLNetworkModule using the specified host and port. The
	 * supplied SSLSocketFactory is used to supply the network socket.
	 * 
	 * @param factory
	 *            the {@link SSLSocketFactory} to be used in this SSLNetworkModule
	 * @param host
	 *            the Hostname of the Server
	 * @param port
	 *            the Port of the Server
	 * @param resourceContext
	 *            Resource Context
	 */
	public SSLNetworkModule(SSLSocketFactory factory, String host, int port, String resourceContext) {
		super(factory, host, port, resourceContext);
		this.host = host;
		this.port = port;
		log.setResourceName(resourceContext);
	}

	/**
	 * Returns the enabled cipher suites.
	 * 
	 * @return a string array of enabled Cipher suites
	 */
	public String[] getEnabledCiphers() {
		return enabledCiphers;
	}

	/**
	 * Sets the enabled cipher suites on the underlying network socket.
	 * 
	 * @param enabledCiphers
	 *            a String array of cipher suites to enable
	 */
	public void setEnabledCiphers(String[] enabledCiphers) {
		final String methodName = "setEnabledCiphers";
		this.enabledCiphers = enabledCiphers;
		if ((socket != null) && (enabledCiphers != null)) {
			if (log.isLoggable(Logger.FINE)) {
				String ciphers = "";
				for (int i = 0; i < enabledCiphers.length; i++) {
					if (i > 0) {
						ciphers += ",";
					}
					ciphers += enabledCiphers[i];
				}
				// @TRACE 260=setEnabledCiphers ciphers={0}
				log.fine(CLASS_NAME, methodName, "260", new Object[] { ciphers });
			}
			((SSLSocket) socket).setEnabledCipherSuites(enabledCiphers);
		}
	}

	public void setSSLhandshakeTimeout(int timeout) {
		super.setConnectTimeout(timeout);
		this.handshakeTimeoutSecs = timeout;
	}

	public HostnameVerifier getSSLHostnameVerifier() {
		return hostnameVerifier;
	}

	public void setSSLHostnameVerifier(HostnameVerifier hostnameVerifier) {
		this.hostnameVerifier = hostnameVerifier;
	}

	public boolean isHttpsHostnameVerificationEnabled() {
		return httpsHostnameVerificationEnabled;
	}

	public void setHttpsHostnameVerificationEnabled(boolean httpsHostnameVerificationEnabled) {
		this.httpsHostnameVerificationEnabled = httpsHostnameVerificationEnabled;
	}

	public void start() throws IOException, MqttException {
		super.start();
		setEnabledCiphers(enabledCiphers);
		int soTimeout = socket.getSoTimeout();
		// RTC 765: Set a timeout to avoid the SSL handshake being blocked indefinitely
		socket.setSoTimeout(this.handshakeTimeoutSecs * 1000);

		// SNI support.  Should be automatic under some circumstances - not all, apparently
		SSLParameters sslParameters = ((SSLSocket)socket).getSSLParameters();
		List<SNIServerName> sniHostNames = new ArrayList<SNIServerName>(sslParameters.getServerNames());
		sniHostNames.add(new SNIHostName(host));
		sslParameters.setServerNames(sniHostNames);

		// If default Hostname verification is enabled, use the same method that is used with HTTPS
		if (this.httpsHostnameVerificationEnabled) {
			sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
		}

		((SSLSocket) socket).setSSLParameters(sslParameters);

		((SSLSocket) socket).startHandshake();
		if (hostnameVerifier != null) {
			SSLSession session = ((SSLSocket) socket).getSession();
			if(!hostnameVerifier.verify(host, session)) {
				session.invalidate();
				socket.close();
				throw new SSLPeerUnverifiedException("Host: " + host + ", Peer Host: " + session.getPeerHost());
			}
		}
		// reset timeout to default value
		socket.setSoTimeout(soTimeout);
	}

	public String getServerURI() {
		return "ssl://" + host + ":" + port;
	}
}
