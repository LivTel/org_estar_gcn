// GCNSwiftClient.java
// $Header: /home/cjm/cvs/org_estar_gcn/GCNSwiftClient.java,v 1.2 2005-02-03 10:22:27 cjm Exp $
package org.estar.gcn;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

//import ngat.util.*;
import org.estar.astrometry.*;

/**
 * Client side tester for GCN_Server.
 * New version, writes packet out in 1 go.
 * Creates Swift Alerts
 */
public class GCNSwiftClient extends Thread
{
	/**
	 * Revision control system version id.
	 */
	public final static String RCSID = "$Id: GCNSwiftClient.java,v 1.2 2005-02-03 10:22:27 cjm Exp $";
	/**
	 * Integer used to describe alert type.
	 */
	public final static int ALERT_TYPE_UNKNOWN = 0;
	/**
	 * Integer used to describe alert type.
	 */
	public final static int ALERT_TYPE_BAT = 1;
	/**
	 * Integer used to describe alert type.
	 */
	public final static int ALERT_TYPE_XRT = 2;
	/**
	 * Hostname.
	 */
	String host;
	/**
	 * Port number.
	 */
	int port;
	/**
	 * Sequence number.
	 */
	int seq;
	/**
	 * Number of conenction attempts.
	 */
	int count;
	/**
	 * Delay in milliseconds between connection attempts.
	 */
	long delay;
	/**
	 * Socket to send packet out on.
	 */
	Socket socket = null;
	/**
	 * Data output stream connected to socket.
	 */
	DataOutputStream dataOut = null;
	/**
	 * Data output stream connected to a byte array. Allows whole packet to be sent to the socket in 1 go.
	 * @see #byteOut
	 */
	DataOutputStream byteDataOut = null;
	/**
	 * Underlying byte array output stream.
	 */
	ByteArrayOutputStream byteOut = null;
	/**
	 * Input data stream.
	 */
	DataInputStream in = null;

