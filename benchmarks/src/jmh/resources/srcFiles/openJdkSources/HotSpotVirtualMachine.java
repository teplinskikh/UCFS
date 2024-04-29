/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.tools.attach;

import com.sun.tools.attach.AttachOperationFailedException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.spi.AttachProvider;
import jdk.internal.misc.VM;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Properties;
import java.util.stream.Collectors;

/*
 * The HotSpot implementation of com.sun.tools.attach.VirtualMachine.
 */

public abstract class HotSpotVirtualMachine extends VirtualMachine {

    private static final long CURRENT_PID = pid();

    @SuppressWarnings("removal")
    private static long pid() {
        PrivilegedAction<ProcessHandle> pa = () -> ProcessHandle.current();
        return AccessController.doPrivileged(pa).pid();
    }

    private static final boolean ALLOW_ATTACH_SELF;
    static {
        String s = VM.getSavedProperty("jdk.attach.allowAttachSelf");
        ALLOW_ATTACH_SELF = "".equals(s) || Boolean.parseBoolean(s);
    }

    HotSpotVirtualMachine(AttachProvider provider, String id)
        throws AttachNotSupportedException, IOException
    {
        super(provider, id);

        int pid;
        try {
            pid = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            throw new AttachNotSupportedException("Invalid process identifier: " + id);
        }

        if (!ALLOW_ATTACH_SELF && (pid == 0 || pid == CURRENT_PID)) {
            throw new IOException("Can not attach to current VM");
        }
    }

    /*
     * Load agent library
     * If isAbsolute is true then the agent library is the absolute path
     * to the library and thus will not be expanded in the target VM.
     * if isAbsolute is false then the agent library is just a library
     * name and it will be expended in the target VM.
     */
    private void loadAgentLibrary(String agentLibrary, boolean isAbsolute, String options)
        throws AgentLoadException, AgentInitializationException, IOException
    {
        if (agentLibrary == null) {
            throw new NullPointerException("agentLibrary cannot be null");
        }

        String msgPrefix = "return code: ";
        String errorMsg = "Failed to load agent library";
        try {
            InputStream in = execute("load",
                                     agentLibrary,
                                     isAbsolute ? "true" : "false",
                                     options);
            String result = readErrorMessage(in);
            if (result.isEmpty()) {
                throw new AgentLoadException("Target VM did not respond");
            } else if (result.startsWith(msgPrefix)) {
                int retCode = Integer.parseInt(result.substring(msgPrefix.length()));
                if (retCode != 0) {
                    throw new AgentInitializationException("Agent_OnAttach failed", retCode);
                }
            } else {
                if (!result.isEmpty()) {
                    errorMsg += ": " + result;
                }
                throw new AgentLoadException(errorMsg);
            }
        } catch (AttachOperationFailedException ex) {
            throw new AgentLoadException(errorMsg + ": " + ex.getMessage());
        }
    }

    /*
     * Load agent library - library name will be expanded in target VM
     */
    public void loadAgentLibrary(String agentLibrary, String options)
        throws AgentLoadException, AgentInitializationException, IOException
    {
        loadAgentLibrary(agentLibrary, false, options);
    }

    /*
     * Load agent - absolute path of library provided to target VM
     */
    public void loadAgentPath(String agentLibrary, String options)
        throws AgentLoadException, AgentInitializationException, IOException
    {
        loadAgentLibrary(agentLibrary, true, options);
    }

