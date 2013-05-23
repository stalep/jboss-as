/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import org.jboss.sasl.callback.DigestHashCallback;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import java.io.IOException;

/**
 * @author Alexey Loubyansky
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class AuthenticationCallbackHandler implements CallbackHandler {

    // After the CLI has connected the physical connection may be re-established numerous times.
    // for this reason we cache the entered values to allow for re-use without pestering the end
    // user.
    private String username;
    private char[] password;
    private String digest;

    public AuthenticationCallbackHandler(String username, char[] password) {
        // A local cache is used for scenarios where no values are specified on the command line
        // and the user wishes to use the connect command to establish a new connection.
        this.username = username;
        this.password = password;
    }

    public AuthenticationCallbackHandler(String username, String digest) {
        this.username = username;
        this.digest = digest;
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
                rcb.setText(defaultText); // For now just use the realm suggested.
            } else if (current instanceof RealmChoiceCallback) {
                throw new UnsupportedCallbackException(current, "Realm choice not currently supported.");
            } else if (current instanceof NameCallback) {
                NameCallback ncb = (NameCallback) current;
                ncb.setName(username);
            } else if (current instanceof PasswordCallback && digest == null) {
                // If a digest had been set support for PasswordCallback is disabled.
                PasswordCallback pcb = (PasswordCallback) current;
                pcb.setPassword(password);
            } else if (current instanceof DigestHashCallback && digest != null) {
                // We don't support an interactive use of this callback so it must have been set in advance.
                DigestHashCallback dhc = (DigestHashCallback) current;
                dhc.setHexHash(digest);
            } else {
                //error("Unexpected Callback " + current.getClass().getName());
                throw new UnsupportedCallbackException(current);
            }
        }
    }


}