	/**
	 * Main program method.
	 */
	public static void main(String args[])
	{
		if (args.length == 0)
		{
			usage();
			System.exit(0);
		}
		RA ra          = null;
		Dec dec        = null;
		double error   = 0.0;
		Date   date    = new Date();
		String dstr    = "";
		String tstr    = "";
		int    trignum = 0;
		int    mesgnum = 0;
		String host = null;
		int port = 8010;
		int alertType = ALERT_TYPE_UNKNOWN;
		// parse arguments
		SimpleDateFormat sdf = new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss");
		for(int i = 0; i < args.length; i++)
		{
			if(args[i].equals("-bat"))
			{
				if(alertType == ALERT_TYPE_UNKNOWN)
					alertType = ALERT_TYPE_BAT;
				else
				{
					System.err.println("GCNSwiftClient:BAT alert type specified but alert type already:"+alertType);
					System.exit(1);
				}
			}
			else if(args[i].equals("-date"))
			{
				if((i+1) < args.length)
				{
					try
					{
						date = sdf.parse(args[i+1]);
						System.err.println("Successfully Parsed date to: "+sdf.format(date)+" = "+date.getTime());
					}
					catch(Exception e)
					{
						System.err.println("GCNSwiftClient:Parsing date:"+args[i+1]+
								   " failed:"+e);
						e.printStackTrace(System.err);
						System.exit(2);
					}
					i++;
				}
				else
				{
					System.err.println("GCNSwiftClient:-date requires a date of the format EEE dd MMM yyyy HH:mm:ss.");
					System.exit(3);
				}
			}
			else if(args[i].equals("-dec"))
			{
				if((i+1) < args.length)
				{
					try
					{
						dec = new Dec();
						dec.parseColon(args[i+1]);
					}
					catch(Exception e)
					{
						System.err.println("GCNSwiftClient:Parsing declination:"+args[i+1]+
								   " failed:"+e);
						e.printStackTrace(System.err);
						System.exit(4);
					}
					i++;
				}
				else
				{
					System.err.println("GCNSwiftClient:-dec requires a declination of the form [+|-]DD:MM:SS.ss.");
					System.exit(5);
				}
			}
			else if(args[i].equals("-error"))
			{
				if((i+1) < args.length)
				{
					try
					{
						error = Double.parseDouble(args[i+1]);
					}
					catch(Exception e)
					{
						System.err.println("GCNSwiftClient:Parsing error box:"+args[i+1]+
								   " failed:"+e);
						e.printStackTrace(System.err);
						System.exit(6);
					}
					i++;
				}
				else
				{
					System.err.println("GCNSwiftClient:-error requires a error box radius in decimal arcminutes.");
					System.exit(7);
				}
			}
			else if(args[i].equals("-help"))
			{
				usage();
				System.exit(0);
			}
			else if(args[i].equals("-host"))
			{
				if((i+1) < args.length)
				{
					host = args[i+1];
					i++;
				}
				else
				{
					System.err.println("GCNSwiftClient:-host requires a hostname.");
					System.exit(8);
				}
			}
			else if(args[i].equals("-port"))
			{
				if((i+1) < args.length)
				{
					try
					{
						port = Integer.parseInt(args[i+1]);
					}
					catch(Exception e)
					{
						System.err.println("GCNSwiftClient:Parsing port number:"+args[i+1]+
								   " failed:"+e);
						e.printStackTrace(System.err);
						System.exit(9);
					}
					i++;
				}
				else
				{
					System.err.println("GCNSwiftClient:-port requires an port number.");
					System.exit(10);
				}
			}
			else if(args[i].equals("-mesgno"))
			{
				if((i+1) < args.length)
				{
					try
					{
						mesgnum = Integer.parseInt(args[i+1]);
					}
					catch(Exception e)
					{
						System.err.println("GCNSwiftClient:Parsing message number:"+args[i+1]+
								   " failed:"+e);
						e.printStackTrace(System.err);
						System.exit(11);
					}
					i++;
				}
				else
				{
					System.err.println("GCNSwiftClient:-mesgno requires an integer.");
					System.exit(12);
				}
			}
			else if(args[i].equals("-ra"))
			{
				if((i+1) < args.length)
				{
					try
					{
						ra = new RA();
						ra.parseColon(args[i+1]);
					}
					catch(Exception e)
					{
						System.err.println("GCNSwiftClient:Parsing right ascension:"+args[i+1]+
								   " failed:"+e);
						e.printStackTrace(System.err);
						System.exit(13);
					}
					i++;
				}
				else
				{
					System.err.println("GCNSwiftClient:-ra requires a right ascension of the form [+|-]HH:MM:SS.ss.");
					System.exit(14);
				}
			}
			else if(args[i].equals("-trigno"))
			{
				if((i+1) < args.length)
				{
					try
					{
						trignum = Integer.parseInt(args[i+1]);
					}
					catch(Exception e)
					{
						System.err.println("GCNSwiftClient:Parsing trigger number:"+args[i+1]+
								   " failed:"+e);
						e.printStackTrace(System.err);
						System.exit(15);
					}
					i++;
				}
				else
				{
					System.err.println("GCNSwiftClient:-trigno requires an integer.");
					System.exit(16);
				}
			}
			else if(args[i].equals("-xrt"))
			{
				if(alertType == ALERT_TYPE_UNKNOWN)
					alertType = ALERT_TYPE_XRT;
				else
				{
					System.err.println("GCNSwiftClient:XTT alert type specified but alert type already:"+alertType);
					System.exit(17);
				}
			}
			else
			{
				System.err.println("GCNSwiftClient: Unknown argument:"+args[i]);
				System.exit(18);
			}
		}// end for
		// create client
		if(host == null)
		{
			System.err.println("GCNSwiftClient: No host defined.");
			System.exit(19);
		}
		if(port == 0)
		{
			System.err.println("GCNSwiftClient: No port number defined.");
			System.exit(20);
		}
		GCNSwiftClient client = new GCNSwiftClient(host, port, 2000);

		if(alertType == ALERT_TYPE_BAT)
		{
			if(ra == null)
			{
				System.err.println("GCNSwiftClient: No ra defined.");
				System.exit(21);
			}
			if(dec == null)
			{
				System.err.println("GCNSwiftClient: No dec defined.");
				System.exit(22);
			}
			try
			{
				client.connect();
				client.sendSwiftBATAlert(ra, dec, error, date, trignum, mesgnum);
			} 
			catch (IOException iox)
			{
				System.err.println("Error opening connection to server: "+iox);
				iox.printStackTrace(System.err);
				System.exit(23);
			}
		}
		else
		{
			System.err.println("Alert type: "+alertType+" not supported yet.");
			System.exit(24);
		}
		System.exit(0);
	}

	public static void usage()
	{
		System.err.println("USAGE: java org.estar.gcn.GCNSwiftClient [options]"+
				   "\n where the following options are supported:-"+
				   "\n -host  <host-addr> Host address for the GCN Server."+
				   "\n -port  <port> Port to connect to at the server."+
				   "\n -ra    <ra> RA of a burst source HH:MM:SS.ss."+
				   "\n -dec   <dec> Declination of a burst source [+|-]DD:MM:SS.ss."+
				   "\n -error <size> Radius of the error box (arc minutes)."+
				   "\n -date  <date> Date of the burst (EEE dd MMM yyyy HH:mm:ss)."+
				   "\n -bat          Send a Swift BAT alert."+
				   "\n -trigno <num> Burst trigger number."+
				   "\n -mesgno <num> Message sequence number (for trigno)");
	}

	public GCNSwiftClient(String host, int port, long delay)
	{
		super("GCN_Simulation");
		this.host = host;
		this.port = port;
		this.delay = delay; 
		count = 0;
		seq = 0;
	}