    /*
     * Load JPLIS agent which will load the agent JAR file and invoke
     * the agentmain method.
     */
    public void loadAgent(String agent, String options)
        throws AgentLoadException, AgentInitializationException, IOException
    {
        if (agent == null) {
            throw new NullPointerException("agent cannot be null");
        }

        String args = agent;
        if (options != null) {
            args = args + "=" + options;
        }
        try {
            loadAgentLibrary("instrument", args);
        } catch (AgentInitializationException x) {
            /*
             * Translate interesting errors into the right exception and
             * message (FIXME: create a better interface to the instrument
             * implementation so this isn't necessary)
             */
            int rc = x.returnValue();
            switch (rc) {
                case JNI_ENOMEM:
                    throw new AgentLoadException("Insuffient memory");
                case ATTACH_ERROR_BADJAR:
                    throw new AgentLoadException(
                        "Agent JAR not found or no Agent-Class attribute");
                case ATTACH_ERROR_NOTONCP:
                    throw new AgentLoadException(
                        "Unable to add JAR file to system class path");
                case ATTACH_ERROR_STARTFAIL:
                    throw new AgentInitializationException(
                        "Agent JAR loaded but agent failed to initialize");
                default :
                    throw new AgentLoadException("" +
                        "Failed to load agent - unknown reason: " + rc);
            }
        }
    }

    /*
     * The possible errors returned by JPLIS's agentmain
     */
    private static final int JNI_ENOMEM                 = -4;
    private static final int ATTACH_ERROR_BADJAR        = 100;
    private static final int ATTACH_ERROR_NOTONCP       = 101;
    private static final int ATTACH_ERROR_STARTFAIL     = 102;

    private static final int ATTACH_ERROR_BADVERSION = 101;

    /*
     * Send "properties" command to target VM
     */
    public Properties getSystemProperties() throws IOException {
        InputStream in = null;
        Properties props = new Properties();
        try {
            in = executeCommand("properties");
            props.load(in);
        } finally {
            if (in != null) in.close();
        }
        return props;
    }

    public Properties getAgentProperties() throws IOException {
        InputStream in = null;
        Properties props = new Properties();
        try {
            in = executeCommand("agentProperties");
            props.load(in);
        } finally {
            if (in != null) in.close();
        }
        return props;
    }

    private static final String MANAGEMENT_PREFIX = "com.sun.management.";

