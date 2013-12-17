/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.as.cli.aesh.commands;

import org.jboss.aesh.cl.Arguments;
import org.jboss.aesh.cl.CommandDefinition;
import org.jboss.aesh.console.command.Command;
import org.jboss.aesh.console.command.CommandOperation;
import org.jboss.aesh.console.command.CommandResult;
import org.jboss.aesh.console.command.ConsoleCommand;
import org.jboss.aesh.terminal.Key;
import org.jboss.aesh.terminal.Shell;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.ControllerAddress;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.aesh.ConnectionContext;
import org.jboss.as.cli.impl.ModelControllerClientFactory;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.logging.Logger;
import org.jboss.sasl.callback.DigestHashCallback;
import org.jboss.sasl.util.HexConverter;

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
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.jboss.as.cli.aesh.commands.ConnectionCommand.ConnectionStatus.*;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
@CommandDefinition(name = "connect", description = "connect to the specified JBoss instance")
public class ConnectionCommand implements Command<CliCommandInvocation>, ConsoleCommand, CallbackHandler {

    @Arguments(defaultValue = {"localhost"})
    private List<String> host;

    private static final Logger log = Logger.getLogger(ConnectionCommand.class);
    private static final String[] FINGERPRINT_ALGORITHMS = new String[] { "MD5", "SHA1" };
    private boolean attached = false;
    private Shell shell;
    private ConnectionStatus status = START;
    private Certificate[] lastChain;
    private ConnectionContext ctx;
    private String realm = null;
    private boolean realmShown = false;
    private CountDownLatch latch;

    private StringBuilder username;
    private StringBuilder password;
    private String digest;

    public ConnectionCommand() {
        this.username = new StringBuilder();
        this.password = new StringBuilder();
    }

    @Override
    public CommandResult execute(final CliCommandInvocation commandInvocation) throws IOException {
        this.ctx = commandInvocation.getCommandContext();

        if(host != null && host.size() > 0) {
            this.shell = commandInvocation.getShell();
            attached = true;
            commandInvocation.attachConsoleCommand(this);
            connectController(host.get(0));
        }

        return CommandResult.SUCCESS;
    }


    private void connectController(String controller) {
        try {
            ControllerAddress address = ctx.getAddressResolver().resolveAddress(controller);

            if(log.isDebugEnabled()) {
                log.debug("connecting to " + address.getHost() + ':' + address.getPort() + " as " + username);
            }
            ModelControllerClient tempClient = ModelControllerClientFactory.CUSTOM.
                    getClient(address, this, ctx.doDisableLocalAuth(), ctx.getSSLContext().getSslContext(),
                            ctx.getConnectionTimeout(), ctx);

            tryConnection(tempClient, address);
            //if no exceptions are thrown we bind it
            ctx.bindClient(tempClient, address);
            log.info("we're connected...");
            System.out.println("we're connected");
            release();
        }
        catch (CommandLineException | IOException e) {
            e.printStackTrace();
        }
    }

       /**
     * Used to make a call to the server to verify that it is possible to connect.
     */
    private void tryConnection(final ModelControllerClient client,
                                  ControllerAddress address) throws CommandLineException {
        try {
            status = TRY_CONNECTION;
            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.setOperationName(Util.READ_ATTRIBUTE);
            builder.addProperty(Util.NAME, Util.NAME);

            client.execute(builder.buildRequest());
            // We don't actually care what the response is we just want to be sure the ModelControllerClient
            // does not throw an Exception.
            status = CONNECTED;
        }
        catch (Exception e) {
            try {
                Throwable current = e;
                while (current != null) {
                    if (current instanceof SaslException) {
                        throw new CommandLineException("Unable to authenticate against controller at " + address.getHost() + ":" + address.getPort(), current);
                    }
                    if (current instanceof SSLException) {
                        handleSSLFailure(ctx);
                    }
                    current = current.getCause();
                }

                // We don't know what happened, most likely a timeout.
                throw new CommandLineException("The controller is not available at " + address.getHost() + ":" + address.getPort(), e);
            }
            finally {
                StreamUtils.safeClose(client);
            }
        }
    }

    private void disconnect() {
        attached = false;
        if(latch != null)
            latch.countDown();
        ctx.bindClient(null, null);
    }

    private void release() {
        attached = false;
    }


