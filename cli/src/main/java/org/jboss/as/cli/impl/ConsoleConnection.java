/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.impl;

import org.jboss.aesh.console.ConsoleCallback;
import org.jboss.as.cli.CliConnection;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.SSLConfig;
import org.jboss.as.cli.security.LazyDelagatingTrustManager;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.StreamUtils;
import org.wildfly.security.manager.WildFlySecurityManager;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ConsoleConnection implements CliConnection {

    /** the command line specified username */
    private String username;
    /** the command line specified password */
    private String password;
    /** the default controller host */
    private String host;
    /** the default controller protocol */
    private String protocol;
    /** the default controller port */
    private int port;
    /** The SSLContext when managed by the CLI */
    private int connectionTimeout;
    /** The SSLContext when managed by the CLI */
    private SSLContext sslContext;
    private ConsoleLoginState state = ConsoleLoginState.DISCONNECTED;
    /** the controller client */
    private ModelControllerClient modelClient;
    /** The TrustManager in use by the SSLContext, a reference is kept to rejected certificates can be captured. */
    private LazyDelagatingTrustManager trustManager;
    private CommandContext commandContext;


    private ConsoleCallback defaultConsoleCallback;

    private CountDownLatch connectionLatch;

    public ConsoleConnection(String host, int port) {
        setHost(host);
        setPort(port);
    }

    public ConsoleConnection(String host, int port, ModelControllerClient modelClient) {
        this(host, port);
        setModelClient(modelClient);
    }

    public ConsoleConnection(String username, String password, String host,
                             String protocol, int port, int connectionTimeout) {
        setUsername(username);
        setPassword(password);
        setHost(host);
        setProtocol(protocol);
        setPort(port);
        setConnectionTimeout(connectionTimeout);
    }

    public ConsoleConnection(String username, String password, String host,
                             String protocol, int port, int connectionTimeout,
                             SSLConfig sslConfig) throws CliInitializationException {
        this(username, password, host, protocol, port, connectionTimeout);
        initSSLContext(sslConfig);
    }

    public ConsoleConnection(String username, String password, String host,
                             String protocol, int port, int connectionTimeout,
                             CommandContext commandContext,
                             ModelControllerClient modelClient,
                             LazyDelagatingTrustManager trustManager,
                             SSLContext sslContext) {
        this(username, password, host, protocol, port, connectionTimeout);
        setCommandContext(commandContext);
        setModelClient(modelClient);
        setTrustManager(trustManager);
        setSSLContext(sslContext);
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public boolean isPasswordSet() {
        return password != null && password.length() > 0;
    }

    @Override
    public boolean isUsernameOrPasswordNull() {
        return username == null || password == null;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public void setHost(String host) {
        this.host = host;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    @Override
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    @Override
    public SSLContext getSslContext() {
        return sslContext;
    }

    @Override
    public void setSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    @Override
    public ConsoleLoginState getState() {
        return state;
    }

    @Override
    public void setState(ConsoleLoginState state) {
        this.state = state;
    }

    @Override
    public ModelControllerClient getModelClient() {
        return modelClient;
    }

    @Override
    public void setModelClient(ModelControllerClient modelClient) {
        this.modelClient = modelClient;
    }

    @Override
    public LazyDelagatingTrustManager getTrustManager() {
        return trustManager;
    }

    @Override
    public void setTrustManager(LazyDelagatingTrustManager trustManager) {
        this.trustManager = trustManager;
    }

    @Override
    public CommandContext getCommandContext() {
        return commandContext;
    }

    @Override
    public void setCommandContext(CommandContext commandContext) {
        this.commandContext = commandContext;
    }

    //@Override
    public ConsoleCallback getDefaultConsoleCallback() {
        return defaultConsoleCallback;
    }

    //@Override
    public void setDefaultConsoleCallback(ConsoleCallback defaultConsoleCallback) {
        this.defaultConsoleCallback = defaultConsoleCallback;
    }

    @Override
    public void initConnectionLatch() {
        if(connectionLatch == null || connectionLatch.getCount() < 1) {
            connectionLatch = new CountDownLatch(1);
            try {
                connectionLatch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void endConnectionLatch() {
        if(connectionLatch != null)
            connectionLatch.countDown();
    }

       /**
     * Initialise the SSLContext and associated TrustManager for this CommandContext.
     *
     * If no configuration is specified the default mode of operation will be to use a lazily initialised TrustManager with no
     * KeyManager.
     */
    private void initSSLContext(SSLConfig sslConfig) throws CliInitializationException {
        // If the standard properties have been set don't enable and CLI specific stores.
        if (WildFlySecurityManager.getPropertyPrivileged("javax.net.ssl.keyStore", null) != null
                || WildFlySecurityManager.getPropertyPrivileged("javax.net.ssl.trustStore", null) != null) {
            return;
        }

        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;

        String trustStore = null;
        String trustStorePassword = null;
        boolean modifyTrustStore = true;

        if (sslConfig != null) {
            String keyStoreLoc = sslConfig.getKeyStore();
            if (keyStoreLoc != null) {
                char[] keyStorePassword = sslConfig.getKeyStorePassword().toCharArray();
                String tmpKeyPassword = sslConfig.getKeyPassword();
                char[] keyPassword = tmpKeyPassword != null ? tmpKeyPassword.toCharArray() : keyStorePassword;

                File keyStoreFile = new File(keyStoreLoc);

                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(keyStoreFile);
                    KeyStore theKeyStore = KeyStore.getInstance("JKS");
                    theKeyStore.load(fis, keyStorePassword);

                    String alias = sslConfig.getAlias();
                    if (alias != null) {
                        KeyStore replacement = KeyStore.getInstance("JKS");
                        replacement.load(null);
                        KeyStore.ProtectionParameter protection = new KeyStore.PasswordProtection(keyPassword);

                        replacement.setEntry(alias, theKeyStore.getEntry(alias, protection), protection);
                        theKeyStore = replacement;
                    }

                    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    keyManagerFactory.init(theKeyStore, keyPassword);
                    keyManagers = keyManagerFactory.getKeyManagers();
                } catch (IOException e) {
                    throw new CliInitializationException(e);
                } catch (GeneralSecurityException e) {
                    throw new CliInitializationException(e);
                } finally {
                    StreamUtils.safeClose(fis);
                }

            }

            trustStore = sslConfig.getTrustStore();
            trustStorePassword = sslConfig.getTrustStorePassword();
            modifyTrustStore = sslConfig.isModifyTrustStore();
        }

        if (trustStore == null) {
            final String userHome = WildFlySecurityManager.getPropertyPrivileged("user.home", null);
            File trustStoreFile = new File(userHome, ".jboss-cli.truststore");
            trustStore = trustStoreFile.getAbsolutePath();
            trustStorePassword = "cli_truststore"; // Risk of modification but no private keys to be stored in the truststore.
        }

        LazyDelagatingTrustManager trustManager = new LazyDelagatingTrustManager(trustStore, trustStorePassword, modifyTrustStore);
        trustManagers = new TrustManager[] { trustManager };
        setTrustManager(trustManager);

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);

            setSSLContext(sslContext);
        } catch (GeneralSecurityException e) {
            throw new CliInitializationException(e);
        }
    }
}
