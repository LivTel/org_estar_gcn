// GCNDatagramScriptStarterLogger.java
package org.estar.gcn;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import org.estar.astrometry.*;

/**
 * An instance of this class is used for logging.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class GCNDatagramScriptStarterLogger
{
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: GCNDatagramScriptStarterLogger.java,v 1.1 2004-10-19 17:10:06 cjm Exp $";
	/**
	 * A static (class-wide) instance of itself, used for static method calls.
	 */
	protected static GCNDatagramScriptStarterLogger logInstance = null;
	/**
	 * The filename to log to, if any.
	 */
	protected String logFilename = null;
	/**
	 * The writer to log to.
	 */
	protected PrintWriter outputWriter = null;
	/**
	 * The date format to use for date formatting.
	 */
	protected DateFormat dateFormat = null;

	/**
	 * Default constructor.
	 * @param lf The filename to log to.
	 * @exception IOException Thrown if init fails.
	 * @see #logFilename
	 * @see #logInstance
	 * @see #init
	 */
	public GCNDatagramScriptStarterLogger(String lf) throws IOException
	{
		super();
		logFilename = lf;
		logInstance = this;
		init();
	}

	/**
	 * Constructor used when logging to stderr.
	 * @exception IOException Thrown if init fails.
	 * @see #logFilename
	 * @see #logInstance
	 * @see #init
	 */
	public GCNDatagramScriptStarterLogger() throws IOException
	{
		super();
		logFilename = null;
		logInstance = this;
		init();
	}

	/**
	 * Log a message to log file. Pre-pend a timestamp.
	 * @param logMessage The message to log.
	 */
	public void logMessage(String logMessage)
	{
		outputWriter.print(dateFormat.format(new Date()));
		outputWriter.println(":log:"+logMessage);
		outputWriter.flush();
	}

	/**
	 * Log an error message to log file. Pre-pend a timestamp.
	 * @param logMessage The message to log as an error.
	 */
	public void errorMessage(String logMessage)
	{
		outputWriter.print(dateFormat.format(new Date()));
		outputWriter.println(":error:"+logMessage);
		outputWriter.flush();
	}

	/**
	 * Log an error message to log file. Pre-pend a timestamp.
	 * @param logMessage The message to log as an error.
	 * @param e An exception to print.
	 */
	public void errorMessage(String logMessage,Exception e)
	{
		outputWriter.print(dateFormat.format(new Date()));
		outputWriter.println(":error:"+logMessage);
		e.printStackTrace(outputWriter);
		outputWriter.flush();
	}

	// static versions of log/error
	/**
	 * Log a message to log file. Pre-pend a timestamp.
	 * @param logMessage The message to log.
	 * @see #logInstance
	 * @see #logMessage
	 */
	public static void log(String logMessage)
	{
		logInstance.logMessage(logMessage);
	}

	/**
	 * Log an error message to log file. Pre-pend a timestamp.
	 * @param logMessage The message to log as an error.
	 * @see #logInstance
	 * @see #errorMessage
	 */
	public static void error(String logMessage)
	{
		logInstance.errorMessage(logMessage);
	}

	/**
	 * Log an error message to log file. Pre-pend a timestamp.
	 * @param logMessage The message to log as an error.
	 * @param e An exception to print.
	 * @see #logInstance
	 * @see #errorMessage
	 */
	public static void error(String logMessage,Exception e)
	{
		logInstance.errorMessage(logMessage,e);
	}

	// protected
	/**
	 * Initialise. If logFilename open writer log file.
	 * Otherwise open outputWriter to stdout.
	 * @exception IOException Thrown if the file writer fails.
	 * @see #logFilename
	 * @see #outputWriter
	 * @see #dateFormat
	 */
	protected void init() throws IOException
	{
		if (logFilename != null)
		{
			outputWriter = new PrintWriter(new BufferedWriter(new FileWriter(logFilename)));
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
