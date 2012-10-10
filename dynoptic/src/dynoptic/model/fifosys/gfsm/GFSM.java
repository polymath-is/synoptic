package dynoptic.model.fifosys.gfsm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import mcscm.McScMCExample;
import dynoptic.main.DynopticMain;
import dynoptic.model.AbsFSMState;
import dynoptic.model.fifosys.FifoSys;
import dynoptic.model.fifosys.cfsm.CFSM;
import dynoptic.model.fifosys.cfsm.fsm.FSM;
import dynoptic.model.fifosys.cfsm.fsm.FSMState;
import dynoptic.model.fifosys.gfsm.observed.fifosys.ObsFifoSys;
import dynoptic.model.fifosys.gfsm.observed.fifosys.ObsFifoSysState;

import synoptic.model.channelid.ChannelId;
import synoptic.model.event.DistEventType;

/**
 * <p>
 * A GFSM captures the execution space of a CFSM. We use this model to (1)
 * maintain the observed states/event, and (2) to carry out complex operations
 * like refinement.
 * </p>
 * <p>
 * A GFSM is composed of GFSMStates, which are _partitions_ of the observed
 * global configurations. Refinement causes a re-shuffling of the observations,
 * and new partitions to be created and added to the GFSM. Therefore, a GFSM is
 * highly mutable. Each mutation of the GFSM is a single complete step of the
 * Dynoptic algorithm.
 * </p>
 * <p>
 * This model is easier to deal with than a CFSM because it captures all global
 * information in a single place (e.g., all enabled transitions from a single
 * global configuration). A CFSM can be thought of as an abstraction of a GFSM
 * -- a CFSM does not deal with concrete observations. The CFSM model is useful
 * for visualization and for input to the McScM model checker.
 * </p>
 * <p>
 * A GFSM can also be thought of as a representation of the operational
 * semantics, or some number of executions of some hidden/abstract CFSM. Note
 * that although it captures or describes prior executions, it cannot actually
 * be executed or maintain instantaneous execution state -- for this, use
 * FifoSysExecution.
 * </p>
 */
public class GFSM extends FifoSys<GFSMState> {

    public static Logger logger = Logger.getLogger("GFSM");

    /** Used when converting GFSM to a CFSM representation. */
    private int nextScmId = 0;

    /** Used when converting GFSM to a CFSM representation. */
    private int nextFsmStateId = 0;

    /**
     * Creates a new GFSM from observed ObsFifoSys traces, using default initial
     * partitioning strategy (by the list of elements at the head of all of the
     * queues in the system), from a list of traces.
     * 
     * @param traces
     * @return
     */
    public GFSM(List<ObsFifoSys> traces) {
        super(traces.get(0).getNumProcesses(), traces.get(0).getChannelIds());

        Map<Integer, Set<ObsFifoSysState>> qTopHashToPartition = new LinkedHashMap<Integer, Set<ObsFifoSysState>>();

        for (ObsFifoSys t : traces) {
            assert t.getNumProcesses() == numProcesses;
            assert t.getChannelIds().equals(channelIds);

            // DFS traversal to perform initial partitioning.
            ObsFifoSysState init = t.getInitState();
            addToMap(qTopHashToPartition, init);
            traverseAndPartition(init, qTopHashToPartition);
        }

        Set<ObsFifoSysState> allObs = null;
        if (DynopticMain.assertsOn) {
            allObs = new LinkedHashSet<ObsFifoSysState>();
        }

        // Create the GFSMState partitions based off of sets of observations.
        for (Set<ObsFifoSysState> set : qTopHashToPartition.values()) {
            states.add(new GFSMState(numProcesses, set));
            if (DynopticMain.assertsOn) {
                allObs.addAll(set);
            }
        }
        recomputeAlphabet();

        // Now, assert that if two ObsFifoSysStates are identical, then they are
        // assigned to the same partition.
        if (DynopticMain.assertsOn) {
            checkPartitioningConsistency(allObs);
        }

    }

