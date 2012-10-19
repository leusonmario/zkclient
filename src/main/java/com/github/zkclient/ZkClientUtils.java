/**
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.zkclient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.zkclient.exception.ZkInterruptedException;

public class ZkClientUtils {

    public static RuntimeException convertToRuntimeException(Throwable e) {
        if (e instanceof RuntimeException) {
            return (RuntimeException) e;
        }
        retainInterruptFlag(e);
        return new RuntimeException(e);
    }

    /**
     * This sets the interrupt flag if the catched exception was an {@link InterruptedException}. Catching such an
     * exception always clears the interrupt flag.
     * 
     * @param catchedException
     *            The catched exception.
     */
    public static void retainInterruptFlag(Throwable catchedException) {
        if (catchedException instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public static void rethrowInterruptedException(Throwable e) throws InterruptedException {
        if (e instanceof InterruptedException) {
            throw (InterruptedException) e;
        }
        if (e instanceof ZkInterruptedException) {
            throw (ZkInterruptedException) e;
        }
    }
    public static String leadingZeros(long number, int numberOfLeadingZeros) {
        return String.format("%0" + numberOfLeadingZeros + "d", number);
    }

    public static String toString(ZkClient zkClient) {
        return toString(zkClient, "/", PathFilter.ALL);
    }

    public static String toString(ZkClient zkClient, String startPath, PathFilter pathFilter) {
        final int level = 1;
        final StringBuilder builder = new StringBuilder("+ (" + startPath + ")");
        builder.append("\n");
        addChildrenToStringBuilder(zkClient, pathFilter, level, builder, startPath);
        return builder.toString();
    }

    private static void addChildrenToStringBuilder(ZkClient zkClient, PathFilter pathFilter, final int level, final StringBuilder builder, final String startPath) {
        final List<String> children = zkClient.getChildren(startPath);
        for (final String node : children) {
            String nestedPath;
            if (startPath.endsWith("/")) {
                nestedPath = startPath + node;
            } else {
                nestedPath = startPath + "/" + node;
            }
            if (pathFilter.showChilds(nestedPath)) {
                builder.append(getSpaces(level - 1) + "'-" + "+" + node + "\n");
                addChildrenToStringBuilder(zkClient, pathFilter, level + 1, builder, nestedPath);
            } else {
                builder.append(getSpaces(level - 1) + "'-" + "-" + node + " (contents hidden)\n");
            }
        }
    }

    private static String getSpaces(final int level) {
        String s = "";
        for (int i = 0; i < level; i++) {
            s += "  ";
        }
        return s;
    }

    public static interface PathFilter {

        public static PathFilter ALL = new PathFilter() {

            @Override
            public boolean showChilds(String path) {
                return true;
            }
        };

        boolean showChilds(String path);
    }

    public final static String OVERWRITE_HOSTNAME_SYSTEM_PROPERTY = "zkclient.hostname.overwritten";

    public static String[] getLocalHostNames() {
        final Set<String> hostNames = new HashSet<String>();
        // we add localhost to this set manually, because if the ip 127.0.0.1 is
        // configured with more than one name in the /etc/hosts, only the first
        // name
        // is returned
        hostNames.add("localhost");
        try {
            final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            for (final Enumeration<NetworkInterface> ifaces = networkInterfaces; ifaces.hasMoreElements();) {
                final NetworkInterface iface = ifaces.nextElement();
                InetAddress ia = null;
                for (final Enumeration<InetAddress> ips = iface.getInetAddresses(); ips.hasMoreElements();) {
                    ia = ips.nextElement();
                    hostNames.add(ia.getCanonicalHostName());
                    hostNames.add(ipToString(ia.getAddress()));
                }
            }
        } catch (final SocketException e) {
            throw new RuntimeException("unable to retrieve host names of localhost");
        }
        return hostNames.toArray(new String[hostNames.size()]);
    }

    private static String ipToString(final byte[] bytes) {
        final StringBuffer addrStr = new StringBuffer();
        for (int cnt = 0; cnt < bytes.length; cnt++) {
            final int uByte = bytes[cnt] < 0 ? bytes[cnt] + 256 : bytes[cnt];
            addrStr.append(uByte);
            if (cnt < 3)
                addrStr.append('.');
        }
        return addrStr.toString();
    }

    public static int hostNamesInList(final String serverList, final String[] hostNames) {
        final String[] serverNames = serverList.split(",");
        for (int i = 0; i < hostNames.length; i++) {
            final String hostname = hostNames[i];
            for (int j = 0; j < serverNames.length; j++) {
                final String serverNameAndPort = serverNames[j];
                final String serverName = serverNameAndPort.split(":")[0];
                if (serverName.equalsIgnoreCase(hostname)) {
                    return j;
                }
            }
        }
        return -1;
    }

    public static boolean hostNameInArray(final String[] hostNames, final String hostName) {
        for (final String name : hostNames) {
            if (name.equalsIgnoreCase(hostName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPortFree(int port) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("localhost", port), 200);
            socket.close();
            return false;
        } catch (SocketTimeoutException e) {
            return true;
        } catch (ConnectException e) {
            return true;
        } catch (SocketException e) {
            if (e.getMessage().equals("Connection reset by peer")) {
                return true;
            }
            throw new RuntimeException(e);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getLocalhostName() {
        String property = System.getProperty(OVERWRITE_HOSTNAME_SYSTEM_PROPERTY);
        if (property != null && property.trim().length() > 0) {
            return property;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException e) {
            throw new RuntimeException("unable to retrieve localhost name");
        }
    }
}