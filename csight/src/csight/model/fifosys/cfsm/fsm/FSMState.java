package csight.model.fifosys.cfsm.fsm;

import java.util.List;
import java.util.Map;
import java.util.Set;

import csight.invariants.BinaryInvariant;
import csight.model.AbsFSMState;
import csight.model.fifosys.channel.channelid.LocalEventsChannelId;
import csight.util.Util;

import synoptic.model.event.DistEventType;

/**
 * <p>
 * Represents a state of a simple FSM that is an NFA.
 * </p>
 * <p>
 * An FSMState maintains abstract transitions to other FSMState instances. It is
 * completely disassociated from the observed transitions and states. Note that
 * an FSMState can have multiple transitions on the same event that go to
 * different FSMState instances (the FSM can be an NFA).
 * </p>
 */
public class FSMState extends AbsFSMState<FSMState, DistEventType> {
    // Whether or not this state is an accepting/initial state.
    private boolean isAccept;
    private boolean isInitial;

    // Transitions to other FSMState instances.
    private final Map<DistEventType, Set<FSMState>> transitions;

    // The process that this state is associated with. Initially this is -1, but
    // once a transition on an event is added, the pid is set based on the event
    // type.
    private int pid = -1;

    // The id used by this FSMState in scm and promela output. This state id is
    // a positive integer that is unique to this state in the FSM.
    private final int stateId;

    public FSMState(boolean isAccept, boolean isInitial, int pid, int scmId) {
        this.isAccept = isAccept;
        this.isInitial = isInitial;
        this.pid = pid;
        this.stateId = scmId;
        transitions = Util.newMap();
    }

    // //////////////////////////////////////////////////////////////////

    @Override
    public boolean isInitial() {
        return isInitial;
    }

    @Override
    public boolean isAccept() {
        return isAccept;
    }

    @Override
    public Set<DistEventType> getTransitioningEvents() {
        return transitions.keySet();
    }

    /**
     * Returns the set of all possible following states for this FSMState and an
     * event.
     * 
     * @param event
     * @return
     */
    @Override
    public Set<FSMState> getNextStates(DistEventType event) {
        assert event != null;
        assert transitions.containsKey(event);

        // NOTE: Unfortunately, we can't return
        // Collections.unmodifiableSet(transitions.get(event))
        // because transitions are iterated over and at the same time modified
        // in CFSM.recurseAddSendToEventTx(). This is a potential, but
        // difficult, FIXME.
        return Util.newSet(transitions.get(event));
    }

    public String toLongString() {
        return "FSM_state: init[" + isInitial + "], accept[" + isAccept
                + "] id[" + stateId + "]";
    }

    public String toShortIntString() {
        // return String.valueOf(pid) + "." + String.valueOf(scmId);
        return String.valueOf(stateId);
    }

    @Override
    public String toString() {
        return toShortIntString();
    }