    /**
     * Checks that the following property is true for a set of observations: if
     * two observations have identical process states, then belong to the same
     * GFSMState partition.
     */
    private static void checkPartitioningConsistency(
            Set<ObsFifoSysState> obsToCheck) {
        for (ObsFifoSysState s1 : obsToCheck) {
            for (ObsFifoSysState s2 : obsToCheck) {
                if (s1.getFSMStates().equals(s2.getFSMStates())) {
                    assert s1.getParent().equals(s2.getParent());
                }
            }
        }
    }

    /**
     * Constructor helper -- adds an observation to the map, by hashing on its
     * top of queue event types.
     */
    private void addToMap(
            Map<Integer, Set<ObsFifoSysState>> qTopHashToPartition,
            ObsFifoSysState obs) {
        int hash = obs.getChannelStates().topOfQueuesHash();
        if (!qTopHashToPartition.containsKey(hash)) {
            Set<ObsFifoSysState> partition = new LinkedHashSet<ObsFifoSysState>();
            qTopHashToPartition.put(hash, partition);
        }
        qTopHashToPartition.get(hash).add(obs);
    }

    /**
     * Constructor helper -- DFS traversal of the observed traces, building up
     * an initial partitioning.
     */
    private void traverseAndPartition(ObsFifoSysState curr,
            Map<Integer, Set<ObsFifoSysState>> qTopHashToPartition) {
        for (ObsFifoSysState next : curr.getNextStates()) {
            addToMap(qTopHashToPartition, next);
            traverseAndPartition(next, qTopHashToPartition);
        }
    }

    /** Creates an empty GFSM. */
    public GFSM(int numProcesses, List<ChannelId> channelIds) {
        super(numProcesses, channelIds);
    }

    // //////////////////////////////////////////////////////////////////

    @Override
    public Set<GFSMState> getInitStates() {
        Set<GFSMState> ret = new LinkedHashSet<GFSMState>();
        for (GFSMState s : states) {
            if (s.isInitial()) {
                ret.add(s);
            }
        }
        return ret;
    }

    @Override
    public Set<GFSMState> getAcceptStates() {
        Set<GFSMState> ret = new LinkedHashSet<GFSMState>();
        for (GFSMState s : states) {
            if (s.isAccept()) {
                ret.add(s);
            }
        }
        return ret;
    }

    @Override
    public String toString() {
        String ret = "GFSM[num-states=" + states.size() + "] : ";
        for (GFSMState s : states) {
            ret += "\n\t" + s.toString();
        }
        return ret;
    }

    // //////////////////////////////////////////////////////////////////

    /** Returns the set of partitions that are accepting for a pid. */
    public Set<GFSMState> getAcceptStatesForPid(int pid) {
        Set<GFSMState> ret = new LinkedHashSet<GFSMState>();
        for (GFSMState s : states) {
            if (s.isAcceptForPid(pid)) {
                ret.add(s);
            }
        }
        return ret;
    }

    /** Returns the set of partitions that are initial for a pid. */
    public Set<GFSMState> getInitStatesForPid(int pid) {
        Set<GFSMState> ret = new LinkedHashSet<GFSMState>();
        for (GFSMState s : states) {
            if (s.isInitForPid(pid)) {
                ret.add(s);
            }
        }
        return ret;
    }

    /** Adds a new partition/state s to this GFSM. */
    public void addAllGFSMStates(Collection<GFSMState> newStates) {
        assert !states.containsAll(newStates);

        states.addAll(newStates);
        recomputeAlphabet();
    }

    /** Adds a new partition/state s to this GFSM. */
    public void addGFSMState(GFSMState s) {
        assert !states.contains(s);

        states.add(s);
        recomputeAlphabet();
    }

    /** Removes the partition/state s from this GFSM. */
    public void removeGFSMState(GFSMState s) {
        assert states.contains(s);

        states.remove(s);
        recomputeAlphabet();
    }

    /**
     * Checks if the partitions and events paths associated with cExample are
     * feasible in this GFSM.
     */
    public boolean feasible(CompleteGFSMCExample cExample) {
        // 1. Check that all partitions in path are in pGraph
        for (GFSMState s : cExample.getPartitionPath()) {
            if (!this.states.contains(s)) {
                return false;
            }
        }

        // 2. Check that event transitions between partitions in path are still
        // as specified by cExample are still valid in pGraph.
        List<GFSMState> path = cExample.getPartitionPath();

        int i = 0;
        for (DistEventType e : cExample.getMcScMCExample().getEvents()) {
            if (!path.get(i).getNextStates(e).contains(path.get(i + 1))) {
                return false;
            }
            i += 1;
        }

        return true;
    }

