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
package org.jboss.as.cli.callback;

import org.jboss.aesh.console.ConsoleCallback;
import org.jboss.aesh.console.ConsoleOutput;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.Console;
import org.jboss.as.cli.impl.ModelControllerClientFactory;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.cli.security.LazyDelagatingTrustManager;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.logging.Logger;
import org.jboss.sasl.util.HexConverter;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ConsoleLoginCallback implements ConsoleCallback {

    private static final String[] FINGERPRINT_ALGORITHMS = new String[] { "MD5", "SHA1" };

    private static final Logger log = Logger.getLogger(ConsoleLoginCallback.class);
    private CommandContext commandContext;
    private Console console;
    private String username;
    private String password;
    private String defaultHost;
    private String defaultProtocol;
    private int defaultPort;
    private int connectionTimeout;
    private SSLContext sslContext;
    private ModelControllerClient modelClient;
    private LoginState loginState;


    /** The TrustManager in use by the SSLContext, a reference is kept to rejected certificates can be captured. */
    private LazyDelagatingTrustManager trustManager;

    public ConsoleLoginCallback(CommandContext commandContext,
                                LazyDelagatingTrustManager trustManager,
                                Console console,
                                String defaultHost,
                                int defaultControlerPort,
                                String defaultProtocol,
                                int timeout,
                                SSLContext sslContext) {
        this.trustManager = trustManager;
        this.commandContext = commandContext;
        this.console = console;
        this.defaultHost = defaultHost;
        this.defaultPort = defaultControlerPort;
        this.defaultProtocol = defaultProtocol;
        this.connectionTimeout = timeout;
        this.sslContext = sslContext;
        this.loginState = LoginState.TRY_CONNECT;
        setup();
    }

    @Override
    public int readConsoleOutput(ConsoleOutput output) throws IOException {
        //we are expecting user to accept (or not) certificate
        if(loginState == LoginState.SERTIFICATE) {
            String response = output.getBuffer();
            if (response != null && response.length() == 1) {
                switch (response.toLowerCase(Locale.ENGLISH).charAt(0)) {
                    case 'n':
                        disconnect();
                    case 't':
                        trustManager.storeChainTemporarily(trustManager.getLastFailedCertificateChain());
                        connect();
                    case 'p':
                        if (trustManager.isModifyTrustStore()) {
                            trustManager.storeChainPermenantly(trustManager.getLastFailedCertificateChain());
                            connect();
                        }
                }
            }
        }
        else if(loginState == LoginState.USERNAME) {
            username = output.getBuffer();
            console.setPrompt("Password: ");
            loginState = LoginState.PASSWORD;
        }
        else if(loginState == LoginState.PASSWORD) {
            password = output.getBuffer();
            if(password != null && password.length() > 0)
                connect();
        }
        return 0;
    }

    private void setup() {
        if(loginState == LoginState.TRY_CONNECT) {
            try {
                connect();
            }
            catch (IOException e) {
                console.setCallback(this);
                console.setPrompt("Username: ");
                loginState = LoginState.USERNAME;
            }
        }
        //atm this can never happen
        else {
            console.setCallback(this);
            console.setPrompt("Username: ");
            loginState = LoginState.USERNAME;
        }
    }

    //cleanup and go back to initial status
    private void disconnect() {
        commandContext.bindClient(null);
    }

    private void connect() throws IOException {
        CallbackHandler cbh = new AuthenticationCallbackHandler(username, password);
        if(log.isDebugEnabled()) {
            log.debug("connecting to " +  + ':' + defaultPort + " as " + username);
        }
        modelClient = ModelControllerClientFactory.CUSTOM.
                getClient(defaultProtocol, defaultHost, defaultPort, cbh, sslContext, connectionTimeout,
                        (ModelControllerClientFactory.ConnectionCloseHandler) commandContext);

        try {
            tryConnection(modelClient);
            if(loginState == LoginState.CONNECT) {
                //call commandContext and confirm that we have connection
                commandContext.bindClient(modelClient, defaultHost, defaultPort);
            }
            else if(loginState == LoginState.NOT_CONNECTING) {
                //call disconnect or just start with username again
                disconnect();
            }
            //the other states will be caught by user input
        } catch (CommandLineException e) {
            console.print(e.getMessage());
            console.printNewLine();
        }

    }

    /**
     * Used to make a call to the server to verify that it is possible to connect.
     */
    private void tryConnection(final ModelControllerClient client) throws CommandLineException {
        try {
            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.setOperationName(Util.READ_ATTRIBUTE);
            builder.addProperty(Util.NAME, Util.NAME);

            client.execute(builder.buildRequest());
            loginState = LoginState.CONNECT;
            // We don't actually care what the response is we just want to be sure the ModelControllerClient
            // does not throw an Exception.
        } catch (Exception e) {
            try {
                if(e != null) {
                    if (e instanceof SaslException) {
                        throw new CommandLineException("Unable to authenticate against controller at " + defaultHost + ":" + defaultPort, e);
                    }
                    if (e instanceof SSLException) {
                        handleSSLFailure();
                            //throw new CommandLineException("Unable to negotiate SSL connection with controller at " + defaultHost + ":" + defaultPort);
                    }
                }
                // We don't know what happened, most likely a timeout.
                throw new CommandLineException("The controller is not available at " + defaultHost + ":" + defaultPort, e);
            } finally {
                StreamUtils.safeClose(client);
            }
        }
    }

    /**
     * Handle the last SSL failure, prompting the user to accept or reject the certificate of the remote server.
     *
     */
    private void handleSSLFailure() throws CommandLineException {
        Certificate[] lastChain;
        if (trustManager == null || (lastChain = trustManager.getLastFailedCertificateChain()) == null) {
            loginState = LoginState.NOT_CONNECTING;
            return;
        }
        //error("Unable to connect due to unrecognised server certificate");
        for (Certificate current : lastChain) {
            if (current instanceof X509Certificate) {
                X509Certificate x509Current = (X509Certificate) current;
                Map<String, String> fingerprints = generateFingerprints(x509Current);
                printLine("Subject    - " + x509Current.getSubjectX500Principal().getName());
                printLine("Issuer     - " + x509Current.getIssuerDN().getName());
                printLine("Valid From - " + x509Current.getNotBefore());
                printLine("Valid To   - " + x509Current.getNotAfter());
                for (String alg : fingerprints.keySet()) {
                    printLine(alg + " : " + fingerprints.get(alg));
                }
                printLine("");
            }
        }

        if (trustManager.isModifyTrustStore()) {
            console.print("Accept certificate? [N]o, [T]emporarily, [P]ermenantly : ");
        } else {
            console.print("Accept certificate? [N]o, [T]emporarily : ");
        }
        console.printNewLine();
        loginState = LoginState.SERTIFICATE;
    }

    private void printLine(String line) {
        console.print(line);
        console.printNewLine();
    }

    private Map<String, String> generateFingerprints(final X509Certificate cert) throws CommandLineException {
        Map<String, String> fingerprints = new HashMap<String, String>(FINGERPRINT_ALGORITHMS.length);
        for (String current : FINGERPRINT_ALGORITHMS) {
            try {
                fingerprints.put(current, generateFingerPrint(current, cert.getEncoded()));
            } catch (GeneralSecurityException e) {
                throw new CommandLineException("Unable to generate fingerprint", e);
            }
        }

        return fingerprints;
    }

    private String generateFingerPrint(final String algorithm, final byte[] cert) throws GeneralSecurityException {
        StringBuilder sb = new StringBuilder();

        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] digested = md.digest(cert);
        String hex = HexConverter.convertToHexString(digested);
        boolean started = false;
        for (int i = 0; i < hex.length() - 1; i += 2) {
            if (started) {
                sb.append(":");
            } else {
                started = true;
            }
            sb.append(hex.substring(i, i + 2));
        }

        return sb.toString();
    }
}

enum LoginState {
    TRY_CONNECT,
    USERNAME,
    PASSWORD,
    SERTIFICATE,
    NOT_CONNECTING,
    CONNECT
}