    /**
     * Handle the last SSL failure, prompting the user to accept or reject the certificate of the remote server.
     */
    private void handleSSLFailure(ConnectionContext ctx) throws CommandLineException {

        if (ctx.getSSLContext().getTrustManager() == null || (lastChain = ctx.getSSLContext().getTrustManager().getLastFailedCertificateChain()) == null) {
            return;
        }
        printLine("Unable to connect due to unrecognised server certificate");
        status = CERTIFICATE;

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
        if (ctx.getSSLContext().getTrustManager().isModifyTrustStore()) {
            printLine("Accept certificate? [N]o, [T]emporarily, [P]ermenantly : ");
        }
        else
            printLine("Accept certificate? [N]o, [T]emporarily : ");

    }



    private void printLine(String line) {
        shell.out().println(line);
    }

    @Override
    public void processOperation(CommandOperation operation) throws IOException {
        if(status == CERTIFICATE) {
            if(operation.getInputKey() == Key.n ||
                    operation.getInputKey() == Key.N) {
                //not accepting certificate
            }
            else if(operation.getInputKey() == Key.t ||
                    operation.getInputKey() == Key.T) {
                ctx.getSSLContext().getTrustManager().storeChainTemporarily(lastChain);
            }
            else if(operation.getInputKey() == Key.p ||
                    operation.getInputKey() == Key.P) {
                if (ctx.getSSLContext().getTrustManager().isModifyTrustStore()) {
                    ctx.getSSLContext().getTrustManager().storeChainPermenantly(lastChain);
                }

            }
        }
        else if(status == USERNAME) {
            if(operation.getInputKey() == Key.ENTER) {
                if(username.length() < 1) {
                    shell.out().println("Need to give a username!");
                    shell.out().print("Username: ");
                }
                else if(latch != null && latch.getCount() > 0)
                    latch.countDown();
            }
            else
                username.append(operation.getInputKey().getAsChar());
        }
        else if(status == PASSWORD) {
            if(operation.getInputKey() == Key.ENTER) {
                if(username.length() < 1) {
                    shell.out().println("Need to give a password!");
                    shell.out().print("Password: ");
                }
                else if(latch != null && latch.getCount() > 0)
                    latch.countDown();
            }
            else
                password.append(operation.getInputKey().getAsChar());

        }
    }

    @Override
    public boolean isAttached() {
        return attached;
    }

    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        // Special case for anonymous authentication to avoid prompting user for their name.
        if (callbacks.length == 1 && callbacks[0] instanceof NameCallback) {
            ((NameCallback) callbacks[0]).setName("anonymous CLI user");
            return;
        }

        for (Callback current : callbacks) {
            if (current instanceof RealmCallback) {
                RealmCallback rcb = (RealmCallback) current;
                String defaultText = rcb.getDefaultText();
                realm = defaultText;
                rcb.setText(defaultText); // For now just use the realm suggested.
            }
            else if (current instanceof RealmChoiceCallback) {
                throw new UnsupportedCallbackException(current, "Realm choice not currently supported.");
            }
            else if (current instanceof NameCallback) {
                NameCallback ncb = (NameCallback) current;
                if (username == null) {
                    printLine("Authenticating against security realm: " + realm);
                    latch = new CountDownLatch(1);
                    shell.out().print("Username: ");
                    status = USERNAME;
                    try {
                        latch.await();
                        ncb.setName(username.toString());

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (username == null || username.length() == 0) {
                        throw new SaslException("No username supplied.");
                    }
                }
                else
                    ncb.setName(username.toString());
            }
            else if (current instanceof PasswordCallback && digest == null) {
                // If a digest had been set support for PasswordCallback is disabled.
                PasswordCallback pcb = (PasswordCallback) current;
                if (password == null) {
                    printLine("Authenticating against security realm: " + realm);
                    String temp;
                    latch = new CountDownLatch(1);
                    shell.out().println("Password: ");
                    status = PASSWORD;
                    try {
                        latch.await();
                        pcb.setPassword(password.toString().toCharArray());
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                else
                    pcb.setPassword(password.toString().toCharArray());

            }
            else if (current instanceof DigestHashCallback && digest != null) {
                // We don't support an interactive use of this callback so it must have been set in advance.
                DigestHashCallback dhc = (DigestHashCallback) current;
                dhc.setHexHash(digest);
            }
            else {
                log.error("Unexpected Callback " + current.getClass().getName());
                throw new UnsupportedCallbackException(current);
            }
        }
    }

    enum ConnectionStatus {
        START,
        TRY_CONNECTION,
        CONNECTED,
        USERNAME,
        PASSWORD,
        CERTIFICATE;
    }

    private Map<String, String> generateFingerprints(final X509Certificate cert) throws CommandLineException  {
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
