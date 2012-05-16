package synoptic.invariants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import gov.nasa.ltl.graph.Graph;

import synoptic.model.event.Event;
import synoptic.model.event.EventType;
import synoptic.model.interfaces.INode;

/**
 * Temporal Invariant representing a kTail for some length k. Not a binary
 * invariant as tails are of varying length. Currently construction of regular
 * expressions for kTail invariants is handled in the InvariMint.model.InvModel
 * class. TODO: move that logic here.
 * 
 * @author jennyabrahamson
 */
public class KTailInvariant implements ITemporalInvariant {

    private final Set<String> relations;

    // This tail's list of events
    private final List<EventType> tail;

    // The set of events that immediately follow this tail
    private final Set<EventType> following;

    /**
     * Returns a KTailInvariant for the given series. On construction, the
     * invariant has a tail but no follow events.
     */
    public KTailInvariant(List<EventType> eventTail,
            Set<EventType> followEvents, String relation) {
        this.relations = new LinkedHashSet<String>();
        this.relations.add(relation);
        this.tail = Collections.unmodifiableList(new ArrayList<EventType>(
                eventTail));
        this.following = new HashSet<EventType>(followEvents);
    }

    /**
     * Returns a KTailInvariant for the given series. On construction, the
     * invariant has a tail but no follow events.
     */
    public KTailInvariant(List<EventType> eventTail, Set<EventType> followEvents) {
        this(eventTail, followEvents, Event.defaultTimeRelationString);
    }

    /**
     * Returns a list of the (ordered) events in this tail.
     */
    public List<EventType> getTailEvents() {
        return this.tail;
    }

    /**
     * Returns a list of the events that can immediately follow this tail.
     */
    public List<EventType> getFollowEvents() {
        return new ArrayList<EventType>(following);
    }

    /**
     * Returns a regular expression for a tail with the given tail events and
     * follow set.
     */
    public static String getRegex(List<Character> tailEvents,
            List<Character> followSet) {
        StringBuilder expression = new StringBuilder("(");

        String last = "";
        last += tailEvents.get(0);
        expression.append("[^" + last + "]");
        for (int i = 1; i < tailEvents.size(); i++) {
            char next = tailEvents.get(i);
            expression.append("|" + last + "[^" + next + "]");
            last += next;
        }

        expression.append("|" + last + "(" + followSet.get(0));
        for (int i = 1; i < followSet.size(); i++) {
            expression.append("|" + followSet.get(i));
        }

        expression.append("))*");
        return expression.toString();
    }

    @Override
    public <T extends INode<T>> List<T> shorten(List<T> path) {
        return path;
    }

    @Override
    public String getShortName() {
        return "kTail";
    }

    @Override
    public String getLongName() {
        return "kTail Invariant";
    }

    @Override
    public String toString() {
        return tail + " tail followed by " + following;
    }

    @Override
    public Set<String> getRelations() {
        return relations;
    }

    @Override
    public Set<EventType> getPredicates() {
        return new HashSet<EventType>(tail);
    }

    /**
     * This invariant is not used during refinement or coarsening, so LTL has
     * been left undefined
     */
    @Override
    public String getLTLString() {
        throw new UnsupportedOperationException();
    }

    /**
     * This invariant is not used during refinement or coarsening, so LTL has
     * been left undefined
     */
    @Override
    public Graph getAutomaton() {
        throw new UnsupportedOperationException();
    }
}