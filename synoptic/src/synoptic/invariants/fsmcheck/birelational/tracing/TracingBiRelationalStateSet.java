package synoptic.invariants.fsmcheck.birelational.tracing;

import java.util.HashSet;
import java.util.Set;

import synoptic.invariants.fsmcheck.TracingStateSet;
import synoptic.main.SynopticMain;
import synoptic.model.interfaces.INode;

public abstract class TracingBiRelationalStateSet<T extends INode<T>> extends TracingStateSet<T> {

    // We have not yet seen the projected graph.
    private boolean beforeProjectedGraph;
    // Whether or not we are traversing the projected graph.
    private boolean inProjectedGraph;
    /* Make sure beforeProjectedGraph and inProjectedGraph get
     * set before calling transition.
     */
    private boolean initialized;
    
    private Set<String> relations;
    private Set<String> closureRelations;
    
    private TracingStateSet<T> tracingSet;
    
    /* Keeps track of the potential counter-example while the stateset has not
     * yet seen the projected graph.
     */
    private HistoryNode preHistory;
    
    public TracingBiRelationalStateSet(TracingStateSet<T> tracingSet) {
        this.initialized = false; 
        
        this.relations = new HashSet<String>();
        this.closureRelations = new HashSet<String>();
        
        this.preHistory = null;
        
        setTracingSet(tracingSet);
    }
    
    protected void setTracingSet(TracingStateSet<T> tracingSet){
        this.tracingSet = tracingSet;
    }
    
    public void addRelation(String relation) {
        relations.add(relation);
    }
    
    public boolean tracksRelation(String relation) {
        return relations.contains(relation);
    }
    
    public void addClosureRelation(String closureRelation) {
        closureRelations.add(closureRelation);
    }
    
    public boolean tracksClosureRelation(String closureRelation) {
        return closureRelations.contains(closureRelation);
    }
    
    @Override
    public void transition(T input) {
        tracingSet.transition(input);
    }
    
    // --relation--> (input)
    @Override
    public void transition(T input, String relation, Set<String> outgoingRelations) {
        SynopticMain synopticMain = SynopticMain.getInstanceWithExistenceCheck();
        
        boolean multipleRelationsEnabled = synopticMain.options.multipleRelations;
        
        if (!multipleRelationsEnabled) {
            throw new IllegalStateException("Multiple relations disabled.");
        }
        
        if (!initialized) {
            throw new IllegalStateException("Tracing set uninitialized.");
        }
        
        if (outgoingRelations == null) {
            throw new NullPointerException();
        }
        
        if (!(tracksRelation(relation) || tracksClosureRelation(relation))) {
            throw new IllegalStateException("Relation: " + relation +
                    " is not being tracked by this StateSet");
        } 
        
        boolean relationsIntersectOutgoing = setsIntersect(relations, outgoingRelations);
        
        if (beforeProjectedGraph) {
            
            extendPreHistory(input);
            
            if (relationsIntersectOutgoing) {
                beforeProjectedGraph = false;
                inProjectedGraph = true;
                setInitialEventTest(input, preHistory);
            }
            
        } else if (inProjectedGraph) {
            
            if (!relations.contains(relation)) {
                inProjectedGraph = false;
                transitionHistoryExtend(input);
            } else {
                transition(input);
            }
            
        } else { // not in projected graph
            
            if (relationsIntersectOutgoing) {
                inProjectedGraph = true;
                transition(input);
            } else {
                transitionHistoryExtend(input);
            }
        }
    }
    
    @Override
    public void transitionEventTest(T input) {
        tracingSet.transitionEventTest(input);
    }
    
    @Override
    public void transitionHistoryExtend(T input) {
        tracingSet.transitionHistoryExtend(input);
    }
    
    @Override
    public void setInitial(T x) {
        tracingSet.setInitial(x);
    }
    
    /**
     * Must check to see if we are already in the projected graph on
     * the initial node.
     */
    @Override
    public void setInitial(T x, Set<String> outgoingRelations) {
        initialized = true;
        
        if (setsIntersect(relations, outgoingRelations)) {
            beforeProjectedGraph = false;
            inProjectedGraph = true;
            setInitial(x);
        } else {
            beforeProjectedGraph = true;
            inProjectedGraph = false;
            setInitialHistoryReset();
            extendPreHistory(x);
        }
    }
    
    @Override
    public void setInitialEventTest(T x, HistoryNode newHistory) {
        tracingSet.setInitialEventTest(x, newHistory);
    }
    
    @Override
    public void setInitialHistoryReset() {
        tracingSet.setInitialHistoryReset();
    }
    
    public void extendPreHistory(T x) {
        if (preHistory == null) {
            preHistory = new HistoryNode(x, null, 1);
        } else {
            preHistory = extendIfNonNull(x, preHistory);
        }
    }
    
    public static boolean setsIntersect(Set<String> a, Set<String> b) {
        for (String aString : a) {
            if (b.contains(aString)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public HistoryNode failpath() {
        return tracingSet.failpath();
    }

    @Override
    public TracingStateSet<T> copy() {
        return tracingSet.copy();
    }

    @Override
    public void mergeWith(TracingStateSet<T> other) {
        tracingSet.mergeWith(other);
    }

    @Override
    public boolean isSubset(TracingStateSet<T> other) {
        return tracingSet.isSubset(other);
    }

    @Override
    public String toString() {
        return tracingSet.toString();
    }
    
    @Override
    public boolean isFail() {
        return !beforeProjectedGraph && super.isFail();
    }
}

    