import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

import ngat.util.*;

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
	public final static String RCSID = "$Id: GCNSwiftClient.java,v 1.1 2005-01-10 14:45:21 cjm Exp $";
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
	 *
	 */
	int count;
	/**
	 *
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
			return;
		}

		CommandParser parser = new CommandParser("@");
		try
		{
			parser.parse(args);
		} catch (ParseException px)
		{
			System.err.println("Error parsing command arguments: "+px);
			usage();
			return;
		}
		double ra      = 0.0;
		double dec     = 0.0;
		double error   = 0.0;
		Date   date    = new Date();
		String dstr    = "";
		String tstr    = "";
		int    trignum = 0;
		int    mesgnum = 0;

		ConfigurationProperties map = parser.getMap();
		
		try {
			ra    = map.getDoubleValue("ra", 0.0);
			dec   = map.getDoubleValue("dec", 0.0);
			error = map.getDoubleValue("error", 0.0);
			trignum = map.getIntValue("trigno", -1);
			mesgnum = map.getIntValue("mesgno", -1);
		} catch (Exception e) {}
		
		dstr  = map.getProperty("date", "");
	
		SimpleDateFormat sdf = new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss");
	
		try {
			date = sdf.parse(dstr);
			System.err.println("Successfully Parsed date to: "+sdf.format(date)+" = "+date.getTime());
		} catch (ParseException px) {	   
			date = new Date(); 
			System.err.println("Error parsing date: "+px+" setting to now: "+sdf.format(date));
		}
		String host = map.getProperty("host", "ltccd1");
		int port = 8010;
		try {
			port = map.getIntValue("port", 8010);
		} catch (Exception e) {}
		
		GCNSwiftClient client = new GCNSwiftClient(host, port, 2000);
	
		String bat = map.getProperty("bat", "none");
		String xrt = map.getProperty("xrt", "none");
		if (!bat.equals("none")) {
			try {
				client.connect();
				client.sendSwiftBATAlert(ra, dec, error, date, trignum, mesgnum);
			} catch (IOException iox) {
				System.err.println("Error opening connection to server: "+iox);
				iox.printStackTrace(System.err);
				return;
			}
		}
	}

	public static void usage()
	{
		System.err.println("USAGE: java GCNSwiftClient [options]"+
				   "\n where the following options are supported:-"+
				   "\n @host  <host-addr> Host address for the GCN Server."+
				   "\n @port  <port> Port to connect to at the server."+
				   "\n @ra    <ra> RA of a burst source (decimal degrees)."+
				   "\n @dec   <dec> Declination of a burst source (decimal degrees)."+
				   "\n @error <size> Radius of the error box (arc minutes)."+
				   "\n @date  <date> Date of the burst (EEE dd MMM yyyy HH:mm:ss)."+
				   "\n @bat <id>   Send a Swift BAT alert with GRB-ID = id."+
				   "\n @trigno <num> Burst trigger number."+
				   "\n @mesgno <num> Message sequence number (for trigno)");
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

		SwiftBatAlert(double ra, double dec, double error, Date date, int trigNum, int mesgNum)
		{
			this.burstRA  = (int)(ra*10000.0);
			this.burstDec = (int)(dec*10000.0);
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
       
	public void sendSwiftBATAlert(double ra, double dec, double error, Date date, int trigNum, int mesgNum) 
		throws IOException
	{
		SwiftBatAlert alert = new SwiftBatAlert(ra, dec, error, date, trigNum, mesgNum);
		alert.write();
	}
}