    /**
     * Refines partition part into setLeft and setRight -- keeping setLeft in
     * part, and extracting the events in setRight. If (setLeft \\Union setRight
     * != part) then the observations in part that are in neither of the two
     * sets are assigned at random to setLeft or setRight with uniform
     * probability.
     * 
     * @param part
     * @param setLeft
     * @param setRight
     */
    public void refineWithRandNonRelevantObsAssignment(GFSMState part,
            Set<ObsFifoSysState> setLeft, Set<ObsFifoSysState> setRight) {
        assert setLeft.size() > 0;
        assert setRight.size() > 0;

        // We know that setLeft and setRight have to be isolated, but what
        // about the observations in part that are in neither of these two sets?
        // Our strategy is to assign these at random, either to setLeft or
        // setRight.
        Random rand = new Random();

        for (ObsFifoSysState s : part.getObservedStates()) {
            if (!setLeft.contains(s) && !setRight.contains(s)) {
                // Assign s to setLeft or setRight at random.
                if (rand.nextInt(2) == 0) {
                    setLeft.add(s);
                } else {
                    setRight.add(s);
                }
            }
        }

        // Perform the complete refinement.
        this.refine(part, setRight);
    }

    /**
     * Constructs a CFSM from a GFSM. It performs the necessary traversal of the
     * GFSM to construct/specify all the process FSMs that should be part of the
     * CFSM.
     * 
     * @param gfsm
     * @return
     */
    public CFSM getCFSM() {
        // This is the CFSM that we will return, once we populate it with all
        // the process FSMs.
        CFSM cfsm = new CFSM(numProcesses, channelIds);

        logger.info("GFSM -> CFSM: " + this.toString() + "\n");

        Set<FSMState> initFSMStates = new LinkedHashSet<FSMState>();
        Set<FSMState> acceptFSMStates = new LinkedHashSet<FSMState>();
        Set<GFSMState> nonPidTxClosureStates = new LinkedHashSet<GFSMState>();
        Map<GFSMState, FSMState> stateMap = new LinkedHashMap<GFSMState, FSMState>();

        Set<GFSMState> gvisited = new LinkedHashSet<GFSMState>();

        Set<FSMState> txClosure = new LinkedHashSet<FSMState>();
        Set<FSMState> fvisited = new LinkedHashSet<FSMState>();

        // Create an FSM per pid.
        for (int pid = 0; pid < numProcesses; pid++) {
            logger.info("Building FSM for pid " + pid);
            // States in each FSM have to be uniquely numbered in the scm
            // output.
            nextScmId = 0;

            // Create a new FSM state corresponding to each GFSMState.
            for (GFSMState gstate : states) {
                // NOTE: accepting() for fstate is UNRELIABLE until it is
                // dynamically computed below.
                FSMState fstate = new FSMState(gstate.isAccept(),
                        gstate.isInitForPid(pid), pid, nextScmId);
                if (fstate.isInitial()) {
                    initFSMStates.add(fstate);
                }
                nextScmId += 1;
                stateMap.put(gstate, fstate);
            }

            logger.info("GFSMState->FSMState[pid=" + pid + "] stateMap : "
                    + stateMap.toString());

            // Create transitions between FSMState instances based on
            // corresponding GFSMState transitions, as well as the non-pid
            // transition transitive closure for each GFSMState.

            for (GFSMState gstate : states) {
                // Find the states that can be reached through non-pid
                // transitions (we treat them as epsilon transitions).
                nonPidTxClosureStates.clear();
                gvisited.clear();
                // TODO: cache reachability for explored states and re-use this
                // information.
                AbsFSMState.findNonPidTransitiveClosure(pid, gstate, gvisited,
                        nonPidTxClosureStates);

                nonPidTxClosureStates.add(gstate);

                FSMState fstate = stateMap.get(gstate);

                for (GFSMState g : nonPidTxClosureStates) {

                    // If we can reach an accepting g from gstate using non-pid
                    // (epsilon) transitions, then this process can terminate at
                    // gstate, or equivalently, at the corresponding fstate.
                    if (g.isAccept()) {
                        fstate.setAccept(true);
                        acceptFSMStates.add(fstate);
                    }

                    // Add the GFSMState transitions to fstate.
                    for (DistEventType e : g.getTransitioningEvents()) {
                        // Only create this pid's event transitions.
                        if (e.getPid() != pid) {
                            continue;
                        }
                        for (GFSMState gNext : g.getNextStates(e)) {
                            FSMState fNext = stateMap.get(gNext);
                            fstate.addTransition(e, fNext);
                        }
                    }
                }
            }

            // Remove any non-reachable FSM states.

            // 1. Build the transitive closure of state from all initial states.
            for (FSMState istate : initFSMStates) {
                fvisited.clear();
                AbsFSMState.findTransitiveClosure(istate, fvisited, txClosure);
            }

            // 2. Remove those FSM states that are not reachable from any
            // initial state -- that are not in txClosure -- from stateMap.
            Set<GFSMState> keySet = stateMap.keySet();
            Iterator<GFSMState> keyItr = keySet.iterator();
            while (keyItr.hasNext()) {
                GFSMState gstate = keyItr.next();
                FSMState fstate = stateMap.get(gstate);
                // Skip states that are initial -- we can always reach these.
                if (fstate.isInitial()) {
                    continue;
                }

                if (!txClosure.contains(fstate)) {
                    // Remove from stateMap, as well as accepting set of states.
                    keyItr.remove();
                    acceptFSMStates.remove(fstate);
                }
            }

            // We want to create the smallest possible FSM for efficiency (McScM
            // runs faster on smaller models) and so that the models are simple
            // to inspect.

            // 1. Merge any FSM states that are bisimular --- if their behavior
            // is indistinguishable.
            boolean merged = false;
            do {
                merged = false;
                keySet = stateMap.keySet();
                keyItr = keySet.iterator();
                while (keyItr.hasNext()) {
                    GFSMState gstate = keyItr.next();
                    GFSMState gstate2 = findBisimularFSMState(gstate, stateMap);

                    // Did not find a corresponding gstate2 that maps to fstate2
                    // that is bisimular to fstate.
                    if (gstate2 == null) {
                        continue;
                    }

                    FSMState fstate = stateMap.get(gstate);
                    FSMState fstate2 = stateMap.get(gstate2);

                    // Merges fstate INTO fstate2.
                    remapPredTxns(fstate, fstate2, stateMap);

                    // Remove gstate/fstate from the stateMap, and remove
                    // fstate from accepting set
                    keyItr.remove();
                    acceptFSMStates.remove(fstate);
                    initFSMStates.remove(fstate);
                    merged = true;
                    break;
                }
                // Re-try all possible n^2 merges if we've just merged two
                // states, since this might induce further state equivalence and
                // merging.
            } while (merged);

            // 2. TODO: Check if after bisimulation merging above the FSM is
            // now a DFA and we can use standard minimization to further
            // minimize the FSM.

            assert !acceptFSMStates.isEmpty();
            assert !initFSMStates.isEmpty();

            // Create the FSM for this pid, and add it to the CFSM.
            FSM fsm = new FSM(pid, initFSMStates, acceptFSMStates,
                    stateMap.values(), nextScmId);

            cfsm.addFSM(fsm);

            stateMap.clear();
            initFSMStates.clear();
            acceptFSMStates.clear();
            txClosure.clear();
        }
        return cfsm;
    }

