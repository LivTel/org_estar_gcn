// GCNLogger.java
package org.estar.gcn;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import org.estar.astrometry.*;
import org.estar.log.*;

/**
 * An instance of this class is used for logging.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class GCNLogger implements ILogger
{
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: GCNLogger.java,v 1.1 2004-06-29 15:26:56 cjm Exp $";
	/**
	 * The writer to log to.
	 */
	protected PrintWriter outputWriter = null;
	/**
	 * The date format to use for date formatting.
	 */
	protected DateFormat dateFormat = null;

	/**
	 * Default constructor. Will end up logging to stderr.
	 * @exception IOException Thrown if init fails.
	 * @see #init
	 */
	public GCNLogger() throws IOException
	{
		super();
		init(null);
	}

	/**
	 * Constructor used when not to log to file. 
	 * @exception IOException Thrown if init fails.
	 * @see #init
	 */
	public GCNLogger(File f) throws IOException
	{
		super();
		init(f);
	}

	/**
	 * Log a message to log file. Pre-pend a timestamp.
	 * @param logMessage The message to log.
	 * @see #outputWriter
	 * @see #dateFormat
	 */
	public void log(String logMessage)
	{
		outputWriter.print(dateFormat.format(new Date()));
		outputWriter.println(":log:"+logMessage);
		outputWriter.flush();
	}

	/**
	 * Log an error message to log file. Pre-pend a timestamp.
	 * @param logMessage The message to log as an error.
	 * @see #outputWriter
	 * @see #dateFormat
	 */
	public void error(String logMessage)
	{
		outputWriter.print(dateFormat.format(new Date()));
		outputWriter.println(":error:"+logMessage);
		outputWriter.flush();
	}

	/**
	 * Log an error message to log file. Pre-pend a timestamp.
	 * @param logMessage The message to log as an error.
	 * @param e An exception to print.
	 * @see #outputWriter
	 * @see #dateFormat
	 */
	public void error(String logMessage,Exception e)
	{
		outputWriter.print(dateFormat.format(new Date()));
		outputWriter.println(":error:"+logMessage);
		e.printStackTrace(outputWriter);
		outputWriter.flush();
	}

	// protected
	/**
	 * Initialise. If filename was set, open writer log file.
	 * Otherwise open outputWriter to stdout.
	 * @param filename The filename to open. Can be null, in which case stdout is used.
	 * @exception IOException Thrown if the file writer fails.
	 * @see #outputWriter
	 * @see #dateFormat
	 */
	protected void init(File filename) throws IOException
	{
		if(filename != null)
		{
			outputWriter = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
		}
		else
		{
			outputWriter = new PrintWriter(new OutputStreamWriter(System.out));
		}
		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
	}
}
//
// $Log: not supported by cvs2svn $
//
