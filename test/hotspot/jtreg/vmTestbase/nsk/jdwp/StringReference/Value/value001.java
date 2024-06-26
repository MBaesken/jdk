/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package nsk.jdwp.StringReference.Value;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdwp.*;

import java.io.*;
import java.util.*;

public class value001 {
    static final int JCK_STATUS_BASE = 95;
    static final int PASSED = 0;
    static final int FAILED = 2;
    static final String PACKAGE_NAME = "nsk.jdwp.StringReference.Value";
    static final String TEST_CLASS_NAME = PACKAGE_NAME + "." + "value001";
    static final String DEBUGEE_CLASS_NAME = TEST_CLASS_NAME + "a";

    static final String JDWP_COMMAND_NAME = "StringReference.Value";
    static final int JDWP_COMMAND_ID = JDWP.Command.StringReference.Value;

    public static void main (String argv[]) {
        int result = run(argv, System.out);
        if (result != 0) {
            throw new RuntimeException("Test failed");
        }
    }

    public static int run(String argv[], PrintStream out) {
        return new value001().runIt(argv, out);
    }

    public int runIt(String argv[], PrintStream out) {

        boolean success = true;

        try {
            ArgumentHandler argumentHandler = new ArgumentHandler(argv);
            Log log = new Log(out, argumentHandler);

            try {

                Binder binder = new Binder(argumentHandler, log);
                log.display("Start debugee VM");
                Debugee debugee = binder.bindToDebugee(DEBUGEE_CLASS_NAME);
                Transport transport = debugee.getTransport();
                IOPipe pipe = debugee.createIOPipe();

                log.display("Waiting for VM_INIT event");
                debugee.waitForVMInit();

                log.display("Querying for IDSizes");
                debugee.queryForIDSizes();

                log.display("Resume debugee VM");
                debugee.resume();

                log.display("Waiting for command: " + "ready");
                String cmd = pipe.readln();
                log.display("Received command: " + cmd);

                try {

                    // create string in debugee

                    String originalStringValue = "Testing string value";
                    long stringID = 0;

                    {
                        // Suspend debuggee to avoid GC
                        log.display("Suspending debuggee");
                        debugee.suspend();
                        log.display("Debuggee suspended");

                        log.display("Create command packet" + "CreateString"
                                    + " with string value: " + originalStringValue);
                        CommandPacket command = new CommandPacket(JDWP.Command.VirtualMachine.CreateString);
                        command.addString(originalStringValue);
                        command.setLength();

                        log.display("Waiting for reply to command");
                        ReplyPacket reply = debugee.receiveReplyFor(command);
                        log.display("Valid reply packet received");

                        log.display("Parsing reply packet");
                        reply.resetPosition();

                        stringID = reply.getObjectID();
                        log.display("  stringID: " + stringID);

                        // Disable garbage collection of the String
                        log.display("Disabling collection of String object");
                        command = new CommandPacket(JDWP.Command.ObjectReference.DisableCollection);
                        command.addObjectID(stringID);
                        command.setLength();
                        reply = debugee.receiveReplyFor(command);
                        reply.resetPosition();
                        log.display("Collection disabled");

                        // Resume debugee now that we have disabled collection of the String
                        log.display("Resuming debuggee");
                        debugee.resume();
                        log.display("Debuggee resumed");
                    }

                    // begint test of JDWP command

                    log.display("Create command packet" + JDWP_COMMAND_NAME
                                + "with stringID: " + stringID);
                    CommandPacket command = new CommandPacket(JDWP_COMMAND_ID);
                    command.addObjectID(stringID);
                    command.setLength();

                    log.display("Sending command packet:\n" + command);
                    transport.write(command);

                    log.display("Waiting for reply packet");
                    ReplyPacket reply = new ReplyPacket();
                    transport.read(reply);
                    log.display("Reply packet received:\n" + reply);

                    log.display("Checking reply packet header");
                    reply.checkHeader(command.getPacketID());

                    log.display("Parsing reply packet:");
                    reply.resetPosition();

                    String stringValue = reply.getString();
                    log.display("  stringValue:   " + stringValue);

                    if (! stringValue.equals(originalStringValue)) {
                        log.complain("Received value does not equals original value:"
                                        + originalStringValue);
                        success = false;
                    }

                    if (! reply.isParsed()) {
                        log.complain("Extra trailing bytes found in reply packet at: " + reply.currentPosition());
                        success = false;
                    } else {
                        log.display("Reply packet parsed successfully");
                    }

                    // end test of JDWP command

                    // Re-enable garbage collection of the String
                    log.display("Enabling collection of String object");
                    command = new CommandPacket(JDWP.Command.ObjectReference.EnableCollection);
                    command.addObjectID(stringID);
                    command.setLength();
                    reply = debugee.receiveReplyFor(command);
                    reply.resetPosition();
                    log.display("Collection enabled");

                } catch (Exception e) {
                    log.complain("Caught exception while testing JDWP command: " + e);
                    success = false;
                } finally {
                    log.display("Sending command: " + "quit");
                    pipe.println("quit");

                    log.display("Waiting for debugee exits");
                    int code = debugee.waitFor();
                    if (code == JCK_STATUS_BASE + PASSED) {
                        log.display("Debugee PASSED with exit code: " + code);
                    } else {
                        log.complain("Debugee FAILED with exit code: " + code);
                        success = false;
                    }
                }

            } catch (Exception e) {
                log.complain("Caught unexpected exception while communicating with debugee: " + e);
                e.printStackTrace(out);
                success = false;
            }

            if (!success) {
                log.complain("TEST FAILED");
                return FAILED;
            }

        } catch (Exception e) {
            out.println("Caught unexpected exception while starting the test: " + e);
            e.printStackTrace(out);
            out.println("TEST FAILED");
            return FAILED;
        }

        out.println("TEST PASSED");
        return PASSED;

    }

}