    /**
     * Changes transitions from all predecessors of fstate to transition instead
     * to fstate2.
     */
    private void remapPredTxns(FSMState fstate, FSMState fstate2,
            Map<GFSMState, FSMState> stateMap) {
        // Find predecessors of fstate.

        // TODO: we need a better way of doing this -- need a map from children
        // to parents
        for (FSMState fPred : stateMap.values()) {
            if (fPred == fstate) {
                continue;
            }
            if (!fPred.getNextStates().contains(fstate)) {
                continue;
            }

            // TODO: need a better way of doing this, too -- have a way to
            // update transitions to states in bulk.
            for (DistEventType e : new LinkedHashSet<DistEventType>(
                    fPred.getTransitioningEvents())) {
                if (fPred.getNextStates(e).contains(fstate)) {
                    fPred.rmTransition(e, fstate);
                    fPred.addTransition(e, fstate2);
                }
            }
        }
    }

    /**
     * Attempts to find an FSM state, fstate2, that is bisimular (behaviorally
     * indistinguishable) from the FSM state stateMap[gstate]. Returns gstate2
     * such that fstate2 = stateMap[gstate2]. If no such fstate2 exists, then
     * returns null.
     */
    private GFSMState findBisimularFSMState(GFSMState gstate,
            Map<GFSMState, FSMState> stateMap) {
        FSMState fstate = stateMap.get(gstate);

        for (GFSMState gstate2 : stateMap.keySet()) {
            if (gstate == gstate2) {
                continue;
            }
            FSMState fstate2 = stateMap.get(gstate2);

            // States must have identical initial/accept properties
            // to be mergeable.
            if (fstate.isInitial() != fstate2.isInitial()) {
                continue;
            }
            if (fstate.isAccept() != fstate2.isAccept()) {
                continue;
            }

            // Compare transitions of fstate and fstate2
            Set<DistEventType> txns1 = fstate.getTransitioningEvents();
            Set<DistEventType> txns2 = fstate2.getTransitioningEvents();
            if (!txns1.equals(txns2)) {
                continue;
            }

            boolean txns_identical = true;
            for (DistEventType tx : txns1) {
                if (!fstate.getNextStates(tx).equals(fstate2.getNextStates(tx))) {
                    // States reachable along tx are different,
                    // therefore fstate and fstate2 are not
                    // bisimular.
                    txns_identical = false;
                    break;
                }
            }
            if (!txns_identical) {
                continue;
            }

            return gstate2;
        }
        return null;
    }

