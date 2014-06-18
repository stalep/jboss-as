package org.jboss.as.cli.connection;

import org.jboss.aesh.console.AeshConsoleBufferBuilder;
import org.jboss.aesh.console.AeshInputProcessorBuilder;
import org.jboss.aesh.console.ConsoleBuffer;
import org.jboss.aesh.console.InputProcessor;
import org.jboss.aesh.console.Prompt;
import org.jboss.as.cli.command.CliCommandInvocation;
import org.jboss.as.protocol.GeneralTimeoutHandler;
import org.jboss.sasl.callback.DigestHashCallback;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import javax.security.sasl.SaslException;
import java.io.IOException;

public class AuthenticationCallbackHandler implements CallbackHandler {

    private CliCommandInvocation commandInvocation;
    // After the CLI has connected the physical connection may be re-established numerous times.
    // for this reason we cache the entered values to allow for re-use without pestering the end
    // user.
    private String realm = null;
    private boolean realmShown = false;

    private String username;
    private char[] password;
    private String digest;
    private GeneralTimeoutHandler timeoutHandler = new GeneralTimeoutHandler();

    public AuthenticationCallbackHandler(String username, char[] password,
                                         CliCommandInvocation commandInvocation) {
        // A local cache is used for scenarios where no values are specified on the command line
        // and the user wishes to use the connect command to establish a new connection.
        this.username = username;
        this.password = password;
        this.commandInvocation = commandInvocation;
    }

    public AuthenticationCallbackHandler(String username, String digest) {
        this.username = username;
        this.digest = digest;
    }

    @Override
    public void handle(final Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        try {
            timeoutHandler.suspendAndExecute(new Runnable() {

                @Override
                public void run() {

                    try {
                        dohandle(callbacks);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } catch (UnsupportedCallbackException e) {
                        throw new RuntimeException(e);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else if (e.getCause() instanceof UnsupportedCallbackException) {
                throw (UnsupportedCallbackException) e.getCause();
            }
            throw e;
        }

    }

    private void dohandle(Callback[] callbacks) throws IOException, UnsupportedCallbackException, InterruptedException {
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
            } else if (current instanceof RealmChoiceCallback) {
                throw new UnsupportedCallbackException(current, "Realm choice not currently supported.");
            } else if (current instanceof NameCallback) {
                NameCallback ncb = (NameCallback) current;
                if (username == null) {
                    showRealm();
                    username = promptForInput("Username: ", null, commandInvocation);
                    if (username == null || username.length() == 0) {
                        throw new SaslException("No username supplied.");
                    }
                }
                ncb.setName(username);
            } else if (current instanceof PasswordCallback && digest == null) {
                // If a digest had been set support for PasswordCallback is disabled.
                PasswordCallback pcb = (PasswordCallback) current;
                if (password == null) {
                    showRealm();
                    String temp = promptForInput("Password: ", '\u0000', commandInvocation);
                    if (temp != null) {
                        password = temp.toCharArray();
                    }
                }
                pcb.setPassword(password);
            } else if (current instanceof DigestHashCallback && digest != null) {
                // We don't support an interactive use of this callback so it must have been set in advance.
                DigestHashCallback dhc = (DigestHashCallback) current;
                dhc.setHexHash(digest);
            } else {
                commandInvocation.getShell().err().println("Unexpected Callback " + current.getClass().getName());
                throw new UnsupportedCallbackException(current);
            }
        }
    }

    private String promptForInput(String prompt, Character mask,
                                  CliCommandInvocation invocation) throws IOException, InterruptedException {

        ConsoleBuffer consoleBuffer = new AeshConsoleBufferBuilder()
                .shell(invocation.getShell())
                .prompt(new Prompt(prompt, mask))
                .create();
        InputProcessor inputProcessor = new AeshInputProcessorBuilder()
                .consoleBuffer(consoleBuffer)
                .create();

        consoleBuffer.displayPrompt();
        String result;
        do {
            result = inputProcessor.parseOperation(invocation.getInput());
        }
        while(result == null );
        return result;
    }

    private void showRealm() {
        if (realmShown == false && realm != null) {
            realmShown = true;
            commandInvocation.getShell().out().println("Authenticating against security realm: " + realm);
        }
    }
}