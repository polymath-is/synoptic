package csight.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import csight.CSightTest;
import csight.invariants.AlwaysFollowedBy;
import csight.invariants.AlwaysPrecedes;
import csight.invariants.BinaryInvariant;
import csight.invariants.EventuallyHappens;
import csight.invariants.NeverFollowedBy;
import csight.model.fifosys.channel.channelstate.ImmutableMultiChState;
import csight.model.fifosys.gfsm.GFSM;
import csight.model.fifosys.gfsm.observed.ObsDistEventType;
import csight.model.fifosys.gfsm.observed.ObsFSMState;
import csight.model.fifosys.gfsm.observed.ObsMultFSMState;
import csight.model.fifosys.gfsm.observed.fifosys.ObsFifoSys;
import csight.model.fifosys.gfsm.observed.fifosys.ObsFifoSysState;
import csight.util.Util;

import synoptic.invariants.AlwaysFollowedInvariant;
import synoptic.invariants.AlwaysPrecedesInvariant;
import synoptic.invariants.NeverFollowedInvariant;
import synoptic.invariants.TemporalInvariantSet;
import synoptic.main.AbstractMain;
import synoptic.main.parser.TraceParser;
import synoptic.model.EventNode;
import synoptic.model.channelid.ChannelId;
import synoptic.model.event.DistEventType;

public class CSightMainTests extends CSightTest {

    public CSightMain dyn;
    public CSightOptions opts;

    public List<String> getBasicArgsStr() throws Exception {
        List<String> args = Util.newList();
        args.add("--mcPath");
        args.add(super.getMcPath());
        args.add("-o");
        args.add("test-output" + File.separator + "test");
        return args;
    }

    public List<String> getSpinArgsStr() throws Exception {
        List<String> args = Util.newList();
        args.add("--mcType");
        args.add("spin");
        args.add("--mcPath");
        args.add(super.getMcPath("spin"));
        args.add("-o");
        args.add("test-output" + File.separator + "test-spin");
        return args;
    }

    public List<String> getParallelArgs() throws Exception {
        List<String> args = Util.newList();
        args.add("--runParallel");
        args.add("-p");
        args.add("4");
        return args;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // Reset the SynopticMain singleton reference.
        AbstractMain.instance = null;
    }

    // //////////////////// Check error conditions during options processing.