    @Override
    public int hashCode() {
        int ret = 31;
        ret = ret * 31 + (isAccept ? 1 : 0);
        ret = ret * 31 + (isInitial ? 1 : 0);
        // FIXME: Issue 276
        // Not using transitions because they cause a stack overflow.
        ret = ret * 31 + pid;
        ret = ret * 31 + stateId;
        return 1;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof FSMState)) {
            return false;
        }

        FSMState state = (FSMState) other;

        if (state.isAccept != this.isAccept) {
            return false;
        }

        if (state.isInitial != this.isInitial) {
            return false;
        }

        // FIXME: Issue 276
        // Not using transitions because they cause a stack overflow.

        if (state.pid != this.pid) {
            return false;
        }

        if (state.stateId != this.stateId) {
            return false;
        }

        return true;
    }

    // //////////////////////////////////////////////////////////////////

    public void setAccept() {
        isAccept = true;
    }

    /** Returns the pid that this state is associated with. */
    public int getPid() {
        return pid;
    }

    /** Returns this state's id. */
    public int getStateId() {
        return stateId;
    }

    /**
     * Adds a new transition to a state s on event e from this state.
     * 
     * @param e
     * @param s
     */
    public void addTransition(DistEventType e, FSMState s) {
        assert e != null;
        assert s != null;
        assert pid == e.getPid();

        addTransitionNoChecks(e, s);
    }

    /**
     * Adds a new synthetic transition that is associated with an invariant
     * channel. For now, this is similar to addTransition and omits a check that
     * disallow two processes from sending to the same queue.
     * 
     * @param e
     * @param s
     */
    public void addSynthTransition(DistEventType e, FSMState s) {
        assert e != null;
        assert s != null;
        assert e.isSynthSendEvent();

        addTransitionNoChecks(e, s);
    }

    /**
     * Removes an existing transition to a state s on event e from this state.
     * 
     * @param e
     * @param s
     */
    public void rmTransition(DistEventType e, FSMState s) {
        assert e != null;
        assert s != null;
        assert pid == e.getPid();
        assert transitions.containsKey(e);

        Set<FSMState> children = transitions.get(e);
        assert children.contains(s);

        children.remove(s);
        if (children.isEmpty()) {
            transitions.remove(e);
        }
    }

    /**
     * Returns an SCM representation of this FSMState. Updates the internal
     * mapping maintained by localEventsChId to account for any local event
     * transitions from this state.
     */
    public String toScmString(LocalEventsChannelId localEventsChId) {
        String ret = "state " + stateId + " :\n";

        String eStr;
        for (DistEventType e : transitions.keySet()) {
            // Build an scm representation of this event type.
            if (e.isCommEvent()) {
                eStr = e.toString(
                        Integer.toString(e.getChannelId().getScmId()), ' ');
            } else {
                // Local event: use local queue for local events.
                String eTypeStr = e.getScmEventFullString();
                localEventsChId.addLocalEventString(e, eTypeStr);
                eStr = localEventsChId.getScmId() + " ! " + eTypeStr;
            }

            for (FSMState next : transitions.get(e)) {
                ret += "to " + next.getStateId() + " : when true , " + eStr
                        + " ;\n";
            }
        }

        return ret;
    }

    // ///////////////////////////////////////////////////////////////

    /**
     * Returns a Promela representation of this FSMState.
     */
    public String toPromelaString(List<BinaryInvariant> invariants,
            String labelPrefix) {
        // Every state starts with a label.
        String ret = labelPrefix + "_" + getStateId() + ":\n";

        if (isAccept()) {
            // Tell Spin that this is a valid endstate for the process.
            ret += "end_" + labelPrefix + "_" + getStateId() + ":\n";
        }

        // Promela do statements will non-deterministically
        // choose one of the valid branches.
        ret += "    do\n";

        for (DistEventType e : transitions.keySet()) {
            for (FSMState s : transitions.get(e)) {

                // Do not delete printTrace. This is not a debugging statement.
                // The print statements are executed in the Spin trail and
                // provide an easy target from which to parse the
                // counterexample. They also have no overhead in verification
                // runs and we only use them in the trail.
                String printTrace = String.format(
                        "printf(\"CSightTrace[%s]\\n\")", e.toString());

                // traceString is the in-Promela version of the trace string.
                // This is used by our never claim to track the event.
                String traceString;

                traceString = "recentEvent.type = OTHEREVENT";
                for (BinaryInvariant invariant : invariants) {
                    if (e.equals(invariant.getFirst())
                            || e.equals(invariant.getSecond())) {
                        traceString = e.toPromelaTraceString();
                        break; // Break out after the first match.
                    }
                }
                // We only change the terminal state status if there is a change
                // in the acceptance of the state.
                String terminalState = "";
                if (isAccept != s.isAccept) {
                    terminalState = String.format("terminal[%d] = %d;",
                            getPid(), (s.isAccept ? 1 : 0));
                }

                ret += String.format("      :: atomic { %s; %s; %s; %s} -> ",
                        e.toPromelaString(), traceString, printTrace,
                        terminalState);

                ret += "goto " + s.getPromelaName(labelPrefix) + ";\n";
            }
        }

        /*
         * Jump to end of process. This is safe to do only under these
         * circumstances: The state has no outgoing transitions and is a
         * terminal state. This indicates that this is a final state that cannot
         * leave the state.
         * 
         * We don't want to keep the process in this state loop indefinitely as
         * it will extend our traces with no benefit. We instead choose to
         * explicitly end the process by going to the terminal end label at the
         * end of the process.
         */
        if (transitions.keySet().size() == 0 && isAccept()) {
            ret += "     :: recentEvent.type = OTHEREVENT -> ";
            ret += "goto end_" + labelPrefix + ";\n";
        }
        ret += "    od;\n";
        return ret;
    }

    /**
     * Label name for the state in Promela.
     * 
     * @param labelPrefix
     * @return
     */
    public String getPromelaName(String labelPrefix) {
        String ret = "";
        ret += labelPrefix;
        ret += "_" + getStateId();

        return ret;

    }

    // //////////////////////////////////////////////////////////////////

    /** Adds a transition from this state on e to state s. */
    private void addTransitionNoChecks(DistEventType e, FSMState s) {
        Set<FSMState> following;
        if (transitions.get(e) == null) {
            following = Util.newSet();
            transitions.put(e, following);
        } else {
            following = transitions.get(e);
        }

        // Since following is a set, it's okay if we have added the transition
        // to s on e before.
        following.add(s);
    }

}
