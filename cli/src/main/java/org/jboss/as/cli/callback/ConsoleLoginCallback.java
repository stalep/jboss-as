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
import org.jboss.as.cli.CliConnection;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.Console;
import org.jboss.as.cli.impl.ConsoleLoginState;
import org.jboss.as.cli.impl.ModelControllerClientFactory;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.logging.Logger;
import org.jboss.sasl.callback.DigestHashCallback;
import org.jboss.sasl.util.HexConverter;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import javax.security.sasl.SaslException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ConsoleLoginCallback implements ConsoleCallback, CallbackHandler, Runnable {

    private static final String[] FINGERPRINT_ALGORITHMS = new String[] { "MD5", "SHA1" };

    private static final Logger log = Logger.getLogger(ConsoleLoginCallback.class);
    private Console console;
    private CliConnection cliConnection;
    private CountDownLatch latch;


    public ConsoleLoginCallback(Console console, CliConnection cliConnection) {
        this.console = console;
        this.cliConnection = cliConnection;
    }

    @Override
    public void run() {
        setup();
    }

    @Override
    public int readConsoleOutput(ConsoleOutput output) throws IOException {
        //we are expecting user to accept (or not) certificate
        if(cliConnection.getState() == ConsoleLoginState.SERTIFICATE) {
            String response = output.getBuffer();
            if (response != null && response.length() == 1) {
                switch (response.toLowerCase(Locale.ENGLISH).charAt(0)) {
                    case 'n':
                        disconnect();
                    case 't':
                        cliConnection.getTrustManager().storeChainTemporarily(cliConnection.getTrustManager().getLastFailedCertificateChain());
                        connect();
                    case 'p':
                        if (cliConnection.getTrustManager().isModifyTrustStore()) {
                            cliConnection.getTrustManager().storeChainPermenantly(cliConnection.getTrustManager().getLastFailedCertificateChain());
                            connect();
                        }
                }
            }
        }
        else if(cliConnection.getState() == ConsoleLoginState.USERNAME) {
            String username = output.getBuffer();
            if(username != null && username.length() > 0) {
                cliConnection.setUsername(username);
                if(latch != null && latch.getCount() > 0)
                    latch.countDown();
            }
            else {
                printLine("Need to give a username!");
            }
        }
        else if(cliConnection.getState() == ConsoleLoginState.PASSWORD) {
            String password = output.getBuffer();
            if(password != null && password.length() > 0) {
                cliConnection.setPassword(password);
                if(latch != null && latch.getCount() > 0)
                    latch.countDown();
            }
            else {
                printLine("Need to give a password!");
            }
        }
        return 0;
    }

    private void setup() {
        if(cliConnection.getState() == ConsoleLoginState.DISCONNECTED ||
                cliConnection.getState() == ConsoleLoginState.CONNECTED) {
            try {
                connect();
            }
            catch (IOException e) {
                printLine("Unable to authenticate, type username and password manually:");
                cliConnection.setState( ConsoleLoginState.USERNAME);
            }
        }
    }

    //cleanup and go back to initial status
    private void disconnect() {
       cliConnection.getCommandContext().bindClient(null);
    }

    private void connect() throws IOException {
        //CallbackHandler cbh = new AuthenticationCallbackHandler(cliConnection.getUsername(), cliConnection.getPassword());
        if(log.isDebugEnabled()) {
            log.debug("connecting to " +  + ':' + cliConnection.getHost() + " as " + cliConnection.getUsername());
        }
        ModelControllerClient modelClient = ModelControllerClientFactory.CUSTOM.
                getClient(cliConnection.getProtocol(), cliConnection.getHost(), cliConnection.getPort(),
                        (CallbackHandler) this, cliConnection.getSslContext(), cliConnection.getConnectionTimeout(),
                        (ModelControllerClientFactory.ConnectionCloseHandler) cliConnection.getCommandContext());

        try {
            tryConnection(modelClient);
            if(cliConnection.getState() == ConsoleLoginState.CONNECTED) {
                //call commandContext and confirm that we have connection
                cliConnection.setModelClient(modelClient);
                cliConnection.getCommandContext().bindClient(modelClient, cliConnection.getHost(),
                        cliConnection.getPort());
                //commandContext.bindClient(modelClient, defaultHost, defaultPort);

            }
            else if(cliConnection.getState() == ConsoleLoginState.DISCONNECTED) {
                cliConnection.getCommandContext().bindClient(null, cliConnection.getHost(),
                        cliConnection.getPort());
                //call disconnect or just start with username again
                disconnect();
            }
            //the other states will be caught by user input
        }
        catch (CommandLineException e) {
            cliConnection.setState(ConsoleLoginState.DISCONNECTED);
            printLine(e.getMessage());
            cliConnection.getCommandContext().bindClient(null, cliConnection.getHost(), cliConnection.getPort());
        }

    }

    /**
     * Used to make a call to the server to verify that it is possible to connect.
     */
    private void tryConnection(final ModelControllerClient client) throws CommandLineException,IOException {
        try {
            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.setOperationName(Util.READ_ATTRIBUTE);
            builder.addProperty(Util.NAME, Util.NAME);

            client.execute(builder.buildRequest());
            cliConnection.setState(ConsoleLoginState.CONNECTED);
            // We don't actually care what the response is we just want to be sure the ModelControllerClient
            // does not throw an Exception.
        }
        catch (Exception e) {
            log.info("Connection exception: ",e);
            try {
                Throwable current = e;
                while(current != null) {
                    if (current instanceof SaslException) {
                           throw new CommandLineException("\nUnable to authenticate against controller at " +
                                    cliConnection.getHost()+ ":" + cliConnection.getPort(), current);
                    }
                    if (current instanceof SSLException) {
                        handleSSLFailure();
                            //throw new CommandLineException("Unable to negotiate SSL connection with controller at " + defaultHost + ":" + defaultPort);
                    }
                    current = current.getCause();
                }
                // We don't know what happened, most likely a timeout.
                log.info("Dont know what happened: ",e);
                throw new CommandLineException("The controller is not available at " +
                        cliConnection.getHost()+ ":" + cliConnection.getPort(), e);
            }
            finally {
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
        if (cliConnection.getTrustManager() == null || (lastChain = cliConnection.getTrustManager().getLastFailedCertificateChain()) == null) {
            cliConnection.setState( ConsoleLoginState.DISCONNECTED);
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

        if (cliConnection.getTrustManager().isModifyTrustStore()) {
            console.print("Accept certificate? [N]o, [T]emporarily, [P]ermenantly : ");
        } else {
            console.print("Accept certificate? [N]o, [T]emporarily : ");
        }
        console.printNewLine();
        cliConnection.setState( ConsoleLoginState.SERTIFICATE);
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

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

        String digest = null;
             // Special case for anonymous authentication to avoid prompting user for their name.
        if (callbacks.length == 1 && callbacks[0] instanceof NameCallback) {
            ((NameCallback) callbacks[0]).setName("anonymous CLI user");
            return;
        }

        for (Callback current : callbacks) {
            if (current instanceof RealmCallback) {
                RealmCallback rcb = (RealmCallback) current;
                String defaultText = rcb.getDefaultText();
                rcb.setText(defaultText); // For now just use the realm suggested.
            } else if (current instanceof RealmChoiceCallback) {
                throw new UnsupportedCallbackException(current, "Realm choice not currently supported.");
            } else if (current instanceof NameCallback) {
                NameCallback ncb = (NameCallback) current;
                latch = new CountDownLatch(1);
                console.setCallback(this);
                cliConnection.setState(ConsoleLoginState.USERNAME);
                console.setPrompt("Username: ");
                try {
                    latch.await();
                    ncb.setName(cliConnection.getUsername());
                }
                catch (InterruptedException e) {
                    ncb.setName(cliConnection.getUsername());
                }
            }
            else if (current instanceof PasswordCallback && digest == null) {
                // If a digest had been set support for PasswordCallback is disabled.
                PasswordCallback pcb = (PasswordCallback) current;
                latch = new CountDownLatch(1);
                console.setCallback(this);
                cliConnection.setState(ConsoleLoginState.PASSWORD);
                console.setPrompt("Password: ");
                try {
                    latch.await();
                    pcb.setPassword(cliConnection.getPassword().toCharArray());
                }
                catch (InterruptedException e) {
                    pcb.setPassword(cliConnection.getPassword().toCharArray());
                }
            }
            else if (current instanceof DigestHashCallback && digest != null) {
                // We don't support an interactive use of this callback so it must have been set in advance.
                DigestHashCallback dhc = (DigestHashCallback) current;
                dhc.setHexHash(digest);
            }
            else {
                //error("Unexpected Callback " + current.getClass().getName());
                throw new UnsupportedCallbackException(current);
            }
        }
    }
}
