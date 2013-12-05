/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh.connection;

import org.jboss.as.protocol.StreamUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class LazyDelegatingTrustManager implements X509TrustManager {

    // Configuration based state set on initialisation.

    private final String trustStore;
    private final String trustStorePassword;
    private final boolean modifyTrustStore;

    private Set<X509Certificate> temporarilyTrusted = new HashSet<X509Certificate>();
    private Certificate[] lastFailedCert;
    private X509TrustManager delegate;

    public LazyDelegatingTrustManager(String trustStore, String trustStorePassword, boolean modifyTrustStore) {
        this.trustStore = trustStore;
        this.trustStorePassword = trustStorePassword;
        this.modifyTrustStore = modifyTrustStore;
    }

    /*
     * Methods to allow client interaction for certificate verification.
     */
    public boolean isModifyTrustStore() {
        return modifyTrustStore;
    }

    void setFailedCertChain(final Certificate[] chain) {
        this.lastFailedCert = chain;
    }

    public Certificate[] getLastFailedCertificateChain() {
        try {
            return lastFailedCert;
        } finally {
            // Only one chance to accept it.
            lastFailedCert = null;
        }
    }

    public synchronized void storeChainTemporarily(final Certificate[] chain) {
        for (Certificate current : chain) {
            if (current instanceof X509Certificate) {
                temporarilyTrusted.add((X509Certificate) current);
            }
        }
        delegate = null; // Triggers a reload on next use.
    }

    public synchronized void storeChainPermenantly(final Certificate[] chain) {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            KeyStore theTrustStore = KeyStore.getInstance("JKS");
            File trustStoreFile = new File(trustStore);
            if (trustStoreFile.exists()) {
                fis = new FileInputStream(trustStoreFile);
                theTrustStore.load(fis, trustStorePassword.toCharArray());
                StreamUtils.safeClose(fis);
                fis = null;
            } else {
                theTrustStore.load(null);
            }
            for (Certificate current : chain) {
                if (current instanceof X509Certificate) {
                    X509Certificate x509Current = (X509Certificate) current;
                    theTrustStore.setCertificateEntry(x509Current.getSubjectX500Principal().getName(), x509Current);
                }
            }

            fos = new FileOutputStream(trustStoreFile);
            theTrustStore.store(fos, trustStorePassword.toCharArray());

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to operate on trust store.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to operate on trust store.", e);
        } finally {
            StreamUtils.safeClose(fis);
            StreamUtils.safeClose(fos);
        }

        delegate = null; // Triggers a reload on next use.
    }

        /*
         * Internal Methods
         */

    private synchronized X509TrustManager getDelegate() {
        if (delegate == null) {
            FileInputStream fis = null;
            try {
                KeyStore theTrustStore = KeyStore.getInstance("JKS");
                File trustStoreFile = new File(trustStore);
                if (trustStoreFile.exists()) {
                    fis = new FileInputStream(trustStoreFile);
                    theTrustStore.load(fis, trustStorePassword.toCharArray());
                } else {
                    theTrustStore.load(null);
                }
                for (X509Certificate current : temporarilyTrusted) {
                    theTrustStore.setCertificateEntry(current.getSubjectX500Principal().getName(), current);
                }
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(theTrustStore);
                TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                for (TrustManager current : trustManagers) {
                    if (current instanceof X509TrustManager) {
                        delegate = (X509TrustManager) current;
                        break;
                    }
                }
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Unable to operate on trust store.", e);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to operate on trust store.", e);
            } finally {
                StreamUtils.safeClose(fis);
            }
        }
        if (delegate == null) {
            throw new IllegalStateException("Unable to create delegate trust manager.");
        }

        return delegate;
    }

        /*
         * X509TrustManager Methods
         */

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // The CLI is only verifying servers.
        getDelegate().checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            getDelegate().checkServerTrusted(chain, authType);
        } catch (CertificateException ce) {
            setFailedCertChain(chain);
            throw ce;
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return getDelegate().getAcceptedIssuers();
    }

}