    /**
     * Returns all counter-example paths in GFSM that correspond to the
     * event-based McScMCExample. Since a GFSM is an NFA, we might have multiple
     * matching paths. Constructs the paths using DFS exploration of the GFSM.
     * 
     * @param cExample
     */
    public List<CompleteGFSMCExample> getCExamplePaths(McScMCExample cExample) {
        List<CompleteGFSMCExample> paths = new ArrayList<CompleteGFSMCExample>();
        // Explore potential paths from each global initial state in the GFSM.
        for (GFSMState parent : getInitStates()) {
            List<CompleteGFSMCExample> newPaths = buildCExamplePaths(cExample,
                    0, parent);
            if (newPaths != null) {
                paths.addAll(newPaths);
            }
        }
        return paths;
    }

    /**
     * Returns the longest _partial_ counter-example path in the GFSM that
     * correspond to the event-based McScMCExample. If a complete
     * counter-example is possible, then an AssertionError is thrown: Use
     * getCExamplePaths() to first to determine if a complete counter-example
     * path is impossible and it is necessary to construct a partial
     * counter-example path for refinement.
     * 
     * @param cExample
     */
    public PartialGFSMCExample getLongestPartialCExamplePath(
            McScMCExample cExample) {
        // Explore potential paths from each global initial state in the GFSM.
        PartialGFSMCExample maxPath = null;
        PartialGFSMCExample newPath = null;
        for (GFSMState parent : getInitStates()) {
            newPath = buildCExamplePartialPaths(cExample, 0, parent);
            if (newPath == null) {
                continue;
            }
            if (maxPath == null || maxPath.pathLength() < newPath.pathLength()) {
                maxPath = newPath;
            }
        }

        // maxPath should contains at least one partition -- some initial
        // partition.
        assert maxPath != null;

        // If our path contains just the initial partition, then we haven't even
        // matched the first event in cExample. Instead, we rely on partial path
        // extension code to give us a complete path -- trying all possilbe
        // initial partitions.
        if (maxPath.pathLength() == 1) {
            return new PartialGFSMCExample(cExample);
        }

        return maxPath;
    }

    // //////////////////////////////////////////////////////////////////

