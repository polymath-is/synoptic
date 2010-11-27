package main;

import invariants.TemporalInvariant;
import invariants.TemporalInvariantSet;
import invariants.fsmcheck.FsmModelChecker;

import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;
import java.lang.Integer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import algorithms.bisim.Bisimulation;

import plume.Option;
import plume.Options;
import plume.OptionGroup;

import model.MessageEvent;
import model.export.GraphVizExporter;
import model.input.GraphBuilder;


public class Main implements Callable<Integer> {
	// public class Main {
    /**
     * The current Synoptic version.
     */
    public static final String versionString = "Synoptic version 0.0.2";


    @OptionGroup("General Options")
    ////////////////////////////////////////////////////
    /**
     * Print the short usage message.  This does not include verbosity
     * or debugging options.
     */
    @Option(value="-h Print short usage message", aliases={"-help"})
    public static boolean help = false;
        
    /**
     * Print the extended usage message.  This includes verbosity and
     * debugging options but not internal options.
     */
    @Option("-H Print extended usage message (includes debugging options)")
    public static boolean allHelp = false;
    
    /**
     * Print the current Synoptic version.
     */
    @Option (value="-v Print program version", aliases={"-version"})
    public static boolean version = false;
    // end option group "General Options"


    @OptionGroup("Execution Options")
    ////////////////////////////////////////////////////
    /**
     * Be quiet, do not print much information.
     */
    @Option (value="-Q Be quiet, do not print much information", aliases={"-quiet"})
    public static boolean reallyQuiet = false;

    
    @OptionGroup("Parser Options")
    ////////////////////////////////////////////////////
    /**
     * Regular expression separator string for determining mining granularity 
     */
    @Option (value="-s Separator regular expression", aliases={"-separator"})
    public static String separator = null;

    /**
     * List of regular expression strings for parsing lines in the input files.
     */
    @Option (value="-r Parser regular expressions", aliases={"-regexps"})
    public static String[] regExps = null;

    /**
     * A substitution expression to partition the lines of the input files.
     */
    @Option (value="-p Partition regular expression", aliases={"-partition"})
    public static String partitionRegExp = null;
    // end option group "Parser Options"
    
    
    @OptionGroup ("Input Options")
    ////////////////////////////////////////////////////
    /**
     * Input log files to run Synoptic on. 
     */
    @Option(value="-f <log-filenames> input log filenames", aliases={"-logfiles"})
    public static String[] logFilenames = null;
    // end option group "Input Options"

    
    @OptionGroup("Output Options")
    ////////////////////////////////////////////////////
    /**
     * Output filename which will contain dot output for the final
     * Synoptic representation
     */
    @Option(value="-o Output filename for dot output", aliases={"--output"})
    public static String outputFilename = null;
    // end option group "Output Options"


    /** 
     * NOTE: this group of options is 'unpublicized', which means that
     * it will not appear in the default usage message
     */
    @OptionGroup (value="Verbosity Options", unpublicized=true)
    ////////////////////////////////////////////////////
    /**
     * Dump the complete list of mined invariants for the set of input files
     * to stdout
     */
    @Option("Dump complete list of mined invariant to stdout")
    public static boolean dumpInvariants = false;
    
    /**
     * Dump the dot representations for intermediate Synoptic steps to
     * file. Each of these files will have a name like:
     * <outputFilename>.<S>.<N> where 'outputFilename' is the filename
     * of the final Synoptic output, 'S' is the stage (either 'r' for
     * refinement, or 'c' for coarsening), and 'N' is the step number
     * within the stage (starting from 1 for each stage).
     */
    @Option("Dump dot files from intermediate Synoptic stages to file")
    public static boolean dumpIntermediateStages = false;
    // end option group "Verbosity Options"


    /** 
     * NOTE: this group of options is 'unpublicized', which means that
     * it will not appear in the default usage message
     */
    @OptionGroup (value="Debugging Options", unpublicized=true)
    ////////////////////////////////////////////////////
    /**
     * Do not perform the coarsening stage in Synoptic, and as final
     * output use the most refined representation
     */
    @Option("Do not perform the coarsening stage")
    public static boolean noCoarsening = false;

    /**
     * Do not perform the refinement (and therefore do not perform
     * coarsening) and do not produce any representation as
     * output. This is useful for just printing the list of mined
     * invariants (using the option 'dumpInvariants' above).
     */
    @Option("Do not perform refinement")
    public static boolean noRefinement = false;
    // end option group "Debugging Options"

    
    /** One line synopsis of usage */
    private static String usage_string
        = "synoptic [options] <logfiles-to-analyze>";