    private static boolean checkedKeyName(Object key) {
        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Invalid option (not a String): "+key);
        }
        if (!((String)key).startsWith(MANAGEMENT_PREFIX)) {
            throw new IllegalArgumentException("Invalid option: "+key);
        }
        return true;
    }

    private static String stripKeyName(Object key) {
        return ((String)key).substring(MANAGEMENT_PREFIX.length());
    }

    @Override
    public void startManagementAgent(Properties agentProperties) throws IOException {
        if (agentProperties == null) {
            throw new NullPointerException("agentProperties cannot be null");
        }
        String args = agentProperties.entrySet().stream()
            .filter(entry -> checkedKeyName(entry.getKey()))
            .map(entry -> stripKeyName(entry.getKey()) + "=" + escape(entry.getValue()))
            .collect(Collectors.joining(" "));
        executeJCmd("ManagementAgent.start " + args).close();
    }

    private String escape(Object arg) {
        String value = arg.toString();
        if (value.contains(" ")) {
            return "'" + value + "'";
        }
        return value;
    }

    @Override
    public String startLocalManagementAgent() throws IOException {
        executeJCmd("ManagementAgent.start_local").close();
        String prop = MANAGEMENT_PREFIX + "jmxremote.localConnectorAddress";
        return getAgentProperties().getProperty(prop);
    }



    public void localDataDump() throws IOException {
        executeCommand("datadump").close();
    }

    public InputStream remoteDataDump(Object ... args) throws IOException {
        return executeCommand("threaddump", args);
    }

    public InputStream dumpHeap(Object ... args) throws IOException {
        return executeCommand("dumpheap", args);
    }

    public InputStream heapHisto(Object ... args) throws IOException {
        return executeCommand("inspectheap", args);
    }

    public InputStream setFlag(String name, String value) throws IOException {
        return executeCommand("setflag", name, value);
    }

    public InputStream printFlag(String name) throws IOException {
        return executeCommand("printflag", name);
    }

    public InputStream executeJCmd(String command) throws IOException {
        return executeCommand("jcmd", command);
    }



    /*
     * Execute the given command in the target VM - specific platform
     * implementation must implement this.
     */
    abstract InputStream execute(String cmd, Object ... args)
        throws AgentLoadException, IOException;

    /*
     * Convenience method for simple commands
     */
    public InputStream executeCommand(String cmd, Object ... args) throws IOException {
        try {
            return execute(cmd, args);
        } catch (AgentLoadException x) {
            throw new InternalError("Should not get here", x);
        }
    }


    /*
     * Utility method to read an 'int' from the input stream. Ideally
     * we should be using java.util.Scanner here but this implementation
     * guarantees not to read ahead.
     */
    int readInt(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();

        int n;
        byte buf[] = new byte[1];
        do {
            n = in.read(buf, 0, 1);
            if (n > 0) {
                char c = (char)buf[0];
                if (c == '\n') {
                    break;                  
                } else {
                    sb.append(c);
                }
            }
        } while (n > 0);

        if (sb.length() == 0) {
            throw new IOException("Premature EOF");
        }

        int value;
        try {
            value = Integer.parseInt(sb.toString());
        } catch (NumberFormatException x) {
            throw new IOException("Non-numeric value found - int expected");
        }
        return value;
    }

    /*
     * Utility method to read data into a String.
     */
    String readErrorMessage(InputStream in) throws IOException {
        String s;
        StringBuilder message = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        while ((s = br.readLine()) != null) {
            if (message.length() > 0) {
                message.append(' ');
            }
            message.append(s);
        }
        return message.toString();
    }

    /*
     * Utility method to process the completion status after command execution.
     * If we get IOE during previous command execution, delay throwing it until
     * completion status has been read.
     */
    void processCompletionStatus(IOException ioe, String cmd, InputStream sis) throws AgentLoadException, IOException {
        int completionStatus;
        try {
            completionStatus = readInt(sis);
        } catch (IOException x) {
            sis.close();
            if (ioe != null) {
                throw ioe;
            } else {
                throw x;
            }
        }
        if (completionStatus != 0) {
            String message = readErrorMessage(sis);
            sis.close();

            if (completionStatus == ATTACH_ERROR_BADVERSION) {
                throw new IOException("Protocol mismatch with target VM");
            }

            if (message.isEmpty()) {
                message = "Command failed in target VM";
            }
            throw new AttachOperationFailedException(message);
        }
    }

    /*
     * InputStream for the socket connection to get target VM
     */
    abstract static class SocketInputStream extends InputStream {
        private long fd;

        public SocketInputStream(long fd) {
            this.fd = fd;
        }

        protected abstract int read(long fd, byte[] bs, int off, int len) throws IOException;
        protected abstract void close(long fd) throws IOException;

        public synchronized int read() throws IOException {
            byte b[] = new byte[1];
            int n = this.read(b, 0, 1);
            if (n == 1) {
                return b[0] & 0xff;
            } else {
                return -1;
            }
        }

        public synchronized int read(byte[] bs, int off, int len) throws IOException {
            if ((off < 0) || (off > bs.length) || (len < 0) ||
                ((off + len) > bs.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }
            return read(fd, bs, off, len);
        }

        public synchronized void close() throws IOException {
            if (fd != -1) {
                long toClose = fd;
                fd = -1;
                close(toClose);
            }
        }
    }


    private static long defaultAttachTimeout = 10000;
    private volatile long attachTimeout;

    /*
     * Return attach timeout based on the value of the sun.tools.attach.attachTimeout
     * property, or the default timeout if the property is not set to a positive
     * value.
     */
    long attachTimeout() {
        if (attachTimeout == 0) {
            synchronized(this) {
                if (attachTimeout == 0) {
                    try {
                        String s =
                            System.getProperty("sun.tools.attach.attachTimeout");
                        attachTimeout = Long.parseLong(s);
                    } catch (SecurityException se) {
                    } catch (NumberFormatException ne) {
                    }
                    if (attachTimeout <= 0) {
                       attachTimeout = defaultAttachTimeout;
                    }
                }
            }
        }
        return attachTimeout;
    }

    protected static void checkNulls(Object... args) {
        for (Object arg : args) {
            if (arg instanceof String s) {
                if (s.indexOf(0) >= 0) {
                    throw new IllegalArgumentException("illegal null character in command");
                }
            }
        }
    }
}