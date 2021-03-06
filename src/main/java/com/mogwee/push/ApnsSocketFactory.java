/*
 * Copyright 2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.mogwee.push;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

public class ApnsSocketFactory
{
    private final Type type;

    public static enum Type
    {
        PRODUCTION_PUSH,
        PRODUCTION_FEEDBACK,
        DEVELOPMENT_PUSH,
        DEVELOPMENT_FEEDBACK,
    }

    private final String host;
    private final int port;
    private final SSLSocketFactory socketFactory;

    /**
     * Creates a factory for creating sockets to send push notifications.
     *
     * @param keystore         path to keystore file
     * @param keystorePassword password for keystore
     * @param keystoreType     type of keystore, e.g., JKS
     * @param type             whether socket is for production or development
     * @throws GeneralSecurityException if there's an error initializing the keystore or TLS
     * @throws IOException              if there's an error opening the keystore or creating the socket
     */
    public ApnsSocketFactory(String keystore, String keystorePassword, String keystoreType, Type type) throws GeneralSecurityException, IOException
    {
        this(createContext(new File(keystore), keystorePassword, keystoreType), type);
    }

    /**
     * Creates a factory for creating sockets to send push notifications.
     *
     * @param context context used to create the underlying socket
     * @param type    whether socket is for production or development
     * @throws IOException if there's an error creating the socket
     */
    public ApnsSocketFactory(SSLContext context, Type type) throws IOException
    {
        this.type = type;

        switch (type) {
            case PRODUCTION_PUSH:
                this.host = "gateway.push.apple.com";
                break;
            case PRODUCTION_FEEDBACK:
                this.host = "feedback.push.apple.com";
                break;
            case DEVELOPMENT_PUSH:
                this.host = "gateway.sandbox.push.apple.com";
                break;
            case DEVELOPMENT_FEEDBACK:
                this.host = "feedback.sandbox.push.apple.com";
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }

        switch (type) {
            case PRODUCTION_PUSH:
            case DEVELOPMENT_PUSH:
                this.port = 2195;
                break;
            case PRODUCTION_FEEDBACK:
            case DEVELOPMENT_FEEDBACK:
                this.port = 2196;
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }

        this.socketFactory = context.getSocketFactory();
    }

    public Socket createSocket() throws IOException
    {
        return socketFactory.createSocket(host, port);
    }

    @Override
    public String toString()
    {
        return "ApnsSocketFactory_" + type;
    }

    private static SSLContext createContext(File keystore, String keystorePassword, String keystoreType) throws GeneralSecurityException, IOException
    {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("sunx509");
        KeyStore appleStore = KeyStore.getInstance("JKS");
        InputStream appleStoreInputStream = null;

        try {
            // created by com.mogwee.push.CreateAppleCertificateKeystore (in tests)
            appleStoreInputStream = ApnsSocketFactory.class.getResourceAsStream("/apple.keystore");
            appleStore.load(appleStoreInputStream, "apple".toCharArray());
        }
        finally {
            CloseableUtil.closeQuietly(appleStoreInputStream);
        }

        trustManagerFactory.init(appleStore);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("sunx509");
        SSLContext context = SSLContext.getInstance("TLS");
        char[] password = keystorePassword.toCharArray();
        KeyStore.ProtectionParameter passwordProtection = new KeyStore.PasswordProtection(password);
        KeyStore keyStore = KeyStore.Builder.newInstance(keystoreType, null, keystore, passwordProtection).getKeyStore();

        keyManagerFactory.init(keyStore, password);
        context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

        return context;
    }
}