	protected void connect() throws IOException
	{
		count++;
		if (delay > 512000L) {
			System.err.println("Giving up trying to connect after X attempts");
			return;
		}
		if (socket == null) {	
			System.err.println("Re-connect in "+(delay/1000)+" secs.");
			try {Thread.sleep(2000L);} catch (InterruptedException e){}
			System.err.println("Connection attempt: "+count);
			
			socket = new Socket(host, port);
			System.err.println("Opened connection to: "+host+" : "+port);
			dataOut = new DataOutputStream(socket.getOutputStream()); 
			byteOut = new ByteArrayOutputStream(); 
			byteDataOut = new DataOutputStream(byteOut); 
			System.err.println("Opened output stream");
			in = new DataInputStream(socket.getInputStream());
			System.err.println("Opened input stream");
			count = 0;
			delay = 2000L;	    
		}
	}

	class Reader extends Thread
	{
		DataInputStream in;

		Reader(DataInputStream in)
		{
			this.in = in;
		}

		public void run()
		{
			int val = 0;
			while (true)
			{
				try
				{
					val = in.readInt();
					System.err.println("Val: "+val);
				}
				catch (IOException e)
				{
					System.err.println("Error reading: "+e);
				}
			}
		}
	}

	/**
	 * Write the termination char.
	 */
	protected void writeTerm() throws IOException
	{
		byteDataOut.writeByte(0);
		byteDataOut.writeByte(0);
		byteDataOut.writeByte(0);
		byteDataOut.writeByte(10);
	}

	/**
	 * Write the header.
	 */
	protected void writeHdr(int type, int seq) throws IOException
	{
		byteDataOut.writeInt(type);
		byteDataOut.writeInt(seq);
		byteDataOut.writeInt(1); // HOP_CNT 
	}

	/**
	 * Write the SOD for date.
	 */
	protected void writeSod(long time) throws IOException
	{
		Calendar calendar = Calendar.getInstance();
		Date date = new Date(time);
		calendar.setTime(date);
		int sod = (calendar.get(Calendar.HOUR)*86400 + calendar.get(Calendar.MINUTE)*3600 + 
			   calendar.get(Calendar.SECOND))*100;
		byteDataOut.writeInt(sod);
	}

	/**
	 * Write stuffing bytes. 
	 */
	protected void writeStuff(int from, int to) throws IOException
	{
		for (int i = from; i <= to; i++)
		{
			byteDataOut.writeInt(0);
		}
	}

	class SwiftBatAlert
	{
		double error;
		int type;
		int burstRA;
		int burstDec;
		int trigNum;
		int mesgNum;
		Date date;

		SwiftBatAlert(RA ra, Dec dec, double error, Date date, int trigNum, int mesgNum)
		{
			this.burstRA = (int)((ra.toArcSeconds()/3600.0)*10000.0);
			this.burstDec = (int)((dec.toArcSeconds()/3600.0)*10000.0);
			this.error = error;
			this.trigNum = trigNum;
			this.mesgNum = mesgNum;
			this.date = date;
		}
	
		public void write() throws IOException
		{
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(date);
			long now = date.getTime();
			writeHdr(61, seq);// 0, 1, 2
			writeSod(now); // 3
			int tsn = (mesgNum << 16) | trigNum;
			byteDataOut.writeInt(tsn); // 4
			byteDataOut.writeInt(11910+calendar.get(Calendar.DAY_OF_MONTH)); // 5 - burst_tjd
			// writeInt(burstTJD); // 5
			writeSod(now); // 6
			byteDataOut.writeInt(burstRA); // 7
			byteDataOut.writeInt(burstDec); // 8
			int burstFlux = 1000;
			byteDataOut.writeInt(burstFlux); // 9
			int burstIPeak = 500;
			byteDataOut.writeInt(burstIPeak); // 10
			int errorInt = ((int)((error*10000)/60)); // error in arcmin, int in degrees*10000
			byteDataOut.writeInt(errorInt); // 11
			writeStuff(12,17); // 12-17
			int solnStatus = ((1<<0)|(1<<1)|(1<<2)); // point source, GRB, interesting, (rate trigger).
			byteDataOut.writeInt(solnStatus); // 18
			writeStuff(19,38); // 19-38
			writeTerm(); // 39
			// write whole packet out in one go.
			dataOut.write(byteOut.toByteArray());
			dataOut.flush();
		}
	}

	class Imalive
	{
	
		public void writeImalive() throws IOException
		{
			long now = System.currentTimeMillis();
			Date date = new Date(now);
			
			writeHdr(3, seq);
			writeSod(now);
			writeStuff(4, 38);
			writeTerm();
			// write whole packet out in one go.
			dataOut.write(byteOut.toByteArray());
			dataOut.flush();	  
		}
	}
       
	public void sendSwiftBATAlert(RA ra,Dec dec, double error, Date date, int trigNum, int mesgNum) 
		throws IOException
	{
		SwiftBatAlert alert = new SwiftBatAlert(ra, dec, error, date, trigNum, mesgNum);
		alert.write();
	}
}

//
// $Log: not supported by cvs2svn $
//