    /**
     * Splits, or refines, the partition into two sets of observations --
     * setExtract and the remaining set of events in partition part. Add a newly
     * created partition composed of observations in setExtract.
     */
    private void refine(GFSMState part, Set<ObsFifoSysState> setExtract) {
        assert states.contains(part);
        assert setExtract.size() > 0;

        Set<ObsFifoSysState> obsToCheck = null;
        if (DynopticMain.assertsOn) {
            obsToCheck = part.getObservedStates();
        }

        part.removeAllObs(setExtract);
        states.add(new GFSMState(numProcesses, setExtract));

        part.recreateCachedTransitions();

        // We need to reset inter-partition transitions that contain preceding
        // ObsFifoSysState instances to setExtract. Unfortunately, there is no
        // way to do this in a targeted way -- we have to re-create all
        // transitions if any are suspected of being stale (i.e., need to point
        // to the new GFSMState created above).
        for (GFSMState s : states) {
            if (s != part && s.getNextStates().contains(part)) {
                s.recreateCachedTransitions();
            }
        }

        // Check consistency of just the observations that belong to the
        // original (unrefined) partition.
        if (DynopticMain.assertsOn) {
            checkPartitioningConsistency(obsToCheck);
        }

    }

    /**
     * Returns a single longest partial counter-example path corresponding to
     * cExample, starting at event index, and the corresponding GFSM partition
     * parent. This method is based on buildCExamplePaths() below.
     */
    private PartialGFSMCExample buildCExamplePartialPaths(
            McScMCExample cExample, int eventIndex, GFSMState parent) {
        PartialGFSMCExample path = null, newPath = null;

        // If we reached the end of the events list, then the final partition
        // must be non-accepting, otherwise a complete c-example is possible.
        if (eventIndex == cExample.getEvents().size()) {
            assert !parent.isAccept();

            path = new PartialGFSMCExample(cExample);
            path.addToFrontOfPath(parent);
            return path;
        }

        DistEventType e = cExample.getEvents().get(eventIndex);

        if (!parent.getTransitioningEvents().contains(e)) {
            // We can't make further progress along this partitions path with e
            // as the next event.
            path = new PartialGFSMCExample(cExample);
            path.addToFrontOfPath(parent);
            return path;
        }

        // Recursively build the possible partial paths by traversing through
        // each child that is reachable on e from parent.
        for (GFSMState child : parent.getNextStates(e)) {
            newPath = buildCExamplePartialPaths(cExample, eventIndex + 1, child);
            if (path == null || (newPath.pathLength() > path.pathLength())) {
                path = newPath;
            }
        }

        // Add the current partition to the front of the longest partial path
        // constructed and return.
        if (path == null) {
            path = new PartialGFSMCExample(cExample);
        }
        path.addToFrontOfPath(parent);

        return path;
    }

    /**
     * Returns a set of counter-example paths that correspond to cExample,
     * starting at event index, and the corresponding GFSM partition parent.
     */
    private List<CompleteGFSMCExample> buildCExamplePaths(
            McScMCExample cExample, int eventIndex, GFSMState parent) {
        List<CompleteGFSMCExample> paths = null, newPaths = null;

        // We reached the end of the events list: construct a new GFSMCExample
        // instance containing just the parent partition and return it.
        if (eventIndex == cExample.getEvents().size()) {
            // A counter-example is only valid if it ends at an accepting state.
            if (!parent.isAccept()) {
                return null;
            }
            CompleteGFSMCExample path = new CompleteGFSMCExample(cExample);
            path.addToFrontOfPath(parent);
            newPaths = new ArrayList<CompleteGFSMCExample>();
            newPaths.add(path);
            return newPaths;
        }
        DistEventType e = cExample.getEvents().get(eventIndex);

        if (!parent.getTransitioningEvents().contains(e)) {
            // We can't make further progress along this partitions path with e
            // as the next event.
            return null;
        }

        // Recursively build the paths by traversing through each child that is
        // reachable on e from parent.
        for (GFSMState child : parent.getNextStates(e)) {
            newPaths = buildCExamplePaths(cExample, eventIndex + 1, child);
            if (paths == null) {
                paths = newPaths;
            } else if (newPaths != null) {
                paths.addAll(newPaths);
            }
        }

        // If the rest of the counter-example does not result in any valid
        // sub-paths then we can't generate one.
        if (paths == null) {
            return null;
        }

        // Otherwise, add the current partition to the front of the paths
        // constructed and return.
        for (CompleteGFSMCExample path : paths) {
            path.addToFrontOfPath(parent);
        }
        return paths;
    }
}