package org.jboss.as.cli.connection;

import org.jboss.as.protocol.GeneralTimeoutHandler;
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
 * A trust manager that by default delegates to a lazily initialised TrustManager, this TrustManager also support both
 * temporarily and permanently accepting unknown server certificate chains.
 *
 * This class also acts as an aggregation of the configuration related to TrustStore handling.
 *
 * It is not intended that Certificate management requests occur if this class is registered to a SSLContext
 * with multiple concurrent clients.
 */
public class LazyDelegatingTrustManager implements X509TrustManager {

    // Configuration based state set on initialisation.
    private final String trustStore;
    private final String trustStorePassword;
    private final boolean modifyTrustStore;

    private Set<X509Certificate> temporarilyTrusted = new HashSet<>();
    private X509TrustManager delegate;
    private final GeneralTimeoutHandler timeoutHandler;

    public LazyDelegatingTrustManager(String trustStore, String trustStorePassword,
                                      GeneralTimeoutHandler timeoutHandler, boolean modifyTrustStore) {
        this.timeoutHandler = timeoutHandler;
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
    public void checkServerTrusted(final X509Certificate[] chain, String authType) throws CertificateException {
        boolean retry;
        do {
            retry = false;
            try {
                getDelegate().checkServerTrusted(chain, authType);
            } catch (CertificateException ce) {
                if (retry == false) {
                    timeoutHandler.suspendAndExecute(new Runnable() {

                        @Override
                        public void run() {
                            //TODO: need to implement this
                                    /*
                                try {
                                    handleSSLFailure(chain);
                                } catch (CommandLineException e) {
                                    throw new RuntimeException(e);
                                }
                                     */
                        }
                    });

                    if (delegate == null) {
                        retry = true;
                    } else {
                        throw ce;
                    }
                } else {
                    throw ce;
                }
            }
        } while (retry);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return getDelegate().getAcceptedIssuers();
    }

}