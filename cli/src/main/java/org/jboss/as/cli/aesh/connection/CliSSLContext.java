/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh.connection;

import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.SSLConfig;
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

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CliSSLContext {

    private LazyDelegatingTrustManager trustManager;
    private SSLContext sslContext;

    public CliSSLContext(SSLConfig sslConfig) throws CliInitializationException {
        init(sslConfig);
    }

    public LazyDelegatingTrustManager getTrustManager() {
        return trustManager;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    /**
     * Initialise the SSLContext and associated TrustManager for this CommandContext.
     *
     * If no configuration is specified the default mode of operation will be to use a lazily initialised TrustManager with no
     * KeyManager.
     */
    private void init(SSLConfig sslConfig) throws CliInitializationException {
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

        trustManager = new LazyDelegatingTrustManager(trustStore, trustStorePassword, modifyTrustStore);
        trustManagers = new TrustManager[] { trustManager };

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);

            this.sslContext = sslContext;
        } catch (GeneralSecurityException e) {
            throw new CliInitializationException(e);
        }
    }

}