   /**
    * The main method to perform the inference algorithm.  See user
    * documentation an explanation of the options.
    *
    * @param args - command-line options
    */        
    public static void main(String[] args) throws Exception {
        // this directly sets the static member options of the Main class
        Options options = new Options (usage_string, Main.class);
        // TODO: currently not using commandlineArgs for anything..
        String[] commandLineArgs = options.parse_or_usage(args);
        
//        for (int i = 0; i < args.length; i++) {
//        	System.out.println("arg " + i + " : " + args[i]);
//        }

        // Display help for all option groups, including 'unpublicized' ones
        if (allHelp) {
            System.out.println(
                options.usage("General Options",
                              "Execution Options",
                              "Parser Options",
                              "Input Options",
                              "Output Options",
                              "Verbosity Options",
                              "Debugging Options"));
            return;
        }

        // Display help just for the 'publicized' option groups
        if (help) {
            options.print_usage();
            return;
        }

        if (version) {
            System.out.println(Main.versionString);
            return;
        }

        Main mainInstance = new Main();
        Integer ret = mainInstance.call();
		System.out.println("Main.call() returned " + ret.toString());
		System.exit(ret); 
    }

    /***********************************************************/
    
    public Main() {
    	// TODO: can set up graphical state here
    }
    
    /**
     * Prints out a message unless the reallyQuiet option is set 
     * @param msg string to print
     */
    public void VerbosePrint(String msg) {
    	if (! Main.reallyQuiet) {
    		System.out.println(msg);
    	}
    }

    /**
     *  The workhorse method, which uses TraceParser to parse the input files, and calls
     *  the primary Synoptic functions to perform refinement\coarsening and
     *  finally outputs the final graph to the output file (specified as a
     *  command line option).
     */
	@Override
	public Integer call() throws Exception {
		// TODO: is there a way to print all the set Options?
		String debug_msg = 
				"logfiles: " + Main.logFilenames + 
				"\nseparator: " + Main.separator +
				"\nregExps: " + Main.regExps +
				"\npartitionRegExp: " + Main.partitionRegExp; 
		VerbosePrint(debug_msg);

		TraceParser parser = new TraceParser();
		TraceParser.LOG = Logger.getLogger("Parser Logger");
		
		VerbosePrint("Setting up the log file parser.");
		
		if (Main.regExps != null) {
			// The regExps string is assumed to be comma delimited
			// with each regular expression enclosed in double quotes.
			for (String exp : Main.regExps) {
				VerbosePrint("\taddRegex with exp:" + exp);
				parser.addRegex(exp);
			}
			parser.setPartitioner(Main.partitionRegExp != null ? Main.partitionRegExp :
				"\\k<FILE>");
		} else {
			parser.addRegex("^\\s*$(?<SEPCOUNT++>)");
			parser.addRegex("(?<TIME>)?(?<TYPE>.*)");

			parser.setPartitioner(Main.partitionRegExp != null ? Main.partitionRegExp :
				"\\k<SEPCOUNT>\\k<FILE>");
		}

		if (Main.separator != null) {
			parser.addSeparator(Main.separator);
		}
 
		if (Main.logFilenames == null) return 1;
		
		// Parses all the log filenames, constructing the parsedEvents List.
		List<TraceParser.Occurrence> parsedEvents = new ArrayList<TraceParser.Occurrence>();
		
		VerbosePrint("Parsing input files..");
		parser.builder = new GraphBuilder();
		
		// The logFilenames string is assumed to be comma delimited
		// with each filename enclosed in double quotes.
		for (String filename : Main.logFilenames) {
			VerbosePrint("\tcalling parseTraceFile with filename:" + filename);
			parsedEvents.addAll(parser.parseTraceFile(filename, -1));
		}

		// If we parsed any events, then run Synoptic.
		VerbosePrint("Running Synoptic..");
		parser.generateDirectTemporalRelation(parsedEvents, true);
		model.Graph<MessageEvent> inputGraph = ((GraphBuilder) parser.builder).getRawGraph();
		PartitionGraph result = new PartitionGraph(inputGraph, true);
		
		TemporalInvariantSet invariants = result.getInvariants();
		FsmModelChecker<MessageEvent> checker = new FsmModelChecker<MessageEvent>(invariants, inputGraph);
		checker.runToCompletion();
		
		Bisimulation.refinePartitions(result);
		VerbosePrint("Merging..");
		Bisimulation.mergePartitions(result);
		
		TemporalInvariantSet unsatisfied = invariants.getUnsatisfiedInvariants(result);
		
		// If we were given an output filename then export the resulting graph into this filename 
		if (Main.outputFilename != null) {
			VerbosePrint("Exporting final graph..");
			GraphVizExporter exporter = new GraphVizExporter();
			exporter.exportAsDotAndPngFast(Main.outputFilename, result);
		}
		
		return new Integer(0);
	}
}

