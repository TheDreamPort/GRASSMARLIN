package core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.EventLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jnetpcap.Pcap;

import core.document.Session;
import core.document.fingerprint.FPDocument;
import core.document.graph.LogicalGraph;
import core.document.graph.LogicalNode;
import core.document.graph.PhysicalGraph;
import core.document.graph.PhysicalNode;
import core.fingerprint.FProcessor;
import core.fingerprint3.Fingerprint;
import iadgov.offlinepcap.PCAPImport;

public class CommandLineInterface {

	private static final Logger logger = LogManager.getLogger(CommandLineInterface.class);


	public static void main(String[] args) {
		String fingerprintDirectoryPath;
		String pcapFilePath;
		
		/*
		 * Ensure libjnetpcap is installed
		 */
		try {
			System.loadLibrary("jnetpcap");
			logger.trace(Pcap.libVersion());
		} catch (UnsatisfiedLinkError e) {
			logger.error("Missing libjnetpcap.jar Ensure LD_LIBRARY_PATH points to a directory with these objects");
			logger.error(e);
			return;
		}

		Options options = new Options();
		options.addOption("f", "fingerprints", true, "Directory containing GRASMARLIN fingerprint xml files");
		options.addOption("p", "pcap", true, "Pcap file to parse");
		CommandLineParser parser = new DefaultParser();

		try {
			CommandLine cmd = parser.parse(options, args);
			fingerprintDirectoryPath = cmd.getOptionValue("f");
			pcapFilePath = cmd.getOptionValue("p");
			
			if(!cmd.hasOption("p") || !cmd.hasOption("f")) {
				logger.error("Both fingerprints and pcap values are required");
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp( "minnow", options, true );
				return;
			}
			logger.trace("Using pcap file: [{}]", pcapFilePath);
			logger.trace("Using fingerprint directory: [{}]", fingerprintDirectoryPath);
			List<String> nodes_xml = grassMarlin(fingerprintDirectoryPath, pcapFilePath);
			for(String xml : nodes_xml) {
				System.out.println(xml);
			}
		} catch (ParseException e) {
			logger.error(e);
		}

	}
	
	private static List<String> grassMarlin(String fingerprintsDirPath, String pcap) {
		List<String> toReturn = new ArrayList<String>();
		FPDocument fpdoc = FPDocument.getInstance();
		logger.trace("Created new FPDocument");
		try {
			DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(fingerprintsDirPath));
			for (Path path : directoryStream) {
				logger.trace("Loading path for fingerprint [{}]", path);
				fpdoc.load(path);
			}
			logger.trace("All fingerprint xml files loaded");
			List<Fingerprint> fingerprints = fpdoc.getAllFingerprints();
			FProcessor processor = new FProcessor(fingerprints);
			PCAPImport pcapImport = new PCAPImport(FileSystems.getDefault().getPath(pcap),
					fingerprints);
			logger.trace("Created new pcap import processor");

			LogicalGraph logicalGraph = new LogicalGraph();
			PhysicalGraph physicalGraph = new PhysicalGraph();
			Session session = new Session(logicalGraph, physicalGraph);
			session.ProcessPcap(pcapImport);
			logger.trace("Session finished processing pcap");
			logger.debug("Found [{}] nodes", logicalGraph.getRawNodeList().size());
			
			logger.trace("Dumping logical node xml");
			for (LogicalNode node : logicalGraph.getRawNodeList()) {
				logger.trace(node.toXml().toString());
				toReturn.add(node.toXml().toString());
			}
			logger.trace("Dumping physical node xml");
			for (PhysicalNode node: physicalGraph.getRawNodeList()) {
				logger.trace(node.toXml().toString());
				toReturn.add(node.toXml().toString());
			}

		} catch (JAXBException je) {
			System.out.println("JAXBException");
			System.out.println(je);
		} catch (IOException ioe) {
			System.out.println("IO Exception");
			System.out.println(ioe);
		}
		return toReturn;
	}
}