    @Test(expected = OptionException.class)
    public void missingChannelSpec() throws Exception {
        List<String> args = getBasicArgsStr();
        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);
    }

    @Test(expected = OptionException.class)
    public void missingLogFiles() throws Exception {
        List<String> args = getBasicArgsStr();
        args.add("-q");
        args.add("M:0->1");
        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);
        dyn.run();
    }

    @Test(expected = OptionException.class)
    public void emptyLogFile() throws Exception {
        List<String> args = getBasicArgsStr();
        args.add("-q");
        args.add("M:0->1");
        args.add("../traces/EndToEndDynopticTests/empty-trace.txt");
        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);
        dyn.run();
    }

    // //////////////////// Test conversion of Synoptic invariants to CSight
    // invariants.

    @Test
    public void convertAFby() {
        DistEventType x = DistEventType.LocalEvent("x", 0);
        DistEventType y = DistEventType.LocalEvent("y", 0);

        TemporalInvariantSet synInvs = new TemporalInvariantSet();
        synInvs.add(new AlwaysFollowedInvariant(x, y, "t"));
        List<csight.invariants.BinaryInvariant> dynInvs = CSightMain
                .synInvsToDynInvs(synInvs);
        assertTrue(dynInvs.size() == 1);

        BinaryInvariant dInv = dynInvs.iterator().next();
        assertTrue(dInv instanceof AlwaysFollowedBy);
        assertTrue(dInv.getFirst().equals(x));
        assertTrue(dInv.getSecond().equals(y));
    }

    @Test
    public void convertAP() {
        DistEventType x = DistEventType.LocalEvent("x", 0);
        DistEventType y = DistEventType.LocalEvent("y", 0);

        TemporalInvariantSet synInvs = new TemporalInvariantSet();
        synInvs.add(new AlwaysPrecedesInvariant(x, y, "t"));
        List<csight.invariants.BinaryInvariant> dynInvs = CSightMain
                .synInvsToDynInvs(synInvs);
        assertTrue(dynInvs.size() == 1);

        BinaryInvariant dInv = dynInvs.iterator().next();
        assertTrue(dInv instanceof AlwaysPrecedes);
        assertTrue(dInv.getFirst().equals(x));
        assertTrue(dInv.getSecond().equals(y));
    }

    @Test
    public void convertNFby() {
        DistEventType x = DistEventType.LocalEvent("x", 0);
        DistEventType y = DistEventType.LocalEvent("y", 0);

        TemporalInvariantSet synInvs = new TemporalInvariantSet();
        synInvs.add(new NeverFollowedInvariant(x, y, "t"));
        List<csight.invariants.BinaryInvariant> dynInvs = CSightMain
                .synInvsToDynInvs(synInvs);
        assertTrue(dynInvs.size() == 1);

        BinaryInvariant dInv = dynInvs.iterator().next();
        assertTrue(dInv instanceof NeverFollowedBy);
        assertTrue(dInv.getFirst().equals(x));
        assertTrue(dInv.getSecond().equals(y));
    }

    @Test
    public void convertEventually() {
        DistEventType x = DistEventType.newInitialDistEventType();
        DistEventType y = DistEventType.LocalEvent("y", 0);

        TemporalInvariantSet synInvs = new TemporalInvariantSet();
        synInvs.add(new AlwaysFollowedInvariant(x, y, "t"));
        List<csight.invariants.BinaryInvariant> dynInvs = CSightMain
                .synInvsToDynInvs(synInvs);
        assertTrue(dynInvs.size() == 1);

        BinaryInvariant dInv = dynInvs.iterator().next();
        assertTrue(dInv instanceof EventuallyHappens);
        assertTrue(dInv.getFirst().equals(DistEventType.INITIALEventType));
        assertTrue(((EventuallyHappens) dInv).getEvent().equals(y));
        assertTrue(dInv.getSecond().equals(y));
    }

    // //////////////////// Test file parsing.

    @Test
    public void testParseEventsFromFiles() throws Exception {
        List<String> args = getBasicArgsStr();
        args.add("../traces/EndToEndDynopticTests/simple-po-concurrency/trace.txt");
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("M:0->1");
        args.add("-i");
        args.add("-d");
        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);
        dyn.initializeSynoptic();

        TraceParser parser = new TraceParser(opts.regExps,
                opts.partitionRegExp, opts.separatorRegExp, opts.dateFormat);
        List<String> logFilenames = opts.logFilenames;

        List<EventNode> parsedEvents = dyn.parseEventsFromFiles(parser,
                logFilenames);

        assertTrue(parsedEvents.size() == 4);
        assertTrue(dyn.getNumProcesses() == 2);

        assertTrue(dyn.getChannelIds().size() == 1);
        ChannelId cid = dyn.getChannelIds().get(0);

        int branchesProduct = 1;
        for (EventNode n : parsedEvents) {
            assertTrue(n.getEType() instanceof DistEventType);

            DistEventType etype = ((DistEventType) n.getEType());

            if (etype.getPid() == 0) {
                if (etype.isLocalEvent()) {
                    assertTrue(etype.getEType().equals("e1"));
                    branchesProduct *= 2;
                }

                if (etype.isCommEvent()) {
                    assertTrue(etype.getEType().equals("m"));
                    assertTrue(etype.isSendEvent());
                    assertTrue(etype.getChannelId() == cid);
                    branchesProduct *= 3;
                }

            } else if (etype.getPid() == 1) {
                if (etype.isLocalEvent()) {
                    assertTrue(etype.getEType().equals("f1"));
                    branchesProduct *= 5;
                }

                if (etype.isCommEvent()) {
                    assertTrue(etype.getEType().equals("m"));
                    assertTrue(etype.isRecvEvent());
                    assertTrue(etype.getChannelId() == cid);
                    branchesProduct *= 7;
                }
            }
        }
        // Make sure we've visited all cases above exactly once.
        assertTrue(branchesProduct == (2 * 3 * 5 * 7));
    }

    // //////////////////// Integration tests.

    private void runDynFromFileArgs(List<String> args) throws Exception {
        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);

        long startTime = System.currentTimeMillis();
        dyn.run();
        long endTime = System.currentTimeMillis();
        long msTime = (endTime - startTime);
        long sTime = msTime / 1000;
        logger.info("CSight run took: " + msTime + "ms ~ " + sTime + "s");
    }

    public List<String> getABPArgs() {
        List<String> args = Util.newList();
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("M:0->1;A:1->0");
        args.add("-i");
        args.add("-d");
        return args;
    }

    @Test
    public void runABPSuccess() throws Exception {
        List<String> args = getBasicArgsStr();
        args.addAll(getABPArgs());
        args.add("../traces/AlternatingBitProtocol/trace_po_sr_simple.txt");
        runDynFromFileArgs(args);
    }

    @Test
    public void runSpinABPSuccess() throws Exception {
        List<String> args = getSpinArgsStr();
        args.addAll(getABPArgs());
        args.add("../traces/AlternatingBitProtocol/trace_po_sr_simple.txt");
        runDynFromFileArgs(args);
    }

    @Test
    public void runABPSuccessParallel() throws Exception {
        List<String> args = getBasicArgsStr();
        args.addAll(getABPArgs());
        args.addAll(getParallelArgs());
        args.add("../traces/AlternatingBitProtocol/trace_po_sr_simple.txt");
        runDynFromFileArgs(args);
    }

    /**
     * This Alternating Bit Protocol has two terminal states. After the A ? a0
     * and after the A ? a1. This is almost full ABP without the timeouts.
     */
    @Test
    public void runABPTwoTerminalSuccess() throws Exception {
        List<String> args = getBasicArgsStr();
        args.addAll(getABPArgs());
        args.add("../traces/AlternatingBitProtocol/trace_po_sr_no_timeout.txt");
        runDynFromFileArgs(args);
    }

    /**
     * This Alternating Bit Protocol has two terminal states. After the A ? a0
     * and after the A ? a1. This is almost full ABP without the timeouts.
     */
    @Test
    public void runSpinABPTwoTerminalSuccess() throws Exception {
        List<String> args = getSpinArgsStr();
        args.addAll(getABPArgs());
        args.add("../traces/AlternatingBitProtocol/trace_po_sr_no_timeout.txt");
        runDynFromFileArgs(args);
    }

    @Test
    public void runABPTwoTerminalSuccessParallel() throws Exception {
        List<String> args = getBasicArgsStr();
        args.addAll(getABPArgs());
        args.addAll(getParallelArgs());
        args.add("../traces/AlternatingBitProtocol/trace_po_sr_no_timeout.txt");
        runDynFromFileArgs(args);
    }

    @Test
    public void runABPLongTraceSuccess() throws Exception {
        List<String> args = getBasicArgsStr();
        args.addAll(getABPArgs());
        args.add("../traces/AlternatingBitProtocol/trace_po_long.txt");
        runDynFromFileArgs(args);
    }

    @Test
    public void runSpinABPLongTraceSuccess() throws Exception {
        List<String> args = getSpinArgsStr();
        args.addAll(getABPArgs());
        args.add("../traces/AlternatingBitProtocol/trace_po_long.txt");
        // runDynFromFileArgs(args);
    }

    @Test
    public void runABPLongTraceSuccessParallel() throws Exception {
        List<String> args = getBasicArgsStr();
        args.addAll(getABPArgs());
        args.addAll(getParallelArgs());
        args.add("../traces/AlternatingBitProtocol/trace_po_long.txt");
        // runDynFromFileArgs(args);
    }

    @Test
    public void runSpinABPLongerTraceSuccess() throws Exception {
        List<String> args = getSpinArgsStr();
        args.addAll(getABPArgs());
        args.add("../traces/AlternatingBitProtocol/trace_po_sr_longer.txt");
        // runDynFromFileArgs(args);
    }

    @Test
    public void runTCPTrace() throws Exception {
        List<String> args = getBasicArgsStr();
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)#.*$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("sc:0->1;cs:1->0");
        args.add("-i");
        args.add("-d");
        args.add("../traces/Tcp/po_tcp_log.txt");
        runDynFromFileArgs(args);
    }

    @Test
    public void runSpinTCPTrace() throws Exception {
        List<String> args = getSpinArgsStr();
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)#.*$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("sc:0->1;cs:1->0");
        args.add("-i");
        args.add("-d");
        args.add("../traces/Tcp/po_tcp_log.txt");
        runDynFromFileArgs(args);
    }

    @Test
    public void runTCPTraceParallel() throws Exception {
        List<String> args = getBasicArgsStr();
        args.addAll(getParallelArgs());
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)#.*$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("sc:0->1;cs:1->0");
        args.add("-i");
        args.add("-d");
        args.add("../traces/Tcp/po_tcp_log.txt");
        runDynFromFileArgs(args);
    }

    @Test
    public void runVoldemortTrace() throws Exception {
        List<String> args = getBasicArgsStr();
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)#.*$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("cr1:0->1;r1c:1->0;cr2:0->2;r2c:2->0");
        args.add("-i");
        args.add("-d");
        args.add("../traces/Voldemort/trace_client_put_get.txt");
        // runDynFromFileArgs(args);
    }

    @Test
    public void runSpinVoldemortTrace() throws Exception {
        List<String> args = getSpinArgsStr();
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)#.*$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("cr1:0->1;r1c:1->0;cr2:0->2;r2c:2->0");
        args.add("-i");
        args.add("-d");
        args.add("../traces/Voldemort/trace_client_put_get.txt");
        // runDynFromFileArgs(args);
    }

    @Test
    public void runVoldemortTraceParallel() throws Exception {
        List<String> args = getBasicArgsStr();
        args.addAll(getParallelArgs());
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)#.*$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("cr1:0->1;r1c:1->0;cr2:0->2;r2c:2->0");
        args.add("-i");
        args.add("-d");
        args.add("../traces/Voldemort/trace_client_put_get.txt");
        // runDynFromFileArgs(args);
    }

    /**
     * A simple PO example with p0 sending a message, and p1 receiving the
     * message, performing a local action, and replying with an ack. This is
     * recorded as 1, and 2 iterations.
     */
    @Test
    public void runSimpleReqRes() throws Exception {
        List<String> args = getBasicArgsStr();
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)#.*$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("A:0->1;B:1->0");
        args.add("-i");
        args.add("-d");
        args.add("-minimize");
        args.add("../traces/abstract/request-response-po/trace.txt");
        runDynFromFileArgs(args);
    }

    /**
     * A simple PO example with p0 sending a message, and p1 receiving the
     * message, performing a local action, and replying with an ack. This is
     * recorded as 1, and 2 iterations.
     */
    @Test
    public void runSpinSimpleReqRes() throws Exception {
        List<String> args = getSpinArgsStr();
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)#.*$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("A:0->1;B:1->0");
        args.add("-i");
        args.add("-d");
        args.add("-minimize");
        args.add("../traces/abstract/request-response-po/trace.txt");
        runDynFromFileArgs(args);
    }

    /**
     * The same example as above, but with parallel model checking.
     */
    @Test
    public void runSimpleReqResParallel() throws Exception {
        List<String> args = getBasicArgsStr();
        args.addAll(getParallelArgs());
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)#.*$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("A:0->1;B:1->0");
        args.add("-i");
        args.add("-d");
        args.add("-minimize");
        args.add("../traces/abstract/request-response-po/trace.txt");
        runDynFromFileArgs(args);
    }

    /** A trivial example with 4 total events. */
    @Test
    public void runSimpleConcurrencyFileSuccess() throws Exception {
        List<String> args = getBasicArgsStr();
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("M:0->1");
        args.add("-i");
        args.add("-d");
        args.add("../traces/EndToEndDynopticTests/simple-po-concurrency/trace.txt");
        runDynFromFileArgs(args);
    }

    /** Same as the above, but uses a String input instead of a file input. */
    @Test
    public void runSimpleConcurrencyStringSuccess() throws Exception {
        List<String> args = getBasicArgsStr();
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("M:0->1");

        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);

        String log = "1,0 e1\n" + "0,1 f1\n" + "2,0 M!m\n" + "2,2 M?m";
        dyn.run(log);
    }

    /** Same as the above, but uses Spin instead of McScm. */
    @Test
    public void runSpinSimpleConcurrencyStringSuccess() throws Exception {
        List<String> args = getSpinArgsStr();
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("M:0->1");

        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);

        String log = "1,0 e1\n" + "0,1 f1\n" + "2,0 M!m\n" + "2,2 M?m";
        dyn.run(log);
    }

    /** Same as the above, but with parallel model checking. */
    @Test
    public void runSimpleConcurrencyStringSuccessParallel() throws Exception {
        List<String> args = getBasicArgsStr();
        args.addAll(getParallelArgs());
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("M:0->1");

        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);

        String log = "1,0 e1\n" + "0,1 f1\n" + "2,0 M!m\n" + "2,2 M?m";
        dyn.run(log);
    }

    /** A slightly more complex example than the above. */
    @Test
    public void runSimpleConcurrencyString2Success() throws Exception {
        List<String> args = getBasicArgsStr();
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("M:0->1;A:1->0");

        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);

        String log = "1,0 M!m\n" + "2,0 M!m\n" + "3,2 A?a\n" + "1,1 M?m\n"
                + "1,2 A!a\n" + "2,3 M?m\n" + "2,4 A!a\n" + "4,4 A?a\n";

        dyn.run(log);
    }

    /**
     * The same example as above but with Spin.
     */
    @Test
    public void runSpinSimpleConcurrencyString2Success() throws Exception {
        List<String> args = getSpinArgsStr();
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("M:0->1;A:1->0");

        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);

        String log = "1,0 M!m\n" + "2,0 M!m\n" + "3,2 A?a\n" + "1,1 M?m\n"
                + "1,2 A!a\n" + "2,3 M?m\n" + "2,4 A!a\n" + "4,4 A?a\n";

        dyn.run(log);
    }

    /**
     * The same example as above but with parallel model checking.
     */
    @Test
    public void runSimpleConcurrencyString2SuccessParallel() throws Exception {
        List<String> args = getBasicArgsStr();
        args.addAll(getParallelArgs());
        args.add("-r");
        args.add("^(?<VTIME>)(?<TYPE>)$");
        args.add("-s");
        args.add("^--$");
        args.add("-q");
        args.add("M:0->1;A:1->0");

        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);

        String log = "1,0 M!m\n" + "2,0 M!m\n" + "3,2 A?a\n" + "1,1 M?m\n"
                + "1,2 A!a\n" + "2,3 M?m\n" + "2,4 A!a\n" + "4,4 A?a\n";

        dyn.run(log);
    }

    /**
     * Test check and refine loop to skip model checking when all partitions of
     * the GFSM are singletons
     */
    @Test
    public void testCheckInvsRefineGFSMWithSingletonPartition()
            throws Exception {
        ChannelId cid0 = new ChannelId(0, 1, 0);
        DistEventType eSend = DistEventType.SendEvent("e", cid0);
        DistEventType eRecv = DistEventType.RecvEvent("e", cid0);

        List<BinaryInvariant> invs = Util.newList();
        BinaryInvariant inv = new AlwaysPrecedes(eSend, eRecv);
        invs.add(inv);
        GFSM pGraph = createSingletonGFSM();

        List<String> args = getBasicArgsStr();
        args.add("-q");
        args.add("M:0->1;A:1->0");

        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);

        assertEquals(0, dyn.checkInvsRefineGFSM(invs, pGraph));
    }

    /**
     * Test check and refine loop to not skip model checking when partitions of
     * the GFSM are not singletons
     */
    @Test
    public void testCheckInvsRefineGFSMWithNonSingleton() throws Exception {
        List<ObsFSMState> Pi = Util.newList();
        List<ObsFSMState> Pm = Util.newList();
        List<ObsFSMState> Pf = Util.newList();

        ObsFSMState p0i = ObsFSMState.namedObsFSMState(0, "M", true, false);
        ObsFSMState p1i = ObsFSMState.namedObsFSMState(1, "A", true, false);
        Pi.add(p0i);
        Pi.add(p1i);
        ObsMultFSMState obsPi = ObsMultFSMState.getMultiFSMState(Pi);

        ObsFSMState p0m = ObsFSMState.namedObsFSMState(0, "M", false, false);
        ObsFSMState p1m = ObsFSMState.namedObsFSMState(1, "A", false, false);
        Pm.add(p0m);
        Pm.add(p1m);
        ObsMultFSMState obsPm = ObsMultFSMState.getMultiFSMState(Pm);

        ObsFSMState p0f = ObsFSMState.namedObsFSMState(0, "M", false, true);
        ObsFSMState p1f = ObsFSMState.namedObsFSMState(1, "A", false, true);
        Pf.add(p0f);
        Pf.add(p1f);
        ObsMultFSMState obsPf = ObsMultFSMState.getMultiFSMState(Pf);

        ChannelId cid0 = new ChannelId(0, 1, 0);
        ChannelId cid1 = new ChannelId(1, 0, 1);
        DistEventType eSend = DistEventType.SendEvent("e", cid0);
        DistEventType eRecv = DistEventType.RecvEvent("e", cid0);

        List<ChannelId> cids = Util.newList();
        cids.add(cid0);
        cids.add(cid1);

        ImmutableMultiChState PiChstate = ImmutableMultiChState
                .fromChannelIds(cids);
        ImmutableMultiChState PmChstate = PiChstate.getNextChState(eSend);
        ImmutableMultiChState PfChstate = PmChstate.getNextChState(eRecv);

        ObsFifoSysState Si = ObsFifoSysState.getFifoSysState(obsPi, PiChstate);
        ObsFifoSysState Sm = ObsFifoSysState.getFifoSysState(obsPm, PmChstate);
        ObsFifoSysState Sf = ObsFifoSysState.getFifoSysState(obsPf, PfChstate);
        ObsDistEventType obsESend = new ObsDistEventType(eSend, 0);
        ObsDistEventType obsERecv = new ObsDistEventType(eRecv, 0);

        // Si -> Sm -> Sf
        Si.addTransition(obsESend, Sm);
        Sm.addTransition(obsERecv, Sf);

        List<ObsFifoSys> traces = Util.newList(1);

        Set<ObsFifoSysState> states = Util.newSet();
        states.add(Si);
        states.add(Sm);
        states.add(Sf);

        ObsFifoSys trace = new ObsFifoSys(cids, Si, Sf, states);
        traces.add(trace);

        GFSM pGraph = new GFSM(traces, 1);

        List<BinaryInvariant> invs = Util.newList();
        BinaryInvariant inv = new AlwaysPrecedes(eSend, eRecv);
        invs.add(inv);

        List<String> args = getBasicArgsStr();
        args.add("-q");
        args.add("M:0->1;A:1->0");

        opts = new CSightOptions(args.toArray(new String[0]));
        dyn = new CSightMain(opts);

        assertTrue(0 < dyn.checkInvsRefineGFSM(invs, pGraph));
    }
}
